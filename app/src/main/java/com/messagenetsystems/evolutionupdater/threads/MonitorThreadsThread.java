package com.messagenetsystems.evolutionupdater.threads;

/** MonitorThreadsThread
 *
 * Process to keep an eye on running threads.
 * NOTE: You should be careful to configure this thread to start well after the threads it monitors startup!
 *
 * Revisions:
 *  2019.04.22      Chris Rider     Created.
 */

import android.content.Context;
import android.os.Bundle;
import android.os.Message;
import android.util.Log;

import com.messagenetsystems.evolutionupdater.MainUpdaterService;
import com.messagenetsystems.evolutionupdater.R;
import com.messagenetsystems.evolutionupdater.SystemFunctions;

import java.util.Date;

public class MonitorThreadsThread extends Thread {
    private static String TAG = MonitorThreadsThread.class.getSimpleName();

    private SystemFunctions systemFunctions;
    private int initialWaitPeriodMS;
    private int workCycleRestPeriodMS;

    // Names of the thread instances used in MainUpdaterService...
    public static final String THREADNAME_checkForUpdatesThread = "checkForUpdatesThread";
    public static final String THREADNAME_installUpdatesThread = "installUpdatesThread";

    private int workCycleRestPeriodMS_CheckForUpdatesThread;
    private int workCycleRestPeriodMS_InstallUpdatesThread;

    private String key_command;
    private String cmd_startThread;
    private String key_threadName;

    /** Constructor */
    public MonitorThreadsThread(Context context) {

        // Initialize misc. class instances
        systemFunctions = new SystemFunctions(context);

        // Initialize strings
        key_command = context.getResources().getString(R.string.bundle_keyname_command);
        cmd_startThread = context.getResources().getString(R.string.bundle_command_startThread);
        key_threadName = context.getResources().getString(R.string.bundle_keyname_threadName);

        // Initialize default rest period values from strings (for this thread)
        try {
            initialWaitPeriodMS = context.getResources().getInteger(R.integer.threadInitialWait_monitorThreads_seconds) * 1000;
            workCycleRestPeriodMS = context.getResources().getInteger(R.integer.threadInterval_monitorThreads_seconds) * 1000;
        } catch (Exception e) {
            Log.w(TAG, "Exception caught trying to get thread configuration parameters from strings.xml. Falling back to hard-coded values.\n"+e.getMessage());
            initialWaitPeriodMS = 50 * 1000;
            workCycleRestPeriodMS = 60 * 1000;
        }

        // Initialize default rest period values from strings (for CheckForUpdatesThread)
        try {
            workCycleRestPeriodMS_CheckForUpdatesThread = context.getResources().getInteger(R.integer.threadInterval_checkForUpdateDownload_seconds) * 1000;
        } catch (Exception e) {
            Log.w(TAG, "Exception caught trying to get CheckForUpdatesThread configuration parameters from strings.xml. Falling back to hard-coded values.\n"+e.getMessage());
            workCycleRestPeriodMS_CheckForUpdatesThread = 60 * 1000;
        }

        // Initialize default rest period values from strings (for InstallUpdatesThread)
        try {
            workCycleRestPeriodMS_InstallUpdatesThread = context.getResources().getInteger(R.integer.threadInterval_checkForUpdateInstall_seconds) * 1000;
        } catch (Exception e) {
            Log.w(TAG, "Exception caught trying to get InstallUpdatesThread configuration parameters from strings.xml. Falling back to hard-coded values.\n"+e.getMessage());
            workCycleRestPeriodMS_InstallUpdatesThread = 60 * 1000;
        }
    }

