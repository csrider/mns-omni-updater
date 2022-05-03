package com.messagenetsystems.evolutionupdater.threads;

/** InstallUpdatesThread
 *
 * Thread for monitoring downloaded updates status and installing as necessary.
 *
 * It should run fairly frequently (every minute for ideal time resolution), and keep in mind that
 * it will obey the time window defined in strings.xml (only updating within that window).
 *
 * DEV-NOTES:
 *  To add more packages: Initialize package names, add to allDownloadedUpdatesMatchInstalledPackages
 *
 * Revisions:
 *  2019.02.13      Chris Rider     Created.
 *  2019.04.05      Chris Rider     Refactored.
 *  2019.04.19      Chris Rider     Added flasher lights app.
 *  2019.10.10      Chris Rider     Added omniwatchdogwatcher app.
 */

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.messagenetsystems.evolutionupdater.MainUpdaterService;
import com.messagenetsystems.evolutionupdater.R;
import com.messagenetsystems.evolutionupdater.SystemFunctions;

import java.util.Date;


public class InstallUpdatesThread extends Thread {
    private String TAG = InstallUpdatesThread.class.getSimpleName();

    private Context context;
    private SystemFunctions systemFunctions;
    private int initialWaitPeriodMS;
    private int workCycleRestPeriodMS;
    private String timeWindowOpen_strings, timeWindowClose_strings;
    private String timeWindowOpen_runtime = null;
    private String timeWindowClose_runtime = null;
    private String timeWindowOpen, timeWindowClose;
    public static volatile String packageIsUpdating;

