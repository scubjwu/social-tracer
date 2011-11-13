package ro.pub.acs.tracer;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.UUID;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

/**
 * Class for the "Main" tab.
 * @author Radu Ioan Ciobanu
 */
public class MainActivity extends Activity {
	
	// logcat tag.
	static final String LOG_TAG = "Tracer";
	
	// initial strings for text fields.
	String name = "Name";
	String group = "Group";
	String year = "Year";
	String facebookURL = "Facebook URL";
	
	// layout objects.
	ToggleButton startStopButton;
	Button fillInDataButton;
	Button uploadButton;
	SeekBar discoveryIntervalSeekBar;
	
	// booleans for reading and writing user data.
	boolean written = false;
	boolean read = false;
	
	// dialog constants.
	private static final int FILL_IN_DIALOG = 0;
	private static final int UPLOAD_DIALOG = 1;
	private static final int UPLOAD_ERROR_DIALOG = 2;
	
	/** debug boolean. */
	public static boolean DEBUG = false;
	
	/** log boolean. */
	public static boolean LOG = true;
	
	// Bluetooth service variables.
	private BluetoothTracerService bluetoothTracerService;
	boolean isBound = false;
	Intent bluetoothTracerIntent;
	private static final int REQUEST_ENABLE_BT = 0;
	boolean initialBluetoothState = false;
	
	/** Thread object for the Bluetooth server. */
	public BluetoothServerThread bluetoothServer;
	
	/** MAC address of the current device. */
	public static String MAC = "";
	
	/** discovery interval. */
	public static int discoveryInterval = 300;
	
	// wake lock variables.
	PowerManager powerManager;
	PowerManager.WakeLock traceWakeLock;
	PowerManager.WakeLock uploadWakeLock;
	
	// handler for Toast in thread.
	Handler toastHandler = new Handler();
	
	// handler for Dialog in thread.
	Handler dialogHandler = new Handler();
	
	// Runnable that signals the start of uploading data.
	Runnable toastRunnableStart = new Runnable() {
		public void run() {
			Toast.makeText(getApplicationContext(), "Upload started", Toast.LENGTH_SHORT)
				.show();
			setStatus("uploading");
		}
	};
	
	// Runnable that signals the finish of uploading data.
	Runnable toastRunnableFinish = new Runnable() {
		public void run() {
			Toast.makeText(getApplicationContext(), "Upload finished", Toast.LENGTH_SHORT)
				.show();
			setStatus("none");
		}
	};
	
	// Runnable that starts an "upload error".
	Runnable dialogRunnable = new Runnable() {
		public void run() {
			showDialog(UPLOAD_ERROR_DIALOG);
		}
	};
	
	// thread that uploads data.
	Thread uploadThread;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
	    setContentView(R.layout.t_main);
	    
	    if (LOG)
	    	Log.d(MainActivity.LOG_TAG, "Called onCreate");
	    
	    // create the tracer Intent.
	    bluetoothTracerIntent = new Intent(MainActivity.this, BluetoothTracerService.class);
	    
	    // get the buttons and seek bar.
	    startStopButton = (ToggleButton)findViewById(R.id.startStopButton);
	    fillInDataButton = (Button)findViewById(R.id.fillInDataButton);
	    uploadButton = (Button)findViewById(R.id.uploadButton);
	    discoveryIntervalSeekBar = (SeekBar)findViewById(R.id.discoveryIntervalSeekBar);
	    
	    // set discovery interval and seek bar position.
	    setDiscoveryInterval(5 * 60);
	    discoveryIntervalSeekBar.setMax(29 * 60);
	    discoveryIntervalSeekBar.setProgress(4 * 60);
	    