    /** Main runnable routine (executes once whenever the initialized thread is commanded to start running) */
    @Override
    public void run() {
        final String TAGG = "run: ";
        long cycleNumber = 0;
        Log.d(TAG, TAGG + "Invoked.");

        long pid = Thread.currentThread().getId();
        Log.i(TAG, TAGG + "Thread now running with process ID = " + pid);

        final int threadLastRunDateIsNull_maxAllowed = 5;
        int threadLastRunDateIsNullCount_checkForUpdatesThread = 0;
        int threadLastRunDateIsNullCount_installUpdatesThread = 0;

        long threadLastRunSecondsAgoCurrent_checkForUpdatesThread = 0;
        long threadLastRunSecondsAgoCurrent_installUpdatesThread = 0;

        // As long as our thread is supposed to be running, start doing work-cycles until it's been flagged to interrupt (rest period happens at the end of the cycle)...
        while (!Thread.currentThread().isInterrupted()) {

            // Take a rest before beginning our first iteration (to give time for the things we're monitoring to potentially come online)...
            if (cycleNumber == 0) {
                try {
                    java.lang.Thread.sleep(initialWaitPeriodMS);
                } catch (InterruptedException e) {
                    Log.e(TAG, TAGG + "Exception caught trying to sleep for first-run (" + workCycleRestPeriodMS + "ms).\n" + e.getMessage());
                    Thread.currentThread().interrupt();
                }
            } else {
                // Take a rest before continuing with this iteration (to make sure this thread doesn't run full tilt)...
                try {
                    Thread.sleep(workCycleRestPeriodMS);
                } catch (InterruptedException e) {
                    Log.e(TAG, TAGG + "Exception caught trying to sleep for interval (" + workCycleRestPeriodMS + "ms). Thread stopping.\n" + e.getMessage());
                    Thread.currentThread().interrupt();
                }
            }

            /* START MAIN THREAD-WORK
             * Note: you don't need to exit or break for normal work; instead, only continue (so cleanup and rest can occur at the end of the iteration/cycle) */

            // Indicate that this thread is beginning work tasks (may be useful for outside processes to know this)
            cycleNumber++;
            Log.v(TAG, TAGG + "=======================================(start)");
            Log.v(TAG, TAGG + "BEGINNING WORK CYCLE #" + cycleNumber + "...");

            // Check CheckForUpdatesThread...
            Log.i(TAG, TAGG+"Checking thread: MainUpdaterService."+THREADNAME_checkForUpdatesThread+"...");
            if (MainUpdaterService.threadLastRunDate_checkForUpdatesThread == null) {
                //null, so maybe not started running yet (increment counter for this case, so we can catch excessive cases)
                Log.i(TAG, TAGG+" The "+THREADNAME_checkForUpdatesThread+" has not reported any Date stamps ("+threadLastRunDateIsNullCount_checkForUpdatesThread+" of "+threadLastRunDateIsNull_maxAllowed+" max allowed).");
                threadLastRunDateIsNullCount_checkForUpdatesThread++;
                if (threadLastRunDateIsNullCount_checkForUpdatesThread > threadLastRunDateIsNull_maxAllowed) {
                    //null count is too high, so thread probably never started up
                    requestThreadStart(THREADNAME_checkForUpdatesThread);
                    threadLastRunDateIsNullCount_checkForUpdatesThread = 0;     //reset counter
                }
            } else {
                //there's a last-run Date, so investigate it for timeliness (defined as no more than twice its run-interval)
                threadLastRunSecondsAgoCurrent_checkForUpdatesThread = getMsSinceLastRunDateUpdate(THREADNAME_checkForUpdatesThread);
                if (threadLastRunSecondsAgoCurrent_checkForUpdatesThread > workCycleRestPeriodMS_CheckForUpdatesThread * 2) {
                    Log.w(TAG, TAGG+" The "+THREADNAME_checkForUpdatesThread+" has not run in "+threadLastRunSecondsAgoCurrent_checkForUpdatesThread+" milliseconds.");
                    requestThreadStart(THREADNAME_checkForUpdatesThread);
                } else {
                    Log.i(TAG, TAGG+" The "+THREADNAME_checkForUpdatesThread+" seems healthy (last ran "+threadLastRunSecondsAgoCurrent_checkForUpdatesThread/1000+"s ago).");
                }
            }

            // Check InstallUpdatesThread...
            Log.i(TAG, TAGG+"Checking thread: MainUpdaterService."+THREADNAME_installUpdatesThread+"...");
            if (MainUpdaterService.threadLastRunDate_installUpdatesThread == null) {
                //null, so maybe not started running yet (increment counter for this case, so we can catch excessive cases)
                Log.i(TAG, TAGG+" The "+THREADNAME_installUpdatesThread+" has not reported any Date stamps ("+threadLastRunDateIsNullCount_installUpdatesThread+" of "+threadLastRunDateIsNull_maxAllowed+" max allowed).");
                threadLastRunDateIsNullCount_installUpdatesThread++;
                if (threadLastRunDateIsNullCount_installUpdatesThread > threadLastRunDateIsNull_maxAllowed) {
                    //null count is too high, so thread probably never started up
                    requestThreadStart(THREADNAME_installUpdatesThread);
                    threadLastRunDateIsNullCount_installUpdatesThread = 0;     //reset counter
                }
            } else {
                //there's a last-run Date, so investigate it for timeliness (defined as no more than twice its run-interval)
                threadLastRunSecondsAgoCurrent_installUpdatesThread = getMsSinceLastRunDateUpdate(THREADNAME_installUpdatesThread);
                if (threadLastRunSecondsAgoCurrent_installUpdatesThread > workCycleRestPeriodMS_InstallUpdatesThread * 2) {
                    Log.w(TAG, TAGG+" The "+THREADNAME_installUpdatesThread+" has not run in "+threadLastRunSecondsAgoCurrent_installUpdatesThread+" milliseconds.");
                    requestThreadStart(THREADNAME_installUpdatesThread);
                } else {
                    Log.i(TAG, TAGG+" The "+THREADNAME_installUpdatesThread+" seems healthy (last ran "+threadLastRunSecondsAgoCurrent_installUpdatesThread/1000+"s ago).");
                }
            }

            /* END MAIN THREAD-WORK */
        }//end while

        // Loop above has exited (therefore, thread is stopping)
        cleanup();
    }//end run

