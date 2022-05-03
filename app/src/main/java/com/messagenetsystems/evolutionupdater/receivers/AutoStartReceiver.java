package com.messagenetsystems.evolutionupdater.receivers;

/** AutoStartReceiver
 * Handles receiving various triggers from the system for starting our main updater service.
 *
 * Revisions:
 *  2018.10.17  Chris Rider     Created (cloned from EvolutionWatchdog).
 */

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.messagenetsystems.evolutionupdater.MainUpdaterService;

public class AutoStartReceiver extends BroadcastReceiver {
    private static final String TAG = AutoStartReceiver.class.getSimpleName();

    /** Specify what happens when we receive the boot-up notification from the OS **/
    @Override
    public void onReceive(Context context, Intent intent) {
        String TAGG = "onReceive ("+String.valueOf(intent.getAction())+"): ";
        Log.d(TAG, TAGG+"Running.");

        Intent intentToStart;

        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {

            // Define our app's start-up service...
            intentToStart = new Intent(context, MainUpdaterService.class);                          //define an intent to start our service

            // Start it...
            Log.d(TAG, TAGG+"Starting MainService...");
            context.startService(intentToStart);                                                    //make the call to actually start the service, using that intent
        } else {
            Log.w(TAG, TAGG+"Intent action did not match conditions needed to start service.");
        }

    }
}
