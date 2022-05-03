package com.messagenetsystems.evolutionupdater.threads;

/** ServerSocketThread
 * Listens for and handles network connections.
 *
 * Revisions:
 *  2018.10.24  Chris Rider     Created.
 */

import android.content.Context;
import android.content.Intent;
import android.net.UrlQuerySanitizer;
import android.util.Log;

import com.messagenetsystems.evolutionupdater.MainUpdaterService;
import com.messagenetsystems.evolutionupdater.R;
import com.messagenetsystems.evolutionupdater.SystemFunctions;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;

//public class ServerSocketThread implements Runnable {
public class ServerSocketThread extends Thread {
    private String TAG = ServerSocketThread.class.getSimpleName();

    private Context appContext;

    private ServerSocket serverSocket;
    private Socket socket;
    private CommunicationThread commThread;
    private SystemFunctions systemFunctions;

    private int socketPort;

    private int socketMaxBacklog;                   //maximum length of the queue of incoming connections before socket rejects incoming requests
    private int socketPerfPrefConnectionTime;       //an int expressing the relative importance of a short connection time
    private int socketPerfPrefLatency;              //an int expressing the relative importance of low latency
    private int socketPerfPrefBandwidth;            //an int expressing the relative importance of high bandwidth

    private volatile boolean flag_isThreadAlive = false;
    private volatile boolean flag_isWaitingForConnection = false;
    private volatile boolean flag_isProcessingConnection = false;
    private volatile boolean flag_shutdownRequested = false;

    /** Constructor */
    public ServerSocketThread(Context appContext) {
        this.appContext = appContext;
        this.socketPort = appContext.getResources().getInteger(R.integer.socket_server_port);
        this.systemFunctions = new SystemFunctions(appContext);
    }

    @Override
    public void run() {
        final String TAGG = "run: ";
        TAG = TAG + " #"+Thread.currentThread().getId();
        Log.v(TAG, TAGG+"Invoked.");

        // Initialize and setup our socket listener
        try {
            socketMaxBacklog = 50;                  //maximum length of the queue of incoming connections before socket rejects incoming requests
            socketPerfPrefConnectionTime = 1;       //relative importance of short connection time
            socketPerfPrefLatency = 2;              //relative importance of low latency
            socketPerfPrefBandwidth = 0;            //relative importance of high bandwidth

            serverSocket = new ServerSocket();

            serverSocket.setReceiveBufferSize(64000);
            serverSocket.setPerformancePreferences(socketPerfPrefConnectionTime, socketPerfPrefLatency, socketPerfPrefBandwidth);   //NOTE: must be done before binding to an address happens
            serverSocket.setReuseAddress(true); //true allows the socket to be bound even though a previous connection is in a timeout state

            serverSocket.bind(new InetSocketAddress(socketPort), socketMaxBacklog);

            Log.d(TAG, TAGG+"serverSocket instantiated...\n" +
                    "Local Port:     " + serverSocket.getLocalPort() +"\n" +
                    "Rx Buffer Size: " + serverSocket.getReceiveBufferSize());

        } catch (IOException e) {
            Log.e(TAG, TAGG+"IOException caught! Killing thread.");
            Log.v(TAG, TAGG+e.getMessage());
            Thread.currentThread().interrupt();
        }

        // As long as our thread is supposed to be running... Actually listen for connections on the socket (the .accept method below is blocking)
        while (!Thread.currentThread().isInterrupted()
                && !flag_shutdownRequested) {

            flag_isThreadAlive = true;

            try {
                // Hold and listen for a connection to the socket. Once made, it returns
                // NOTE: This blocks execution of all following code until that happens!
                Log.d(TAG, TAGG+"Listening for a socket connection...");
                flag_isWaitingForConnection = true;
                socket = serverSocket.accept();

                // Once a connection is made
                flag_isWaitingForConnection = false;
                flag_isProcessingConnection = true;
                socket.setKeepAlive(true);  //not sure if necessary - works with or without
                //socket.setTcpNoDelay(true); //(not necessary; don't seem to hurt) disable Nagle's algorithm (a buffering scheme that delays things) by setting TCP_NODELAY on the socket
                //socket.setTrafficClass(0x10);   //IPTOS_LOWDELAY (probably only obeyed by nearest router... maybe not at all)

                // Pass the socket connection to our worker thread to handle the communication, and run it
                Log.d(TAG, TAGG+"A socket connection was received from " + socket.getRemoteSocketAddress() + "! Handing socket to worker thread...");
                commThread = new CommunicationThread(appContext, socket);
                new Thread(commThread).start();
            } catch (SocketTimeoutException e) {
                Log.e(TAG, TAGG+"SocketTimeoutException caught: "+ e.getMessage());
            } catch (IOException e) {
                Log.e(TAG, TAGG+"IOException caught: "+ e.getMessage());
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                Log.e(TAG, TAGG+"Exception caught: "+ e.getMessage());
                Thread.currentThread().interrupt();
            }

            // this is the end of the loop-iteration, so check whether we  will stop or continue
            if (Thread.currentThread().isInterrupted()) {
                Log.d(TAG, TAGG+"Stopping.");
                flag_isThreadAlive = false;
            }
        }
    }//end run


