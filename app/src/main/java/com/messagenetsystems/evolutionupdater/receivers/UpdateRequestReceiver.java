package com.messagenetsystems.evolutionupdater.receivers;

/** UpdateRequestReceiver
 * Handles receiving request to begin an update, and starting the update process.
 *
 * Revisions:
 *  2018.10.18  Chris Rider     Created (not yet actually used).
 */

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.messagenetsystems.evolutionupdater.R;
import com.messagenetsystems.evolutionupdater.SystemFunctions;
import com.messagenetsystems.evolutionupdater.activities.UpdatingActivity;

public class UpdateRequestReceiver extends BroadcastReceiver {
    private static final String TAG = UpdateRequestReceiver.class.getSimpleName();

    @Override
    public void onReceive(Context context, Intent intent) {
        String TAGG = "onReceive ("+String.valueOf(intent.getAction())+"): ";
        Log.v(TAG, TAGG+"Invoked.");

        final String intentAction = context.getResources().getString(R.string.intentAction_triggerOmniUpdater);

        if (intent.getAction().equals(intentAction)) {

            // Define our app's start-up service...
            Intent intentToStartUpdaterActivity = new Intent(context, UpdatingActivity.class);      //define an intent to start our updater activity
            intentToStartUpdaterActivity.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);                   //required to call startActivity from outside of the activity's context

            // Kill watchdog and main app
            SystemFunctions systemFunctions = new SystemFunctions(context);
            systemFunctions.stopEvolutionWatchdogApp();
            systemFunctions.stopEvolutionApp();

            // Start it...
            Log.d(TAG, TAGG+"Starting UpdatingActivity...");
            context.startActivity(intentToStartUpdaterActivity);                                    //make the call to actually start the activity, using that intent

        } else {
            Log.w(TAG, TAGG+"Intent action did not match conditions needed to start updater activity.");
        }
    }
}
