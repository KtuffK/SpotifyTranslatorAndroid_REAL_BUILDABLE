package com.krisreply.spotifytranslator;

import android.app.Notification;
import android.content.SharedPreferences;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

public class SpotifyNotificationListener extends NotificationListenerService {
    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null || sbn.getPackageName() == null) return;
        if (!sbn.getPackageName().equals("com.spotify.music")) return;

        Notification n = sbn.getNotification();
        if (n == null || n.extras == null) return;

        CharSequence title = n.extras.getCharSequence(Notification.EXTRA_TITLE);
        CharSequence text = n.extras.getCharSequence(Notification.EXTRA_TEXT);

        if (title == null || text == null) return;

        SharedPreferences p = getSharedPreferences("spotify_translator_prefs", MODE_PRIVATE);
        p.edit()
                .putString("notif_song", title.toString())
                .putString("notif_artist", text.toString())
                .apply();
    }
}
