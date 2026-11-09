package de.art.jarvis;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/** Schnellzugriff ohne dauerhaft laufenden Hilfsprozess. Aufnahme startet erst in der App. */
public final class JarvisTileService extends TileService {
    @Override public void onStartListening() {
        super.onStartListening();
        Tile tile = getQsTile();
        if (tile == null) return;
        tile.setState(Tile.STATE_INACTIVE);
        tile.setSubtitle("Öffnen");
        tile.updateTile();
    }

    @SuppressWarnings("deprecation")
    @Override public void onClick() {
        super.onClick();
        Intent open = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 2003, open,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        } else {
            startActivityAndCollapse(open);
        }
    }
}
