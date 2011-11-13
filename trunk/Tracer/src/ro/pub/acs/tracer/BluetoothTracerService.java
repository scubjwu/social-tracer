package ro.pub.acs.tracer;

import java.io.DataOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

/**
 * Service for Bluetooth tracing.
 * @author Radu Ioan Ciobanu
 */
public class BluetoothTracerService extends Service {

	// notitifaction variables.
	private NotificationManager notificationManager;
	private int NOTIFICATION = R.string.bluetoothTracerServiceNotification;
	Handler toastHandler = new Handler();
	
	// IntentFilters for Bluetooth actions.
	private IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
	private IntentFilter filter2 = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
	
	// current Bluetooth adapter.
	BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
	
	// list of currently discovered devices.
	private List<Device> devices = new ArrayList<Device>();
	
	// Bluetooth broadcast receiver.
	private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
		public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            
            if (MainActivity.LOG)
	            Log.i(MainActivity.LOG_TAG, "Called BroadcastReceiver onReceive");
	            
            if (MainActivity.DEBUG)
            	toastHandler.post(new ToastRunnable("Called BroadcastReceiver onReceive"));
            
            // discovery finds a device.
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                
            	// get the BluetoothDevice object from the Intent.
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                
                if (MainActivity.LOG)
                	Log.i(MainActivity.LOG_TAG, device.getName() + "," + device.getAddress()
                			+ "," + System.currentTimeMillis());
                
                if (MainActivity.DEBUG)
	                toastHandler.post(new ToastRunnable(device.getName() + "," + device.getAddress()
	                    	+ "," + System.currentTimeMillis()));
                
                // add discovered device to list of devices.
                devices.add(new Device(device.getName(), device.getAddress(),
                		System.currentTimeMillis()));
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
            	// discovery finishes.
            	
            	if (MainActivity.LOG)
            		Log.i(MainActivity.LOG_TAG, "Discovery finished");
            	
            	if (MainActivity.DEBUG)
            		toastHandler.post(new ToastRunnable("Discovery finished"));
            	            	
            	// write data to log file.
            	if (writeData(devices))
            			devices.clear();
            	
            	if (MainActivity.LOG)
            		Log.d(MainActivity.LOG_TAG, "Written");
    			