    /** Constructor */
    public InstallUpdatesThread(Context context) {
        // Initialize misc. class instances
        this.context = context;
        systemFunctions = new SystemFunctions(context);

        // Update-process stuff
        packageIsUpdating = null;
        populateTimeWindow_fromRuntimeFile(systemFunctions);
        populateTimeWindow_fromStrings(context);
        populateTimeWindow_toUse();

        // Initialize default rest period values from strings
        try {
            initialWaitPeriodMS = context.getResources().getInteger(R.integer.threadInitialWait_checkForUpdateInstall_seconds) * 1000;
            workCycleRestPeriodMS = context.getResources().getInteger(R.integer.threadInterval_checkForUpdateInstall_seconds) * 1000;
        } catch (Exception e) {
            Log.w(TAG, "Exception caught trying to get thread configuration parameters from strings.xml. Falling back to hard-coded values.\n"+e.getMessage());
            initialWaitPeriodMS = 35 * 1000;        //NOTE: we start this thread 30 seconds after the CheckForUpdatesThread starts, so they are more likely to run in a staggered manner
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

        String thisPackageName;
        int thisPackageDownloadStatus;

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
                // Take a rest before next iteration (to make sure this thread doesn't run full tilt)...
                try {
                    //updateCurrentStatus(Thread.THREAD_STATUS_SLEEPING);
                    Thread.sleep(workCycleRestPeriodMS);
                } catch (InterruptedException e) {
                    Log.e(TAG, TAGG+"Exception caught trying to sleep for interval ("+workCycleRestPeriodMS+"ms). Thread stopping.\n" + e.getMessage());
                    //updateCurrentStatus(Thread.THREAD_STATUS_SLEEP_ERROR);
                    Thread.currentThread().interrupt();
                }
            }

            // Update the main service's last-run Date stamp
            MainUpdaterService.threadLastRunDate_installUpdatesThread = new Date();

            /* START MAIN THREAD-WORK
             * Note: you don't need to exit or break for normal work; instead, only continue (so cleanup and rest can occur at the end of the iteration/cycle) */

            // Indicate that this thread is beginning work tasks (may be useful for outside processes to know this)
            cycleNumber++;
            Log.v(TAG, TAGG+"=======================================(start)");
            Log.v(TAG, TAGG+"BEGINNING WORK CYCLE #"+cycleNumber+"...");

            // Get current time
            currentTime = systemFunctions.getCurrentTime24();

            // Read runtime values (in case they've updated since last iteration)
            if (systemFunctions.getRuntimeFlag_asBoolean("UPDATE_INSTALL_DISALLOW")) {
                Log.i(TAG, TAGG+"Runtime flag is currently set to disallow update installation, so nothing to do this iteration. Reset flag to allow update installations again.");
                systemFunctions.updateNotificationWithText("Runtime flag is disallowing update installations.");
                continue;
            }
            populateTimeWindow_fromRuntimeFile();
            populateTimeWindow_toUse();

            // Check whether we're within our time window for doing updates.
            // If so, then see if we're eligible to actually install updates (all updates are downloaded)
            if (systemFunctions.timeIsWithinTimeWindow(currentTime, timeWindowOpen, timeWindowClose)) {
                Log.d(TAG, TAGG+"Current time ("+currentTime+") is within our time window ("+timeWindowOpen+"-"+timeWindowClose+").");

                // If package is not currently trying to download (hopefully already downloaded and ready to test)...
                thisPackageName = MainUpdaterService.packageName_evolution;
                thisPackageDownloadStatus = CheckForUpdatesThread.downloadStatus_evolution;
                if (thisPackageDownloadStatus != CheckForUpdatesThread.STATUS_DOWNLOAD_INITIATED && thisPackageDownloadStatus != CheckForUpdatesThread.STATUS_DOWNLOAD_QUEUED) {
                    // If downloaded package is different than what's actually installed, update installed package!
                    if (!downloadedPackageMatchesInstalledPackage(thisPackageName)) {
                        Log.i(TAG, TAGG+"Downloaded package ("+thisPackageName+") is different than that installed. Update is warranted!");
                        initiateUpdate(thisPackageName);
                    }
                }

                // If package is not currently trying to download (hopefully already downloaded and ready to test)...
                thisPackageName = MainUpdaterService.packageName_evolutionWatchdog;
                thisPackageDownloadStatus = CheckForUpdatesThread.downloadStatus_evolutionWatchdog;
                if (thisPackageDownloadStatus != CheckForUpdatesThread.STATUS_DOWNLOAD_INITIATED && thisPackageDownloadStatus != CheckForUpdatesThread.STATUS_DOWNLOAD_QUEUED) {
                    // If downloaded package is different than what's actually installed, update installed package!
                    if (!downloadedPackageMatchesInstalledPackage(thisPackageName)) {
                        Log.i(TAG, TAGG+"Downloaded package ("+thisPackageName+") is different than that installed. Update is warranted!");
                        initiateUpdate(thisPackageName);
                    }
                }

                // If package is not currently trying to download (hopefully already downloaded and ready to test)...
                thisPackageName = MainUpdaterService.packageName_evolutionFlasherLights;
                thisPackageDownloadStatus = CheckForUpdatesThread.downloadStatus_evolutionFlasherLights;
                if (thisPackageDownloadStatus != CheckForUpdatesThread.STATUS_DOWNLOAD_INITIATED && thisPackageDownloadStatus != CheckForUpdatesThread.STATUS_DOWNLOAD_QUEUED) {
                    // If downloaded package is different than what's actually installed, update installed package!
                    if (!downloadedPackageMatchesInstalledPackage(thisPackageName)) {
                        Log.i(TAG, TAGG+"Downloaded package ("+thisPackageName+") is different than that installed. Update is warranted!");
                        initiateUpdate(thisPackageName);
                    }
                }

                // If package is not currently trying to download (hopefully already downloaded and ready to test)...
                thisPackageName = MainUpdaterService.packageName_omniWatchdogWatcher;
                thisPackageDownloadStatus = CheckForUpdatesThread.downloadStatus_omniWatchdogWatcher;
                if (thisPackageDownloadStatus != CheckForUpdatesThread.STATUS_DOWNLOAD_INITIATED && thisPackageDownloadStatus != CheckForUpdatesThread.STATUS_DOWNLOAD_QUEUED) {
                    // If downloaded package is different than what's actually installed, update installed package!
                    if (!downloadedPackageMatchesInstalledPackage(thisPackageName)) {
                        Log.i(TAG, TAGG+"Downloaded package ("+thisPackageName+") is different than that installed. Update is warranted!");
                        initiateUpdate(thisPackageName);
                    }
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

    }

    private boolean downloadedPackageMatchesInstalledPackage(String packageName) {
        final String TAGG = "downloadedPackageMatchesInstalledPackage("+packageName+"): ";
        Log.v(TAG, TAGG+"Invoked.");

        boolean ret = false;

        try {
            String checksumDownloaded = systemFunctions.calculateChecksumForLocalFile(MainUpdaterService.localPath+"/"+packageName+".apk");
            String checksumInstalled = systemFunctions.calculateChecksumForLocalFile(systemFunctions.getPathForInstalledAPK(packageName));
            if (String.valueOf(checksumDownloaded).equals(String.valueOf(checksumInstalled))) {
                ret = true;
            }
        } catch (Exception e) {
            Log.w(TAG, TAGG+"Exception caught: "+e.getMessage());
        }

        Log.v(TAG, TAGG+"Returning "+String.valueOf(ret));
        return ret;
    }

    private void initiateUpdate(String packageName) {
        final String TAGG = "initiateUpdate("+packageName+"): ";
        Log.v(TAG, TAGG+"Invoked.");

        if (packageIsUpdating != null) {
            Log.i(TAG, TAGG+"Flag packageIsUpdating is set (\""+String.valueOf(packageIsUpdating)+"\"). Aborting update while another update is ongoing.");
            return;
        }

        // Set flags
        // DEV-NOTE: you must remember to reset these if update fails or completes!
        packageIsUpdating = packageName;

        // Send request to update the package
        broadcastIntentToStartUpdateInstallation(packageName);
    }

    private void broadcastIntentToStartUpdateInstallation(String packageName) {
        final String TAGG = "broadcastIntentToStartUpdateInstallation("+packageName+"): ";
        Log.v(TAG, TAGG+"Invoked.");

        Intent intent = new Intent(context.getResources().getString(R.string.intentAction_triggerOmniUpdater_applyUpdates));
        intent.putExtra("appPackageName", packageName);
        intent.putExtra("notifyWhenDone", "installUpdatesThread");  //inform the AsyncTask to notify us when it's done (so we know whether we can download any other files or not)
        context.sendBroadcast(intent);
    }

    private void populateTimeWindow_fromRuntimeFile() {
        populateTimeWindow_fromRuntimeFile(systemFunctions);
    }
    private void populateTimeWindow_fromRuntimeFile(SystemFunctions systemFunctions) {
        final String TAGG = "populateTimeWindow_fromRuntimeFile: ";
        Log.v(TAG, TAGG+"Invoked.");

        timeWindowOpen_runtime = systemFunctions.getRuntimeFlag("UPDATE_INSTALL_WINDOW_START");
        timeWindowClose_runtime = systemFunctions.getRuntimeFlag("UPDATE_INSTALL_WINDOW_END");
    }

    private void populateTimeWindow_fromStrings(Context context) {
        final String TAGG = "populateTimeWindow_fromStrings: ";
        Log.v(TAG, TAGG+"Invoked.");

        timeWindowOpen_strings = context.getResources().getString(R.string.timeWindow_install_opens);;
        timeWindowClose_strings = context.getResources().getString(R.string.timeWindow_install_closes);
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
}
