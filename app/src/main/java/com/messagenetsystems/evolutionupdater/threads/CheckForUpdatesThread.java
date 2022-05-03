package com.messagenetsystems.evolutionupdater.threads;

/** CheckForUpdatesThread
 *
 * Process that periodically checks the server for updates and initiates downloads as needed.
 * If an update is determined to be available, a broadcast is sent to background-download it.
 * Since we use Android's DownloadManager, we can safely queue up multiple downloads simultaneously.
 *
 * How do we tell if an update is available and worth requesting a download?
 *  - Check server app's MD5 checksum value and compare with the checksum of the currently-downloaded app file.
 *  - NOTE: We don't need to care AT ALL about what's actually installed. Here in this thread, we only download and ensure latest is downloaded. Allow other processes to install/update.
 *
 * Revisions:
 *  2018.10.17      Chris Rider     Created (only running thread that doesn't do anything yet).
 *  2019.02.11-13   Chris Rider     Updated to actually do the downloads.
 *  2019.04.04-05   Chris Rider     Refactored and simplified to fix bugs and use Android DownloadManager (which was updated in BackgroundGetUpdatesReceiver).
 *  2019.04.19      Chris Rider     Only check for update on server if network is up.
 *                                  Adding updater and flasher-lights packages.
 *  2019.10.10      Chris Rider     Added support for omniwatchdogwatcher.
 */

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.messagenetsystems.evolutionupdater.MainUpdaterService;
import com.messagenetsystems.evolutionupdater.R;
import com.messagenetsystems.evolutionupdater.SystemFunctions;

import java.util.Date;

public class CheckForUpdatesThread extends Thread {
    private static String TAG = CheckForUpdatesThread.class.getSimpleName();

    public static final int STATUS_DOWNLOAD_UNKNOWN = 0;
    public static final int STATUS_DOWNLOAD_INITIATED = 1;
    public static final int STATUS_DOWNLOAD_QUEUED = 2;
    //public static final int STATUS_DOWNLOAD_UNDERWAY = 3;     //TODO: not necessary? may not even be possible to know unless we can query DownloadManager somehow
    public static final int STATUS_DOWNLOAD_COMPLETED = 4;

    private Context context;
    private SystemFunctions systemFunctions;
    private int initialWaitPeriodMS;
    private int workCycleRestPeriodMS;
    private String timeWindowOpen_strings, timeWindowClose_strings;
    private String timeWindowOpen_runtime = null;
    private String timeWindowClose_runtime = null;
    private String timeWindowOpen, timeWindowClose;

    /** DEV-NOTE: If adding more apps, continue here (your first stop should have been MainUpdaterService)...
     * Then add corresponding logic to constructor and run() routines below.
     * After that, update BackgroundGetUpdatesReceiver and DownloadManagerCompletedReceiver classes. */
    public static volatile int downloadStatus_evolution;
    public static volatile int downloadStatus_evolutionWatchdog;
    public static volatile int downloadStatus_evolutionUpdater;
    public static volatile int downloadStatus_evolutionFlasherLights;
    public static volatile int downloadStatus_omniWatchdogWatcher;

    /** Constructor */
    public CheckForUpdatesThread(Context context) {

        // Initialize misc. class instances
        this.context = context;
        systemFunctions = new SystemFunctions(context);

        // Download process stuff
        populateTimeWindow_fromRuntimeFile(systemFunctions);
        populateTimeWindow_fromStrings(context);
        populateTimeWindow_toUse();

        // Initialize default rest period values from strings
        try {
            initialWaitPeriodMS = context.getResources().getInteger(R.integer.threadInitialWait_checkForUpdateDownload_seconds) * 1000;
            workCycleRestPeriodMS = context.getResources().getInteger(R.integer.threadInterval_checkForUpdateDownload_seconds) * 1000;
        } catch (Exception e) {
            Log.w(TAG, "Exception caught trying to get thread configuration parameters from strings.xml. Falling back to hard-coded values.\n"+e.getMessage());
            initialWaitPeriodMS = 5 * 1000;
            workCycleRestPeriodMS = 60 * 1000;
        }
    }

