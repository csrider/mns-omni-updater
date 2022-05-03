package com.messagenetsystems.evolutionupdater;

/** InstallDownloadedAPK
 * Governs installation of an already-downloaded APK file (which the main app should have taken care of for us).
 * This is necessary since part of the update (reinstallation) requires the main app to stop running, which would of course stop the udpate process.
 *
 * Revisions:
 *  2018.09.24  Chris Rider     Creation.
 */

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import java.io.IOException;

public class DownloadAndInstallAPK extends AsyncTask<Object, Void, Void> {
    final String TAG = DownloadAndInstallAPK.class.getSimpleName();
    long numBytesRead = 0;

    @Override
    protected Void doInBackground(Object... objects) {
        final String TAGG = "doInBackground: ";
        Context context = (Context) objects[0];

        /*
        String remoteHost = getSharedPrefsServerIPv4(context);
        String filepath, filename_evolution, filename_evolutionWatchdog;
        String downloadPath;

        Process process;

        // Figure out the web path on the server
        try {
            filepath = context.getResources().getString(R.string.updatePackageServerPath);
        } catch (Exception e) {
            Log.w(TAG, TAGG+"Exception caught trying to get APK download directory from strings.xml (using ~silentm instead): "+e.getMessage());
            filepath = "~silentm";
        }

        // Figure out the APK filename for main app
        try {
            filename_evolution = context.getResources().getString(R.string.packageFilename_evolution);
        } catch (Exception e) {
            Log.w(TAG, TAGG+"Exception caught trying to get APK filename from strings.xml (using \"com.messagenetsystems.evolution.apk\" instead): "+e.getMessage());
            filename_evolution = "/com.messagenetsystems.evolution.apk";
        }

        // Assemble the download path. we should be looking for this:
        // http://<serverIP>/home/silentm/public_html/bin/debug-app.apk
        downloadPath = "http://" + remoteHost + "/" + filepath;
        Log.d(TAG, TAGG + "path to file: " + downloadPath + "/" + filename_evolution);

        try {
            Log.v(TAG, TAGG + "context.getCacheDir().getPath(): " + context.getCacheDir().getAbsolutePath());

            // Check if file exists for download
            if (NetworkUtils.doesRemoteFileExist_http(downloadPath+"/"+filename_evolution)) {
                // Since it exists, download it.
                Log.d(TAG, TAGG+"Downloading APK...");
                numBytesRead = NetworkUtils.downloadFileHTTP(context, downloadPath, filename_evolution, 30, context.getCacheDir().getPath());  //30 max retries, at 100ms each, will retry for 1 second
                Log.d(TAG, TAGG+"numBytesRead = " + numBytesRead);

                //TODO: check if it actually downloaded
                //TODO: maybe add some checksum validation, too?

                // Try to change permissions, and then run our install command
                try {
                    Log.d(TAG, TAGG+"Changing permissions...");
                    process = Runtime.getRuntime().exec(new String[]{"su", "-c", "/system/bin/chmod 777 " + context.getCacheDir().getPath()+"/"+filename_evolution});
                    if ((process.waitFor() != 0)) {
                        throw new SecurityException();
                    }

                    Log.d(TAG, TAGG+"Stopping watchdog...");
                    process = Runtime.getRuntime().exec(new String[]{"su", "-c", "am force-stop com.messagenetsystems.evolutionwatchdog"});
                    if ((process.waitFor() != 0)) {
                        throw new SecurityException();
                    }

                    Log.d(TAG, TAGG+"Reinstalling main app with new APK...");
                    process = Runtime.getRuntime().exec(new String[]{"su", "-c", "/system/bin/pm install -t -r " +context.getCacheDir().getPath()+"/"+filename_evolution});
                    if ((process.waitFor() != 0)) {
                        throw new SecurityException();
                    }
                }catch (IOException io) {
                    Log.e(TAG, TAGG + "IOException caught trying to install APK: " + io.getMessage());
                }catch(SecurityException e) {
                    Log.e(TAG, TAGG +"Security Exception caught trying to install APK: " + String.valueOf(e));
                }catch (Exception e){
                    Log.e(TAG, TAGG + "Exception caught trying to install APK: " + e.getMessage());
                }
            } else {
                Log.w(TAG, TAGG + "File doesn't exist on server for download.");
            }
        } catch (Exception e) {
            Log.e(TAG, TAGG + "Exception caught trying to download APK file. " + e.getMessage());
        }

        // *** Now do the watchdog update *** //

        // Figure out the APK filename for watchdog
        try {
            filename_evolutionWatchdog = context.getResources().getString(R.string.package_filename_watchdog_release);
        } catch (Exception e) {
            Log.w(TAG, TAGG+"Exception caught trying to get watchdog APK filename from strings.xml (using \"com.messagenetsystems.evolutionwatchdog.apk\" instead): "+e.getMessage());
            filename_evolutionWatchdog = "/com.messagenetsystems.evolutionwatchdog.apk";
        }

        // Assemble the download path. we should be looking for this:
        // http://<serverIP>/home/silentm/public_html/bin/debug-app.apk
        downloadPath = "http://" + remoteHost + "/" + filepath;
        Log.d(TAG, TAGG + "path to watchdog file: " + downloadPath + "/" + filename_evolutionWatchdog);

        try {
            Log.v(TAG, TAGG + "context.getCacheDir().getPath(): " + context.getCacheDir().getAbsolutePath());

            // Check if file exists for download
            if (NetworkUtils.doesRemoteFileExist_http(downloadPath+"/"+filename_evolutionWatchdog)) {
                // Since it exists, download it.
                numBytesRead = NetworkUtils.downloadFileHTTP(context, downloadPath, filename_evolutionWatchdog, 30, context.getCacheDir().getPath());  //30 max retries, at 100ms each, will retry for 1 second
                Log.d(TAG, TAGG+"numBytesRead = " + numBytesRead);

                //TODO: check if it actually downloaded
                //TODO: maybe add some checksum validation, too?

                // Try to change permissions, and then run our install command
                try {
                    Log.d(TAG, TAGG+"Changing permissions...");
                    process = Runtime.getRuntime().exec(new String[]{"su", "-c", "/system/bin/chmod 777 " + context.getCacheDir().getPath()+filename_evolutionWatchdog});
                    if ((process.waitFor() != 0)) {
                        throw new SecurityException();
                    }

                    Log.d(TAG, TAGG+"Reinstalling watchdog app with new APK...");
                    process = Runtime.getRuntime().exec(new String[]{"su", "-c", "/system/bin/pm install -t -r " +context.getCacheDir().getPath()+filename_evolutionWatchdog});
                    if ((process.waitFor() != 0)) {
                        throw new SecurityException();
                    }
                }catch (IOException io) {
                    Log.e(TAG, TAGG + "IOException caught trying to install watchdog APK: " + io.getMessage());
                }catch(SecurityException e) {
                    Log.e(TAG, TAGG +"Security Exception caught trying to install watchdog APK: " + String.valueOf(e));
                }catch (Exception e){
                    Log.e(TAG, TAGG + "Exception caught trying to install watchdog APK: " + e.getMessage());
                }
            } else {
                Log.w(TAG, TAGG + "File doesn't exist on server for download.");
            }
        } catch (Exception e) {
            Log.e(TAG, TAGG + "Exception caught trying to download watchdog APK file. " + e.getMessage());
        }

        // Try to start things back up again
        try {
            Log.d(TAG, TAGG+"Starting main app...");
            process = Runtime.getRuntime().exec(new String[]{"su", "-c", "am start -n com.messagenetsystems.evolution/com.messagenetsystems.evolution.StartupActivity"});
            if ((process.waitFor() != 0)) {
                throw new SecurityException();
            }

            Log.d(TAG, TAGG+"Starting watchdog app...");
            process = Runtime.getRuntime().exec(new String[]{"su", "-c", "am start -n com.messagenetsystems.evolutionwatchdog/com.messagenetsystems.evolutionwatchdog.StartupActivity"});
            if ((process.waitFor() != 0)) {
                throw new SecurityException();
            }
        }catch (IOException io) {
            Log.e(TAG, TAGG + "IOException caught trying to start APK: " + io.getMessage());
        }catch(SecurityException e) {
            Log.e(TAG, TAGG +"Security Exception caught trying to start APK: " + String.valueOf(e));
        }catch (Exception e){
            Log.e(TAG, TAGG + "Exception caught trying to start APK: " + e.getMessage());
        }

        */

        return null;

    }
}