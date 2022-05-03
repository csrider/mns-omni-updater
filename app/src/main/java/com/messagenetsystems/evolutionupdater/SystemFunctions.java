package com.messagenetsystems.evolutionupdater;

/** System Functions
 *
 * Revisions:
 *  2018.10.17  Chris Rider     Created.
 *  2019.02.12  Chris Rider     Added ability to read text file directly from server without saving a downloaded file.
 *  2019.02.13  Chris Rider     Added getPathForInstalledAPK and time methods to support windows.
 *  2019.02.28  Chris Rider     Added ability to downgrade a packagemanager installation.
 *  2019.04.19  Chris Rider     Fixing bugs and making sure successful update installation is more explicit and certain.
 *                              Made pm install command also grant all (including runtime) permissions.
 *  2019.10.14  Chris Rider     Added network methods (so far unused) and improved network available check logging.
 */

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Environment;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/** SystemFunctions
 *
 * Revisions:
 *  2018.10.17  Chris Rider     Created.
 */

public class SystemFunctions {
    private static final String TAG = SystemFunctions.class.getSimpleName();
    private Context appContext;

    public static String serverIP = null;

    private int wifiCycleCounter = 0;

    /** Constructor */
    public SystemFunctions(Context appContext) {
        this.appContext = appContext;

        serverIP = getSharedPrefsServerIPv4_su_evolution();
    }

    /** Destructor */
    public void cleanup() {
        this.appContext = null;
    }

    /** Check whether the specified app is currently a "running" process.
     * Requires root. Depends on unix 'ps' command result.
     * Returns false by default.
     */
    public boolean specifiedAppProcessIsRunning(String appPackageName) {
        final String TAGG = "specifiedAppProcessIsRunning(\""+appPackageName+"\"): ";

        Process process;
        boolean ret = false;
        BufferedReader stdin;

        Log.d(TAG, TAGG+"Checking whether app is a running process...");
        try {
            // Run the unix command, should return a count of matches
            // Note: -w option gives us exact match.
            process = Runtime.getRuntime().exec(new String[]{"su", "-c", "/system/bin/ps | /system/bin/grep -c -w "+appPackageName});
            if ((process.waitFor() != 0)) {
                throw new SecurityException();
            }

            // Process the output of the above command (read the count of matches it produced)
            stdin = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String cmdResult = stdin.readLine();
            Log.v(TAG, TAGG+"Result of process check = \""+cmdResult+"\".");
            int cmdResultAsInt = Integer.parseInt(cmdResult);

            // Set the return value depending on our result
            if (cmdResultAsInt == 1) {
                ret = true;
            } else if (cmdResultAsInt > 1) {
                /* NOTE: Didn't end up needing this after using -w grep exact match option!
                //Note: This could happen, for instance if you specify "com.messagenetsystems.evolution" and the ps command finds (since both processes contain the match criteria):
                //  USER      PID   PPID  VSIZE   RSS    WCHAN      PC           NAME
                //  u0_a60    1464  310   2479096 135984 SyS_epoll_ 7776fedb40 S com.messagenetsystems.evolution
                //  u0_a59    1502  310   1558444  78948 SyS_epoll_ 7776fedb40 S com.messagenetsystems.evolutionwatchdog
                Log.i(TAG, TAGG+"There appears to be more than one match. This is probably OK, though.");
                ret = true;
                */
                Log.w(TAG, TAGG+"There are "+cmdResult+" matches. There should only be one!");
                ret = true;
            }
        } catch (IOException io) {
            Log.e(TAG, TAGG + "IOException caught: " + io.getMessage());
        } catch(SecurityException e) {
            Log.e(TAG, TAGG +"Security Exception caught: " + String.valueOf(e));
        } catch (Exception e){
            Log.e(TAG, TAGG + "Exception caught: " + e.getMessage());
        }

        Log.v(TAG, TAGG+"Returning "+String.valueOf(ret)+".");
        return ret;
    }

    /** Check whether the specified app is currently in the foreground.
     * Requires root. Uses dumpsys command.
     * Returns false by default.
     */
    public boolean specifiedAppIsInForeground(String appPackageName) {
        final String TAGG = "specifiedAppIsInForeground(\""+appPackageName+"\"): ";

        Process process;
        boolean ret = false;
        BufferedReader stdin;

        Log.d(TAG, TAGG+"Checking whether app is in the foreground...");
        try {
            // Run the unix command to give us the currently foreground app's namespace
            process = Runtime.getRuntime().exec(new String[]{"su", "-c", "/system/bin/dumpsys window windows | /system/bin/grep -E 'mCurrentFocus' | /system/bin/busybox awk '{print $3}' | /system/bin/cut -d'/' -f1"});
            if ((process.waitFor() != 0)) {
                throw new SecurityException();
            }

            // Process the output of the above command (read the app's namespace it produced)
            stdin = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String cmdResult = stdin.readLine();
            Log.v(TAG, TAGG+"Result of process check = \""+cmdResult+"\".");

            // Set the return value depending on our result
            Log.v(TAG, TAGG+"Command result = \""+cmdResult+"\".");
            if (appPackageName.equals(String.valueOf(cmdResult))) {
                ret = true;
            } else if ("".equals(String.valueOf(cmdResult))) {
                //in bench testing, this might sometimes happen (where dumpsys reports no mCurrentFocus), probably when switching activity screens and there's lag
                //return an assumed true so we don't errantly do stuff
                ret = true;
            }
        } catch (IOException io) {
            Log.e(TAG, TAGG + "IOException caught: " + io.getMessage());
        } catch(SecurityException e) {
            Log.e(TAG, TAGG +"Security Exception caught: " + String.valueOf(e));
        } catch (Exception e){
            Log.e(TAG, TAGG + "Exception caught: " + e.getMessage());
        }

        Log.v(TAG, TAGG+"Returning "+String.valueOf(ret)+".");
        return ret;
    }

    /** Check whether the specified class name (e.g. activity) is currently in the foreground.
     * Requires root. Uses dumpsys command.
     * Returns false by default.
     */
    public boolean specifiedClassIsInForeground(String className) {
        final String TAGG = "specifiedClassIsInForeground(\""+className+"\"): ";

        Process process;
        boolean ret = false;
        BufferedReader stdin;

        Log.d(TAG, TAGG+"Checking whether class is in the foreground...");
        try {
            // Run the unix command to give us the currently foreground app's namespace
            process = Runtime.getRuntime().exec(new String[]{"su", "-c", "/system/bin/dumpsys window windows | /system/bin/grep -E 'mCurrentFocus' | /system/bin/busybox awk '{print $3}' | /system/bin/cut -d'/' -f2 | /system/bin/cut -d'.' -f4 | /system/bin/cut -d'}' -f1"});
            if ((process.waitFor() != 0)) {
                throw new SecurityException();
            }

            // Process the output of the above command (read the app's namespace it produced)
            stdin = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String cmdResult = stdin.readLine();
            Log.v(TAG, TAGG+"Result of process check = \""+cmdResult+"\".");

            // Set the return value depending on our result
            Log.v(TAG, TAGG+"Command result = \""+cmdResult+"\".");
            if (className.equals(String.valueOf(cmdResult))) {
                ret = true;
            }
        } catch (IOException io) {
            Log.e(TAG, TAGG + "IOException caught: " + io.getMessage());
        } catch(SecurityException e) {
            Log.e(TAG, TAGG +"Security Exception caught: " + String.valueOf(e));
        } catch (Exception e){
            Log.e(TAG, TAGG + "Exception caught: " + e.getMessage());
        }

        Log.v(TAG, TAGG+"Returning "+String.valueOf(ret)+".");
        return ret;
    }

    /** Start a specified app.
     * Requires root.
     * Returns true if app start command exited in a way that give us confidence the app started.
     * Note: Running this is also the proper way to bring a background app to the foreground.
     */
    public boolean startSpecifiedApp(String appPackageName, String appClassToStart) {
        final String TAGG = "startSpecifiedApp(\""+appPackageName+"."+appClassToStart+"\"): ";

        boolean ret = false;
        Process process = null;
        OutputStream stdin;     //used to write commands to shell... using OutputStream type, we can execute commands like writing commands in terminal
        InputStream stdout;     //used to read output of a command we executed... using InputStream type, we can input command's output to our routine here
        InputStream stderr;     //used to read errors of a command we executed... using InputStream type, we can input command's errors to our routine here
        BufferedReader br;
        String line;

        try {
            // Start a super-user process under which to execute our commands as root
            Log.d(TAG, TAGG + "Starting super-user shell session...");
            process = Runtime.getRuntime().exec("su");

            // Get process streams
            stdin = process.getOutputStream();
            stdout = process.getInputStream();
            stderr = process.getErrorStream();

            // Construct and execute command (new-line is like hitting enter)
            Log.d(TAG, TAGG + "Starting app via activity manager...");
            stdin.write(("am start -n "+appPackageName+"/"+appPackageName+"."+appClassToStart+"\n").getBytes());

            // Exit the shell
            stdin.write(("exit\n").getBytes());

            // Flush and close the stdin stream
            stdin.flush();
            stdin.close();

            // Read output of the executed command
            Log.v(TAG, TAGG+"Reading output of executed command...");
            br = new BufferedReader(new InputStreamReader(stdout));
            while ((line = br.readLine()) != null) {
                Log.v(TAG, TAGG+"stdout line: "+line);
                if (line.contains("Starting:")) {
                    ret = true;
                }
            }
            br.close();

            // Read error stream of the executed command
            Log.v(TAG, TAGG+"Reading errors of executed command...");
            br = new BufferedReader(new InputStreamReader(stderr));
            while ((line = br.readLine()) != null) {
                Log.w(TAG, TAGG+"stderr line: "+line);
                ret = false;
            }
            br.close();

            // Wait for process to finish
            process.waitFor();
            process.destroy();

        //} catch(SecurityException e) {
        //    Log.e(TAG, TAGG +"Security Exception caught: " + String.valueOf(e));
        } catch (Exception e) {
            Log.e(TAG, TAGG + "Exception caught: " + e.getMessage());
        }

        // Cleanup
        if (process != null) {
            process.destroy();
        }

        // Return
        Log.v(TAG, TAGG+"Returning: "+String.valueOf(ret));
        return ret;
    }