    			// start new thread.
    			new DiscoveryThread().start();
            }
        }
    };
	
	/**
     * Class used by clients to access this service.
     * @author Radu Ioan Ciobanu
     */
    public class LocalBinder extends Binder {
        BluetoothTracerService getService() {
            return BluetoothTracerService.this;
        }
    }
	
    @Override
    public void onCreate() {
        notificationManager = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);

        // register the filters.
        registerReceiver(mReceiver, filter);
        registerReceiver(mReceiver, filter2);
        
        // display a notification.
        showNotification();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (MainActivity.LOG)
        	Log.i(MainActivity.LOG_TAG, "Received start id " + startId + ": " + intent);
        
        // get MAC address and write it.
        MainActivity.MAC = bluetoothAdapter.getAddress();
        writeMAC(MainActivity.MAC);
        
        if (MainActivity.DEBUG)
	        Toast.makeText(this, "MAC = " + bluetoothAdapter.getAddress(),
	        		Toast.LENGTH_SHORT).show();
        if (MainActivity.LOG)
        	Log.i(MainActivity.LOG_TAG, "MAC = " + bluetoothAdapter.getAddress());
        
        // start first discovery Thread.
        new DiscoveryThread().start();
        
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        // cancel the persistent notification.
        notificationManager.cancel(NOTIFICATION);

        // notify user.
        Toast.makeText(this, R.string.bluetoothTracerServiceStopped,
        		Toast.LENGTH_SHORT).show();
        
        if (MainActivity.LOG)
        	Log.i(MainActivity.LOG_TAG, "Bluetooth Tracer Service stopped");
        
        // unregister the receiver.
        unregisterReceiver(mReceiver);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }
    
    // object that receives interactions from clients.
    private final IBinder binder = new LocalBinder();

    /**
     * Shows a notification while this service is running.
     */
    private void showNotification() {
        Toast.makeText(this, R.string.bluetoothTracerServiceStarted,
        		Toast.LENGTH_SHORT).show();
        
        if (MainActivity.LOG)
        	Log.i(MainActivity.LOG_TAG, "Bluetooth Tracer Service started!");
    }
    
    /**
     * Writes device log data to internal storage.
     * @param devices list of discovered devices
     */
    private boolean writeData(List<Device> devices) {
    	try {
			FileOutputStream fos = openFileOutput("LogFile", Context.MODE_APPEND);
			PrintWriter out = new PrintWriter(new OutputStreamWriter(fos));
			
			for (Device device : devices) {
				out.println(device.name + "#" + device.MAC + "#" + device.time);
								
				// log info.
				if (MainActivity.LOG)
					Log.i(MainActivity.LOG_TAG, "Saved data: " + device.name
							+ ", " + device.MAC + ", " + device.time);
				
				if (MainActivity.DEBUG)
					toastHandler.post(new ToastRunnable("Saved data: " + device.name
							+ ", " + device.MAC + ", " + device.time));
			}
			
			out.flush();
			out.close();
			fos.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			return false;
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
    	
    	return true;
    }
    
    /**
     * Checks which paired devices are in range.
     */
    private void checkPairedDevices() {
    	Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();

    	if (pairedDevices.size() > 0) {
    		for (BluetoothDevice device : pairedDevices) {
        		
    			if (MainActivity.LOG)
    				Log.d(MainActivity.LOG_TAG, "Checking " + device.getName()
    						+ ", " + device.getAddress());

    			if (MainActivity.DEBUG)
    				toastHandler.post(new ToastRunnable("Checking" + device.getName()
    						+ ", " + device.getAddress()));
        	}
        }
        
    	// cancel any ongoing discovery.
    	if (MainActivity.LOG)
    		Log.d(MainActivity.LOG_TAG, "cancel discovery");
        bluetoothAdapter.cancelDiscovery();
        
        // try to connect to paired devices.
        for (BluetoothDevice pairedDevice : pairedDevices) {
        	BluetoothSocket tmp = null;

            try {
            	// create a socket to connect to paired device.
            	Method m = pairedDevice.getClass().getMethod("createRfcommSocket", new Class[] { int.class });
            	tmp = (BluetoothSocket) m.invoke(pairedDevice, 1);
            } catch (NoSuchMethodException e) {
            	if (MainActivity.LOG)
            		Log.w(MainActivity.LOG_TAG, "Exception " + e.toString());
            	continue;
            } catch (IllegalAccessException e) {
            	if (MainActivity.LOG)
            		Log.w(MainActivity.LOG_TAG, "Exception " + e.toString());
            	continue;
            } catch (InvocationTargetException e) {
            	if (MainActivity.LOG)
            		Log.w(MainActivity.LOG_TAG, "Exception " + e.toString());
            	continue;
            }
            
            try {
                // connect to paired device.
            	tmp.connect();
                
            	if (MainActivity.LOG)
            		Log.i(MainActivity.LOG_TAG, pairedDevice.getName() + " is ON");
                
            	if (MainActivity.DEBUG)
            		toastHandler.post(new ToastRunnable(pairedDevice.getName() + " is ON"));
                
            	tmp.close();
                
            	// add to list of discovered devices.
                devices.add(new Device(pairedDevice.getName(), pairedDevice.getAddress(),
                		System.currentTimeMillis()));
            } catch (IOException connectException) {
            	if (MainActivity.LOG)
            		Log.w(MainActivity.LOG_TAG, "Exception " + connectException.toString());
            	
                try {
                	if (MainActivity.LOG)
                		Log.i(MainActivity.LOG_TAG, pairedDevice.getName() + " is OFF");

                	if (MainActivity.DEBUG)
                		toastHandler.post(new ToastRunnable(pairedDevice.getName() + " is OFF"));
                    
                	// close connection.
                	tmp.close();
                } catch (IOException closeException) {
                	if (MainActivity.LOG)
                		Log.w(MainActivity.LOG_TAG, "Exception " + closeException.toString());
                }
            }
        }
    }
    
    /**
     * Writes MAC address to internal storage.
     * @param MAC MAC address of the device
     */
    private void writeMAC(String MAC) {
		try {
			FileOutputStream fos = openFileOutput("MAC", Context.MODE_PRIVATE);
			DataOutputStream dos = new DataOutputStream(fos);
			dos.writeBytes(MAC);
			dos.close();
			fos.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
    
    /**
     * Class for an encountered Bluetooth device.
     * @author Radu Ioan Ciobanu
     */
    private class Device {
    	String name;
    	String MAC;
    	long time;
    	
    	/**
    	 * Constructor for the Device class.
    	 * @param name name of the device
    	 * @param MAC MAC address of the device
    	 * @param time timestamp of when the device was encountered
    	 */
    	public Device(String name, String MAC, long time) {
    		this.name = name;
    		this.MAC = MAC;
    		this.time = time;
    	}
    }
    
    /**
     * Thread that starts a Bluetooth discovery.
     * @author Radu Ioan Ciobanu
     */
    private class DiscoveryThread extends Thread {
    	public void run() {
    		
    		if (MainActivity.LOG)
				Log.d(MainActivity.LOG_TAG, "Going to sleep");
    		
    		try {
				Thread.sleep(MainActivity.discoveryInterval * 1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
				
				if (MainActivity.LOG)
					Log.w(MainActivity.LOG_TAG, "Sleep exception " + e.toString());
			}
			
			if (MainActivity.LOG)
				Log.d(MainActivity.LOG_TAG, "Woke up");
			
    		checkPairedDevices();
        	bluetoothAdapter.startDiscovery();
    	}
    }
    
    /**
     * Runnable for outputting toast messages.
     * @author Radu Ioan Ciobanu
     */
    private class ToastRunnable implements Runnable {
    	String message = "";
    	
    	/**
    	 * Constructor for ToastRunnable class.
    	 * @param message message to be outputted
    	 */
    	public ToastRunnable(String message) {
    		this.message = message;
    	}
    	
    	/**
    	 * Method for outputting data.
    	 */
    	public void run() {
    		Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
    	}
    }
}
