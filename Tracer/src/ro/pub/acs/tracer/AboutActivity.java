package ro.pub.acs.tracer;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

/**
 * 
 * @author Radu
 * Class for the "About" tab.
 */
public class AboutActivity extends Activity {
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		TextView textview = new TextView(this);
		textview.setText("This is the About tab");
		setContentView(textview);
    }
}
