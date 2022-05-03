package com.messagenetsystems.evolutionupdater.receivers;

/** ApplyUpdatesReceiver
 * Handles receiving request to begin installation of already-present update files.
 *
 * Revisions:
 *  2018.10.19      Chris Rider     Created.
 *  2019.04.05      Chris Rider     Updated to work with refactoring of InstallUpdatesThread.
 */

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.messagenetsystems.evolutionupdater.MainUpdaterService;
import com.messagenetsystems.evolutionupdater.R;
import com.messagenetsystems.evolutionupdater.SystemFunctions;
import com.messagenetsystems.evolutionupdater.activities.UpdatingActivitySimple;

public class ApplyUpdatesReceiver extends BroadcastReceiver {
    private static final String TAG = ApplyUpdatesReceiver.class.getSimpleName();

    @Override
    public void onReceive(Context context, Intent intent) {
        String TAGG = "onReceive ("+String.valueOf(intent.getAction())+"): ";
        Log.v(TAG, TAGG+"Invoked.");

        final String intentAction = context.getResources().getString(R.string.intentAction_triggerOmniUpdater_applyUpdates);

        if (intent.getAction().equals(intentAction)) {
            SystemFunctions systemFunctions = new SystemFunctions(context);
            String appPackageName = null;
            String appPackageName_short = "";
            String filename = null;
            int maxRetriesLeft;
            boolean okToContinue = true;
            Intent intentToStartActivity;

            // If intent's extras specify a specific app to update, then only do that
            // Else do all available
            if (intent.hasExtra("appPackageName")) {
                appPackageName = intent.getStringExtra("appPackageName");
                appPackageName_short = appPackageName.replace("com.messagenetsystems.", "");

                // Get any other flags/params from intent
                String notifyWhenDone = intent.getStringExtra("notifyWhenDone");    //get the resource to notify, if provided with one

                // Construct a filename to pass to the installation routine
                filename = MainUpdaterService.localPath+"/"+appPackageName + ".apk";

                // Kill any prerequisite apps first (like watchdog)
                if (appPackageName.equals(MainUpdaterService.packageName_evolution)) {

                    //for main app, we also need to kill watchdog before killing main app
                    systemFunctions.updateNotificationWithText("Preparing update: Killing watchdog.");
                    maxRetriesLeft = 5;
                    while (!systemFunctions.stopSpecifiedApp(MainUpdaterService.packageName_evolutionWatchdog) && maxRetriesLeft > 0) {
                        maxRetriesLeft--;
                    }
                    if (maxRetriesLeft == 0) {
                        Log.e(TAG, TAGG + "Could not kill watchdog. Aborting update.");
                        okToContinue = false;
                        systemFunctions.updateNotificationWithText("Failed to kill watchdog. Update aborted.");
                    }
                }

                // Continue update of specified app
                if (okToContinue) {
                    systemFunctions.updateNotificationWithText("Starting update"+" ("+appPackageName_short+")");

                    // Start the update activity
                    intentToStartActivity = new Intent(context, UpdatingActivitySimple.class);
                    intentToStartActivity.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);                  //required to call startActivity from outside of the activity's context
                    intentToStartActivity.putExtra("appPackageName", appPackageName);
                    intentToStartActivity.putExtra("filename", filename);                           //NOTE: This is the filename with its path prepended!
                    intentToStartActivity.putExtra("notifyWhenDone", notifyWhenDone);
                    context.startActivity(intentToStartActivity);

                    // At this point, the activity takes care of the rest!
                }

            } else {
                Log.w(TAG, TAGG+"TODO: install all available updates"); //TODO
            }

            //cleanup
            systemFunctions.cleanup();
            systemFunctions = null;
            intentToStartActivity = null;

        } else {
            Log.w(TAG, TAGG+"Intent action did not match conditions needed to start updater activity.");
        }
    }
}