    /** Stops a specified app.
     * Requires root.
     */
    public boolean stopSpecifiedApp(String appPackageName) {
        final String TAGG = "stopSpecifiedApp(\""+appPackageName+"\"): ";

        boolean ret = true;
        Process process = null;
        OutputStream stdin;     //used to write commands to shell... using OutputStream type, we can execute commands like writing commands in terminal
        InputStream stdout;     //used to read output of a command we executed... using InputStream type, we can input command's output to our routine here
        InputStream stderr;     //used to read errors of a command we executed... using InputStream type, we can input command's errors to our routine here
        BufferedReader br;
        String line;

        try {
            // Start a super-user process under which to execute our commands as root
            Log.d(TAG, TAGG + "Starting super-user shell session...");
            process = Runtime.getRuntime().exec("su");

            // Get process streams
            stdin = process.getOutputStream();
            stdout = process.getInputStream();
            stderr = process.getErrorStream();

            // Construct and execute command (new-line is like hitting enter)
            Log.d(TAG, TAGG + "Stopping app via activity manager...");
            stdin.write(("am force-stop "+appPackageName+"\n").getBytes());

            // Exit the shell
            stdin.write(("exit\n").getBytes());

            // Flush and close the stdin stream
            stdin.flush();
            stdin.close();

            // Read output of the executed command
            Log.v(TAG, TAGG+"Reading output of executed command...");
            br = new BufferedReader(new InputStreamReader(stdout));
            while ((line = br.readLine()) != null) {
                Log.v(TAG, TAGG+"stdout line: "+line);
            }
            br.close();

            // Read error stream of the executed command
            Log.v(TAG, TAGG+"Reading errors of executed command...");
            br = new BufferedReader(new InputStreamReader(stderr));
            while ((line = br.readLine()) != null) {
                Log.w(TAG, TAGG+"stderr line: "+line);
                ret = false;
            }
            br.close();

            // Wait for process to finish
            process.waitFor();
            process.destroy();

        //} catch(SecurityException e) {
        //    Log.e(TAG, TAGG +"Security Exception caught: " + String.valueOf(e));
        } catch (Exception e) {
            Log.e(TAG, TAGG + "Exception caught: " + e.getMessage());
        }

        // Cleanup
        if (process != null) {
            process.destroy();
        }

        // Return
        Log.v(TAG, TAGG+"Returning: "+String.valueOf(ret));
        return ret;
    }

    /** Start the main app.
     * Requires root.
     * Note: Running this is also the proper way to bring a background app to the foreground.
     */
    public void startEvolutionApp() {
        final String TAGG = "startEvolutionApp: ";

        String appPackageName, appClassToStart;

        try {
            // Get app information
            appPackageName = appContext.getResources().getString(R.string.appPackageName_evolution);
            appClassToStart = appContext.getResources().getString(R.string.startupClass_evolution);

            // Check whether app is currently running
            if (specifiedAppProcessIsRunning(appPackageName)) {
                Log.i(TAG, TAGG+"App appears to already be running. Could just be in the background.");
            }

            // Try to start the app
            Log.d(TAG, TAGG + "Starting/foregrounding app...");
            startSpecifiedApp(appPackageName, appClassToStart);
        } catch (Exception e) {
            Log.e(TAG, TAGG + "Exception caught: " + e.getMessage());
        }
    }

    /** Stop the main app.
     * Requires root.
     */
    public void stopEvolutionApp() {
        final String TAGG = "stopEvolutionApp: ";

        String appPackageName;

        try {
            // Get app information
            appPackageName = appContext.getResources().getString(R.string.appPackageName_evolution);

            // Try to stop the app
            Log.d(TAG, TAGG + "Stopping app...");
            stopSpecifiedApp(appPackageName);
        } catch (Exception e) {
            Log.e(TAG, TAGG + "Exception caught: " + e.getMessage());
        }
    }

    /** Stop the watchdog app.
     * Requires root.
     */
    public void stopEvolutionWatchdogApp() {
        final String TAGG = "stopEvolutionWatchdogApp: ";

        String appPackageName;

        try {
            // Get app information
            appPackageName = appContext.getResources().getString(R.string.appPackageName_evolutionWatchdog);

            // Try to stop the app
            Log.d(TAG, TAGG + "Stopping app...");
            stopSpecifiedApp(appPackageName);
        } catch (Exception e) {
            Log.e(TAG, TAGG + "Exception caught: " + e.getMessage());
        }
    }

    /** Bring main app to foreground.
     * Requires root.
     * This is intended to bring the main app (if in the background or recents) to the foreground.
     * It's accomplished through the same command you use to start an app, BTW. Just wrapping it here for ease-of-understanding.
     */
    public void foregroundEvolutionApp() {
        final String TAGG = "foregroundEvolutionApp: ";

        Log.v(TAG, TAGG+"Foregrounding evolution main app.");
        startEvolutionApp();
    }

    /** Determine and return the complete filename and path of the specified app's shared prefs.
     * Requires root.
     * Returns null if can't figure out, error, or no way to know.
     */
    public String determineAppSharedPrefsPathAndFile(String appPackageName) {
        final String TAGG = "determineAppSharedPrefsPathAndFile: ";

        String ret = null;

        try {
            if (appPackageName.equals(appContext.getResources().getString(R.string.appPackageName_evolution))) {
                Log.v(TAG, TAGG + "Constructing shared prefs filename and path for " + appPackageName);
                ret = appContext.getResources().getString(R.string.appDataPath_evolution) + "/" +
                        appContext.getResources().getString(R.string.sharedPrefsFileSubdir_evolution) + "/" +
                        appContext.getResources().getString(R.string.sharedPrefsFilename_evolution);
            } else {
                Log.w(TAG, TAGG + "Unhandled for " + appPackageName);
            }
        } catch (Exception e) {
            Log.e(TAG, TAGG + "Exception caught for " + appPackageName + ": "+ e.getMessage());
        }

        Log.v(TAG, TAGG+"Returning: "+ String.valueOf(ret));
        return ret;
    }

    /** Returns the server IP configured for Omni (using programatic methods -may not work).
     * First tries evolution main-app's shared-prefs, then falls back to provisioning data.
     * Provisioning data should at least be available if this app is here.
     * Returns null if can't figure out, error, or no way to know.
     */
    public String getSharedPrefsServerIPv4_evolution() {
        final String TAGG = "getSharedPrefsServerIPv4_evolution: ";

        String ret = null;

        try {
            int eventType;
            boolean done = false;
            String currentTag;
            String currentAttribute;

            // Get app's shared-prefs XML filename and path
            String sharedPrefsFile = determineAppSharedPrefsPathAndFile(appContext.getResources().getString(R.string.appPackageName_evolution));

            // Get some XML parser objects and feed in our shared-prefs XML file
            XmlPullParserFactory xmlPullParserFactory = XmlPullParserFactory.newInstance();
            XmlPullParser xpp = xmlPullParserFactory.newPullParser();
            xpp.setInput( new FileReader(sharedPrefsFile) );

            // Parse the file for server info
            /* Condensed example of XML file:
            <?xml version='1.0' encoding='utf-8' standalone='yes' ?>
            <map>
                <string name="serverIPv4">192.168.1.58</string>
            </map>
             */
            String spKeyname_serverIP = appContext.getResources().getString(R.string.sharedPrefsValueKey_serverIP);
            eventType = xpp.getEventType();
            while (eventType != XmlPullParser.END_DOCUMENT && !done) {
                if (eventType == XmlPullParser.START_TAG) {
                    currentTag = xpp.getName();
                    if (currentTag.equalsIgnoreCase("string")) {
                        currentAttribute = xpp.getAttributeValue(null, "name");
                        if (currentAttribute.equals(spKeyname_serverIP)) {
                            ret = xpp.getText();
                            done = true;
                        }
                    }
                }
                eventType = xpp.next();
            }
        } catch (Exception e) {
            Log.e(TAG, TAGG + "Exception caught: "+ e.getMessage());
        }

        Log.v(TAG, TAGG+"Returning: "+ String.valueOf(ret));
        return ret;
    }

    /** Returns the server IP configured for Omni (using super user access).
     * First tries evolution main-app's shared-prefs, then falls back to provisioning data TODO
     * Provisioning data should at least be available if this app is here.
     * Returns null if can't figure out, error, or no way to know.
     */
    public String getSharedPrefsServerIPv4_su_evolution() {
        final String TAGG = "getSharedPrefsServerIPv4_su_evolution: ";

        String ret = null;
        Process process;
        OutputStream stdin;     //used to write commands to shell... using OutputStream type, we can execute commands like writing commands in terminal
        InputStream stdout;     //used to read output of a command we executed... using InputStream type, we can input command's output to our routine here
        InputStream stderr;     //used to read errors of a command we executed... using InputStream type, we can input command's errors to our routine here
        BufferedReader br;
        String line;

        try {
            // Get the search criteria (ex. <string name="serverIPv4">192.168.1.58</string>)
            String spKeyname_serverIP = appContext.getResources().getString(R.string.sharedPrefsValueKey_serverIP);

            // Get app's shared-prefs XML filename and path (ex. /data/user/0/com.messagenetsystems.evolution/shared_prefs/com.messagenetsystems.evolution_preferences.xml)
            String sharedPrefsFile = determineAppSharedPrefsPathAndFile(appContext.getResources().getString(R.string.appPackageName_evolution));

            // Start a super-user process under which to execute our commands as root
            Log.d(TAG, TAGG + "Starting super-user shell session...");
            process = Runtime.getRuntime().exec("su");

            // Get process streams
            stdin = process.getOutputStream();
            stdout = process.getInputStream();
            stderr = process.getErrorStream();

            // Construct and execute command (new-line is like hitting enter)
            Log.d(TAG, TAGG + "Looking in shared prefs file for configured server IP...");
            stdin.write(("/system/bin/grep "+spKeyname_serverIP+" "+sharedPrefsFile+" | /system/bin/cut -d'>' -f2 | /system/bin/cut -d'<' -f1\n").getBytes());

            // Exit the shell
            stdin.write(("exit\n").getBytes());

            // Flush and close the stdin stream
            stdin.flush();
            stdin.close();

            // Read output of the executed command
            Log.v(TAG, TAGG+"Reading output of executed command...");
            br = new BufferedReader(new InputStreamReader(stdout));
            while ((line = br.readLine()) != null) {
                Log.v(TAG, TAGG+"stdout line: "+line);
                ret = line;
            }
            br.close();

            // Read error stream of the executed command
            Log.v(TAG, TAGG+"Reading errors of executed command...");
            br = new BufferedReader(new InputStreamReader(stderr));
            while ((line = br.readLine()) != null) {
                Log.w(TAG, TAGG+"stderr line: "+line);
            }
            br.close();

            // Wait for process to finish
            process.waitFor();
            process.destroy();
        } catch (Exception e) {
            Log.e(TAG, TAGG + "Exception caught: "+ e.getMessage());
        }

        Log.v(TAG, TAGG+"Returning: "+ String.valueOf(ret));
        return ret;
    }

