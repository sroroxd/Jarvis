package de.art.jarvis;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/** Unsichtbarer Schnellstart: lässt Jarvis sofort zuhören (ideal für die Seitentaste). */
public class ListenActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (JarvisService.isRunning()) {
            startService(new Intent(this, JarvisService.class).setAction(JarvisService.ACTION_LISTEN));
        } else {
            startActivity(new Intent(this, MainActivity.class));
        }
        finish();
        overridePendingTransition(0, 0);
    }
}
