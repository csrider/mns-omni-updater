package com.messagenetsystems.evolutionupdater;

/** MainUpdaterService
 * This is intended to be the main service for the Evolution Updater app.
 * It shall handle all software updates (downloading, installing, etc.).
 * We had to make this a separate app because it's problematic to update a running app and have it begin again after the update.
 *
 * Revisions:
 *  2018.10.17      Chris Rider     Created.
 *  2019.02.13      Chris Rider     Added InstallUpdatesThread.
 *  2019.04.19      Chris Rider     Adding updater and flasher-lights packages.
 *  2019.10.10      Chris Rider     Added support for omniwatchdogwatcher (as well as forgotten evolutionflasherlights stuff).
 */

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.messagenetsystems.evolutionupdater.receivers.BackgroundGetUpdatesReceiver;
import com.messagenetsystems.evolutionupdater.threads.CheckForUpdatesThread;
import com.messagenetsystems.evolutionupdater.threads.MonitorThreadsThread;
import com.messagenetsystems.evolutionupdater.threads.ServerSocketThread;
import com.messagenetsystems.evolutionupdater.threads.InstallUpdatesThread;

import java.util.Date;

public class MainUpdaterService extends Service {
    private static final String TAG = MainUpdaterService.class.getSimpleName();

    private Context appContext;

    private int appPID;

    private NotificationCompat.Builder mNotifBuilder;

    private SystemFunctions systemFunctions;
    public static String serverIP;
    public static String serverPath;
    public static String localPath;

    /** DEV-NOTE: Start here when adding packages to update.
     * Then follow the all found-usages to know where else to update logic (you may want to start in CheckForUpdatesThread and InstallUpdatesThread). */
    public static String packageName_evolution;
    public static String packageName_evolutionWatchdog;
    public static String packageName_evolutionUpdater;
    public static String packageName_evolutionFlasherLights;
    public static String packageName_omniWatchdogWatcher;

    protected static Thread checkForUpdatesThread;
    protected Thread serverSocketThread;
    protected Thread installUpdatesThread;
    protected Thread monitorThreadsThread;

    public static volatile Date threadLastRunDate_checkForUpdatesThread = null;                     //value set/updated by that thread's run routine, and checked by MonitorThreadsThread
    public static volatile Date threadLastRunDate_installUpdatesThread = null;                      //value set/updated by that thread's run routine, and checked by MonitorThreadsThread

    public static String serverChecksumRequestStatus = "";

    public static Handler msgHandler;

    private String intentFilter_backgroundGetUpdates;
    private BroadcastReceiver backgroundGetUpdatesReceiver;

    public static volatile boolean flag_isDownloading = false;          //intended as a raw, actually-downloading flag, set/used by DownloadFileInBackground and ServerSocketThread)
    public static volatile String flag_isDownloadingPackage = null;     //improved version of flag_isDownloading, gives us the knowledge of what we're downloading (set/used by DownloadFileInBackground and CheckForUpdatesThread)
    //public static volatile long androidDownloadManager_queueID = -1;
    public static volatile int currentDownloadProgress = 0;
    public static volatile long getTimeOfLastDownloadProgressUpdate_raw = 0;

    public static volatile boolean flag_isDownloading_evolution = false;
    public static volatile boolean flag_isDownloading_evolutionupdater = false;
    public static volatile boolean flag_isDownloading_evolutionwatchdog = false;
    public static volatile boolean flag_isDownloading_evolutionFlasherLights = false;
    public static volatile boolean flag_isDownloading_omniWatchdogWatcher = false;

    public static volatile int downloadRetriesAttempted = 0;

    public static volatile boolean isDownloadingUpdates = false;    //flag for overall is downloading -set to true when beginning to download an update, and reset to false when you're sure there are no more to download

    /** Constructor */
    public MainUpdaterService() {
    }

    /***********************************************************************************************
     * Service methods...
     */

    /** Service onCreate handler **/
    @Override
    public void onCreate() {
        super.onCreate();
        final String TAGG = "onCreate: ";
        Log.d(TAG, TAGG+"Invoked.");

        initialize();
    }

