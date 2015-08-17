# How to Use Social Tracer #

This page details the way the Social Tracer application is installed and run on an Android phone.

## System requirements ##

The current version of Social Tracer requires Android API level 7, which means that the Android version should be at least 2.1 (Eclair). If there are many participants to the experiment that have an Android phone with a lower API level, the minimum requirements will probably be changed.

## Installation ##

In order to install the app, an apk file will be available in the [Downloads](http://code.google.com/p/social-tracer/downloads/list) section soon. This file can either be downloaded from the Android phone's browser and then installed from the phone, or it can be downloaded on a PC and installed using _adb_. The Android Debug Bridge (_adb_) can be found in the Android SDK for either Windows or Linux.

To install Social Tracer using _adb_ on Windows, the following command must be given:

```
 adb.exe install ro.pub.acs.tracer.apk
```

To install Social Tracer using _adb_ on Linux, the following command must be given:

```
 adb install ro.pub.acs.tracer.apk
```

The application uses the following permissions:
  * BLUETOOTH
  * BLUETOOTH\_ADMIN
  * INTERNET
  * WAKE\_LOCK

The Bluetooth permissions are used to access and control the Bluetooth interface, while the Internet permission is used to allow the application to upload the collected data to a central server. The wake lock permission is used to acquire a processor wake lock so that the Bluetooth does not get turned off when the device enters deep sleep.

## Getting Started ##

The main activity of the Social Tracer application looks like this:

![http://social-tracer.googlecode.com/svn/trunk/Images/device-2011-10-29-124412.png](http://social-tracer.googlecode.com/svn/trunk/Images/device-2011-10-29-124412.png)

The first step that must be taken when the application is first run is to fill in data about the user running it. This is done by pressing the "Fill In Data" button, which will open the following dialog:

![http://social-tracer.googlecode.com/svn/trunk/Images/device-2011-10-29-124439.png](http://social-tracer.googlecode.com/svn/trunk/Images/device-2011-10-29-124439.png)

There are fields for name, group, year and Facebook URL. None of these fields are compulsory and they should only be filled if the user wishes so. However, in order for the results of the experiment to be valid, at least the group and year should be filled. These are used to create a community overlay of the individuals participating in the experiment, based on the way students are split into groups. This will give a better idea on the interactions between communities, along with being a good way to test any existing community detection algorithms.

The Facebook URL is used mainly in the same way, except that it helps create a social overlay. Social Tracer only requires the URL of the Facebook page (it does not require logging in and giving access to the Facebook account), so that we can see the friends list and see which participants in the experiment have a friendship relationship.

## Tracing ##

The main purpose of Social Tracer is to gather data about interactions with other mobile device that use Bluetooth, and to log it. However, before starting a trace (by using the "On/Off" button), two other steps should be performed.

Firstly, it is important to note that Android does not allow for a device to be set to "Discoverable" on Bluetooth for more that 5 minutes at a time. This makes sense from a security point of view, as well as regarding battery consumption. Therefore, in order for two devices participating in this experiment to be able to see each other at any point, they have to be paired before the actual start of the experiment.

Secondly, the Social Tracer app regularly scans Bluetooth devices in range when performing the tracing. The discovery interval can be set before starting the trace by using the slider in the main activity. The accepted interval is between 5 and 30 minutes. However, it should be noted that the longer the interval, the sparser will the collected data be, so information that is important to the experiment can be lost.

The tracing is started by using the "On/Off" button, and is performed by a separate background service. While tracing is running, the phone can be put to "suspend", while the application is still running. A text view in the main activity shows the status of the application. To stop tracing, the same "On/Off" button must be pressed.

If Bluetooth is not turned on when the tracing starts, a dialog will pop up asking the user permission to turn it on, as seen in the snapshot below. When tracing stops, if Bluetooth was not on when it had started, it is turned back off.

![http://social-tracer.googlecode.com/svn/trunk/Images/device-2011-10-29-124509.png](http://social-tracer.googlecode.com/svn/trunk/Images/device-2011-10-29-124509.png)

## Uploading ##

Data gathered by Social Tracer is stored locally on the Android phone. However, at certain intervals of time it has to be uploaded to a central server. This is done through the "Upload" button present in the main activity. Once data is uploaded to the server, it is deleted from the phone, so that the space it had occupied is freed from the phone.

Note that data can only be uploaded while tracing is stopped, and vice versa: tracing can only be started while no uploading is performed. We recommend that the participants to the experiment upload data to the central server every 2-3 days (this will depend on the number of total participants to the experiment).