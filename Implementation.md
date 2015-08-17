# Implementation #

This page details the way the Social Tracer application is implemented.

## General ##

Social Tracer is an Android application that collects data about Bluetooth encounters for a device. It only uses four permissions (BLUETOOTH, BLUETOOTH\_ADMIN, INTERNET and WAKE\_LOCK) and does not access personal data from the phone. The entire source code is available at the [Source](http://code.google.com/p/social-tracer/source/checkout) section, and the .apk will be available soon at the [Downloads](http://code.google.com/p/social-tracer/downloads/list) section.

## Bluetooth ##

When starting tracing, Bluetooth is opened. Then, at regular time intervals (selected by the user prior to starting to trace) a search for devices in range is performed. The paired devices are checked first. The advantage of paired devices is that a connection to them can be created even if they are not set to discoverable. Therefore, in order to find paired devices in range, we have to cycle through the list of paired devices and attempt to connect to each of them:

```
Method m = pairedDevice.getClass().getMethod("createRfcommSocket", new Class[] { int.class });
BluetoothSocket socket = (BluetoothSocket)m.invoke(pairedDevice, 1);
socket.connect();
```

If the `connect()` function throws an exception, it means that the device is not in range. If it succeeds, then the device is in range and the connection can then be dropped (the device name and address are already known).

After scanning all paired devices, a discovery of unpaired devices in range is then performed, using a `BroadcastReceiver` with an `IntentFilter` set to `BluetoothDevice.ACTION_FOUND`.

## Data format ##

When data is logged for tracing, it is saved in a log file as an Android local file named "LogFile". An entry in a log file contains three fields: name, address and timestamp. The name and address are taken from the `BluetoothDevice` specific to the device found (using `getName()` and `getAddress()`), where the name is given by the user when setting up the Bluetooth, and the address is the unique MAC address of the device. The timestamp is computed using `System.currentTimeMillis()`. These entries are separated by the "#" symbol when written to the file.

## Uploading ##

For uploading data to the central server, the data is kept in the same format as when saving it, and it is done with 200 entries at a time. The central server listens on a `ServerSocket` for incoming connections, and saves the data from a connection to a file which has the name composed of the MAC of the logging device and the time of the server.

## Battery Consumption ##

The application does not consume a large amount of battery life, mainly because it can also run while the device is in "suspend". It does however keep a partial wake lock acquired while it is running (a partial wake lock keeps the CPU on while it is acquired).

We tested on two Samsung Galaxy S phones with Android 2.2.1. The application was run for 200 minutes, with WiFi and GPS turned off. The two phones had been paired to each other prior to running the application, as well as to a Nokia 6300 phone and to a Dell Inspiron 6400 laptop. The laptop and the Nokia phone were left on during the whole duration of the experiment, and the discovery interval was set to 300 seconds.

The experiment started with both phones fully charged. After we started tracing, we put the phones in "suspend" and left them like that for the entire duration of the experiment. When the testing was over, one of the Samsung Galaxy phones was left with 87% of the battery, and the other one with 86%.

Another experiment made in similar conditions showed that in 4 hours, the battery level went from 84% to 73% on one Samsung Galaxy S phone, and from 88% to 78% on the other one.