package com.example.nicnowak.trailcounter;

import android.bluetooth.BluetoothAdapter;
import android.os.Bundle;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.views.MapView;
import org.osmdroid.config.Configuration;
import android.app.Activity;
import android.content.Context;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.bluetooth.BluetoothSocket;
import android.bluetooth.BluetoothDevice;
import android.location.LocationManager;
import android.text.method.ScrollingMovementMethod;
import android.location.LocationListener;
import android.location.Location;
import java.util.Calendar;
import java.util.Stack;
import java.util.Scanner;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.File;
import java.util.UUID;

import android.view.View;
import android.widget.Toast;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.util.Set;
import android.content.Intent;
import org.osmdroid.api.IMapController;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.OverlayItem;
import org.osmdroid.views.overlay.ItemizedIconOverlay;
import android.widget.Button;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import android.os.Handler;
import android.os.SystemClock;
import android.widget.TextView;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;
import org.osmdroid.views.overlay.compass.CompassOverlay;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.io.PrintWriter;

//TrailCounter
//Written by Nick Nowak

//This app plots various points on a map and reads data from the HC05 module.

//Main Activity contains the map and buttons
public class MainActivity extends Activity {
    TextView textView;
    private BluetoothAdapter mBluetoothAdapter = null;
    private BluetoothSocket btSocket = null;
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    InputStream inputStream = null;
    private OutputStream outStream = null;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        //Stuff that makes the map API work.
        super.onCreate(savedInstanceState);
        Context ctx = getApplicationContext();
        //important! set your user agent to prevent getting banned from the osm servers
        Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx));
        setContentView(R.layout.activity_main);
        MapView map = (MapView) findViewById(R.id.map);
        map.setTileSource(TileSourceFactory.MAPNIK);
        //Allows the user to zoom in and out with two fingers/
        map.setMultiTouchControls(true);
        //Use mapController to set the default zoom level.
        final IMapController mapController = map.getController();
        mapController.setZoom(12);

        //Set as many points as you wish. Might be added via button later on.
        GeoPoint startPoint = new GeoPoint(46.5108,-87.4093);
        GeoPoint loc_TC02 = new GeoPoint(46.57355, -87.41553);
        GeoPoint loc_TC03 = new GeoPoint(46.5155,-87.4257);
        GeoPoint loc_TC04 = new GeoPoint(46.5123,-87.4235);
        GeoPoint loc_TC05 = new GeoPoint(46.57355, -87.41553);
        GeoPoint loc_TC06 = new GeoPoint(46.57355, -87.41553);
        GeoPoint loc_TC07 = new GeoPoint(46.57355, -87.41553);
        
        //Now the map is zoomed in on startPoint, the Noquemanon trailhead.
        mapController.setCenter(startPoint);
        Marker TC01 = new Marker(map);
        TC01.setPosition(startPoint);
        TC01.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        TC01.setTitle("TC01");
        //Add all markers to a list to perform comparison operations later.
        ArrayList<Marker> markers = new ArrayList<Marker>();
        markers.add(0, TC01);markers.add(1, TC02);markers.add(2, TC03);markers.add(3, TC04);
        
        //Add all the points to the map Overlay so they become visible.
        map.getOverlays().add(0, TC01);
        
        //Two buttons that run a function when clicked. Created in activity_main.xml
        Button btbutton = (Button) findViewById(R.id.bluetooth);
        Button sendfile = (Button) findViewById(R.id.bluetooth2);
        
        //Textview contains a welcome message and is also used for previewing data
        textView = (TextView) findViewById(R.id.MyTextView);
        
        //Allows the user to scroll through the textview.
        textView.setMovementMethod(new ScrollingMovementMethod());
        
        //Display welcome message.
        textView.append("Welcome to TrailCounter v1.0! \n\n");
        textView.append("The first time you use this app you must pair your phone with each sensor so it has permission to connect.\n\n");
        textView.append("To do this select 'HC-05' in your phone's Bluetooth menu when you turn on the counter. \n\n" );
        textView.append("Another line for tests");
        
        //Configure GpsMyLocationProvider, which provides the app with the user's location.
        GpsMyLocationProvider imlp = new GpsMyLocationProvider(this.getBaseContext());
        imlp.setLocationUpdateMinDistance(10);
        imlp.setLocationUpdateMinTime(1);

        //LocationOverlay is used to display user's location on the map.
        MyLocationNewOverlay myLocationOverlay = new MyLocationNewOverlay(imlp, map);

        // plot user's location on the map.
        myLocationOverlay.enableMyLocation();
        myLocationOverlay.enableFollowLocation();
        map.getOverlays().add(4, myLocationOverlay);

        //Set a listener that causes the buttons to run a method when clicked.
        sendfile.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                BlueTooth();
            }
        });

        btbutton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v){
                writeToFile(textView.getText().toString());
                Toast.makeText(getApplicationContext(), "Wrote data to TrailCounter directory.", Toast.LENGTH_SHORT).show();
            }
        });

    }

    public void onResume() {
        super.onResume();
        //this will refresh the osmdroid configuration on resuming.a
        //if you make changes to the configuration, use
        //SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        //Configuration.getInstance().save(this, prefs);
        Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this));
    }

    private void BlueTooth() {
        //BlueTooth() connects, writes, reads, and collects the data from the HC05.

        //Erase welcome message or previous data.
        textView.setText("");

        //mBluetoothAdapter is an object that contains the phone's bluetooth adapter.
        BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        //Check to see if Bluetooth is enabled. If not, ask the user to enable it.
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableAdapter = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableAdapter, 0);
        }

        //Only happens if phone does not have functioning bluetooth adapter.
        if (mBluetoothAdapter == null) {
            Toast.makeText(getApplicationContext(), "Bluetooth null !", Toast.LENGTH_SHORT).show();
        }
        
        if (mBluetoothAdapter != null && mBluetoothAdapter.isEnabled()) {
            //Get a list of all paired devices in range.
            Set<BluetoothDevice> bondedDevices = mBluetoothAdapter.getBondedDevices();

            if (bondedDevices.isEmpty()) {
                Toast.makeText(getApplicationContext(), "Please Pair the Device first", Toast.LENGTH_SHORT).show();
            } 
            
            else {
                //Iterate through list until HC-05 is found.
                for (BluetoothDevice iterator : bondedDevices) {
                    if (iterator.getName().equals("HC-05")) {
                        BluetoothDevice device = iterator;
                        
                        //Form socket with device
                        try {btSocket = device.createRfcommSocketToServiceRecord(MY_UUID);} catch (IOException ex) {}
                        //Connect to device.
                        try {btSocket.connect();} catch (IOException ex) {}
                        if (btSocket.isConnected()) {
                            //Write 'r' to arduino to tell it to send data.
                            try {
                                outStream = btSocket.getOutputStream();
                                inputStream = btSocket.getInputStream();
                                outStream.write('r');
                                //Reading code is ran inside a thread for reliable results.
                                ConnectedThread myThread = new ConnectedThread(btSocket);
                                myThread.start();
                                //Use a stack to reverse contents of textView so newest data is first.
                                    String a = textView.getText().toString();
                                    Stack<String> st = new Stack<>();
                                    Scanner scanner = new Scanner(a);
                                    while (scanner.hasNextLine()) {
                                        String line = scanner.nextLine();
                                        st.push(line);
                                    }
                                    textView.setText("");
                                    while (!st.empty()){
                                        textView.append(st.pop() + "\n");
                                    }
                                
                            }
                            
                            catch (IOException ex) {
                                Toast.makeText(getApplicationContext(), "Error writing to Arduino.", Toast.LENGTH_SHORT).show();
                            }
                        }
                    }
                }
            }
        }
    }


    //Writes a string to a textfile. In Java the max size of a string is heap size/2. Usually around 2gb.
    private void writeToFile(String data) {
        
        //File is written to external storage, which means its readable outside the app.
        File root = Environment.getExternalStorageDirectory();

        //Save to TrailCounter directory.
        File path = new File(root.getAbsolutePath() + "/TrailCounter");

        //Make directory if it does not exist.
        path.mkdirs();

        //Get the time for a timestamp
        String time = java.text.DateFormat.getDateTimeInstance().format(Calendar.getInstance().getTime());

        //Should be whichever TrailCounter was read from. Like TCXX.
        String filename = time +  "-TCTest.txt";
        File file = new File(path, filename);

        //Use a PrintWriter to write to a text file.
        try {
            FileOutputStream f = new FileOutputStream(file);
            PrintWriter pw = new PrintWriter(f);
            pw.append(data); //your string which you want to store
            pw.flush();
            pw.close();
            try {
                f.close();
            } 
            catch (IOException ex) {}

        } 
        catch (FileNotFoundException e) {}
    }

    //Read data in thread 
    private class ConnectedThread extends Thread {
        final Handler handler = new Handler();
        public ConnectedThread(BluetoothSocket socket) {
            InputStream tmpIn = null;
        }
        public void run() {
            byte[] buffer = new byte[256]; int bytes;
            while (true) {
                try {
                    //Read bytes in packets of 256.
                    bytes = inputStream.read(buffer);
                    //the bytes are converted to a string and added to the textview.
                    final String readMessage = new String(buffer, 0, bytes);
                    handler.post(new Runnable() {
                        public void run() {
                           //This must be done because it is impossible to update the UI from within a thread.
                           textView.append(readMessage);
                        }
                    });
                }

                catch (IOException e) {
                    //When there is no more data to read the thread is closed.
                    break;
                }
            }
        }
    }
}