    /** Cleanup */
    private void cleanup() {
        try {
            systemFunctions.cleanup();
        } catch (Exception e) {
            Log.w(TAG, "cleanup: Exception caught: "+e.getMessage());
        }
    }

    /*
    private boolean isThreadStillReportingLastRunTime(String threadName) {
        final String TAGG = "isThreadStillReportingLastRunTime(\""+String.valueOf(threadName)+"\"): ";
        boolean ret = true; //just to prevent new bugs, assume it's ok  -CR 4/22/19

        if (threadName == null || threadName.isEmpty()) {
            Log.w(TAG, TAGG+"Invalid threadName argument.");
        } else if ("checkForUpdatesThread".equals(String.valueOf(threadName))) {
            ret = MainUpdaterService.threadLastRunDate_checkForUpdatesThread;
        } else if ("installUpdatesThread".equals(String.valueOf(threadName))) {
            ret = MainUpdaterService.threadLastRunDate_installUpdatesThread;
        } else {
            Log.w(TAG, TAGG+"Unhandled threadName argument.");
        }

        Log.v(TAG, TAGG+"Returning \""+String.valueOf(ret)+"\".");
        return ret;
    }
    */

    private long getMsSinceLastRunDateUpdate(String threadName) {
        final String TAGG = "getMsSinceLastRunDateUpdate(\""+String.valueOf(threadName)+"\"): ";
        long ret = 0;
        Date lastRunDateReported = null;
        long currentDateMS, reportedDateMS, differenceMS;

        // Get the last-reported Date for the specified thread
        if (threadName == null || threadName.isEmpty()) {
            Log.w(TAG, TAGG+"Invalid threadName argument.");
        } else if (THREADNAME_checkForUpdatesThread.equals(String.valueOf(threadName))) {
            lastRunDateReported = MainUpdaterService.threadLastRunDate_checkForUpdatesThread;
        } else if (THREADNAME_installUpdatesThread.equals(String.valueOf(threadName))) {
            lastRunDateReported = MainUpdaterService.threadLastRunDate_installUpdatesThread;
        } else {
            Log.w(TAG, TAGG+"Unhandled threadName argument.");
        }

        // Calculate milliseconds since last reported Date
        try {
            if (lastRunDateReported != null) {
                currentDateMS = new Date().getTime();
                reportedDateMS = lastRunDateReported.getTime();
                differenceMS = currentDateMS - reportedDateMS;
                ret = differenceMS;
            }
        } catch (Exception e) {
            Log.w(TAG, TAGG+"Exception caught calculating difference: "+e.getMessage());
        }

        Log.v(TAG, TAGG+"Returning "+String.valueOf(ret)+".");
        return ret;
    }

    private void requestThreadStart(String threadName) {
        final String TAGG = "requestThreadStart(\"" + String.valueOf(threadName) + "\"): ";

        Message msg = MainUpdaterService.msgHandler.obtainMessage();

        try {
            if (msg == null) {
                Log.w(TAG, TAGG + "Could not obtainMessage from MainUpdaterService.msgHandler. Cannot request thread start!");
            } else {
                Bundle msgObj = new Bundle();
                msgObj.putString(key_command, cmd_startThread);
                msgObj.putString(key_threadName, threadName);
                msg.obj = msgObj;
                MainUpdaterService.msgHandler.sendMessage(msg);
            }
        } catch (Exception e) {
            Log.w(TAG, TAGG+"Exception caught: "+e.getMessage());
        }
    }
}
