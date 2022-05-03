package com.messagenetsystems.evolutionupdater.activities;

/** UpdatingActivitySimple
 * This is the update activity started by the ApplyUpdatesReceiver.
 * It continues where that left off and actually installs the update.
 *
 * Revisions:
 *  2018.10.19      Chris Rider     Created.
 *  2018.10.25      Chris Rider     Added notification update.
 *  2019.04.05      Chris Rider     Updated to work with refactoring of InstallUpdatesThread and ApplyUpdatesReceiver.
 *  2019.04.08      Chris Rider     Now supporting providing a reason to the update result logging (not really robust yet, but capable). Tweaked screen text update to prevent problems for sure.
 */

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.TextView;

import com.messagenetsystems.evolutionupdater.MainUpdaterService;
import com.messagenetsystems.evolutionupdater.R;
import com.messagenetsystems.evolutionupdater.SystemFunctions;
import com.messagenetsystems.evolutionupdater.threads.InstallUpdatesThread;

public class UpdatingActivitySimple extends Activity {
    private static final String TAG = UpdatingActivitySimple.class.getSimpleName();

    private Context appContext;
    private Intent intent;
    private SystemFunctions systemFunctions;
    private int maxRetriesLeft;
    private String appPackageName;
    private String appPackageName_short;
    private String filename;
    private String notifyWhenDone;
    private boolean updateSucceeded = false;
    private String updateResultReason = null;
    String startupClassName = "";
    private String notifText_updateInstallPreparing;
    private String notifText_updateInstallUnderway;
    private String notifText_updateInstallFailed;
    private String notifText_normalAppAlive;
    private TextView textView_status1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_updating_simple);
        final String TAGG = "onCreate: ";
        Log.v(TAG, TAGG+"Invoked.");

        appContext = getApplicationContext();
        intent = this.getIntent();
        systemFunctions = new SystemFunctions(appContext);

        appPackageName = intent.getStringExtra("appPackageName");
        appPackageName_short = appPackageName.replace("com.messagenetsystems.", "");
        filename = intent.getStringExtra("filename");       //NOTE: This is the filename with path prepended!!!
        notifyWhenDone = intent.getStringExtra("notifyWhenDone");

        notifText_normalAppAlive = appContext.getResources().getString(R.string.notification_text_runningPID) + android.os.Process.myPid();
        notifText_updateInstallPreparing = appContext.getResources().getString(R.string.notification_text_updateInstallationPrepare);
        notifText_updateInstallUnderway = appContext.getResources().getString(R.string.notification_text_updateInstallationUnderway);
        notifText_updateInstallFailed = appContext.getResources().getString(R.string.notification_text_updateInstallationFailed);
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        final String TAGG = "onPostCreate: ";
        Log.v(TAG, TAGG+"Invoked.");

        // Update notification
        systemFunctions.updateNotificationWithText(notifText_updateInstallPreparing+" ("+appPackageName_short+")");

        // Kill the app we're going to update
        maxRetriesLeft = 5;
        while (!systemFunctions.stopSpecifiedApp(appPackageName) && maxRetriesLeft > 0) {
            maxRetriesLeft--;
        }
        if (maxRetriesLeft == 0) {
            Log.w(TAG, TAGG + "Could not kill \"" + appPackageName + "\". Will try updating anyway.");
        }

        // Give the activity time to render before starting the update
        Handler delayedStart = new Handler();
        delayedStart.postDelayed(doTheStuff, 2000);
    }

    @Override
    protected void onResume() {
        super.onResume();
        final String TAGG = "onResume: ";
        Log.v(TAG, TAGG+"Invoked.");

        try {
            textView_status1 = (TextView) findViewById(R.id.tvUpdatingSimpleStatus1);
            textView_status1.setText("Omni Updating (" + appPackageName_short+")");
        } catch (Exception e) {
            Log.w(TAG, TAGG+"Exception caught trying to update screen text with details: "+e.getMessage());
        }
    }

    @Override
    protected void onDestroy() {
        final String TAGG = "onDestroy: ";
        Log.v(TAG, TAGG+"Invoked.");

        appContext = null;
        intent = null;

        systemFunctions.cleanup();
        systemFunctions = null;

        super.onDestroy();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        final String TAGG = "onNewIntent: ";
        Log.v(TAG, TAGG+"Invoked.");

        if (intent.getExtras() != null
                && intent.getExtras().getBoolean("requestFinish")) {
            Log.i(TAG, TAGG+"Intent received to request we finish this activity.");
            UpdatingActivitySimple.this.finish();
        }
    }

    private Runnable doTheStuff = new Runnable() {
        final String TAGG = "doTheStuff: ";

        String result = SystemFunctions.INSTALL_PACKAGE_RESULT_UNKNOWN;

        @Override
        public void run() {
            systemFunctions.updateNotificationWithText(notifText_updateInstallUnderway + " (" + appPackageName_short + ")");
            maxRetriesLeft = 5;
            /* DEPRECATED
            while (!systemFunctions.installPackage(filename).equals(systemFunctions.INSTALL_PACKAGE_RESULT_SUCCESS) && maxRetriesLeft > 0) {
                maxRetriesLeft--;
            }
            */

            // Different processes for system apps versus regular user apps...
            if (appPackageName.equals(MainUpdaterService.packageName_omniWatchdogWatcher)) {
                // Begin the system-app installation
                // this needs to update as a system app (which requires RW system mount and post-update reboot)
                while (!result.contains(SystemFunctions.INSTALL_PACKAGE_RESULT_SUCCESS) && maxRetriesLeft > 0){
                    Log.v(TAG, TAGG+"Invoking package installation routine (for system app), "+maxRetriesLeft+" tries left...");
                    result = systemFunctions.installPackage_systemApp(filename, appPackageName+".apk");                    //this routine takes care of remounting, copying file, etc.
                    maxRetriesLeft--;
                }
                if (!result.contains(SystemFunctions.INSTALL_PACKAGE_RESULT_SUCCESS)
                        && maxRetriesLeft <= 0) {
                    // No explicit success, and no retries remaining...
                    Log.e(TAG, TAGG + "Could not install \"" + filename + "\" after many retries.");
                    updateSucceeded = false;
                    updateResultReason = result + " (after many after many UpdatingActivitySimple.doTheStuff() retries)";
                } else if (result.contains(SystemFunctions.INSTALL_PACKAGE_RESULT_SUCCESS)) {
                    // We got an explicit success from SystemFunctions.installPackage_systemApp()!
                    updateSucceeded = true;
                    updateResultReason = result;
                } else {
                    updateSucceeded = false;
                    updateResultReason = result + " (UpdatingActivitySimple.doTheStuff() encountered unhandled condition after executing SystemFunctions.installPacakge())";
                }

                // If success, we will need to reboot; else show problem
                if (updateSucceeded) {
                    Log.i(TAG, TAGG + "Updated succeeded, rebooting device so it can finish installing.");
                    systemFunctions.updateNotificationWithText(notifText_normalAppAlive);
                    systemFunctions.saveInstallationAttemptResultInfo(appPackageName, systemFunctions.UPDATE_INSTALLATION_RESULT_SUCCESS, updateResultReason);
                    systemFunctions.doReboot();
                } else {
                    Log.i(TAG, TAGG + "Updated failed, show problem.");
                    systemFunctions.updateNotificationWithText(notifText_updateInstallFailed + " (" + appPackageName_short + ")");
                    systemFunctions.saveInstallationAttemptResultInfo(appPackageName, systemFunctions.UPDATE_INSTALLATION_RESULT_FAILURE, updateResultReason);
                }

                // Reset the flag so other updates can happen
                InstallUpdatesThread.packageIsUpdating = null;

                // Close the updating screen
                UpdatingActivitySimple.this.finish();
            } else {
                // Begin the user-app installation
                while (!result.contains(SystemFunctions.INSTALL_PACKAGE_RESULT_SUCCESS) && maxRetriesLeft > 0) {
                    Log.v(TAG, TAGG+"Invoking package installation routine, "+maxRetriesLeft+" tries left...");
                    result = systemFunctions.installPackage(filename);
                    maxRetriesLeft--;
                }
                if (!result.contains(SystemFunctions.INSTALL_PACKAGE_RESULT_SUCCESS)
                        && maxRetriesLeft <= 0) {
                    // No explicit success, and no retries remaining...
                    Log.e(TAG, TAGG + "Could not install \"" + filename + "\" after many retries.");
                    updateSucceeded = false;
                    updateResultReason = result + " (after many after many UpdatingActivitySimple.doTheStuff() retries)";
                } else if (result.contains(SystemFunctions.INSTALL_PACKAGE_RESULT_SUCCESS)) {
                    // We got an explicit success from SystemFunctions.installPackage()!
                    updateSucceeded = true;
                    updateResultReason = result;
                } else {
                    updateSucceeded = false;
                    updateResultReason = result + " (UpdatingActivitySimple.doTheStuff() encountered unhandled condition after executing SystemFunctions.installPacakge())";
                }

                // If success, restart stuff; else show problem
                // (DEV-NOTE: this is where we set notification so it persists with problem message if needed, instead of resetting notification in onDestroy)
                if (updateSucceeded) {
                    Log.i(TAG, TAGG + "Updated succeeded, starting app.");
                    systemFunctions.updateNotificationWithText(notifText_normalAppAlive);
                    systemFunctions.saveInstallationAttemptResultInfo(appPackageName, systemFunctions.UPDATE_INSTALLATION_RESULT_SUCCESS, updateResultReason);
                } else {
                    Log.i(TAG, TAGG + "Updated failed, show problem.");
                    systemFunctions.updateNotificationWithText(notifText_updateInstallFailed + " (" + appPackageName_short + ")");
                    systemFunctions.saveInstallationAttemptResultInfo(appPackageName, systemFunctions.UPDATE_INSTALLATION_RESULT_FAILURE, updateResultReason);
                }

                // Start the updated app
                if (appPackageName.equals(appContext.getResources().getString(R.string.appPackageName_evolution))) {
                    startupClassName = appContext.getResources().getString(R.string.startupClass_evolution);
                } else if (appPackageName.equals(appContext.getResources().getString(R.string.appPackageName_evolutionWatchdog))) {
                    startupClassName = appContext.getResources().getString(R.string.startupClass_evolutionWatchdog);
                } else {
                    startupClassName = "StartupActivity";
                    Log.w(TAG, TAGG + "Unhandled app package, defaulting to \"" + startupClassName + "\".");
                }
                systemFunctions.startSpecifiedApp(appPackageName, startupClassName);

                // Reset the flag so other updates can happen
                InstallUpdatesThread.packageIsUpdating = null;

                // Close the updating screen
                UpdatingActivitySimple.this.finish();
            }
        }
    };
}