    /** Service onStart handler **/
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        final String TAGG = "onStartCommand: ";
        Log.d(TAG, TAGG+"Invoked.");

        // Running in foreground better ensures Android won't kill us
        startForeground(0, null);

        // Get the system process ID for our running service and include it in the TAG for logging
        try {
            final int existingPID = appPID;
            appPID = android.os.Process.myPid();
            Log.i(TAG, TAGG+"Now running with process ID = "+ appPID);

            systemFunctions.updateNotificationBuilderObjectText(mNotifBuilder, getResources().getString(R.string.notification_text_runningPID) + appPID);

            if (appPID == existingPID) {
                Log.w(TAG, TAGG+"Service is already running (as #"+existingPID+"), but initialize has executed again (as #"+ appPID +").");
            }
        } catch (Exception e) {
            Log.w(TAG, TAGG+"Exception caught trying to get process ID: "+e.getMessage());
        }

        // Show the notification bar icon
        if (mNotifBuilder != null) {
            systemFunctions.showNotif(mNotifBuilder);
        } else {
            Log.w(TAG, TAGG+"Notification builder is null, unable to show notification.");
        }

        // Start all threads
        startAllThreads();

        // Register all broadcast receivers
        registerReceivers();

        // Ensure this service is very hard to kill and that it even restarts if needed
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        final String TAGG = "onDestroy: ";
        Log.d(TAG, TAGG+"Invoked.");

        cleanup();