    /** Getters and Setters
     */
    public boolean getIsThreadAlive() {
        return flag_isThreadAlive;
    }
    public boolean getIsProcessingConnection() {
        return flag_isProcessingConnection;
    }
    public boolean getIsWaitingForConnection() {
        return flag_isWaitingForConnection;
    }
    public boolean getShutdownRequested() {
        return flag_shutdownRequested;
    }


    /** Discrete communication handler thread
     * This is what processes the incoming communication from the server.
     * It parses the request received via socket, and directs it where to go to make stuff happen.
     **/
    private class CommunicationThread implements Runnable {
        private String TAGG = "CommunicationThread";

        private Socket clientSocket;
        private BufferedReader input;
        private OutputStream output;

        private String readChar;
        private String readLine;

        private String requestType;
        private String contentType;
        private int contentLength = 0;

        private UrlQuerySanitizer urlSanitizer;
        String[] readSplit;
        String scriptNameParams;
        String requestBodyContent = "";
        JSONObject jsonObject = null;

        CommunicationThread(Context appContext, Socket clientSocket) {
            this.clientSocket = clientSocket;

            SocketAddress remoteAddress = clientSocket.getRemoteSocketAddress();

            Log.d(TAG, TAGG+": Setting up a communication run w"+ remoteAddress.toString() +"...");
            this.clientSocket.getRemoteSocketAddress();

            try {
                this.input = new BufferedReader(new InputStreamReader(this.clientSocket.getInputStream()));
                this.output = clientSocket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, TAGG+": IOException caught: "+ e.getMessage());
            }
        }

