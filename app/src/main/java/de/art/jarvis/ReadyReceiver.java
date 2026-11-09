package de.art.jarvis;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

/** Bereitschaft nach Neustart/Update: kein Dienst, kein Mikrofon, kein Netzwerk. */
public final class ReadyReceiver extends BroadcastReceiver {
    private static final String CHANNEL = "jarvis_ready";
    private static final int ID = 2002;

    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) return;
        if (!new Prefs(context).readyAfterRestart() || JarvisService.isRunning()) return;
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) return;
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (!nm.areNotificationsEnabled()) return;
        nm.createNotificationChannel(new NotificationChannel(CHANNEL,
                "Bereitschaft nach Neustart", NotificationManager.IMPORTANCE_LOW));
        Intent open = new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent tap = PendingIntent.getActivity(context, ID, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        nm.notify(ID, new Notification.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_jarvis)
                .setContentTitle(context.getString(R.string.app_name) + " ist bereit")
                .setContentText("Antippen und starten. Das Mikrofon ist noch aus.")
                .setContentIntent(tap).setAutoCancel(true).setOnlyAlertOnce(true)
                .setVisibility(Notification.VISIBILITY_PUBLIC).build());
    }

    static void cancel(Context context) {
        context.getSystemService(NotificationManager.class).cancel(ID);
    }
}
