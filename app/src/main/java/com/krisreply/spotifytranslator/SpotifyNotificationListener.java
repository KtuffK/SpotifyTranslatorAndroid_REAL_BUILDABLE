package com.krisreply.spotifytranslator;

import android.app.Notification;
import android.content.SharedPreferences;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

public class SpotifyNotificationListener extends NotificationListenerService {
    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        scanActiveNotifications();
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        saveIfSpotify(sbn);
    }

    private void scanActiveNotifications() {
        try {
            StatusBarNotification[] active = getActiveNotifications();
            if (active == null) return;

            for (StatusBarNotification sbn : active) {
                saveIfSpotify(sbn);
            }
        } catch (Exception ignored) {}
    }

    private void saveIfSpotify(StatusBarNotification sbn) {
        if (sbn == null || sbn.getPackageName() == null) return;
        if (!"com.spotify.music".equals(sbn.getPackageName())) return;

        Notification n = sbn.getNotification();
        if (n == null || n.extras == null) return;

        CharSequence title = n.extras.getCharSequence(Notification.EXTRA_TITLE);
        CharSequence text = n.extras.getCharSequence(Notification.EXTRA_TEXT);
        CharSequence subText = n.extras.getCharSequence(Notification.EXTRA_SUB_TEXT);

        String song = title == null ? "" : title.toString().trim();
        String artist = text == null ? "" : text.toString().trim();

        if (artist.isEmpty() && subText != null) {
            artist = subText.toString().trim();
        }

        if (song.isEmpty() || artist.isEmpty()) return;

        if (song.equalsIgnoreCase("Spotify")) return;
        if (artist.equalsIgnoreCase("Spotify")) return;

        SharedPreferences prefs = getSharedPreferences("spotify_translator_prefs", MODE_PRIVATE);
        prefs.edit()
                .putString("notif_song", song)
                .putString("notif_artist", artist)
                .apply();
    }
}