        public void run() {
            TAGG = TAGG + " #" + Thread.currentThread().getId() +": ";

            int i = 0;
            BufferedWriter bufOut = new BufferedWriter(new OutputStreamWriter(output));
            String defaultResponse = "pong";

            Log.v(TAG, TAGG+"Running.");

            //SAMPLE HTTP REQUEST:
            //  POST / HTTP/1.1
            //  User-Agent: curl/7.19.7 (x86_64-redhat-linux-gnu) libcurl/7.19.7 NSS/3.14.0.0 zlib/1.2.3 libidn/1.18 libssh2/1.4.2
            //  Host: 192.168.1.241:8080
            //  Accept: */*
            //  Content-Type: application/json
            //  Content-Length: 18
            //      (NOTE: this blank line is usually a \r\n -- interpreted as a double line-return, creating the blank line)
            //  {"username":"xyz"}

            // The following loop will run until the worker thread is interrupted...
            // We will form iterations for each line that's read-in from the request.

            while (!Thread.currentThread().isInterrupted()) {
                i++;
                try {
                    // Read each line in the request (forms the basis for our iteration)
                    readLine = input.readLine();
                    Log.v(TAG, TAGG+"Raw request line #"+i+" = '"+ readLine +"'.");

                    if (readLine == null) {
                        //we're at the end of our request, so kill the thread (and allow loop to end)
                        Log.v(TAG, TAGG+"Request line is null. Returning basic response (pong).");

                        bufOut.write(defaultResponse); bufOut.newLine(); bufOut.flush(); bufOut.close();     //send a response back to server, just in case it needs to know we're alive, but we're unable to read the request
                        Thread.currentThread().interrupt();
                    } else {
                        //we're in one of the request's lines, so figure out as necessary...

                        //for our first line, determine http method
                        if (i == 1) {
                            //need to figure out type of HTTP
                            if (readLine.contains("POST /")) {
                                requestType = "POST";
                            } else if (readLine.contains("GET /")) {
                                requestType = "GET";
                            } else {
                                Log.w(TAG, TAGG+"Unhandled HTTP method. Returning basic response and quitting.");
                                bufOut.write(defaultResponse); bufOut.newLine(); bufOut.flush(); bufOut.close();     //send a response back to server, just in case it needs to know we're alive, but we're unable to read the request
                                Thread.currentThread().interrupt();
                                continue;
                            }
                            Log.i(TAG, TAGG+"Parsed HTTP method = '" + requestType + "'.");
                        }

                        //determine content-type
                        if (readLine.contains("Content-Type: application/json")) {
                            contentType = "json";
                            Log.d(TAG, TAGG+"Parsed Content-type = '" + contentType + "'.");
                            continue;   //jump to next iteration/line
                        }

                        //determine content-length
                        if (readLine.contains("Content-Length:")) {
                            readSplit = readLine.split(": ");  //split the raw request by literal spaces
                            contentLength = Integer.parseInt(readSplit[1]);
                            Log.v(TAG, TAGG+"Parsed Content-Length = " + contentLength + ".");
                            continue;   //jump to next iteration/line
                        }

                        //once we reach the header-body separator, figure out what we're dealing with and parse accordingly...
                        Log.v(TAG, TAGG+"Header-Body separator encountered. Parsing request body...");

                        //for GET requests, handle as if it were just normal legacy banner stuff
                        if (requestType.equals("GET")) {
                            // Parse the script & parameters portion of the request
                            // (our raw HTTP request looks like: GET /signmsg?password=&clearsign=true HTTP/1.1)
                            readSplit = readLine.split(" ");  //split the raw request method by literal spaces
                            scriptNameParams = readSplit[1];
                            Log.d(TAG, TAGG+"Script and params = '"+ scriptNameParams +"'.");

                            // Parse the GET request's query string
                            // (use .getValue("param") method to get value)
                            urlSanitizer = new UrlQuerySanitizer(scriptNameParams);

                            // Parse the line's basic purpose to get started
                            if (readLine.contains("GET /ping?password=")) {
                                bufOut.write(defaultResponse); bufOut.newLine(); bufOut.flush(); bufOut.close();     //send a response back to server
                            } else if (readLine.contains("GET /favicon.ico")) {
                                //ignore these requests
                            } else if (readLine.contains("GET /reboot?password=")) {
                                //request from server to reboot device
                                Log.i(TAG, TAGG+"Client is requesting we reboot. Returning acknowledgement.");
                                bufOut.write("rebooting"); bufOut.newLine(); bufOut.flush(); bufOut.close();     //send a response back to server
                            } else if (readLine.contains("GET /restart?password=")) {
                                //request from server to restart app
                                Log.i(TAG, TAGG+"Server is requesting we restart the app. Returning acknowledgement.");
                                bufOut.write("app restarting"); bufOut.newLine(); bufOut.flush(); bufOut.close();     //send a response back to server
                            } else if (readLine.contains("GET /poweroff?password=")) {
                                //request from server to power-off the device
                                Log.i(TAG, TAGG+"Server is requesting we power-off. Returning acknowledgement.");
                                bufOut.write("powering off"); bufOut.newLine(); bufOut.flush(); bufOut.close();     //send a response back to server

                            } else if (readLine.contains("GET /updateBackgroundDownload?app=evolution&password=")) {
                                Log.i(TAG, TAGG+"Server is requesting we background-download update for main app.");
                                Intent myIntent = new Intent(appContext.getResources().getString(R.string.intentAction_triggerOmniUpdater_getUpdatesBackground));
                                myIntent.putExtra("appPackageName", "com.messagenetsystems.evolution");
                                appContext.sendBroadcast(myIntent);
                                bufOut.write("update download started"); bufOut.newLine(); bufOut.flush(); bufOut.close();     //send a response back to server
                            } else if (readLine.contains("GET /updateBackgroundDownload?app=evolutionwatchdog&password=")) {
                                Log.i(TAG, TAGG+"Server is requesting we background-download update for watchdog.");
                                Intent myIntent = new Intent(appContext.getResources().getString(R.string.intentAction_triggerOmniUpdater_getUpdatesBackground));
                                myIntent.putExtra("appPackageName", "com.messagenetsystems.evolutionwatchdog");
                                appContext.sendBroadcast(myIntent);
                                bufOut.write("update download started"); bufOut.newLine(); bufOut.flush(); bufOut.close();     //send a response back to server
                            } else if (readLine.contains("GET /updateBackgroundDownload?app=evolutionupdater&password=")) {
                                Log.i(TAG, TAGG+"Server is requesting we background-download update for updater.");
                                Intent myIntent = new Intent(appContext.getResources().getString(R.string.intentAction_triggerOmniUpdater_getUpdatesBackground));
                                myIntent.putExtra("appPackageName", "com.messagenetsystems.evolutionupdater");
                                appContext.sendBroadcast(myIntent);
                                bufOut.write("update download started"); bufOut.newLine(); bufOut.flush(); bufOut.close();     //send a response back to server

                            } else if (readLine.contains("GET /stopUpdateBackgroundDownload?app=evolution&password=")) {
                                Log.i(TAG, TAGG+"Server is requesting we stop background-download update for main app.");
                                Intent myIntent = new Intent(appContext.getResources().getString(R.string.intentAction_triggerOmniUpdater_stopGettingUpdatesBackground));
                                myIntent.putExtra("appPackageName", "com.messagenetsystems.evolution");
                                appContext.sendBroadcast(myIntent);
                                bufOut.write("update download stopping"); bufOut.newLine(); bufOut.flush(); bufOut.close();     //send a response back to server
                            } else if (readLine.contains("GET /stopUpdateBackgroundDownload?app=evolutionwatchdog&password=")) {
                                Log.i(TAG, TAGG+"Server is requesting we stop background-download update for watchdog.");
                                Intent myIntent = new Intent(appContext.getResources().getString(R.string.intentAction_triggerOmniUpdater_stopGettingUpdatesBackground));
                                myIntent.putExtra("appPackageName", "com.messagenetsystems.evolutionwatchdog");
                                appContext.sendBroadcast(myIntent);
                                bufOut.write("update download stopping"); bufOut.newLine(); bufOut.flush(); bufOut.close();     //send a response back to server
                            } else if (readLine.contains("GET /stopUpdateBackgroundDownload?app=evolutionupdater&password=")) {
                                Log.i(TAG, TAGG+"Server is requesting we stop background-download update for updater.");
                                Intent myIntent = new Intent(appContext.getResources().getString(R.string.intentAction_triggerOmniUpdater_stopGettingUpdatesBackground));
                                myIntent.putExtra("appPackageName", "com.messagenetsystems.evolutionupdater");
                                appContext.sendBroadcast(myIntent);
                                bufOut.write("update download stopping"); bufOut.newLine(); bufOut.flush(); bufOut.close();     //send a response back to server

                            } else if (readLine.contains("GET /updateDownloadCheck?app=evolution&password=")) {
                                Log.i(TAG, TAGG+"Server is requesting we check download status of evolution update.");
                                if (MainUpdaterService.flag_isDownloading) {
                                    bufOut.write("Downloading update ("+MainUpdaterService.currentDownloadProgress+"%, retry #"+MainUpdaterService.downloadRetriesAttempted+")"); bufOut.newLine(); bufOut.flush(); bufOut.close();     //send a response back to server
                                } else if (systemFunctions.localPackageFileIsValid("com.messagenetsystems.evolution")) {
                                    bufOut.write("OK! update download matches local md5 file"); bufOut.newLine(); bufOut.flush(); bufOut.close();     //send a response back to server
                                } else {
                                    bufOut.write("Warning! update download does not match local md5 file"); bufOut.newLine(); bufOut.flush(); bufOut.close();     //send a response back to server
                                }
                            } else if (readLine.contains("GET /updateDownloadCheck?app=evolutionwatchdog&password=")) {
                                Log.i(TAG, TAGG+"Server is requesting we check download status of evolution watchdog update.");
                                if (MainUpdaterService.flag_isDownloading) {
                                    bufOut.write("Downloading update ("+MainUpdaterService.currentDownloadProgress+"%, retry #"+MainUpdaterService.downloadRetriesAttempted+")"); bufOut.newLine(); bufOut.flush(); bufOut.close();     //send a response back to server
                                } else if (systemFunctions.localPackageFileIsValid("com.messagenetsystems.evolutionwatchdog")) {
                                    bufOut.write("OK! update download matches local md5 file"); bufOut.newLine(); bufOut.flush(); bufOut.close();     //send a response back to server
                                } else {
                                    bufOut.write("Warning! update download does not match local md5 file"); bufOut.newLine(); bufOut.flush(); bufOut.close();     //send a response back to server
                                }
                            } else if (readLine.contains("GET /updateDownloadCheck?app=evolutionupdater&password=")) {
                                Log.i(TAG, TAGG+"Server is requesting we check download status of evolution updater update.");
                                if (MainUpdaterService.flag_isDownloading) {
                                    bufOut.write("Downloading update ("+MainUpdaterService.currentDownloadProgress+"%, retry #"+MainUpdaterService.downloadRetriesAttempted+")"); bufOut.newLine(); bufOut.flush(); bufOut.close();     //send a response back to server
                                } else if (systemFunctions.localPackageFileIsValid("com.messagenetsystems.evolutionupdater")) {
                                    bufOut.write("OK! update download matches local md5 file"); bufOut.newLine(); bufOut.flush(); bufOut.close();     //send a response back to server
                                } else {
                                    bufOut.write("Warning! update download does not match local md5 file"); bufOut.newLine(); bufOut.flush(); bufOut.close();     //send a response back to server
                                }

                            } else if (readLine.contains("GET /updateInstall?app=evolution&password=")) {
                                Log.i(TAG, TAGG+"Server is requesting we install background-downloaded updates for main app.");
                                Intent myIntent = new Intent(appContext.getResources().getString(R.string.intentAction_triggerOmniUpdater_applyUpdates));
                                myIntent.putExtra("appPackageName", "com.messagenetsystems.evolution");
                                appContext.sendBroadcast(myIntent);
                                bufOut.write("update installation started"); bufOut.newLine(); bufOut.flush(); bufOut.close();     //send a response back to server
                            } else if (readLine.contains("GET /updateInstall?app=evolutionwatchdog&password=")) {
                                Log.i(TAG, TAGG+"Server is requesting we install background-downloaded updates for watchdog.");
                                Intent myIntent = new Intent(appContext.getResources().getString(R.string.intentAction_triggerOmniUpdater_applyUpdates));
                                myIntent.putExtra("appPackageName", "com.messagenetsystems.evolutionwatchdog");
                                appContext.sendBroadcast(myIntent);
                                bufOut.write("update installation started"); bufOut.newLine(); bufOut.flush(); bufOut.close();     //send a response back to server
                            } else {
                                //unknown kind of request
                                Log.w(TAG, TAGG+"The received request is not recognized. Ignoring.");
                                Log.v(TAG, readLine);
                                bufOut.write("A response is not developed yet"); bufOut.newLine(); bufOut.flush(); bufOut.close();  //send a response back to server
                            }

                            Thread.currentThread().interrupt();                                     //we got and did everything we need at this point, so wrap everything up
                        }//end if (GET)
                    }//end else (readLine != null)
                }//end try
                catch (IOException e) {
                    Log.e(TAG, TAGG+"IOException caught: "+ e.getMessage());
                    Thread.currentThread().interrupt();
                }
                catch (Exception e) {
                    Log.e(TAG, TAGG+"Exception caught: "+ e.getMessage());
                    Thread.currentThread().interrupt();
                }

                // this is the end of the loop-iteration, so check whether we  will stop or continue
                if (Thread.currentThread().isInterrupted()) {
                    Log.v(TAG, TAGG+"Stopping.");
                    flag_isProcessingConnection = false;
                }
            }//end while (...thread is not interrupted)

            try {
                Log.v(TAG, TAGG+"Closing client socket connection.");
                clientSocket.close();
            } catch (Exception e) {
                Log.e(TAG, TAGG+"Exception caught closing client socket connection: "+ e.getMessage());
            }
        }//end run()
    }//end CommunicationThread
}
