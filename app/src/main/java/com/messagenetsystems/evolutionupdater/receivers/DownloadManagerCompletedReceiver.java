package com.messagenetsystems.evolutionupdater.receivers;

/** DownloadManagerCompletedReceiver
 *
 * Listens for the onCompleted broadcast from the Android DownloadManager, so we know when a download has completed.
 * You should register it like follows, in order for it to listen for on-completion broadcasts:
 *  [context].registerReceiver([this-receiver-instance], new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
 *
 * Note: As an added convenience, this will try to unregister itself when the download-completion intent is received.
 *
 * Revisions:
 *  2018.04.05      Chris Rider     Creation.
 *  2019.04.19      Chris Rider     Implemented short package name for notifications.
 *  2019.10.10      Chris Rider     Added support for omniwatchdogwatcher (as well as forgotten evolutionflasherlights stuff).
 */

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.support.annotation.Nullable;
import android.util.Log;

import com.messagenetsystems.evolutionupdater.MainUpdaterService;
import com.messagenetsystems.evolutionupdater.SystemFunctions;
import com.messagenetsystems.evolutionupdater.threads.CheckForUpdatesThread;

public class DownloadManagerCompletedReceiver extends BroadcastReceiver {

    final String TAG = DownloadManagerCompletedReceiver.class.getSimpleName();

    protected Context appContext;
    private long downloadID_enqueuedFile;       //the download ID that DownloadManager assigns whatever file is enqueued to download
    private String packageName;
    private String packageName_short;
    private String filename_enqueued;           //the filename that DownloadManager is enqueued to download
    private boolean doResetMainFlags;           //whether we should reset the main flags indicating download-in-progress / completed
    private String resourceToNotify;

    /** Constructor */
    public DownloadManagerCompletedReceiver(Context appContext, long downloadID, String packageName, String filename, boolean doResetMainFlags, @Nullable String resourceToNotify) {
        this.appContext = appContext;
        this.downloadID_enqueuedFile = downloadID;
        this.packageName = packageName;
        this.packageName_short = packageName.replace("com.messagenetsystems.", "");
        this.filename_enqueued = filename;
        this.doResetMainFlags = doResetMainFlags;
        this.resourceToNotify = resourceToNotify;
    }

    /** What happens when we receive the broadcast from DownloadManager */
    @Override
    public void onReceive(Context context, Intent intent) {
        final String TAGG = "onReceive: ";
        final String intentAction = DownloadManager.ACTION_DOWNLOAD_COMPLETE;

        if (intent.getAction() != null
                && intent.getAction().equals(intentAction)) {

            long downloadID_fromIntent = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);

            if (downloadID_enqueuedFile == downloadID_fromIntent) {
                // Update this package's download-status so it's known to be done
                CheckForUpdatesThread.setPackageDownloadStatus(packageName, CheckForUpdatesThread.STATUS_DOWNLOAD_COMPLETED);

                // Reset flags
                if (doResetMainFlags) {
                    MainUpdaterService.flag_isDownloading = false;
                    MainUpdaterService.flag_isDownloadingPackage = null;
                    MainUpdaterService.flag_isDownloading_evolution = false;
                    MainUpdaterService.flag_isDownloading_evolutionwatchdog = false;
                    MainUpdaterService.flag_isDownloading_evolutionupdater = false;
                    MainUpdaterService.flag_isDownloading_evolutionFlasherLights = false;
                    MainUpdaterService.flag_isDownloading_omniWatchdogWatcher = false;
                    MainUpdaterService.isDownloadingUpdates = false;
                }

                // Notify any specified resource
                if (resourceToNotify != null) {
                    if (resourceToNotify.isEmpty()) {
                        Log.v(TAG, TAGG+"Resource to notify is not null, but is empty. Doing nothing, as we don't know what to notify.");
                    } else {
                        Log.d(TAG, TAGG + "Resource specified to notify (\"" + String.valueOf(resourceToNotify) + "\") but as-of-yet unhandled!");    //TODO
                    }
                }

                Log.d(TAG, TAGG + "DownloadManager completed: \"" + filename_enqueued + "\".");

                // Update notification
                SystemFunctions.updateNotificationWithText(appContext, "DownloadManager completed \"" + packageName_short + "\" APK.");

                // Try to unregister self since we're done with it and don't fully trust garbage-collection
                // (yes, ideally, this is normally done in the instantiating class, but we want to be efficient as possible)
                try {
                    appContext.unregisterReceiver(this);
                    //LocalBroadcastManager.getInstance(context).unregisterReceiver(this);  //TODO: try this if above line fails
                    Log.v(TAG, TAGG+"Invoked call to unregister DownloadManagerCompletedReceiver, hopefully it worked.");
                } catch (Exception e) {
                    Log.w(TAG, TAGG+"Exception caught trying to unregister self: "+e.getMessage());
                }
            }

        } else {
            Log.w(TAG, TAGG+"Intent filter does not match DownloadManager.ACTION_DOWNLOAD_COMPLETE.");
        }
    }
}