    /** Main runnable routine (executes once whenever the initialized thread is commanded to start running) */
    @Override
    public void run() {
        final String TAGG = "run: ";
        long cycleNumber = 0;
        Log.d(TAG, TAGG+"Invoked.");

        long pid = Thread.currentThread().getId();
        Log.i(TAG, TAGG+"Thread now running with process ID = "+ pid);

        String currentTime;

        // As long as our thread is supposed to be running, start doing work-cycles until it's been flagged to interrupt (rest period happens at the end of the cycle)...
        while (!Thread.currentThread().isInterrupted()) {

            // Take a rest before beginning our first iteration (to give time for the things we're monitoring to potentially come online)...
            if (cycleNumber == 0) {
                try {
                    //updateCurrentStatus(Thread.THREAD_STATUS_SLEEPING);
                    java.lang.Thread.sleep(initialWaitPeriodMS);
                } catch (InterruptedException e) {
                    //Log.e(TAG, TAGG + "Exception caught trying to sleep for first-run (" + workCycleRestPeriodMS + "ms). Broadcasting this error status and stopping.\n" + e.getMessage());
                    //updateCurrentStatus(Thread.THREAD_STATUS_SLEEP_ERROR);
                    Log.e(TAG, TAGG + "Exception caught trying to sleep for first-run (" + workCycleRestPeriodMS + "ms).\n" + e.getMessage());
                    Thread.currentThread().interrupt();
                }
            } else {
                // Take a rest before continuing with this iteration (to make sure this thread doesn't run full tilt)...
                try {
                    //updateCurrentStatus(Thread.THREAD_STATUS_SLEEPING);
                    Thread.sleep(workCycleRestPeriodMS);
                } catch (InterruptedException e) {
                    Log.e(TAG, TAGG + "Exception caught trying to sleep for interval (" + workCycleRestPeriodMS + "ms). Thread stopping.\n" + e.getMessage());
                    //updateCurrentStatus(Thread.THREAD_STATUS_SLEEP_ERROR);
                    Thread.currentThread().interrupt();
                }
            }

            // Update the main service's last-run Date stamp
            MainUpdaterService.threadLastRunDate_checkForUpdatesThread = new Date();

            /* START MAIN THREAD-WORK
             * Note: you don't need to exit or break for normal work; instead, only continue (so cleanup and rest can occur at the end of the iteration/cycle) */

            // Indicate that this thread is beginning work tasks (may be useful for outside processes to know this)
            cycleNumber++;
            Log.v(TAG, TAGG+"=======================================(start)");
            Log.v(TAG, TAGG+"BEGINNING WORK CYCLE #"+cycleNumber+"...");

            // Get current time
            currentTime = systemFunctions.getCurrentTime24();

            // Read runtime values (in case they've updated since last iteration)
            if (systemFunctions.getRuntimeFlag_asBoolean("UPDATE_DOWNLOAD_DISALLOW")) {
                Log.i(TAG, TAGG+"Runtime flag is currently set to disallow update download, so nothing to do this iteration. Reset flag to allow update downloads again.");
                systemFunctions.updateNotificationWithText("Runtime flag is disallowing update downloads.");
                continue;
            }
            populateTimeWindow_fromRuntimeFile();
            populateTimeWindow_toUse();

            // Check whether we're within our time window for downloading updates.
            if (systemFunctions.timeIsWithinTimeWindow(currentTime, timeWindowOpen, timeWindowClose)) {
                Log.d(TAG, TAGG + "Current time (" + currentTime + ") is within our time window (" + timeWindowOpen + "-" + timeWindowClose + ").");

                // First, determine whether the network is available
                // (there's no sense even trying to check with server if there's no network connection)
                if (!systemFunctions.isNetworkAvailable()) {
                    Log.w(TAG, TAGG+"Network is not available, skipping update checks this time.");
                    continue;
                }

                // Compare local-downloaded-APK and server-APK checksum values
                // (this is how we know if the server has a different version than what is downloaded)
                // Note: We only do the comparison if the file is not in the middle of trying to download!
                //...

                // If package is not currently trying to download...
                if (downloadStatus_evolution != STATUS_DOWNLOAD_INITIATED && downloadStatus_evolution != STATUS_DOWNLOAD_QUEUED) {
                    // If downloaded package is different than what's on the server, download it!
                    if (isServerAppChecksumDifferentThanAppDownloaded(MainUpdaterService.packageName_evolution)) {
                        downloadStatus_evolution = STATUS_DOWNLOAD_INITIATED;
                        initiateDownload(MainUpdaterService.packageName_evolution);
                    }
                } else {
                    Log.d(TAG, TAGG + MainUpdaterService.packageName_evolution + " is already trying to download, skipping checksum difference test.");
                }

                // If package is not currently trying to download...
                if (downloadStatus_evolutionWatchdog != STATUS_DOWNLOAD_INITIATED && downloadStatus_evolutionWatchdog != STATUS_DOWNLOAD_QUEUED) {
                    // If downloaded package is different than what's on the server, download it!
                    if (isServerAppChecksumDifferentThanAppDownloaded(MainUpdaterService.packageName_evolutionWatchdog)) {
                        downloadStatus_evolutionWatchdog = STATUS_DOWNLOAD_INITIATED;
                        initiateDownload(MainUpdaterService.packageName_evolutionWatchdog);
                    }
                } else {
                    Log.d(TAG, TAGG + MainUpdaterService.packageName_evolutionWatchdog + " is already trying to download, skipping checksum difference test.");
                }

                // If package is not currently trying to download...
                if (downloadStatus_evolutionUpdater != STATUS_DOWNLOAD_INITIATED && downloadStatus_evolutionUpdater != STATUS_DOWNLOAD_QUEUED) {
                    // If downloaded package is different than what's on the server, download it!
                    if (isServerAppChecksumDifferentThanAppDownloaded(MainUpdaterService.packageName_evolutionUpdater)) {
                        downloadStatus_evolutionUpdater = STATUS_DOWNLOAD_INITIATED;
                        initiateDownload(MainUpdaterService.packageName_evolutionUpdater);
                    }
                } else {
                    Log.d(TAG, TAGG + MainUpdaterService.packageName_evolutionUpdater + " is already trying to download, skipping checksum difference test.");
                }

                // If package is not currently trying to download...
                if (downloadStatus_evolutionFlasherLights != STATUS_DOWNLOAD_INITIATED && downloadStatus_evolutionFlasherLights != STATUS_DOWNLOAD_QUEUED) {
                    // If downloaded package is different than what's on the server, download it!
                    if (isServerAppChecksumDifferentThanAppDownloaded(MainUpdaterService.packageName_evolutionFlasherLights)) {
                        downloadStatus_evolutionFlasherLights = STATUS_DOWNLOAD_INITIATED;
                        initiateDownload(MainUpdaterService.packageName_evolutionFlasherLights);
                    }
                } else {
                    Log.d(TAG, TAGG + MainUpdaterService.packageName_evolutionFlasherLights + " is already trying to download, skipping checksum difference test.");
                }

                // If package is not currently trying to download...
                if (downloadStatus_omniWatchdogWatcher != STATUS_DOWNLOAD_INITIATED && downloadStatus_omniWatchdogWatcher != STATUS_DOWNLOAD_QUEUED) {
                    // If downloaded package is different than what's on the server, download it!
                    if (isServerAppChecksumDifferentThanAppDownloaded(MainUpdaterService.packageName_omniWatchdogWatcher)) {
                        downloadStatus_omniWatchdogWatcher = STATUS_DOWNLOAD_INITIATED;
                        initiateDownload(MainUpdaterService.packageName_omniWatchdogWatcher);
                    }
                } else {
                    Log.d(TAG, TAGG + MainUpdaterService.packageName_evolutionFlasherLights + " is already trying to download, skipping checksum difference test.");
                }

            } else {
                Log.d(TAG, TAGG+"Current time ("+currentTime+") is outside of our time window ("+timeWindowOpen+"-"+timeWindowClose+"). Nothing to do here.");
            }

            /* END MAIN THREAD-WORK */
        }

        // Loop above has exited (therefore, thread is stopping)
        cleanup();
    }

