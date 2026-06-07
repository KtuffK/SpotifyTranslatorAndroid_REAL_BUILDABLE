package com.krisreply.spotifytranslator;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.content.Intent;
import android.net.Uri;

public class MainActivity extends Activity {
    private TextView output;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        int green = Color.rgb(30, 185, 84);

        ScrollView scroll = new ScrollView(this);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(36, 48, 36, 36);
        scroll.addView(layout);

        TextView title = new TextView(this);
        title.setText("Spotify Translator");
        title.setTextSize(28);
        title.setTextColor(green);
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        title.setPadding(0, 0, 0, 28);
        layout.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        TextView info = new TextView(this);
        info.setText(
                "Buildable Android shell for a Spotify lyric translator.\n\n" +
                "Status: APK build test app.\n\n" +
                "Next real features needed:\n" +
                "1. Spotify OAuth PKCE login\n" +
                "2. Current playing track lookup\n" +
                "3. Lyrics backend/proxy\n" +
                "4. Translation engine\n\n" +
                "The original GitHub project is Python/Tkinter, so this Android app cannot safely use the same sp_dc cookie method."
        );
        info.setTextSize(16);
        info.setPadding(0, 0, 0, 24);
        layout.addView(info);

        Button openSpotify = new Button(this);
        openSpotify.setText("Open Spotify");
        openSpotify.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("spotify:"));
            try {
                startActivity(intent);
            } catch (Exception e) {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://open.spotify.com/")));
            }
        });
        layout.addView(openSpotify);

        output = new TextView(this);
        output.setText("\nReady. APK build works if GitHub Actions succeeds.");
        output.setTextSize(15);
        output.setPadding(0, 24, 0, 0);
        layout.addView(output);

        setContentView(scroll);
    }
}