	    // initialize wake lock.
	    powerManager = (PowerManager)getSystemService(Context.POWER_SERVICE);
	    traceWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TraceWL");
	    uploadWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "UploadWL");
	    
	    // read initial data.
	    read = readData();
	    
	    // set the default status.
	    setStatus("none");
	    
	    // set listener for "start/stop" button.
	    startStopButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
				
				// the case for when the button is checked.
				if (startStopButton.isChecked()) {
					// check if data is not being uploaded.
					if (uploadThread == null ||
							(uploadThread != null && !uploadThread.isAlive())) {
						
						// check if Bluetooth is supported.
						if (bluetoothAdapter == null) {
						    return;
						}
						
						// if Bluetooth is not enabled, start a Bluetooth-enabling Activity.
						if (!bluetoothAdapter.isEnabled()) {
							Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
						    startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
						}
						// if it is enabled, start tracing.
						else
							startTracing();
					} else {
						startStopButton.setChecked(false);
					}
				}
				// the case for when the button is unchecked.
				else {
					// set the buttons as clickable and stop the service.
					uploadButton.setEnabled(true);
					discoveryIntervalSeekBar.setEnabled(true);
					getApplicationContext().stopService(bluetoothTracerIntent);
					doUnbindService();
					
					// disable the Bluetooth adapter.
					if (!initialBluetoothState) {
						bluetoothAdapter.disable();
						checkBluetooth(false);
					}
					
					// set the status.
					setStatus("none");
					
					// stop the Bluetooth server (we do not need it anymore).
					//if (bluetoothServer != null)
						//bluetoothServer.cancel();
					
					// release the wake lock.
					if (traceWakeLock.isHeld())
						traceWakeLock.release();
				}
			}
		});
	    
	    // set listener for "fill in data" button.
	    fillInDataButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				// show the dialog if an upload is not in progress.
				if (uploadThread == null ||
						(uploadThread != null && !uploadThread.isAlive())) {
					showDialog(FILL_IN_DIALOG);
				}
			}
		});
	    
	    // set listener for "upload" button.
	    uploadButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				// if the user has not filled in personal data yet, request that he does so.
				if (!written && !read) {
					showDialog(UPLOAD_DIALOG);
					return;
				}
				
				// upload the data.
				uploadData();
			}
		});
	    
	    // set listener for seek bar.
	    discoveryIntervalSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
			public void onStopTrackingTouch(SeekBar seekBar) {
			}
			
			public void onStartTrackingTouch(SeekBar seekBar) {
			}
			
			public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
				setDiscoveryInterval(progress + 60);
			}
		});
    }
	
	@Override
	public void onResume() {
		super.onResume();
		
		if (LOG)
			Log.d(MainActivity.LOG_TAG, "Called onResume");
		
		// check the Bluetooth status.
		checkBluetooth(false);
	}
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		
		// result for Bluetooth enabling request.
		if (requestCode == REQUEST_ENABLE_BT) {
			
			// if the response is "yes", start tracing.
			if (resultCode == RESULT_OK) {
				if (LOG)
					Log.d(MainActivity.LOG_TAG, "Bluetooth started");
				
				if (DEBUG)
					Toast.makeText(this, "Bluetooth started", Toast.LENGTH_SHORT).show();
				
				TextView bluetoothTextView = (TextView)findViewById(R.id.bluetoothTextView);
				bluetoothTextView.setText("Bluetooth: on");
				startTracing();
            }
			// if the response is "no", return to the main Activity.
			else if (resultCode == RESULT_CANCELED) {
            	startStopButton.setChecked(false);
            }
        }
	}

	@Override
	protected Dialog onCreateDialog(int id) {
		Context context = this;
		final Dialog dialog;
		
		switch (id) {
		case FILL_IN_DIALOG:
			dialog = new Dialog(context);
			
			dialog.setContentView(R.layout.popup);
			dialog.setTitle("Fill In Data");
			
			// set start values for user data.
			if (read) {
				((EditText)dialog.findViewById(R.id.nameTextField)).setText(name);
				((EditText)dialog.findViewById(R.id.groupTextField)).setText(group);
				((EditText)dialog.findViewById(R.id.yearTextField)).setText(year);
				((EditText)dialog.findViewById(R.id.facebookURLTextField)).setText(facebookURL);
				written = true;
			}
			
			// set listener for "ok" button.
		    final Button okButton = (Button)dialog.findViewById(R.id.okButton);
		    okButton.setOnClickListener(new View.OnClickListener() {
				public void onClick(View v) {
					name = ((EditText)dialog.findViewById(R.id.nameTextField))
							.getText().toString();
					group = ((EditText)dialog.findViewById(R.id.groupTextField))
							.getText().toString();
					year = ((EditText)dialog.findViewById(R.id.yearTextField))
							.getText().toString();
					facebookURL = ((EditText)dialog.findViewById(R.id.facebookURLTextField))
							.getText().toString();
					
					// write name, group and year to internal storage.
					writeData();
					
					// set write boolean to true.
					written = true;
					
					// dismiss dialog.
					dialog.dismiss();
				}
			});
			break;
			
		case UPLOAD_DIALOG:
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setMessage("Please fill in data about yourself before uploading")
			       .setCancelable(false)
			       .setPositiveButton("OK", new DialogInterface.OnClickListener() {
			           public void onClick(DialogInterface dialog, int id) {
			        	   dialog.cancel();
			           }
			       });
			AlertDialog alert = builder.create();
			dialog = alert;
			break;
			
		case UPLOAD_ERROR_DIALOG:
			AlertDialog.Builder builder1 = new AlertDialog.Builder(this);
			builder1.setMessage("There has been an error connecting to the server." +
					"Please check your Internet connection and try again.")
			       .setCancelable(false)
			       .setPositiveButton("OK", new DialogInterface.OnClickListener() {
			           public void onClick(DialogInterface dialog, int id) {
			        	   dialog.cancel();
			           }
			       });
			AlertDialog alert1 = builder1.create();
			dialog = alert1;
			break;
			
		default:
			dialog = null;
		}
		
		
		return dialog;
	}
	
	@Override
	public void onStart() {
		super.onStart();
		checkBluetooth(true);
		
		if (LOG)
			Log.d(MainActivity.LOG_TAG, "Called onStart");
	}
	
	/**
	 * Starts the tracing operation.
	 */
	private void startTracing() {
		uploadButton.setEnabled(false);
		discoveryIntervalSeekBar.setEnabled(false);
		
		setStatus("scanning");
		
		doBindService();
		getApplicationContext().startService(bluetoothTracerIntent);

		traceWakeLock.acquire();
		
		// we do not need the Bluetooth server thread when using the default UUID.
		//bluetoothServer = new BluetoothServerThread();
		//bluetoothServer.start();
	}
	
	/**
	 * Checks if Bluetooth is activated on the device.
	 * @param first boolean value for first run
	 */
	private void checkBluetooth(boolean first) {
		BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
		TextView bluetoothTextView = (TextView)findViewById(R.id.bluetoothTextView);
		
		if (btAdapter == null) {
			bluetoothTextView.setText("Bluetooth: not supported");
			return;
		}
		
		if (!btAdapter.isEnabled()) {
			
			if (first)
				initialBluetoothState = false;
			
			bluetoothTextView.setText("Bluetooth: off");
			return;
		}
		
		bluetoothTextView.setText("Bluetooth: on");
	}
	
	/**
	 * Writes user data to internal storage.
	 */
	private void writeData() {
		try {
			FileOutputStream fos = openFileOutput("UserData", Context.MODE_PRIVATE);
			DataOutputStream dos = new DataOutputStream(fos);
			
			dos.writeInt(name.length());
			dos.writeBytes(name);
			dos.writeInt(year.length());
			dos.writeBytes(year);
			dos.writeInt(group.length());
			dos.writeBytes(group);
			dos.writeInt(facebookURL.length());
			dos.writeBytes(facebookURL);
			dos.close();
			fos.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		// log information.
		if (LOG)
			Log.i(LOG_TAG, "Saved data: " + name + ", " + year
					+ ", " + group + ", " + facebookURL);
		
		if (DEBUG)
			Toast.makeText(this, "Saved data: " + name + ", " + year
					+ ", " + group + ", " + facebookURL,
	        		Toast.LENGTH_SHORT).show();
	}
	
	/**
	 * Reads user data from internal storage.
	 * @return {@code}true{@code} if read succeeded, {@code}false{@code} otherwise
	 */
	private boolean readData() {
		int length = -1;
		byte[] buffer;
		
		try {
			FileInputStream fis = openFileInput("UserData");
			DataInputStream dis = new DataInputStream(fis);
			
			length = dis.readInt();
			buffer = new byte[length];
			dis.read(buffer, 0, length);
			name = new String(buffer);
			
			length = dis.readInt();
			buffer = new byte[length];
			dis.read(buffer, 0, length);
			year = new String(buffer);
			
			length = dis.readInt();
			buffer = new byte[length];
			dis.read(buffer, 0, length);
			group = new String(buffer);
			
			length = dis.readInt();
			buffer = new byte[length];
			dis.read(buffer, 0, length);
			facebookURL = new String(buffer);
			
			dis.close();
			fis.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			return false;
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
		
		// log information.
		if (LOG)
			Log.i(LOG_TAG, "Restored data: " + name + ", " + year
					+ ", " + group + ", " + facebookURL);
		
		if (DEBUG)
			Toast.makeText(this, "Restored data: " + name + ", " + year
					+ ", " + group + ", " + facebookURL,
	        		Toast.LENGTH_SHORT).show();
		
		return true;
	}
	
	/**
	 * Starts a new upload thread to send data to the central server.
	 */
	private void uploadData() {
		uploadThread = new UploadThread();
		uploadThread.start();
	}
	
	// ServiceConnection object for accesing the Bluetooth tracer service.
	private ServiceConnection serviceConnection = new ServiceConnection() {
		/**
		 * Called when a connection is established with the service.
		 */
		public void onServiceConnected(ComponentName className, IBinder service) {
			bluetoothTracerService = ((BluetoothTracerService.LocalBinder)service).getService();
	    }

		/**
		 * Called when the service is disconnected.
		 */
	    public void onServiceDisconnected(ComponentName className) {
	        bluetoothTracerService = null;
	    }
	};
	
	/**
	 * Establishes connection with the service.
	 */
	void doBindService() {
		getApplicationContext().bindService(bluetoothTracerIntent,
	    		serviceConnection, Context.BIND_AUTO_CREATE);
	    isBound = true;
	}

	/**
	 * Disconnects from a service.
	 */
	void doUnbindService() {
	    if (isBound) {
	        getApplicationContext().unbindService(serviceConnection);
	        isBound = false;
	    }
	}
	
	@Override
	protected void onDestroy() {
	    super.onDestroy();
	    getApplicationContext().stopService(bluetoothTracerIntent);
	    doUnbindService();
	    
	    if (LOG)
	    	Log.d(MainActivity.LOG_TAG, "Called onDestroy");
	    
	    // no need for a Bluetooth thread anymore.
	    //if (bluetoothServer != null && bluetoothServer.isAlive())
	    	//bluetoothServer.cancel();
	    
	    if (traceWakeLock.isHeld())
	    	traceWakeLock.release();
	    
	    if (uploadWakeLock.isHeld())
	    	uploadWakeLock.release();
	    
	    if (!initialBluetoothState)
	    	BluetoothAdapter.getDefaultAdapter().disable();
	    
	    // kill the current process so that it does not remain running.
	    android.os.Process.killProcess(android.os.Process.myPid());
	}
	
	/**
	 * Sets the current status of the application.
	 * @param status new status
	 */
	private void setStatus(String status) {
		TextView statusTextView = (TextView)findViewById(R.id.statusTextView);
		statusTextView.setText("Status: " + status);
	}
	
	/**
	 * Sets the discovery interval for the device.
	 * @param seconds new discovery interval in seconds
	 */
	private void setDiscoveryInterval(int seconds) {
		TextView discoveryIntervalTextView = (TextView)findViewById(R.id.discoveryIntervalTextView);
	    discoveryIntervalTextView.setText("Discovery interval: " + seconds + "s");
	    discoveryInterval = seconds;
	}
	
	/**
	 * Reads the MAC address from the internal storage.
	 */
	private void readMAC() {
		byte[] buffer;
		
		try {
			FileInputStream fis = openFileInput("MAC");
			DataInputStream dis = new DataInputStream(fis);
			
			buffer = new byte[17];
			dis.read(buffer, 0, 17);
			MAC = new String(buffer);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			MAC = "00:00:00:00:00:00";
		} catch (IOException e) {
			e.printStackTrace();
			MAC = "00:00:00:00:00:00";
		}
	}
	
	/**
	 * Thread for uploading data to the central server.
	 * @author Radu Ioan Ciobanu
	 */
	class UploadThread extends Thread {
		public void run() {
			Socket socket;
			String reply = "";
			
			toastHandler.post(toastRunnableStart);
			
			uploadWakeLock.acquire();
			
			try {
				socket = new Socket("cipsm.hpc.pub.ro", 8080);
				PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
				BufferedReader in = new BufferedReader(new InputStreamReader(
		                socket.getInputStream()));
				
				while (!reply.equals("ACK")) {
					// send first message.
					out.println("upload");
					
					// wait for reply.
					reply = in.readLine();
				}
				
				// send user data (including MAC address).
				if (MAC.equals(""))
					readMAC();
				out.println(name + "#" + year + "#" + group + "#" + facebookURL + "#" + MAC);
				
				StringBuffer buffer = new StringBuffer("");
				int count = 0;
				FileInputStream fis = openFileInput("LogFile");
				BufferedReader inFile = new BufferedReader(new InputStreamReader(fis));
				String nextLine = inFile.readLine();
				
				// send logging data.
				while (true) {
					while (nextLine != null && count < 200) {
						count++;
						buffer.append(nextLine + "#");
						nextLine = inFile.readLine();
					}
					
					if (nextLine == null) {
						// send final data.
						out.println(buffer.toString());
						break;
					}
					
					// send current data.
					out.println(buffer.toString());
					out.flush();
					buffer.setLength(0);
					
					count = 0;
				}
				
				// send closing message.
				out.println("finish");
				
				// now that the file has been sent, delete it.
				getApplicationContext().deleteFile("LogFile");
			} catch (UnknownHostException e) {
				e.printStackTrace();
				dialogHandler.post(dialogRunnable);
			} catch (IOException e) {
				e.printStackTrace();
				dialogHandler.post(dialogRunnable);
			} finally {
				// release the wake lock for uploading.
				if (uploadWakeLock.isHeld())
			    	uploadWakeLock.release();
			}
			
			toastHandler.post(toastRunnableFinish);
		}
	}
	
	/**
	 * Thread for receiving Bluetooth connections (may not need it).
	 * @author Radu Ioan Ciobanu
	 */
	class BluetoothServerThread extends Thread {
		private BluetoothServerSocket serverSocket;
		
		@Override
		public void run() {
	        BluetoothSocket socket = null;
	        
	        // keep listening until exception occurs or a socket is returned.
	        while (true) {
	        	
	        	BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
				
				while (!bluetoothAdapter.isEnabled())
					;
				
		        try {
		            serverSocket = bluetoothAdapter 
		            		.listenUsingRfcommWithServiceRecord("ro.pub.acs", UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"));
		            if (LOG)
		            	Log.d(LOG_TAG, "Server socket created");
		        } catch (IOException e) {
		        	if (LOG)
		        		Log.w(LOG_TAG, "Server socket exception " + e.toString());
		        	e.printStackTrace();
		        	continue;
		        }
	        	
	            try {
	            	if (LOG)
	            		Log.d(LOG_TAG, "Starting to accept connection");
	                
	            	socket = serverSocket.accept();
	                
	            	if (LOG)
	            		Log.d(LOG_TAG, "Connection accepted");
	            } catch (IOException e) {
	            	if (LOG)
	            		Log.w(LOG_TAG, "Accept exception " + e.toString());
	            	
	            	e.printStackTrace();
	                continue;
	            }
	            
	            if (LOG)
	            	Log.d(LOG_TAG, "Exited try-catch");
	            
	            // if a connection was accepted.
	            if (socket != null) {
	                // start a thread to manage the connection.
	            	new BluetoothAuxThread(socket).start();
	                
	            	if (LOG)
	            		Log.d(LOG_TAG, "Started new socket manager");
	            }
	            
	            try {
		            serverSocket.close();
		            
		            if (LOG)
		            	Log.d(LOG_TAG, "Connection cancelled");
		        } catch (IOException e) {
		        	e.printStackTrace();
		        	continue;
		        }
	        }
	    }
	 
		/**
		 * Cancels the listening socket, causing the thread to finish.
		 */
	    public void cancel() {
	        try {
	        	if (serverSocket != null)
	        		serverSocket.close();
	            
	        	if (LOG)
	        		Log.d(LOG_TAG, "Connection cancelled");
	        } catch (IOException e) {
	        	e.printStackTrace();
	        	return;
	        }
	    }
	}
	
	/**
	 * Thread that closes a Bluetooth connection.
	 * @author Radu Ioan Ciobanu
	 */
	class BluetoothAuxThread extends Thread {
		BluetoothSocket socket;
		
		/**
		 * Constructor for the BluetoothAuxThread class.
		 * @param socket socket to be closed
		 */
		public BluetoothAuxThread(BluetoothSocket socket) {
			if (LOG)
				Log.d(LOG_TAG, "Initializing socket manager");
			
			this.socket = socket;			
		}
		
		@Override
		public void run() {
			try {
				socket.close();
				
				if (LOG)
					Log.d(LOG_TAG, "Connection closed");
			} catch (IOException e) {
				if (LOG)
					Log.w(LOG_TAG, "Connection close exception" + e.toString());
				
				e.printStackTrace();
				return;
			}
		}
	}
}