    /** Cleanup */
    private void cleanup() {
        try {
            this.context = null;
            systemFunctions.cleanup();
        } catch (Exception e) {
            Log.w(TAG, "cleanup: Exception caught: "+e.getMessage());
        }
    }

    /** Compare server's package's checksum with that which is currently downloaded on sdcard.
     * We can use this to avoid downloading the same thing over and over again before it's actually installed.
     * NOTE: This reads server-file's checksum value directly from MD5 file via HTTP. No MD5 download is necessary.*/
    private boolean isServerAppChecksumDifferentThanAppDownloaded(String packageName) {
        final String TAGG = "isServerAppChecksumDifferentThanAppDownloaded("+packageName+"): ";
        Log.v(TAG, TAGG+"Invoked.");

        boolean ret;
        String downloadedPackageApkFile;
        String checksumServer, checksumLocal;

        downloadedPackageApkFile = MainUpdaterService.localPath + "/" + packageName + ".apk";            //something like "/sdcard/com.messagenetsystems.evolution.apk"

        checksumLocal = systemFunctions.calculateChecksumForLocalFile(downloadedPackageApkFile);
        checksumServer = systemFunctions.readTextFromServerFile("http://"+MainUpdaterService.serverIP+"/"+MainUpdaterService.serverPath+"/"+packageName+".md5");

        if (checksumServer == null
                || String.valueOf(checksumServer).isEmpty()
                || String.valueOf(checksumServer).equals("")) {
            Log.i(TAG, TAGG + "Server app's checksum can not be determined.");
            ret = false;
        } else if (String.valueOf(checksumServer).equals(String.valueOf(checksumLocal))) {
            Log.d(TAG, TAGG + "Server app's checksum for "+packageName+" (" + checksumServer + ") is same as local downloaded app (" + checksumLocal + ").");
            ret = false;
        } else {
            Log.d(TAG, TAGG + "Server app's checksum for "+packageName+" (" + checksumServer + ") is different than local downloaded app (" + checksumLocal + ").");
            ret = true;
        }

        Log.v(TAG, TAGG+"Returning "+String.valueOf(ret));
        return ret;
    }