    /** Returns the device ID configured for Omni (using super user access).
     * First tries evolution main-app's shared-prefs.
     * Provisioning data should at least be available if this app is here.
     * Returns null if can't figure out, error, or no way to know.
     */
    public String getSharedPrefsDeviceID_su_evolution() {
        final String TAGG = "getSharedPrefsDeviceID_su_evolution: ";

        String ret = null;
        Process process;
        OutputStream stdin;     //used to write commands to shell... using OutputStream type, we can execute commands like writing commands in terminal
        InputStream stdout;     //used to read output of a command we executed... using InputStream type, we can input command's output to our routine here
        InputStream stderr;     //used to read errors of a command we executed... using InputStream type, we can input command's errors to our routine here
        BufferedReader br;
        String line;

        try {
            // Get the search criteria (ex. <string name="serverIPv4">192.168.1.58</string>)
            String spKeyname_deviceID = appContext.getResources().getString(R.string.sharedPrefsValueKey_deviceID);

            // Get app's shared-prefs XML filename and path (ex. /data/user/0/com.messagenetsystems.evolution/shared_prefs/com.messagenetsystems.evolution_preferences.xml)
            String sharedPrefsFile = determineAppSharedPrefsPathAndFile(appContext.getResources().getString(R.string.appPackageName_evolution));

            // Start a super-user process under which to execute our commands as root
            Log.d(TAG, TAGG + "Starting super-user shell session...");
            process = Runtime.getRuntime().exec("su");

            // Get process streams
            stdin = process.getOutputStream();
            stdout = process.getInputStream();
            stderr = process.getErrorStream();

            // Construct and execute command (new-line is like hitting enter)
            Log.d(TAG, TAGG + "Looking in shared prefs file for configured device ID...");
            stdin.write(("/system/bin/grep "+spKeyname_deviceID+" "+sharedPrefsFile+" | /system/bin/cut -d'>' -f2 | /system/bin/cut -d'<' -f1\n").getBytes());

            // Exit the shell
            stdin.write(("exit\n").getBytes());

            // Flush and close the stdin stream
            stdin.flush();
            stdin.close();

            // Read output of the executed command
            Log.v(TAG, TAGG+"Reading output of executed command...");
            br = new BufferedReader(new InputStreamReader(stdout));
            while ((line = br.readLine()) != null) {
                Log.v(TAG, TAGG+"stdout line: "+line);
                ret = line;
            }
            br.close();

            // Read error stream of the executed command
            Log.v(TAG, TAGG+"Reading errors of executed command...");
            br = new BufferedReader(new InputStreamReader(stderr));
            while ((line = br.readLine()) != null) {
                Log.w(TAG, TAGG+"stderr line: "+line);
            }
            br.close();

            // Wait for process to finish
            process.waitFor();
            process.destroy();
        } catch (Exception e) {
            Log.e(TAG, TAGG + "Exception caught: "+ e.getMessage());
        }

        Log.v(TAG, TAGG+"Returning: "+ String.valueOf(ret));
        return ret;
    }

    /** Requests expected checksum value for package update file from server.
     * This just downloads the md5 value file from the server and reads it in with an async thread.
     * The server should have generated that file for the package file.
     * Once a result is read, the async thread will execute the followup method. */
    public final static String CHECKSUM_REQUEST_SUBMITTED = "CHECKSUM_REQUEST_SUBMITTED";
    public String requestChecksumForPackageUpdateFileFromServer(final String appPackageName) {
        final String TAGG = "requestChecksumForPackageUpdateFileFromServer: ";
        Log.v(TAG, TAGG+"Invoked.");

        String ret = null;

        try {
            boolean okToContinue = true;
            String serverPath = appContext.getResources().getString(R.string.updatePackageServerPath);  //~silentm

            String serverChecksumFile = "";
            if (appPackageName.equals(appContext.getResources().getString(R.string.appPackageName_evolution))) {
                serverChecksumFile = appContext.getResources().getString(R.string.packageFileChecksumFile_evolution);
            } else if (appPackageName.equals(appContext.getResources().getString(R.string.appPackageName_evolutionWatchdog))) {
                serverChecksumFile = appContext.getResources().getString(R.string.packageFileChecksumFile_evolutionWatchdog);
            } else {
                Log.w(TAG, TAGG+"No valid app package name provided or cannot determine checksum filename. Aborting.");
                okToContinue = false;
            }

            if (okToContinue) {
                final String checksumURL = "http://" + getSharedPrefsServerIPv4_su_evolution() + "/" + serverPath + "/" + serverChecksumFile;
                if (doesRemoteFileExist_http(checksumURL)) {

                    new Thread(new Runnable(){
                        ArrayList<String> readInLines = new ArrayList<>(); //to read each line
                        public void run(){
                            try {
                                URL url = new URL(checksumURL); //My text file location
                                HttpURLConnection conn=(HttpURLConnection) url.openConnection();
                                conn.setConnectTimeout(15 * 1000);
                                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                                String str;
                                while ((str = in.readLine()) != null) {
                                    readInLines.add(str);
                                }
                                in.close();

                                if (readInLines.size() == 0) {
                                    Log.w(TAG, TAGG+"Could not read checksum file from server");
                                } else if (readInLines.size() == 1) {
                                    Log.d(TAG, TAGG+"Checksum value apparently read from server: "+readInLines.get(0));
                                    requestChecksumForPackageUpdateFileFromServer_followUp(appPackageName, readInLines.get(0));
                                } else {
                                    Log.w(TAG, TAGG+"Unexpected case (more than 1 line read from server checksum file).");
                                }
                            } catch (Exception e) {
                                Log.d(TAG, TAGG+"Exception caught downloading checksum value file: "+e.getMessage());
                            }
                        }
                    }).start();

                    ret = CHECKSUM_REQUEST_SUBMITTED;
                    MainUpdaterService.serverChecksumRequestStatus = ret;

                } else {
                    Log.w(TAG, TAGG + "Checksum file doesn't appear to exist on server. Aborting.");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, TAGG + "Exception caught: "+ e.getMessage());
        }

        Log.v(TAG, TAGG+"Returning: \""+ ret +"\".");
        return ret;
    }
    public void requestChecksumForPackageUpdateFileFromServer_followUp(String appPackageName, String checksum) {
        final String TAGG = "requestChecksumForPackageUpdateFileFromServer_followUp: ";
        Log.v(TAG, TAGG+"Invoked.");

        MainUpdaterService.serverChecksumRequestStatus = checksum;
        //NOTE: Be sure to reset that variable when you're done with it!!!
    }

    /** Calculate MD5 checksum hash for specified file. */
    public String calculateChecksumForLocalFile(String filename) {
        final String TAGG = "calculateChecksumForLocalFile(\""+filename+"\"): ";
        Log.v(TAG, TAGG+"Invoked.");

        String ret = null;

        Process process = null;
        OutputStream stdin;     //used to write commands to shell... using OutputStream type, we can execute commands like writing commands in terminal
        InputStream stdout;     //used to read output of a command we executed... using InputStream type, we can input command's output to our routine here
        InputStream stderr;     //used to read errors of a command we executed... using InputStream type, we can input command's errors to our routine here
        BufferedReader br;
        String line;

        try {
            // Start a super-user process under which to execute our commands as root
            Log.d(TAG, TAGG + "Starting super-user shell session...");
            process = Runtime.getRuntime().exec("su");

            // Get process streams
            stdin = process.getOutputStream();
            stdout = process.getInputStream();
            stderr = process.getErrorStream();

            // Construct and execute command (new-line is like hitting enter)
            Log.d(TAG, TAGG + "Calculating MD5 checksum..");
            stdin.write(("/system/bin/md5sum "+filename+" | /system/bin/busybox awk '{printf $1}'\n").getBytes());

            // Exit the shell
            stdin.write(("exit\n").getBytes());

            // Flush and close the stdin stream
            stdin.flush();
            stdin.close();

            // Read output of the executed command
            Log.v(TAG, TAGG+"Reading output of executed command...");
            br = new BufferedReader(new InputStreamReader(stdout));
            while ((line = br.readLine()) != null) {
                Log.v(TAG, TAGG+"stdout line: "+line);
                ret = line;
            }
            br.close();

            // Read error stream of the executed command
            Log.v(TAG, TAGG+"Reading errors of executed command...");
            br = new BufferedReader(new InputStreamReader(stderr));
            while ((line = br.readLine()) != null) {
                Log.w(TAG, TAGG+"stderr line: "+line);
            }
            br.close();

            // Wait for process to finish
            process.waitFor();
            process.destroy();
        } catch (Exception e) {
            Log.e(TAG, TAGG + "Exception caught: "+ e.getMessage());
        }

        // Cleanup
        if (process != null) {
            process.destroy();
        }

        Log.v(TAG, TAGG+"Returning: \""+ ret +"\".");
        return ret;
    }

    /** Save file with information about most recent updated package attempt.
     * Call this after a package has been attempted updated, to mark the occasion.
     * Information will be saved in JSON format.
     * Revisions:
     *  2019.04.08      Chris Rider     Now support result reason logging. Changed to single package file with multiple log entries, to avoid spamming the /sdcard directory. */
    public final int UPDATE_INSTALLATION_RESULT_UNKNOWN = 0;
    public final int UPDATE_INSTALLATION_RESULT_SUCCESS = 1;
    public final int UPDATE_INSTALLATION_RESULT_FAILURE = 2;
    public void saveInstallationAttemptResultInfo(String appPackageName, int updateResult, @Nullable String resultReason) {
        final String TAGG = "saveInstallationAttemptResultInfo: ";
        Log.v(TAG, TAGG+"Invoked.");

        String savePath;
        String saveFile;
        String updateResultForJSON;
        JSONObject jsonObject;
        Process process;
        OutputStream stdin;     //used to write commands to shell... using OutputStream type, we can execute commands like writing commands in terminal
        InputStream stdout;     //used to read output of a command we executed... using InputStream type, we can input command's output to our routine here
        InputStream stderr;     //used to read errors of a command we executed... using InputStream type, we can input command's errors to our routine here
        BufferedReader br;
        String line;
        Date nowDate;
        String timestamp;

        if (resultReason == null) {
            resultReason = "(details not provided)";
        }

        try {
            nowDate = new Date();

            savePath = appContext.getResources().getString(R.string.updateFileDownloadPath);
            //saveFile = "packageInstallInfo_"+appPackageName+"_"+getDateTimeForFilename(nowDate)+".txt";   //DEPRECATED
            saveFile = "packageInstallInfo_"+appPackageName+".txt";

            if (updateResult == UPDATE_INSTALLATION_RESULT_SUCCESS) {
                updateResultForJSON = "success";
            } else if (updateResult == UPDATE_INSTALLATION_RESULT_FAILURE) {
                updateResultForJSON = "failure";
            } else {
                updateResultForJSON = "unknown";
            }

            jsonObject = new JSONObject();
            try {
                // Populate the JSON object with our data
                jsonObject.put("timestamp", getDateTimeForLogString(nowDate));
                jsonObject.put("appPackageName", appPackageName);
                jsonObject.put("updateResult", updateResultForJSON);
                jsonObject.put("packageFileMD5", calculateChecksumForLocalFile(savePath+"/"+appPackageName+".apk"));
                jsonObject.put("resultReason", resultReason);
            } catch (JSONException je) {
                Log.e(TAG, TAGG + "Exception caught populating JSON: "+ je.getMessage());
            }

            // Save that as a string to the file (we use rooted unix method to be safe and avoid permission issues)...

            // Start a super-user process under which to execute our commands as root
            Log.d(TAG, TAGG + "Starting super-user shell session...");
            process = Runtime.getRuntime().exec("su");

            // Get process streams
            stdin = process.getOutputStream();
            stdout = process.getInputStream();
            stderr = process.getErrorStream();

            // Construct and execute command (new-line is like hitting enter)
            //stdin.write(("/system/bin/echo '"+jsonObject.toString()+"' > "+savePath+"/"+saveFile+"\n").getBytes());   //DEPRECATED
            stdin.write(("/system/bin/echo '"+jsonObject.toString()+"' >> "+savePath+"/"+saveFile+"\n").getBytes());

            // Exit the shell
            stdin.write(("exit\n").getBytes());

            // Flush and close the stdin stream
            stdin.flush();
            stdin.close();

            // Read output of the executed command
            Log.v(TAG, TAGG+"Reading output of executed command...");
            br = new BufferedReader(new InputStreamReader(stdout));
            while ((line = br.readLine()) != null) {
                Log.v(TAG, TAGG+"  stdout line: "+line);
            }
            br.close();

            // Read error stream of the executed command
            Log.v(TAG, TAGG+"Reading errors of executed command...");
            br = new BufferedReader(new InputStreamReader(stderr));
            while ((line = br.readLine()) != null) {
                Log.w(TAG, TAGG+"  stderr line: "+line);
            }
            br.close();

            // Wait for process to finish
            process.waitFor();
            process.destroy();
        } catch (Exception e) {
            Log.e(TAG, TAGG + "Exception caught: "+ e.getMessage());
        }

        jsonObject = null;
    }

    /** Returns a string with the current date/time (or provided Date) appropriate for saving as part of a filename. */
    private String getDateTimeForFilename(Date dateObj) {
        final String TAGG = "getDateTimeForFilename: ";
        Log.v(TAG, TAGG+"Invoked.");

        String ret = "000000000000";
        final String simpleDateFormatPattern = "yyyyMMddHHmm";     //ex. 201812312359

        try {
            // Finalize date (use current if not provided)
            if (dateObj == null) {
                dateObj = new Date();
            }

            // Determine date string
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat(simpleDateFormatPattern);
            ret = simpleDateFormat.format(dateObj);
        } catch (Exception e) {
            Log.e(TAG, TAGG + "Exception caught: "+ e.getMessage());
        }

        Log.v(TAG, TAGG+"Returning: \""+ ret +"\".");
        return ret;
    }

    /** Returns a string with the current date/time (or provided Date) appropriate for saving as part of a log entry string. */
    private String getDateTimeForLogString(Date dateObj) {
        final String TAGG = "getDateTimeForLogString: ";
        Log.v(TAG, TAGG+"Invoked.");

        String ret = "0000-00-00_00:00";
        final String simpleDateFormatPattern = "yyyy-MM-dd_HH:mm";     //ex. 2018-12-31_23:59

        try {
            // Finalize date (use current if not provided)
            if (dateObj == null) {
                dateObj = new Date();
            }

            // Determine date string
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat(simpleDateFormatPattern);
            ret = simpleDateFormat.format(dateObj);
        } catch (Exception e) {
            Log.e(TAG, TAGG + "Exception caught: "+ e.getMessage());
        }

        Log.v(TAG, TAGG+"Returning: \""+ ret +"\".");
        return ret;
    }

    /** Read and return string contents from specified File. */
    public String getFileContents(final File file) throws IOException {
        final InputStream inputStream = new FileInputStream(file);
        final BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));