        super.onDestroy();
    }

    /** Service onBind handler **/
    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }


    /***********************************************************************************************
     * Class methods...
     */

    /** Initialize things **/
    public void initialize() {
        final String TAGG = "initialize(): ";
        Log.d(TAG, TAGG+"Running.");

        appPID = 0;
        mNotifBuilder = null;

        appContext = getApplicationContext();

        // Setup our primary tool kit
        systemFunctions = new SystemFunctions(appContext);

        // Setup the initial notification
        try {
            String notifText = getResources().getString(R.string.notification_text_runningPID) + appPID;
            mNotifBuilder = systemFunctions.createNotificationBuilderObject(notifText);
        } catch (Exception e) {
            Log.w(TAG, TAGG+"Exception caught trying to setup notification: "+e.getMessage());
        }

        // Initialize package names
        packageName_evolution = appContext.getResources().getString(R.string.appPackageName_evolution);
        packageName_evolutionWatchdog = appContext.getResources().getString(R.string.appPackageName_evolutionWatchdog);
        packageName_evolutionUpdater = appContext.getResources().getString(R.string.appPackageName_evolutionUpdater);
        packageName_evolutionFlasherLights = appContext.getResources().getString(R.string.appPackageName_evolutionFlasherLights);
        packageName_omniWatchdogWatcher = appContext.getResources().getString(R.string.appPackageName_omniWatchdogWatcher);

        // Initialize vars
        serverIP = systemFunctions.getSharedPrefsServerIPv4_su_evolution();
        serverPath = appContext.getResources().getString(R.string.updatePackageServerPath);
        localPath = appContext.getResources().getString(R.string.updateFileDownloadPath);

        // Initialize threads
        checkForUpdatesThread = new CheckForUpdatesThread(appContext);
        serverSocketThread = new ServerSocketThread(appContext);
        installUpdatesThread = new InstallUpdatesThread(appContext);
        monitorThreadsThread = new MonitorThreadsThread(appContext);

        msgHandler = new InternalMessageHandler(appContext);

        // Initialize intent filters
        intentFilter_backgroundGetUpdates = appContext.getResources().getString(R.string.intentAction_triggerOmniUpdater_getUpdatesBackground);

        // Instantiate broadcast receivers
        backgroundGetUpdatesReceiver = new BackgroundGetUpdatesReceiver(appContext);

    }

    private void cleanup() {
        // Shutdown threads
        if (checkForUpdatesThread != null)
            checkForUpdatesThread.interrupt();
        if (serverSocketThread != null)
            serverSocketThread.interrupt();
        if (installUpdatesThread != null)
            installUpdatesThread.interrupt();
        if (monitorThreadsThread != null)
            monitorThreadsThread.interrupt();

        // Unregister receivers
        if (backgroundGetUpdatesReceiver != null) {
            appContext.unregisterReceiver(backgroundGetUpdatesReceiver);
            backgroundGetUpdatesReceiver = null;
        }

        // Explicitly mark things for garbage collection (do this very last!)
        appContext = null;
        mNotifBuilder = null;
        checkForUpdatesThread = null;
        serverSocketThread = null;
        monitorThreadsThread = null;

        if (systemFunctions != null) {
            systemFunctions.cleanup();
            systemFunctions = null;
        }

        msgHandler = null;
    }

    public void startAllThreads() {
        final String TAGG = "startAllThreads: ";
        Log.d(TAG, TAGG+"Running.");
        startThread_checkForUpdatesThread();
        startThread_serverSocketThread();
        startThread_installUpdatesThread();
        startThread_monitorThreadsThread();
    }
    public static void startThread_checkForUpdatesThread() {
        final String TAGG = "startThread_checkForUpdatesThread: ";
        Log.d(TAG, TAGG+"Starting a CheckForUpdatesThread instance...");
        checkForUpdatesThread.start();
    }
    public void startThread_serverSocketThread() {
        final String TAGG = "startThread_serverSocketThread: ";
        Log.d(TAG, TAGG+"Starting a ServerSocketThread instance...");
        serverSocketThread.start();
    }
    public void startThread_installUpdatesThread() {
        final String TAGG = "startThread_installUpdatesThread: ";
        Log.d(TAG, TAGG+"Starting an InstallUpdatesThread instance...");
        installUpdatesThread.start();
    }
    public void startThread_monitorThreadsThread() {
        final String TAGG = "startThread_monitorThreadsThread: ";
        Log.d(TAG, TAGG+"Starting a MonitorThreadsThread instance...");
        monitorThreadsThread.start();
    }

    public void registerReceivers() {
        final String TAGG = "registerReceivers: ";
        Log.d(TAG, TAGG+"Running.");

        appContext.registerReceiver(backgroundGetUpdatesReceiver, new IntentFilter(intentFilter_backgroundGetUpdates));
    }

    /***********************************************************************************************
     * Subclass for communication to this service via Messages and Bundles.
     * This can be used to invoke methods from other scopes (for example, to update notification bar with download progress).
     *
     * Revisions:
     *  2018.10.23  Chris Rider     Created (prototyped from watchdog's InterThreadMessageHandler). No real need to use it yet, though.
     *  2019.04.22  Chris Rider     Enabled and implemented for MonitorThreadsThread.
     */
    private static class InternalMessageHandler extends Handler {
        String TAGG = "InternalMessageHandler: ";

        String keyName_command, keyName_threadName;
        String cmd_startThread;

        // Constructor
        InternalMessageHandler(Context context) {
            // Initialize stuff
            this.keyName_command = context.getResources().getString(R.string.bundle_keyname_command);
            this.cmd_startThread = context.getResources().getString(R.string.bundle_command_startThread);
            this.keyName_threadName = context.getResources().getString(R.string.bundle_keyname_threadName);
        }

        // This gets invoked whenever a Message is vectored in
        @Override
        public void handleMessage(Message msg) {
            final String TAGG = this.TAGG + "handleMessage: ";
            Bundle msgObj = (Bundle) msg.obj;
            final String command = msgObj.getString(keyName_command, null);                             //get the command passed to us in the bundle

            // Add cases for commands here!
            if (command.equals(cmd_startThread)) {
                //get thread-name to start (value of the command-key name/value pair sent in bundle) and execute
                String threadNameToStart = msgObj.getString(keyName_threadName);
                if (threadNameToStart == null || threadNameToStart.isEmpty()) {
                    Log.w(TAG, TAGG+"Invalid thread name to start.");
                } else if (MonitorThreadsThread.THREADNAME_checkForUpdatesThread.equals(String.valueOf(threadNameToStart))) {
                    Log.d(TAG, TAGG+"Message received to request start of thread, \""+MonitorThreadsThread.THREADNAME_checkForUpdatesThread+"\".");
                    startThread_checkForUpdatesThread();
                }
            } else {
                Log.w(TAG, TAGG+"Unhandled command from Message: \""+command+"\".");
            }
        }
    }
}
