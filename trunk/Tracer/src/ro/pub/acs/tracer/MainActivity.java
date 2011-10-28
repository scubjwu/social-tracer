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
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

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
	
	boolean written = false;
	boolean read = false;
	
	// dialog constants.
	static final int FILL_IN_DIALOG = 0;
	static final int UPLOAD_DIALOG = 1;
	
	// Bluetooth service.
	private BluetoothTracerService bluetoothTracerService;
	boolean isBound = false;
	
	Intent bluetoothTracerIntent;
	
	private static final int REQUEST_ENABLE_BT = 0;
	
	boolean initialBluetoothState = false;
	
	public static String MAC = "";
	
	public static int discoveryInterval = 300;
	
	public BluetoothServerThread bluetoothServer;
	boolean firstCall = true;
	
	// handler for toast in thread.
	Handler toastHandler = new Handler();
	Runnable toastRunnableStart = new Runnable() {
		public void run() {
			Toast.makeText(getApplicationContext(), "Upload started", Toast.LENGTH_SHORT)
				.show();
			setStatus("uploading");
		}
	};
	Runnable toastRunnableFinish = new Runnable() {
		public void run() {
			Toast.makeText(getApplicationContext(), "Upload finished", Toast.LENGTH_SHORT)
				.show();
			setStatus("none");
		}
	};
	
	// thread that uploads data.
	Thread uploadThread;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
	    setContentView(R.layout.t_main);
	    
	    //bluetoothTracer.init();
	    
	    bluetoothTracerIntent = new Intent(MainActivity.this, BluetoothTracerService.class);
	    
	    startStopButton = (ToggleButton)findViewById(R.id.startStopButton);
	    fillInDataButton = (Button)findViewById(R.id.fillInDataButton);
	    uploadButton = (Button)findViewById(R.id.uploadButton);
	    discoveryIntervalSeekBar = (SeekBar)findViewById(R.id.discoveryIntervalSeekBar);
	    
	    setDiscoveryInterval(5 * 60);
	    discoveryIntervalSeekBar.setMax(29 * 60);
	    discoveryIntervalSeekBar.setProgress(4 * 60);
	    
	    read = readData();
	    
	    /*
	     * Set listener for "start/stop" button.
	     */
	    startStopButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				
				BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
				
				if (startStopButton.isChecked()) {
					if (uploadThread == null ||
							(uploadThread != null && !uploadThread.isAlive())) {
						uploadButton.setEnabled(false);
						discoveryIntervalSeekBar.setEnabled(false);
						
						if (bluetoothAdapter == null) {
						    return;
						}
						
						if (!bluetoothAdapter.isEnabled()) {
							Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
						    startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
						}
						
						setStatus("scanning");
						
						//bluetoothTracer.start();
						doBindService();
						getApplicationContext().startService(bluetoothTracerIntent);
						
						if (firstCall) {
							bluetoothServer = new BluetoothServerThread();
							bluetoothServer.start();
							firstCall = false;
						}
						
					} else {
						startStopButton.setChecked(false);
					}
				}
				else {
					uploadButton.setEnabled(true);
					discoveryIntervalSeekBar.setEnabled(true);
					getApplicationContext().stopService(bluetoothTracerIntent);
					doUnbindService();
					
					if (!initialBluetoothState) {
						bluetoothAdapter.disable();
						checkBluetooth(false);
					}
					
					setStatus("none");
					
					bluetoothServer.cancel();
					
					//BluetoothTracerActivity.stop();
				}
			}
		});
	    
	    /*
	     * Set listener for "fill in data" button.
	     */
	    fillInDataButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				if (uploadThread == null ||
						(uploadThread != null && !uploadThread.isAlive())) {
					showDialog(FILL_IN_DIALOG);
				}
			}
		});
	    
	    /*
	     * Set listener for "upload" button.
	     */
	    uploadButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				if (!written && !read) {
					showDialog(UPLOAD_DIALOG);
					return;
				}
				
				if (MAC.equals(""))
					; //TODO enable BT and get MAC address
				
				// upload MAC as well
				uploadData();
			}
		});
	    
	    /*
	     * Set listener for seek bar.
	     */
	    discoveryIntervalSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
			public void onStopTrackingTouch(SeekBar seekBar) {
			}
			
			public void onStartTrackingTouch(SeekBar seekBar) {
			}
			
			public void onProgressChanged(SeekBar seekBar, int progress,
					boolean fromUser) {
				setDiscoveryInterval(progress + 60);
			}
		});
	    
	    setStatus("none");
    }
	
	@Override
	public void onResume() {
		super.onResume();
		checkBluetooth(false);
	}
	
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == REQUEST_ENABLE_BT) {
			if (resultCode == RESULT_OK) {
				Log.v(MainActivity.LOG_TAG, "Bluetooth started");
				TextView bluetoothTextView = (TextView)findViewById(R.id.bluetoothTextView);
				bluetoothTextView.setText("Bluetooth: on");
            }
        }
	}
	
	protected Dialog onCreateDialog(int id) {
		Context context = this;
		final Dialog dialog;
		
		switch (id) {
		case FILL_IN_DIALOG:
			dialog = new Dialog(context);
			
			dialog.setContentView(R.layout.popup);
			dialog.setTitle("Fill In Data");
			
			/*
			 * Set start values for user data.
			 */
			if (read) {
				((EditText)dialog.findViewById(R.id.nameTextField)).setText(name);
				((EditText)dialog.findViewById(R.id.groupTextField)).setText(group);
				((EditText)dialog.findViewById(R.id.yearTextField)).setText(year);
				((EditText)dialog.findViewById(R.id.facebookURLTextField)).setText(facebookURL);
				written = true;
			}
			
			/*
		     * Set listener for "ok" button.
		     */
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
			
		default:
			dialog = null;
		}
		
		
		return dialog;
	}
	
	public void onStart() {
		super.onStart();
		checkBluetooth(true);
	}
	
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
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		// log info.
		Log.v(LOG_TAG, "Saved data: " + name + ", " + year
				+ ", " + group + ", " + facebookURL);
	}
	
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
			//e.printStackTrace();
			return false;
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
		
		// log info.
		Log.v(LOG_TAG, "Restored data: " + name + ", " + year
				+ ", " + group + ", " + facebookURL);
		
		return true;
	}
	
	private void uploadData() {
		uploadThread = new UploadThread();
		uploadThread.start();
	}
	
	private ServiceConnection serviceConnection = new ServiceConnection() {
	    public void onServiceConnected(ComponentName className, IBinder service) {
	        // This is called when the connection with the service has been
	        // established, giving us the service object we can use to
	        // interact with the service.  Because we have bound to a explicit
	        // service that we know is running in our own process, we can
	        // cast its IBinder to a concrete class and directly access it.
	        bluetoothTracerService = ((BluetoothTracerService.LocalBinder)service).getService();

	        // Tell the user about this for our demo.
	        //Toast.makeText(Binding.this, R.string.local_service_connected,
	          //      Toast.LENGTH_SHORT).show();
	    }

	    public void onServiceDisconnected(ComponentName className) {
	        // This is called when the connection with the service has been
	        // unexpectedly disconnected -- that is, its process crashed.
	        // Because it is running in our same process, we should never
	        // see this happen.
	        bluetoothTracerService = null;
	        //Toast.makeText(Binding.this, R.string.local_service_disconnected,
	          //      Toast.LENGTH_SHORT).show();
	    }
	};
	
	void doBindService() {
	    // Establish a connection with the service.  We use an explicit
	    // class name because we want a specific service implementation that
	    // we know will be running in our own process (and thus won't be
	    // supporting component replacement by other applications).
	    getApplicationContext().bindService(bluetoothTracerIntent,
	    		serviceConnection, Context.BIND_AUTO_CREATE);
	    isBound = true;
	}

	void doUnbindService() {
	    if (isBound) {
	        // Detach our existing connection.
	        getApplicationContext().unbindService(serviceConnection);
	        isBound = false;
	    }
	}
	
	@Override
	protected void onDestroy() {
	    super.onDestroy();
	    doUnbindService();
	}
	
	private void setStatus(String status) {
		TextView statusTextView = (TextView)findViewById(R.id.statusTextView);
		statusTextView.setText("Status: " + status);
	}
	
	private void setDiscoveryInterval(int seconds) {
		TextView discoveryIntervalTextView = (TextView)findViewById(R.id.discoveryIntervalTextView);
	    discoveryIntervalTextView.setText("Discovery interval: " + seconds + "s");
	    discoveryInterval = seconds;
	}
	
	class UploadThread extends Thread {
		public void run() {
			Socket socket;
			String reply = "";
			
			toastHandler.post(toastRunnableStart);
			
			try {
				socket = new Socket("192.168.1.100", 5432); // TODO replace with actual address
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
				out.println(name + "#" + year + "#" + group + "#" + facebookURL + "#" + MAC);
				
				StringBuffer buffer = new StringBuffer("");
				int count = 0;
				FileInputStream fis = openFileInput("LogFile");
				BufferedReader inFile = new BufferedReader(new InputStreamReader(fis));
				String nextLine = inFile.readLine();
				
				while (true) {
					while (nextLine != null && count < 3) {
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
				
				out.println("finish");
				
				// now that the file has been sent, delete it.
				getApplicationContext().deleteFile("LogFile");
			} catch (UnknownHostException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
			toastHandler.post(toastRunnableFinish);
		}
	}
	
	class BluetoothServerThread extends Thread {
		private BluetoothServerSocket serverSocket;
		
		public BluetoothServerThread() {
			// Use a temporary object that is later assigned to mmServerSocket,
	        // because mmServerSocket is final
			
			BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
			
			while (!bluetoothAdapter.isEnabled())
				;
			
	        try {
	            // MY_UUID is the app's UUID string, also used by the client code
	            serverSocket = bluetoothAdapter 
	            		.listenUsingRfcommWithServiceRecord("ro.pub.acs", new UUID(1, 1));
	        } catch (IOException e) {
	        	Log.v(LOG_TAG, "exception...");
	        	e.printStackTrace();
	        }
		}
		
		public void run() {
	        BluetoothSocket socket = null;
	        // Keep listening until exception occurs or a socket is returned
	        while (true) {
	            try {
	            	if (serverSocket == null)
	            		Log.v(LOG_TAG, "NULL");
	                socket = serverSocket.accept();
	            } catch (IOException e) {
	                break;
	            }
	            // If a connection was accepted
	            if (socket != null) {
	                // Do work to manage the connection (in a separate thread)
	                try {
	                	Log.v(LOG_TAG, "Connection accepted");
						socket.close();
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
	                //mmServerSocket.close();
	                //break;
	            }
	        }
	    }
	 
	    /** Will cancel the listening socket, and cause the thread to finish */
	    public void cancel() {
	        try {
	            serverSocket.close();
	            Log.v(LOG_TAG, "Connection cancelled");
	        } catch (IOException e) { }
	    }
	}
}
