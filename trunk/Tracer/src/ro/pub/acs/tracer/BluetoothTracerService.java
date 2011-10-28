package ro.pub.acs.tracer;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import android.app.NotificationManager;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

public class BluetoothTracerService extends Service {
	
	private NotificationManager notificationManager;
	private int NOTIFICATION = R.string.bluetoothTracerServiceNotification;
	private IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
	private IntentFilter filter2 = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
	BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
	private List<Device> devices = new ArrayList<Device>();
	private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.i(MainActivity.LOG_TAG, "AJUNG AICI");
            // When discovery finds a device
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                // Get the BluetoothDevice object from the Intent
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                // Add the name and address to an array adapter to show in a ListView
                //mArrayAdapter.add(device.getName() + "\n" + device.getAddress());
                Log.i(MainActivity.LOG_TAG, device.getName() + "," + device.getAddress()
                		+ "," + System.currentTimeMillis());
                devices.add(new Device(device.getName(), device.getAddress(),
                		System.currentTimeMillis()));
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
            	Log.i(MainActivity.LOG_TAG, "Discovery finished");
            	
            	checkPairedDevices();
            	            	
            	// WRITE TO FILE.
            	writeData(devices);
            	
            	// clear list.
            	devices.clear();
            	
            	// AND THEN SLEEP AND TRY AGAIN?
            	bluetoothAdapter.startDiscovery();
    			try {
					Thread.sleep(1000);
					//Thread.sleep(MainActivity.discoveryInterval * 1000); // TODO this
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
            }
        }
    };
	
	/**
     * Class for clients to access.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with
     * IPC.
     */
    public class LocalBinder extends Binder {
        BluetoothTracerService getService() {
            return BluetoothTracerService.this;
        }
    }
	
    @Override
    public void onCreate() {
        notificationManager = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);

        registerReceiver(mReceiver, filter); // Don't forget to unregister during onDestroy
        registerReceiver(mReceiver, filter2);
        
        // Display a notification about us starting.  We put an icon in the status bar.
        showNotification();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(MainActivity.LOG_TAG, "Received start id " + startId + ": " + intent);
        // We want this service to continue running until it is explicitly
        // stopped, so return sticky.
        
        /*new Thread() {
        	@Override
        	public void run() {
        		// TODO Auto-generated method stub
        		super.run();
        		while (true) {
        			Log.i(MainActivity.LOG_TAG, "startDiscovery");
        			bluetoothAdapter.startDiscovery();
        			try {
						Thread.sleep(1000);
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
        		}
        	}
        }.start();*/
        
        while (!bluetoothAdapter.isEnabled())
        	;
        
        // get MAC address.
        MainActivity.MAC = bluetoothAdapter.getAddress();
        
        checkPairedDevices();
        
        // Register the BroadcastReceiver
        bluetoothAdapter.startDiscovery();
        
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        // Cancel the persistent notification.
        notificationManager.cancel(NOTIFICATION);

        // Tell the user we stopped.
        Toast.makeText(this, R.string.bluetoothTracerServiceStopped,
        		Toast.LENGTH_SHORT).show();
        
        unregisterReceiver(mReceiver);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }
    
    // This is the object that receives interactions from clients.  See
    // RemoteService for a more complete example.
    private final IBinder binder = new LocalBinder();

    /**
     * Show a notification while this service is running.
     */
    private void showNotification() {
        // In this sample, we'll use the same text for the ticker and the expanded notification
        //CharSequence text = getText(R.string.bluetoothTracerServiceStarted);

        /*
        // Set the icon, scrolling text and timestamp
        Notification notification = new Notification(R.drawable.stat_sample, text,
                System.currentTimeMillis());

        // The PendingIntent to launch our activity if the user selects this notification
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
                new Intent(this, LocalServiceActivities.Controller.class), 0);

        // Set the info for the views that show in the notification panel.
        notification.setLatestEventInfo(this, getText(R.string.local_service_label),
                       text, contentIntent);
                       */

        // Send the notification.
        //notificationManager.notify(NOTIFICATION, notification);
    	Toast.makeText(this, "???", Toast.LENGTH_SHORT).show();
    	Log.i(MainActivity.LOG_TAG, "NOTIFICATION!");
    }
    
    private void writeData(List<Device> devices) {
    	try {
			FileOutputStream fos = openFileOutput("LogFile", Context.MODE_APPEND);
			PrintWriter out = new PrintWriter(new OutputStreamWriter(fos));
			
			for (Device device : devices) {
				out.println(device.name + "#" + device.MAC + "#" + device.time);
								
				// log info.
				Log.v(MainActivity.LOG_TAG, "Saved data: " + device.name
						+ ", " + device.MAC + ", " + device.time);
			}
			
			out.flush();
			out.close();
			fos.close();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    }
    
    private void checkPairedDevices() {
    	Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        // If there are paired devices
        if (pairedDevices.size() > 0) {
        	// Loop through paired devices
        	for (BluetoothDevice device : pairedDevices) {
        		Log.i(MainActivity.LOG_TAG, "!" + device.getName() + ", " + device.getAddress());
        	}
        }
        
        for (BluetoothDevice pairedDevice : pairedDevices) {
        	BluetoothSocket tmp = null;

            // Get a BluetoothSocket to connect with the given BluetoothDevice
            try {
                // MY_UUID is the app's UUID string, also used by the server code
                tmp = pairedDevice.createRfcommSocketToServiceRecord(new UUID(1, 1));
                //pairedDevice.
            } catch (IOException e) {
            	Log.i(MainActivity.LOG_TAG, "!exc");
            }
            
            try {
                // Connect the device through the socket. This will block
                // until it succeeds or throws an exception
                tmp.connect();
                Log.i(MainActivity.LOG_TAG, pairedDevice.getName() + "ON");
                tmp.close();
            } catch (IOException connectException) {
                // Unable to connect; close the socket and get out
                try {
                	Log.i(MainActivity.LOG_TAG, pairedDevice.getName() + "OFF");
                    tmp.close();
                } catch (IOException closeException) { }
            }
        }
    }
}

class Device {
	String name;
	String MAC;
	long time;
	
	public Device(String name, String MAC, long time) {
		this.name = name;
		this.MAC = MAC;
		this.time = time;
	}
}