        final StringBuilder stringBuilder = new StringBuilder();

        boolean done = false;

        while (!done) {
            final String line = reader.readLine();
            done = (line == null);

            if (line != null) {
                stringBuilder.append(line);
            }
        }

        reader.close();
        inputStream.close();

        return stringBuilder.toString();
    }

    /** Read checksum value from specified local md5 file */
    public String readChecksumValueFromChecksumFile(String appPackageName) {
        final String TAGG = "readChecksumValueFromChecksumFile(\""+appPackageName+"\"): ";
        Log.v(TAG, TAGG+"Invoked.");

        String ret = null;
        String localPath;
        String localFile;
        File fileObject;

        try {
            localPath = appContext.getResources().getString(R.string.updateFileDownloadPath);
            localFile = appPackageName+".md5";

            fileObject = new File(localPath+"/"+localFile);

            ret = getFileContents(fileObject);
        } catch (Exception e) {
            Log.e(TAG, TAGG + "Exception caught: "+ e.getMessage());
        }

        Log.v(TAG, TAGG+"Returning: \""+ ret +"\".");
        return ret;
    }

    /** Read text from specified server-side file.
     * Returns empty or an actual value. */
    public String readTextFromServerFile(String serverFileURL) {
        final String TAGG = "readTextFromServerFile(\""+serverFileURL+"\")";
        Log.v(TAG, TAGG+"Invoked.");

        StringBuilder ret = new StringBuilder();
        InputStreamReader inputStreamReader = null;
        BufferedReader bufferedReader = null;

        if (isNetworkAvailable()) {
            try {
                URL url = new URL(serverFileURL);
                inputStreamReader = new InputStreamReader(url.openStream());
                bufferedReader = new BufferedReader(inputStreamReader);
                String line;
                while ((line = bufferedReader.readLine()) != null) {
                    //DEV-NOTE: line is one line of text; readLine() strips the newline character(s)
                    ret.append(line);
                }
            } catch (MalformedURLException mue) {
                Log.w(TAG, TAGG + "Malformed URL exception caught: " + mue.getMessage());
            } catch (IOException ioe) {
                Log.w(TAG, TAGG + "IO exception caught: " + ioe.getMessage());
            } catch (Exception e) {
                Log.w(TAG, TAGG + "Exception caught: " + e.getMessage());
            } finally {
                try {
                    bufferedReader.close();
                    inputStreamReader.close();
                } catch (Exception e) {
                    Log.w(TAG, TAGG + "Exception caught trying to close resources: " + e.getMessage());
                }
            }
        } else {
            Log.i(TAG, TAGG+"Network unavailable.");
        }

        Log.v(TAG, TAGG+"Returning \""+ret+"\".");
        return String.valueOf(ret);
    }

    /** Check whether local package file exists.
     */
    public boolean localPackageFileExists(String appPackageName) {
        final String TAGG = "localPackageFileExists(\""+appPackageName+"\"): ";
        Log.v(TAG, TAGG+"Invoked.");

        boolean ret = false;
        String localPath;

        try {
            localPath = appContext.getResources().getString(R.string.updateFileDownloadPath);
            ret = new File(localPath+"/"+appPackageName+".apk").exists();
        } catch (Exception e) {
            Log.e(TAG, TAGG + "Exception caught: "+ e.getMessage());
        }

        Log.v(TAG, TAGG+"Returning: "+ String.valueOf(ret) +".");
        return ret;
    }

    /** Check whether local package file is valid.
     * Depends on there being a corresponding md5 file along with it.
     */
    public boolean localPackageFileIsValid(String appPackageName) {
        final String TAGG = "localPackageFileIsValid(\""+appPackageName+"\"): ";
        Log.v(TAG, TAGG+"Invoked.");

        boolean ret = false;
        String localPath;
        String expectedChecksumValue;
        String calculatedChecksumValue;

        try {
            localPath = appContext.getResources().getString(R.string.updateFileDownloadPath);

            // Read expected checksum from local corresponding checksum file
            expectedChecksumValue = readChecksumValueFromChecksumFile(appPackageName);

            // Calculate checksum for local package file
            calculatedChecksumValue = calculateChecksumForLocalFile(localPath+"/"+appPackageName+".apk");

            // Do they match?
            if (expectedChecksumValue.equals(calculatedChecksumValue)) {
                //match!
                Log.i(TAG, TAGG+"Valid!");
                ret = true;
            } else {
                //no match
                Log.i(TAG, TAGG+"Invalid!");
                ret = false;
            }
        } catch (Exception e) {
            Log.e(TAG, TAGG + "Exception caught: "+ e.getMessage());
        }

        Log.v(TAG, TAGG+"Returning: \""+ ret +"\".");
        return ret;
    }

    /** Get the APK path of an installed app. */
    public String getPathForInstalledAPK(String packageName) {
        final String TAGG = "getPathForInstalledAPK(\""+packageName+"\"): ";
        Log.v(TAG, TAGG+"Invoked.");

        String ret = null;

        Process process = null;
        OutputStream stdin;     //used to write commands to shell... using OutputStream type, we can execute commands like writing commands in terminal
        InputStream stdout;     //used to read output of a command we executed... using InputStream type, we can input command's output to our routine here
        InputStream stderr;     //used to read errors of a command we executed... using InputStream type, we can input command's errors to our routine here
        BufferedReader br;
        String line;

        try {
            // Start a super-user process under which to execute our commands as root
            Log.d(TAG, TAGG + "Starting super-user shell session...");
            process = Runtime.getRuntime().exec("su");

            // Get process streams
            stdin = process.getOutputStream();
            stdout = process.getInputStream();
            stderr = process.getErrorStream();

            // Construct and execute command (new-line is like hitting enter)
            Log.d(TAG, TAGG + "Using PackageManager to find path to APK of installed package...");
            stdin.write(("/system/bin/pm path "+packageName+" | /system/bin/cut -d':' -f2\n").getBytes());

            // Exit the shell
            stdin.write(("exit\n").getBytes());

            // Flush and close the stdin stream
            stdin.flush();
            stdin.close();

            // Read output of the executed command
            Log.v(TAG, TAGG+"Reading output of executed command...");
            br = new BufferedReader(new InputStreamReader(stdout));
            while ((line = br.readLine()) != null) {
                Log.v(TAG, TAGG+"stdout line: "+line);
                ret = line;
            }
            br.close();

            // Read error stream of the executed command
            Log.v(TAG, TAGG+"Reading errors of executed command...");
            br = new BufferedReader(new InputStreamReader(stderr));
            while ((line = br.readLine()) != null) {
                Log.w(TAG, TAGG+"stderr line: "+line);
            }
            br.close();

            // Wait for process to finish
            process.waitFor();
            process.destroy();
        } catch (Exception e) {
            Log.e(TAG, TAGG + "Exception caught: "+ e.getMessage());
        }

        // Cleanup
        if (process != null) {
            process.destroy();
        }

        Log.v(TAG, TAGG+"Returning: \""+ String.valueOf(ret) +"\".");
        return ret;
    }

    /** Install the specified package APK file.
     * Note: requires the entire path included.
     * Note: it's probably a good idea to stop the app first.
     * Revisions:
     *  2019.04.08      Chris Rider     Now returning result string instead of boolean, so we can return actual error from shell if one happened. */
    public static final String INSTALL_PACKAGE_RESULT_UNKNOWN = "SystemFunctions.installPackage(): Unknown";
    public static final String INSTALL_PACKAGE_RESULT_SUCCESS = "SystemFunctions.installPackage(): Success";
    public String installPackage(String packageFile) {
        final String TAGG = "installPackage(\""+packageFile+"\"): ";
        Log.v(TAG, TAGG+"Invoked.");

        String ret = INSTALL_PACKAGE_RESULT_UNKNOWN;

        Process process = null;
        OutputStream stdin;     //used to write commands to shell... using OutputStream type, we can execute commands like writing commands in terminal
        InputStream stdout;     //used to read output of a command we executed... using InputStream type, we can input command's output to our routine here
        InputStream stderr;     //used to read errors of a command we executed... using InputStream type, we can input command's errors to our routine here
        BufferedReader br;
        String line;

        try {
            // Start a super-user process under which to execute our commands as root
            Log.d(TAG, TAGG + "Starting super-user shell session...");
            process = Runtime.getRuntime().exec("su");

            // Get process streams
            stdin = process.getOutputStream();
            stdout = process.getInputStream();
            stderr = process.getErrorStream();

            // Construct and execute command (new-line is like hitting enter)
            Log.d(TAG, TAGG + "Using PackageManager to install the package...");
            stdin.write(("/system/bin/pm install -t -r -d "+packageFile+"\n").getBytes());
            //stdin.write(("/system/bin/pm install -t -r -d -g"+packageFile+"\n").getBytes());      //WARNING! the -g parameter causes failure.. you can run it via SSH manually, but not from this routine for some reason

            // Exit the shell
            stdin.write(("exit\n").getBytes());

            // Flush and close the stdin stream
            stdin.flush();
            stdin.close();

            // Read output of the executed command
            Log.v(TAG, TAGG+"Reading output of executed command...");
            br = new BufferedReader(new InputStreamReader(stdout));
            while ((line = br.readLine()) != null) {
                Log.v(TAG, TAGG+" stdout line: "+line);
                if (line.contains("Success")) {
                    ret = INSTALL_PACKAGE_RESULT_SUCCESS;
                }
            }
            br.close();

            // Read error stream of the executed command
            // Note: This is designed to override any result above, since there should be nothing here upon real/actual success. We only want a strict/explicit success to count!
            Log.v(TAG, TAGG+"Reading errors of executed command...");
            br = new BufferedReader(new InputStreamReader(stderr));
            while ((line = br.readLine()) != null) {
                Log.w(TAG, TAGG+" stderr line: "+line);
                if (ret.equals(INSTALL_PACKAGE_RESULT_SUCCESS)) {
                    //this hopefully shouldn't happen (upon success, there shouldn't be anything in the error stream!), but just in case...
                    Log.w(TAG, TAGG+" The standard output stream reported success, but there is also stuff in error stream ("+String.valueOf(line)+"). Returning unknown.");
                    ret = INSTALL_PACKAGE_RESULT_UNKNOWN + " (succeeded, but error stream output: \""+String.valueOf(line)+"\")";
                } else {
                    ret = String.valueOf(line);
                }
            }
            br.close();

            // Wait for process to finish
            process.waitFor();
            process.destroy();
        } catch (Exception e) {
            Log.e(TAG, TAGG + "Exception caught: "+ e.getMessage());
        }

        // Cleanup
        if (process != null) {
            process.destroy();
        }

        Log.v(TAG, TAGG+"Returning: \""+ ret +"\".");
        return ret;
    }

    /** Install the specified package APK file as a system app.
     * Note: requires the entire path included.
     * NOTE: This assumes app has always been a system-app (isn't in the /data partition).
     * NOTE: Requires a reboot to finish actual installation. Not my rule, just how Android works.
     * Revisions:
     *  2019.10.10      Chris Rider     Created. */
    public String installPackage_systemApp(String packageFile, String packageFilename) {
        final String TAGG = "installPackage_systemApp(\""+packageFile+"\"): ";
        Log.v(TAG, TAGG+"Invoked.");

        String ret = INSTALL_PACKAGE_RESULT_UNKNOWN;

        Process process = null;
        OutputStream stdin;     //used to write commands to shell... using OutputStream type, we can execute commands like writing commands in terminal

        boolean continueInstallation = false;

        // Before attempting anything, try to remount /system as read-write (can't do anything if that doesn't work)...
        try {
            if (mountSystemRW()) {
                Log.v(TAG, TAGG+"System partition was remounted RW, so continuing...");
                continueInstallation = true;
            } else {
                Log.w(TAG, TAGG+"System partition was NOT remounted RW, so aborting!");
            }
        } catch (Exception e) {
            Log.e(TAG, TAGG + "Exception caught: "+ e.getMessage());
        }

        // Only continue if we explicitly indicated it's worthwhile to do so...
        if (continueInstallation) {
            try {
                // Start a super-user process under which to execute our commands as root
                Log.d(TAG, TAGG + "Starting super-user shell session...");
                process = Runtime.getRuntime().exec("su");

                // Get process streams
                stdin = process.getOutputStream();

                // Construct and execute command (new-line is like hitting enter)
                Log.d(TAG, TAGG + "Copying package file to system directory so it can install during next reboot...\nCmd: /system/bin/cp " + packageFile + " /system/priv-app/"+packageFilename+"");
                stdin.write(("/system/bin/cp " + packageFile + " /system/priv-app/"+packageFilename+"\n").getBytes());

                // Exit the shell
                stdin.write(("exit\n").getBytes());

                // Flush and close the stdin stream
                stdin.flush();
                stdin.close();

                // Wait for process to finish
                process.waitFor();
                process.destroy();
            } catch (Exception e) {
                Log.e(TAG, TAGG + "Exception caught: " + e.getMessage());
            }

            // Cleanup
            if (process != null) {
                process.destroy();
            }

            // Check whether copy succeeded
            try {
                String checksumSource = calculateChecksumForLocalFile(packageFile);
                String checksumDestination = calculateChecksumForLocalFile("/system/priv-app/" + packageFilename);
                if (checksumSource.equals(checksumDestination)) {
                    ret = INSTALL_PACKAGE_RESULT_SUCCESS;
                }
            } catch (Exception e) {
                Log.w(TAG, TAGG+"Exception caught checking file copy: "+e.getMessage());
            }
        } else {
            Log.w(TAG, TAGG+"Explicit continue flag is false, so aborting!");
        }

        Log.v(TAG, TAGG+"Returning: \""+ ret +"\".");
        return ret;
    }

    private boolean mountSystemRW() {
        final String TAGG = "mountSystemRW: ";
        Log.v(TAG, TAGG+"Invoked.");

        Boolean retSuccess = false;
        Process proc = null;

        try {// Check mount for RW (provides result for stdout below in order to check)
            //stdin.write(("/system/bin/mount | /system/bin/grep system\n").getBytes());

            Log.d(TAG, TAGG + "Attempting remount of /system partition with read-write access...");
            proc = Runtime.getRuntime().exec("su -c /system/bin/mount -o rw,remount -t ext4 /system");

            if ((proc.waitFor() != 0)) {
                throw new SecurityException();
            }
        } catch (IOException e) {
            Log.e(TAG, TAGG + "IO Exception caught: " + e.getMessage());
        } catch (InterruptedException e) {
            Log.e(TAG, TAGG + "Interrupted Exception caught: " + e.getMessage());
        } catch (SecurityException e) {
            Log.e(TAG, TAGG + "Security Exception caught: " + e.getMessage());
        } catch (Exception e) {
            Log.e(TAG, TAGG + "Exception caught: "+ e.getMessage());
        }

        // Cleanup
        if (proc != null) {
            try {
                proc.destroy();
            } catch (Exception e) {
                Log.w(TAG, TAGG+"Exception caught while cleaning up: "+e.getMessage());
            }
        }

        // Check result and return
        retSuccess = check_mountSystemRW();
        Log.v(TAG, TAGG+"Returning: \""+ String.valueOf(retSuccess) +"\".");
        return retSuccess;
    }

    private boolean check_mountSystemRW() {
        final String TAGG = "check_mountSystemRW: ";
        Log.v(TAG, TAGG+"Invoked.");

        Boolean retWhetherRW = false;
        Process process = null;
        OutputStream stdin;     //used to write commands to shell... using OutputStream type, we can execute commands like writing commands in terminal
        InputStream stdout;     //used to read output of a command we executed... using InputStream type, we can input command's output to our routine here
        BufferedReader br;
        String line;

        try {
            // Start a super-user process under which to execute our commands as root
            Log.d(TAG, TAGG + "Starting super-user shell session...");
            process = Runtime.getRuntime().exec("su");

            // Get process streams
            stdin = process.getOutputStream();
            stdout = process.getInputStream();

            // Construct and execute command (new-line is like hitting enter)
            stdin.write(("/system/bin/mount | /system/bin/grep system\n").getBytes());

            // Exit the shell
            stdin.write(("exit\n").getBytes());

            // Flush and close the stdin stream
            stdin.flush();
            stdin.close();

            // Read output of the executed command
            Log.v(TAG, TAGG + "Reading output of executed command...");
            br = new BufferedReader(new InputStreamReader(stdout));
            while ((line = br.readLine()) != null) {
                Log.v(TAG, TAGG + " stdout line: " + line);
                if (line.contains("(rw,")) {
                    retWhetherRW = true;
                }
            }
            br.close();

            // Wait for process to finish
            process.waitFor();
            process.destroy();
        } catch (Exception e) {
            Log.e(TAG, TAGG + "Exception caught: "+ e.getMessage());
        }

        // Cleanup
        if (process != null) {
            try {
                process.destroy();
            } catch (Exception e) {
                Log.w(TAG, TAGG+"Exception caught while cleaning up: "+e.getMessage());
            }
        }

        Log.v(TAG, TAGG+"Returning: \""+ String.valueOf(retWhetherRW) +"\".");
        return retWhetherRW;
    }

    /** Send a reboot command to the device.
     * Requires root.
     * Set overrideDisallowReboot to true, if you want to force reboot (even if strings.xml disables it).
     * That, for example, might be desired for daily reboots.
     */
    public void doReboot(/*final boolean showAlertBeforeReboot, final boolean overrideDisallowReboot, final boolean dumpLogcat*/) {
        final String TAGG = "doReboot: ";

        if (getRuntimeFlag_rebootIsDisallowed()) {
            Log.w(TAG, TAGG+"Runtime flag set for reboots-disallowed!");
        } else {
            // Do the reboot here and now
            try {
                // DEV-NOTE: These all work, but trying different ones to see which is best (ex. prevent boot to recovery, etc.)
                //  busybox halt: probably just stops all CPU processes --do NOT use halt, you must manually hold power button to turn back on!! power-resume auto-boot won't work!!
                //  busybox poweroff: normally would send ACPI signal to instruct system to power down
                //TODO: make smart enough to know if power is connected or not

                // This was the original command...
                //Process proc = Runtime.getRuntime().exec("/system/bin/reboot -p");

                // These were experiments to see if we can improve things...
                //Process proc = Runtime.getRuntime().exec("su -c /system/bin/setprop sys.powerctl shutdown");
                //Process proc = Runtime.getRuntime().exec("su -c /system/bin/svc power shutdown");                                         //Graceful? (shows "power off / shutting down" on screen)     //used up through 1.1.0
                //Process proc = Runtime.getRuntime().exec("su -c /system/bin/am start -a android.intent.action.ACTION_REQUEST_SHUTDOWN");  //Graceful? (shows "power off / shutting down" on screen)

                // This sometimes seems to halt the system completely where you have to push the button...
                //Process proc = Runtime.getRuntime().exec("su -c /system/bin/busybox poweroff -d 8 -f");                                     //-f avoids using init, and delay might be good to give time for things to quit

                // This resulted in immediate problems (probably because it's a reboot instead of shutdown command
                //Process proc = Runtime.getRuntime().exec("su -c /system/bin/busybox reboot -d 10 -f");

                // THIS WORKS STABLE
                Process proc = Runtime.getRuntime().exec(
                        // Run all the following as root...
                        "su -c " +

                                // Stop apps...
                                "/system/bin/am force-stop com.messagenetsystems.evolution && " +
                                "/system/bin/am force-stop com.messagenetsystems.evolutionwatchdog && " +
                                "/system/bin/am force-stop com.messagenetsystems.evolutionflasherlights && " +
                                "/system/bin/sleep 3 && " +

                                // Long-press the power button...
                                // (for some reason, this also launches the camera app, so close it afterward)
                                "/system/bin/input keyevent --longpress KEYCODE_POWER && " +
                                "/system/bin/sleep 1 && " +
                                "/system/bin/am force-stop com.android.camera2 && " +
                                "/system/bin/sleep 6 && " +

                                // Tap the "power off" option
                                "/system/bin/input tap 1000 500");

                if ((proc.waitFor() != 0)) {
                    throw new SecurityException();
                }
            } catch (IOException e) {
                Log.e(TAG, TAGG + "IO Exception caught: " + e.getMessage());
            } catch (InterruptedException e) {
                Log.e(TAG, TAGG + "Interrupted Exception caught: " + e.getMessage());
            } catch (SecurityException e) {
                Log.e(TAG, TAGG + "Security Exception caught: " + e.getMessage());
            } catch (Exception e) {
                Log.e(TAG, TAGG + "Exception caught: " + e.getMessage());
            }
        }
    }

    /** Returns the boolean value of the runtime flag for reboot-allowed */
    public boolean getRuntimeFlag_rebootIsDisallowed() {
        final String TAGG = "getRuntimeFlag_rebootIsDisallowed";
        boolean ret = true;

        String runtimeFlagName = "REBOOT_DISALLOW_ALL_APPS";
        int runtimeFlagValue = 0;

        try {
            File sdcardDir = Environment.getExternalStorageDirectory();
            File runtimeFlagsFile = new File(sdcardDir, "evoRuntimeFlagsFile"); //dir file is in, and name of file

            BufferedReader bufferedReader = new BufferedReader(new FileReader(runtimeFlagsFile));
            String line;

            while ((line = bufferedReader.readLine()) != null) {
                if (line.contains(runtimeFlagName)) {
                    //we found our flag we care about, so parse the value
                    runtimeFlagValue = Integer.parseInt(line.split("=")[1]);
                }
            }

            bufferedReader.close();
        } catch (IOException ioe) {
            Log.e(TAG, TAGG+"IO Exception caught: "+ioe.getMessage());
        } catch (Exception e) {
            Log.e(TAG, TAGG+"Exception caught: "+e.getMessage());
        }

        if (runtimeFlagValue == 0) {
            ret = false;
        } else if (runtimeFlagValue == 1) {
            ret = true;
        }

        Log.v(TAG, TAGG+"Returning "+String.valueOf(ret));
        return ret;
    }

    /** Install package specifically for the main app. */
    /*
    public boolean installPackage_evolution() {
        final String TAGG = "installPackage_evolution: ";
        Log.v(TAG, TAGG+"Invoked.");

        boolean ret = false;

        try {
            String localApkPath = appContext.getResources().getString(R.string.updateFileDownloadPath);
            String localApkFile = appContext.getResources().getString(R.string.packageFilename_evolution);

            ret = installPackage(localApkPath+"/"+localApkFile);
        } catch (Exception e) {
            Log.e(TAG, TAGG + "Exception caught: "+ e.getMessage());
        }

        Log.v(TAG, TAGG+"Returning: "+ String.valueOf(ret));
        return ret;
    }
    */

    /** Checks if the specified URL is available.
     * Needs the fully qualified path, ex: http://myserverFQDNorIP/resource */
    public static boolean doesRemoteFileExist_http(String completeURL){
        final String TAGG = "doesRemoteFileExist_http(\""+completeURL+"\"): ";
        Log.v(TAG, TAGG+"Invoked.");

        boolean ret = true;
        HttpURLConnection con;

        try {
            HttpURLConnection.setFollowRedirects(false);
            // note : you may also need
            //        HttpURLConnection.setInstanceFollowRedirects(false)
            con = (HttpURLConnection) new URL(completeURL).openConnection();
            if (con != null) {
                con.setRequestMethod("HEAD");
                ret = (con.getResponseCode() == HttpURLConnection.HTTP_OK);
                con.disconnect();
            } else {
                Log.w(TAG, TAGG+"HttpURLConnection could not be opened.");
            }
        }
        catch (Exception e) {
            Log.e(TAG, TAGG + "Exception caught: "+ e.getMessage());
        }

        Log.v(TAG, TAGG+"Returning: "+ String.valueOf(ret));
        return ret;
    }

    /** Download a file via HTTP, given path (http://domain.com/path/) and file (myfile.txt)
     *  Returns the number of bytes downloaded and read to the local file.
     *  Returns -1 if some error happened or resource could not be accessed.
     *  NOTE: This must be run asynchronously from main UI thread, in a worker thread for example...
     *      new Thread(new Runnable() {
     *          public void run() {
     *          DownloadFile();
     *          }
     *      }).start();
     */
    public static long downloadFileHTTP(Context context, String strUrlPath, String strUrlFile, int numOfRetries, String destinationPath){
        final String TAGG = "downloadFileHTTP: ";
        long totalBytesRead = 0;
        InputStream inputStream = null;
        int retryDelayMS = 100;
        int retryCounter;
        final String strUrlWhole = strUrlPath+"/"+strUrlFile;

        if (doesRemoteFileExist_http(strUrlWhole)) {

            try {
                URL url = new URL(strUrlPath + "/" + strUrlFile);
                //InputStream inputStream = url.openStream();         //NOTE: this will throw an I/O error if URL is unavailable

                //routine to try to open URL, and retry if that fails
                for (retryCounter = 0; retryCounter < numOfRetries; retryCounter++) {
                    try {
                        inputStream = url.openStream();
                    } catch (IOException e) {
                        Log.w(TAG, TAGG + "I/O error accessing network resource (" + e.getMessage() + "). Retrying in " + retryDelayMS + "ms.");
                        Thread.sleep(retryDelayMS);
                        //continue;   //try again
                    }
                }

                if (inputStream == null) {
                    return -1;  //hit maximum retries and still no resource available, so return error
                }

                DataInputStream dataInputStream = new DataInputStream(inputStream);

                byte[] buffer = new byte[1024];
                int bytesRead;

                //File file = new File(context.getCacheDir(), strUrlFile);
                File file = new File(destinationPath, strUrlFile);
                Log.d(TAG, TAGG + "Local file-space specified (" + file.getAbsolutePath() + ").");
                FileOutputStream fos = new FileOutputStream(file);

                while ((bytesRead = dataInputStream.read(buffer)) > 0) {
                    fos.write(buffer, 0, bytesRead);
                    // buffer = new byte[153600];
                    totalBytesRead += bytesRead;
                    // logger.debug("Downloaded {} Kb ", (totalBytesRead / 1024));

                    //put progress publish stuff here?
                }

                Log.d(TAG, TAGG + "Total bytes read = " + totalBytesRead);

                dataInputStream.close();
                inputStream.close();
                fos.close();
            } catch (MalformedURLException mue) {
                Log.e(TAG, TAGG + "Malformed URL error. Aborting.", mue);
                return -1;
            } catch (IOException e) {
                Log.e(TAG, TAGG + "I/O error (" + e.getMessage() + "). Aborting.");
                return -1;
            } catch (SecurityException se) {
                Log.e(TAG, TAGG + "Security error. Aborting.", se);
                return -1;
            } catch (Exception e) {
                Log.e(TAG, TAGG + "General error. Aborting.", e);
                return -1;
            }

        } else {
            Log.e(TAG, TAGG+"File does not seem to exist on server. No download attempted.");
        }

        Log.v(TAG, TAGG+"Returning "+totalBytesRead);
        return totalBytesRead;
    }

    /** Inform server that a background download has successfully completed */
    public void informServerBackgroundDownloadHasSucceeded() {

    }

    /** Setup and return a Notification Builder object.
     * Remember: After getting what this returns, you'll need to supply to NotificationManager to actually show it.
     */
    public NotificationCompat.Builder createNotificationBuilderObject(String contentText) {
        return createNotificationBuilderObject(this.appContext, contentText);
    }
    public static NotificationCompat.Builder createNotificationBuilderObject(Context appContext, String contentText) {
        final String TAGG = "createNotificationBuilderObject: ";
        Log.v(TAG, TAGG+"Invoked.");

        NotificationCompat.Builder notifBuilder = null;
        final String notifChannelID = appContext.getResources().getString(R.string.notification_channelID);
        final String notifTitle = "v"+String.valueOf(getAppVersion(appContext))+" - "+appContext.getResources().getString(R.string.notification_title);

        try {
            notifBuilder = new NotificationCompat.Builder(appContext, notifChannelID);
            notifBuilder.setSmallIcon(R.drawable.ic_stat_messagenet_logo_200x200_trans);
            notifBuilder.setContentTitle(notifTitle);
            notifBuilder.setContentText(contentText);
            notifBuilder.setOngoing(true);
            notifBuilder.setPriority(NotificationCompat.PRIORITY_MAX);
        } catch (Exception e) {
            Log.e(TAG, TAGG+"Exception caught: "+e.getMessage());
        }

        Log.v(TAG, TAGG+"Returning "+String.valueOf(notifBuilder));
        return notifBuilder;
    }

    /** Update the specified notification object's text.
     */
    public NotificationCompat.Builder updateNotificationBuilderObjectText(NotificationCompat.Builder notifBuilder, String contentText) {
        final String TAGG = "createNotificationBuilderObject: ";
        Log.v(TAG, TAGG+"Invoked.");

        try {
            synchronized (notifBuilder) {
                notifBuilder.setContentText(contentText);
                notifBuilder.notify();
            }
        } catch (Exception e) {
            Log.e(TAG, TAGG+"Exception caught: "+e.getMessage());
        }

        return notifBuilder;
    }

    /** Finalize and show the provided notification.
     */
    public void showNotif(NotificationCompat.Builder notifBuilder) {
        showNotif(this.appContext, notifBuilder);
    }
    public static void showNotif(Context appContext, NotificationCompat.Builder notifBuilder) {
        final String TAGG = "showNotif: ";
        Log.d(TAG, TAGG + "Invoked.");

        try {
            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(appContext);
            final int notificationId = 0;     //notificationId is a unique int for each notification that you must define
            notificationManager.notify(notificationId, notifBuilder.build());
        } catch (Exception e) {
            Log.e(TAG, TAGG + "Exception caught: " + e.getMessage());
        }
    }

    /** Update the notification with specified text.
     */
    public void updateNotificationWithText(String text) {
        updateNotificationWithText(this.appContext, text);
    }
    public static void updateNotificationWithText(Context appContext, String text) {
        showNotif(appContext, createNotificationBuilderObject(appContext, text));
    }

    /** Broadcast a request to the ClockActivity in main app to show some text in its OSA area.
     * Intent i = new Intent("HANDLE_OSA");
     *  i.putExtra("showOrHideOSA", "show");
     *  i.putExtra("textForOSA", "My Text");
     *  sendBroadcast(i);
     */
    public void doOsaRequest(String text) {
        final String TAGG = "doOsaRequest(\""+text+"\"): ";
        Log.d(TAG, TAGG + "Invoked.");

        Intent intent = new Intent("HANDLE_OSA");       //TODO migrate to strings.xml

        if (text == null) {
            intent.putExtra("showOrHideOSA", "hide");   //TODO migrate to strings.xml
        } else {
            intent.putExtra("showOrHideOSA", "show");   //TODO migrate to strings.xml
            intent.putExtra("textForOSA", text);        //TODO migrate to strings.xml
        }

        appContext.sendBroadcast(intent);
    }

    /** Return the current time (hour and minute) (in 24 hour format) */
    public String getCurrentTime24() {
        final String TAGG = "getCurrentTime24: ";
        Log.v(TAG, TAGG+"Invoked.");

        String ret = "00:00";
        String strTimeFormat = "HH:mm";
        DateFormat dateFormat = new SimpleDateFormat(strTimeFormat, Locale.ENGLISH);

        try {
            Date date = new Date();
            ret = dateFormat.format(date);
        } catch (Exception e) {
            Log.w(TAG, TAGG+"Failed to get valid current time using Date class (will try Calendar next): "+e.getMessage());

            try {
                Calendar cal = Calendar.getInstance();
                Date date = cal.getTime();
                ret = dateFormat.format(date);
            } catch (Exception e2) {
                Log.w(TAG, TAGG+"Failed to get valid current time using Calendar class: "+e2.getMessage());
            }
        }

        Log.v(TAG, TAGG+"Returning \""+ret+"\".");
        return ret;
    }

    /** Return the hour of provided time as an int so you can do easy comparisons.
     * Argument requires "HH:MM" format. */
    public int getHourFromTime(String time) {
        final String TAGG = "getCurrentHourFromTime(\""+time+"\"): ";
        Log.v(TAG, TAGG+"Invoked.");

        int ret;

        try {
            String sub = time.split(":")[0];
            ret = Integer.parseInt(sub);
        } catch (Exception e) {
            Log.w(TAG, TAGG+"Exception caught: "+e.getMessage());
            ret = 0;
        }

        Log.v(TAG, TAGG+"Returning "+String.valueOf(ret)+".");
        return ret;
    }

    /** Return the minutes of provided time as an int so you can do easy comparisons.
     * Argument requires "HH:MM" format. */
    public int getMinutesFromTime(String time) {
        final String TAGG = "getMinutesFromTime(\"" + time + "\"): ";
        Log.v(TAG, TAGG+"Invoked.");

        int ret;

        try {
            String sub = time.split(":")[1];
            ret = Integer.parseInt(sub);
        } catch (Exception e) {
            Log.w(TAG, TAGG+"Exception caught: "+e.getMessage());
            ret = 0;
        }

        Log.v(TAG, TAGG+"Returning "+String.valueOf(ret)+".");
        return ret;
    }

    /** Supplied a String time value formatted like "HH:mm", determine whether it fits within our defined time window */
    public boolean timeIsWithinTimeWindow(String time, String timeWindowOpen, String timeWindowClose) {
        final String TAGG = "timeIsWithinTimeWindow(\""+time+"\",\""+timeWindowOpen+"\",\""+timeWindowClose+"\"): ";
        Log.v(TAG, TAGG+"Invoked.");

        boolean ret = false;

        try {
            Date timeClose = new SimpleDateFormat("HH:mm", Locale.ENGLISH).parse(timeWindowClose);
            Calendar calClose = Calendar.getInstance();
            calClose.setTime(timeClose);

            Date timeOpen = new SimpleDateFormat("HH:mm", Locale.ENGLISH).parse(timeWindowOpen);
            Calendar calOpen = Calendar.getInstance();
            calOpen.setTime(timeOpen);

            Date timeCurrent = new SimpleDateFormat("HH:mm", Locale.ENGLISH).parse(time);
            Calendar calTimeCurrent = Calendar.getInstance();
            calTimeCurrent.setTime(timeCurrent);

            if (calTimeCurrent.getTime().after(calOpen.getTime())
                    && calTimeCurrent.getTime().before(calClose.getTime())) {
                ret = true;
            }
        } catch (Exception e) {
            Log.w(TAG, TAGG+"Exception caught: "+e.getMessage());
        }

        Log.v(TAG, TAGG+"Returning "+String.valueOf(ret));
        return ret;
    }

    /** Supplied a String time value formatted like "HH:mm", determine whether it fits within our defined time window */
    /*
    public boolean timeIsWithinTimeWindow_old(String time, String timeWindowOpen, String timeWindowClose) {
        final String TAGG = "timeIsWithinTimeWindow(\""+time+"\",\""+timeWindowOpen+"\",\""+timeWindowClose+"\"): ";
        Log.v(TAG, TAGG+"Invoked.");

        boolean ret = false;
        int timeWindowOpen_hours = getHourFromTime(timeWindowOpen);
        int timeWindowOpen_minutes = getMinutesFromTime(timeWindowOpen);
        int timeWindowClose_hours = getHourFromTime(timeWindowClose);
        int timeWindowClose_minutes = getMinutesFromTime(timeWindowClose);
        int hourFromTime = getHourFromTime(time);
        int minuteFromTime = getMinutesFromTime(time);

        try {
            if (hourFromTime < timeWindowOpen_hours) {
                //hour is definitely before our window opens
                Log.v(TAG, TAGG+"Hour is definitely before our window opens.");
            } else if (hourFromTime > timeWindowClose_hours) {
                //hour is definitely after our window closes
                Log.v(TAG, TAGG + "Hour is definitely after our window closes.");
            } else if (hourFromTime == timeWindowOpen_hours) {
                //hour is same as when window opens, so need to look at minute resolution
                if (minuteFromTime < timeWindowOpen_minutes) {
                    //we're before our window opens
                } else if (minuteFromTime > timeWindowOpen_minutes) {
                    //we're after our window opens
                    if (minuteFromTime < timeWindowClose_minutes)
                }
            } else {
                //we could be within our window (hourly resolution), so now let's check minutes resolution
                if (hourFromTime == timeWindowOpen_hours
                        && minuteFromTime < timeWindowOpen_minutes) {
                    //we're minutes before our window opens
                    Log.v(TAG, TAGG+"Minute is definitely before our window opens.");
                } else if (hourFromTime == ) {

                }

                if (minuteFromTime < timeWindowOpen_minutes) {
                    //minutes is definitely before our window opens
                    Log.v(TAG, TAGG+"Minute is definitely before our window opens.");
                } else if (minuteFromTime > timeWindowClose_minutes) {
                    //minutes is definitely after our window closes
                    Log.v(TAG, TAGG+"Minute is definitely after our window closes.");
                } else {
                    //we should be within our window, inclusively (minutes and hours resolution)
                    Log.v(TAG, TAGG+"We are within the window.");
                    ret = true;
                }
            }
        } catch (Exception e) {
            Log.w(TAG, TAGG+"Exception caught: "+e.getMessage());
        }

        Log.v(TAG, TAGG+"Returning "+String.valueOf(ret));
        return ret;
    }
    */

    /** Returns the string value of the specified runtime flag.
     * If no value found or no value exists, returns null. */
    public String getRuntimeFlag(String flagName) {
        final String TAGG = "getRuntimeFlag(\""+flagName+"\"): ";
        Log.v(TAG, TAGG+"Invoked.");

        String runtimeFlagValue = "";

        try {
            File sdcardDir = Environment.getExternalStorageDirectory();
            File runtimeFlagsFile = new File(sdcardDir, "evoRuntimeFlagsFile"); //dir file is in, and name of file

            BufferedReader bufferedReader = new BufferedReader(new FileReader(runtimeFlagsFile));
            String line, lineSplit[];

            while ((line = bufferedReader.readLine()) != null) {
                if (line.contains(flagName)) {
                    //we found our flag we care about, so parse the value
                    lineSplit = line.split("=");
                    if (lineSplit.length > 1) {
                        runtimeFlagValue = lineSplit[1];
                    } else {
                        runtimeFlagsFile = null;
                    }
                }
            }

            bufferedReader.close();
        } catch (IOException ioe) {
            Log.e(TAG, TAGG+"IO Exception caught: "+ioe.getMessage());
        } catch (Exception e) {
            Log.e(TAG, TAGG+"Exception caught: "+e.getMessage());
        }

        if (runtimeFlagValue != null) {
            if (runtimeFlagValue.isEmpty() || runtimeFlagValue.length() == 0 || runtimeFlagValue.equals("")) {
                Log.i(TAG, TAGG+"Runtime flag value unavailable.");
                runtimeFlagValue = null;
            }
        }

        Log.v(TAG, TAGG+"Returning \""+runtimeFlagValue+"\".");
        return runtimeFlagValue;
    }
    public boolean getRuntimeFlag_asBoolean(String flagName) {
        final String TAGG = "getRuntimeFlag_asBoolean(\""+flagName+"\"): ";
        String result = getRuntimeFlag(flagName);
        if (result == null) {
            return false;
        } else if (result.equals("0") || result.equalsIgnoreCase("false")) {
            return false;
        } else if (result.equals("1") || result.equalsIgnoreCase("true")) {
            return true;
        } else {
            Log.w(TAG, TAGG+"Unhandled value. Not sure how to parse, so returning false.");
            return false;
        }
    }

    public static String getPackageName(Context context) {
        String val;
        try {
            val = context.getPackageName();
        } catch (Exception e) {
            val = "com.messagenetsystems.evolutionupdater";
        }
        Log.v(TAG, "getPackageName: Returning \""+val+"\".");
        return val;
    }

    /** Return the app version as a string
     */
    public static String getAppVersion(Context context) {
        final String TAGG = "getAppVersion: ";

        String ret;

        try {
            PackageInfo pInfo = context.getPackageManager().getPackageInfo(getPackageName(context), 0);
            ret = pInfo.versionName;
        } catch (Exception e) {
            Log.w(TAG, TAGG+"Exception caught: "+e.getMessage());
            ret = null;
        }

        Log.v(TAG, TAGG+"Returning \""+String.valueOf(ret)+"\".");
        return ret;
    }

    /** Return whether network is available
     */
    public boolean isNetworkAvailable() {
        return isNetworkAvailable(appContext);
    }
    public static boolean isNetworkAvailable(Context context) {
        final String TAGG = "isNetworkAvailable: ";

        boolean ret = false;
        boolean availableOnly = false;

        try {
            ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
            //NOTES: isAvailable (whether connectivity is possible) / isConnected (whether actually connected) / isConnectedOrConnecting (whether connected or trying to connect)
            ret = activeNetworkInfo != null && activeNetworkInfo.isConnected();
            if (!ret) {
                Log.v(TAG, TAGG+"Network not currently connected, digging deeper...");
                ret = activeNetworkInfo != null && activeNetworkInfo.isConnectedOrConnecting();
            }
            if (!ret) {
                Log.v(TAG, TAGG+"Network not currently connected or connecting, digging deeper...");
                availableOnly = activeNetworkInfo != null && activeNetworkInfo.isAvailable();
            }
        } catch (Exception e) {
            Log.w(TAG, TAGG+"Exception caught trying to determine whether network is available.");
        }

        //if no network detected above, we could be wired, so check that...
        if (!ret) {
            Log.v(TAG, TAGG+"No network detected with first check, could be wired? Checking that...");
            if (nicEthIsUp()) {
                ret = true;
            }
        }

        if (!ret && availableOnly) {
            Log.d(TAG, TAGG+"Non-wired network is available, but not connected or connecting.");
            //TODO: maybe some call to cycle the wifi, and recurse back into this, using the counter to control wifi lockout??
            //NOTE: When no-network is logged, restarting the updater app usually fixes it, so above TO-DO might not be necessary.
        }

        Log.v(TAG, TAGG+"Returning \""+String.valueOf(ret)+"\".");
        return ret;
    }

    /** Return whether wired-ethernet interface is UP.
     * 2019.02.05   Chris Rider     Created.
     * 2019.06.14   Chris Rider     Imported from main evolution app with no changes, except to make it static.
     */
    public static boolean nicEthIsUp() {
        final String TAGG = "nicEthIsUp: ";
        Log.v(TAG, TAGG+"Invoked.");

        boolean ret = false;

        Process process = null;
        OutputStream stdin;     //used to write commands to shell... using OutputStream type, we can execute commands like writing commands in terminal
        InputStream stdout;     //used to read output of a command we executed... using InputStream type, we can input command's output to our routine here
        InputStream stderr;     //used to read errors of a command we executed... using InputStream type, we can input command's errors to our routine here
        BufferedReader br;
        String line;

        try {
            // Start a super-user process under which to execute our commands as root
            Log.d(TAG, TAGG + "Starting super-user shell session...");
            process = Runtime.getRuntime().exec("su");

            // Get process streams
            stdin = process.getOutputStream();
            stdout = process.getInputStream();
            stderr = process.getErrorStream();

            // Construct and execute command (new-line is like hitting enter)
            Log.d(TAG, TAGG + "Specifying shell command...");
            stdin.write(("/system/bin/ip link show | /system/bin/grep \"state UP\"\n").getBytes());

            // Exit the shell
            stdin.write(("exit\n").getBytes());

            // Flush and close the stdin stream
            stdin.flush();
            stdin.close();

            // Read output of the executed command
            Log.v(TAG, TAGG+"Reading output of executed command...");
            br = new BufferedReader(new InputStreamReader(stdout));
            while ((line = br.readLine()) != null) {
                Log.v(TAG, TAGG+"stdout line: "+line);
                if (line.toLowerCase().contains("eth0")) {
                    ret = true;
                }
            }
            br.close();

            // Read error stream of the executed command
            Log.v(TAG, TAGG+"Reading errors of executed command...");
            br = new BufferedReader(new InputStreamReader(stderr));
            while ((line = br.readLine()) != null) {
                Log.w(TAG, TAGG+"stderr line: "+line);
            }
            br.close();

            // Wait for process to finish
            process.waitFor();
            process.destroy();

            //} catch(SecurityException e) {
            //    Log.e(TAG, TAGG +"Security Exception caught: " + String.valueOf(e));
        } catch (Exception e) {
            Log.e(TAG, TAGG + "Exception caught: " + e.getMessage());
        }

        // Cleanup
        if (process != null) {
            process.destroy();
        }

        Log.v(TAG, TAGG+"Returning "+String.valueOf(ret)+".");
        return ret;
    }

    /** Check if WiFi is on
     * NOTE: requires application context or memory may leak. */
    public boolean isWifiOn() {
        return isWifiOn(appContext);
    }
    public static boolean isWifiOn(Context context) {
        final String TAGG = "isWifiOn: ";

        try {
            WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wifiManager != null) {
                return wifiManager.isWifiEnabled();
            }
        } catch (Exception e) {
            Log.e(TAG, TAGG+"Exception caught trying to check WiFi. Returning false.");
            Log.d(TAG, TAGG+"Error message: "+ e.getMessage());
            return false;
        }

        Log.w(TAG, TAGG+"Could not execute (perhaps WifiManager did not instantiate?)");
        return false;
    }

    /** Turn on WiFi
     *  NOTE: Must have CHANGE_WIFI_STATE permission in manifest!
     *  NOTE: requires application context or memory may leak.*/
    public boolean turnOnWifi() {
        return turnOnWifi(appContext);
    }
    public static boolean turnOnWifi(Context context) {
        final String TAGG = "turnOnWifi: ";
        Log.v(TAG, TAGG+"Running.");

        boolean ret;
        WifiManager wifiManager;

        // Try to get a WifiManager instance, first.
        try {
            wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);

            if (wifiManager == null) {
                throw new Exception();
            }
        } catch (Exception e) {
            Log.e(TAG, TAGG+"Exception caught trying to get WifiManager instance. Returning false.");
            return false;
        }

        // If we got to this point, then we should have a valid WifiManager instance to work with.
        try {
            if (wifiManager.isWifiEnabled()) {
                Log.d(TAG, TAGG+"Wi-Fi already on, returning true.");
                ret = true;
            } else {
                //turn on wifi
                Log.i(TAG, TAGG+"Enabling WiFi...");
                wifiManager.setWifiEnabled(true);

                //wait a little bit to give time for wifi to come online
                try {
                    Thread.sleep(1000 * 5);   //4s is enough, so do 5s to be safe
                } catch (Exception e) {
                    Log.w(TAG, TAGG+"Exception caught trying to wait for WiFi to enable. (NOTE: failure could be falsely reported below)");
                }

                //test whether we had success
                if (wifiManager.isWifiEnabled()) {
                    Log.i(TAG, TAGG+"SUCCESS! WiFi is now on.");
                    ret = true;
                } else {
                    Log.w(TAG, TAGG+"FAILURE? WiFi is off, or taking a long time.");
                    ret = false;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, TAGG+"Exception caught trying to enable WiFi. Returning false.");
            Log.d(TAG, TAGG+"Error message: "+ e.getMessage());
            ret = false;
        }

        // Explicit cleanup to be extra safe against memory leaks
        wifiManager = null;

        return ret;
    }

    /** Turn off WiFi
     *  NOTE: Must have CHANGE_WIFI_STATE permission in manifest!
     *  NOTE: requires application context or memory may leak. */
    public boolean turnOffWifi() {
        return turnOffWifi(appContext);
    }
    public static boolean turnOffWifi(Context context) {
        final String TAGG = "turnOffWifi: ";
        Log.v(TAG, TAGG+"Running.");

        WifiManager wifiManager;

        try {
            wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wifiManager != null) {
                wifiManager.setWifiEnabled(false);
                Thread.sleep(1000*2);   //give time for wifi to go offline
            } else {
                Log.e(TAG, TAGG+"Could not execute because WifiManager did not instantiate.");
                return false;
            }

            //test whether we had success
            if (!isWifiOn(context)) {
                Log.i(TAG, TAGG+"SUCCESS! isWifiOn reports WiFi is off.");
                wifiManager = null;
                return true;
            } else {
                Log.w(TAG, TAGG+"FAILURE! isWifiOn reports WiFi is on.");
                wifiManager = null;
                return false;
            }
        } catch (Exception e) {
            Log.e(TAG, TAGG+"Exception caught trying to disable WiFi. Returning false.");
            Log.d(TAG, TAGG+"Error message: "+ e.getMessage());
            wifiManager = null;
            return false;
        }
    }

    /** Cycle WiFi (turn off then on again) */
    public void cycleWifi() {
        cycleWifi(appContext);
        wifiCycleCounter++;
    }
    public static void cycleWifi(Context context) {
        Log.v(TAG, "cycleWifi: Running.");
        try {
            Log.i(TAG, "cycleWifi: Restarting WiFi...");
            if (turnOffWifi(context)) {
                Log.d(TAG, "cycleWifi: WiFi is now off.");
            }
            if (turnOnWifi(context)) {
                Log.d(TAG, "cycleWifi: WiFi is now on.");
            }
        } catch (Exception e) {
            Log.e(TAG, "cycleWifi: Exception caught trying to cycle WiFi: "+ e.getMessage() +".");
        }
    }
}