    private void initiateDownload(String packageName) {
        final String TAGG = "initiateDownload("+packageName+"): ";
        Log.v(TAG, TAGG+"Invoked.");

        // Set flags in MainUpdaterService
        // DEV-NOTE: you must remember to reset these if download fails to start or completes!
        MainUpdaterService.flag_isDownloading = true;
        MainUpdaterService.flag_isDownloadingPackage = packageName;
        MainUpdaterService.isDownloadingUpdates = true;

        // Send request to background-download the package
        // Our job here in this thread is done for now!
        broadcastIntentToBackgroundDownload(packageName);
    }

    private void broadcastIntentToBackgroundDownload(String packageName) {
        final String TAGG = "broadcastIntentToBackgroundDownload("+packageName+"): ";
        Log.v(TAG, TAGG+"Invoked.");

        Intent intent = new Intent(context.getResources().getString(R.string.intentAction_triggerOmniUpdater_getUpdatesBackground));
        intent.putExtra("appPackageName", packageName);
        intent.putExtra("notifyWhenDone", "checkForUpdatesThread");  //inform the AsyncTask to notify us when it's done (so we know whether we can download any other files or not)
        context.sendBroadcast(intent);
    }

    private void populateTimeWindow_fromRuntimeFile() {
        populateTimeWindow_fromRuntimeFile(systemFunctions);
    }
    private void populateTimeWindow_fromRuntimeFile(SystemFunctions systemFunctions) {
        final String TAGG = "populateTimeWindow_fromRuntimeFile: ";
        Log.v(TAG, TAGG+"Invoked.");

        timeWindowOpen_runtime = systemFunctions.getRuntimeFlag("UPDATE_DOWNLOAD_WINDOW_START");
        timeWindowClose_runtime = systemFunctions.getRuntimeFlag("UPDATE_DOWNLOAD_WINDOW_END");
    }

    private void populateTimeWindow_fromStrings(Context context) {
        final String TAGG = "populateTimeWindow_fromStrings: ";
        Log.v(TAG, TAGG+"Invoked.");

        timeWindowOpen_strings = context.getResources().getString(R.string.timeWindow_download_opens);
        timeWindowClose_strings = context.getResources().getString(R.string.timeWindow_download_closes);
    }

    private void populateTimeWindow_toUse() {
        final String TAGG = "populateTimeWindow_toUse: ";
        Log.v(TAG, TAGG+"Invoked.");

        if (timeWindowOpen_runtime != null) {
            Log.v(TAG, "Value for runtime flag for window-start available.");
            timeWindowOpen = timeWindowOpen_runtime;
        } else {
            Log.v(TAG, "Value for runtime flag for window-start unavailable. Using strings.xml value instead.");
            timeWindowOpen = timeWindowOpen_strings;
        }
        Log.d(TAG, TAGG+"Setting value \""+timeWindowOpen+"\" for window-open.");

        if (timeWindowClose_runtime != null) {
            Log.v(TAG, "Value for runtime flag for window-end available.");
            timeWindowClose = timeWindowClose_runtime;
        } else {
            Log.v(TAG, "Value for runtime flag for window-end unavailable. Using strings.xml value instead.");
            timeWindowClose = timeWindowClose_strings;
        }
        Log.d(TAG, TAGG+"Setting value \""+timeWindowClose+"\" for window-close.");
    }

    public static void setPackageDownloadStatus(String packageName, int status) {
        final String TAGG = "setPackageDownloadStatus(\""+packageName+"\", "+ String.valueOf(status)+"): ";
        Log.v(TAG, TAGG+"Invoked.");

        /** DEV-NOTE: If adding more apps, continue here (you should have visited MainUpdaterService and CheckForUpdatesThread prior to this). */

        if (packageName.equals(MainUpdaterService.packageName_evolution)) {
            CheckForUpdatesThread.downloadStatus_evolution = status;
        }

        if (packageName.equals(MainUpdaterService.packageName_evolutionWatchdog)) {
            CheckForUpdatesThread.downloadStatus_evolutionWatchdog = status;
        }

        if (packageName.equals(MainUpdaterService.packageName_evolutionUpdater)) {
            CheckForUpdatesThread.downloadStatus_evolutionUpdater = status;
        }

        if (packageName.equals(MainUpdaterService.packageName_evolutionFlasherLights)) {
            CheckForUpdatesThread.downloadStatus_evolutionFlasherLights = status;
        }

        if (packageName.equals(MainUpdaterService.packageName_omniWatchdogWatcher)) {
            CheckForUpdatesThread.downloadStatus_omniWatchdogWatcher = status;
        }
    }
}
