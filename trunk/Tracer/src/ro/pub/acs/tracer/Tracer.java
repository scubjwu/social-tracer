package ro.pub.acs.tracer;

import android.app.TabActivity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TabHost;

public class Tracer extends TabActivity {
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
    	super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        TabHost tabHost = getTabHost();  // The activity TabHost
        TabHost.TabSpec spec;  // Resusable TabSpec for each tab
        Intent intent;  // Reusable Intent for each tab

        // Create an Intent to launch an Activity for the tab (to be reused)
        intent = new Intent().setClass(this, MainActivity.class);

        // Initialize a TabSpec for each tab and add it to the TabHost
        spec = tabHost.newTabSpec("main").setIndicator("Main").setContent(intent);
        tabHost.addTab(spec);

        // Do the same for the other tab
        intent = new Intent().setClass(this, AboutActivity.class);
        spec = tabHost.newTabSpec("about").setIndicator("About").setContent(intent);
        tabHost.addTab(spec);

        tabHost.setCurrentTab(0);
    }
}