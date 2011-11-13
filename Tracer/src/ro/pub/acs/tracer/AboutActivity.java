package ro.pub.acs.tracer;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

/**
 * Class for the "About" tab.
 * @author Radu Ioan Ciobanu
 */
public class AboutActivity extends Activity {
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		TextView textview = new TextView(this);
		textview.setText("Social Tracer is a social tracing application to be used in " +
				"opportunistic networking. Its purpose is to gather data about the social " +
				"interactions of individuals. This is done through an Android application " +
				"that uses a device's Bluetooth to periodically scan for other devices in " +
				"range.\n\nThis is a project done at the Politehnica University of Bucharest, " +
				"and will be used for an experiment that tracks the social behavior of the " +
				"students at the university for a 40-day period. The goal is to gather traces " +
				"similar to the ones from CRAWDAD that can later be used as input to an " +
				"opportunistic networking data dissemination algorithm.\n\nFor more information " +
				"or if you find any bugs, please visit http://code.google.com/p/social-tracer/");
		setContentView(textview);
    }
}
