package com.krisreply.spotifytranslator;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.provider.Settings;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.OutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final String CLIENT_ID = "6752064dce5f4943bdb9343ffd09ff90";
    private static final String REDIRECT_URI = "spotifytranslator://callback";
    private static final String PREFS = "spotify_translator_prefs";

    private EditText artistInput;
    private EditText titleInput;
    private Spinner languageSpinner;
    private TextView statusText;
    private TextView outputText;
    private TextView loginText;

    private String accessToken = "";
    private String codeVerifier = "";
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    private static final String[][] LANGS = new String[][]{
            {"Thai", "th"},
            {"English", "en"},
            {"Spanish", "es"},
            {"French", "fr"},
            {"German", "de"},
            {"Italian", "it"},
            {"Portuguese", "pt"},
            {"Japanese", "ja"},
            {"Korean", "ko"},
            {"Arabic", "ar"}
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        accessToken = prefs().getString("access_token", "");
        codeVerifier = prefs().getString("code_verifier", "");
        buildUi();
        handleIncomingIntent(getIntent());
        updateLoginState();
        requestNotificationAccessOnce();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIncomingIntent(intent);
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(34, 44, 34, 34);
        root.setBackgroundColor(Color.rgb(18, 18, 18));
        scroll.addView(root);

        TextView title = label("Spotify Translator");
        title.setTextColor(Color.rgb(30, 185, 84));
        title.setTextSize(28);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        title.setPadding(0, 0, 0, 22);
        root.addView(title);

        loginText = label("Spotify: checking...");
        loginText.setTextColor(Color.LTGRAY);
        root.addView(loginText);

        Button loginButton = button("Login with Spotify");
        loginButton.setOnClickListener(v -> startSpotifyLogin());
        root.addView(loginButton);

        Button notifButton = button("Use Spotify notification fallback");
        notifButton.setOnClickListener(v -> useSpotifyNotificationFallback());
        root.addView(notifButton);

        artistInput = input("Artist, e.g. Oasis");
        titleInput = input("Song title, e.g. Wonderwall");
        root.addView(artistInput);
        root.addView(titleInput);

        languageSpinner = new Spinner(this);
        String[] names = new String[LANGS.length];
        for (int i = 0; i < LANGS.length; i++) names[i] = LANGS[i][0];
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, names);
        languageSpinner.setAdapter(adapter);
        languageSpinner.setSelection(0);
        root.addView(languageSpinner);

        Button manualButton = button("Manual fetch lyrics + translate");
        manualButton.setOnClickListener(v -> fetchAndTranslateFromFields());
        root.addView(manualButton);

        Button spotifyButton = button("Open Spotify");
        spotifyButton.setOnClickListener(v -> openSpotify());
        root.addView(spotifyButton);

        Button clearButton = button("Clear Spotify login");
        clearButton.setOnClickListener(v -> {
            accessToken = "";
            codeVerifier = "";
            prefs().edit().clear().apply();
            updateLoginState();
            statusText.setText("Spotify login cleared.");
        });
        root.addView(clearButton);

        statusText = label("Ready.");
        statusText.setTextColor(Color.rgb(30, 185, 84));
        statusText.setPadding(0, 22, 0, 16);
        root.addView(statusText);

        outputText = label("");
        outputText.setTextIsSelectable(true);
        outputText.setTextColor(Color.WHITE);
        outputText.setTextSize(15);
        outputText.setPadding(0, 12, 0, 80);
        root.addView(outputText);

        setContentView(scroll);
    }

    private Button button(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        return b;
    }

    private TextView label(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(16);
        t.setPadding(0, 10, 0, 10);
        return t;
    }

    private EditText input(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setSingleLine(true);
        e.setInputType(InputType.TYPE_CLASS_TEXT);
        e.setTextColor(Color.WHITE);
        e.setHintTextColor(Color.GRAY);
        e.setPadding(18, 12, 18, 12);
        e.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        return e;
    }

    private void requestNotificationAccessOnce() {
        boolean asked = prefs().getBoolean("notification_access_asked", false);
        if (!asked) {
            prefs().edit().putBoolean("notification_access_asked", true).apply();
            statusText.setText("Please enable notification access for Spotify Translator.");
            try {
                startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
            } catch (Exception ignored) {}
        }
    }

    private SharedPreferences prefs() {
        return getSharedPreferences(PREFS, MODE_PRIVATE);
    }

    private void updateLoginState() {
        loginText.setText(accessToken == null || accessToken.isEmpty()
                ? "Spotify: not logged in"
                : "Spotify: logged in");
    }

    private void startSpotifyLogin() {
        try {
            codeVerifier = randomCodeVerifier();
            String challenge = codeChallenge(codeVerifier);
            prefs().edit().putString("code_verifier", codeVerifier).apply();

            String authUrl = "https://accounts.spotify.com/authorize"
                    + "?client_id=" + enc(CLIENT_ID)
                    + "&response_type=code"
                    + "&redirect_uri=" + enc(REDIRECT_URI)
                    + "&code_challenge_method=S256"
                    + "&code_challenge=" + enc(challenge)
                    + "&scope=" + enc("user-read-currently-playing user-read-playback-state");

            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(authUrl)));
            statusText.setText("Opening Spotify login...");
        } catch (Exception e) {
            statusText.setText("Login start failed.");
            outputText.setText(e.getMessage());
        }
    }

    private void handleIncomingIntent(Intent intent) {
        if (intent == null) return;

        if (Intent.ACTION_SEND.equals(intent.getAction())) {
            String shared = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (shared != null) handleSpotifySharedText(shared);
            return;
        }

        Uri data = intent.getData();
        if (data == null) return;

        String url = data.toString();
        if (url.contains("open.spotify.com/track/")) {
            handleSpotifySharedText(url);
            return;
        }

        if (!"spotifytranslator".equals(data.getScheme())) return;

        String error = data.getQueryParameter("error");
        if (error != null) {
            statusText.setText("Spotify login failed.");
            outputText.setText(error);
            return;
        }

        String code = data.getQueryParameter("code");
        if (code != null && !code.isEmpty()) exchangeCodeForToken(code);
    }

    private void handleSpotifySharedText(String shared) {
        try {
            String id = extractSpotifyTrackId(shared);
            if (id.isEmpty()) {
                statusText.setText("Spotify link not recognised.");
                outputText.setText(shared);
                return;
            }
            getSpotifyTrackById(id);
        } catch (Exception e) {
            statusText.setText("Spotify share failed.");
            outputText.setText(e.getMessage());
        }
    }

    private String extractSpotifyTrackId(String text) {
        if (text == null) return "";
        int i = text.indexOf("open.spotify.com/track/");
        if (i < 0) return "";
        String part = text.substring(i + "open.spotify.com/track/".length());
        int q = part.indexOf("?");
        if (q >= 0) part = part.substring(0, q);
        int space = part.indexOf(" ");
        if (space >= 0) part = part.substring(0, space);
        return part.trim();
    }

    private void getSpotifyTrackById(String id) {
        if (accessToken == null || accessToken.isEmpty()) {
            statusText.setText("Login with Spotify first, then share the track again.");
            return;
        }

        statusText.setText("Reading Spotify shared track...");
        executor.execute(() -> {
            try {
                String json = spotifyGet("https://api.spotify.com/v1/tracks/" + enc(id));
                JSONObject item = new JSONObject(json);

                String song = item.optString("name", "");
                JSONArray artists = item.optJSONArray("artists");
                if (song.isEmpty() || artists == null || artists.length() == 0) throw new Exception("Could not parse shared track.");

                String artist = artists.getJSONObject(0).optString("name", "");
                if (artist.isEmpty()) throw new Exception("Could not parse shared artist.");

                main.post(() -> {
                    artistInput.setText(artist);
                    titleInput.setText(song);
                    statusText.setText("Shared track found: " + artist + " - " + song);
                });

                fetchAndTranslate(artist, song);
            } catch (Exception e) {
                main.post(() -> {
                    statusText.setText("Spotify shared track failed.");
                    outputText.setText(e.getMessage());
                });
            }
        });
    }

    private void useSpotifyNotificationFallback() {
        String artist = prefs().getString("notif_artist", "").trim();
        String song = prefs().getString("notif_song", "").trim();

        if (artist.isEmpty() || song.isEmpty()) {
            statusText.setText("No Spotify notification found.");
            outputText.setText("Tap Enable notification access, allow Spotify Translator, then play a song in Spotify.");
            return;
        }

        artistInput.setText(artist);
        titleInput.setText(song);
        statusText.setText("Notification track found: " + artist + " - " + song);
        fetchAndTranslate(artist, song);
    }

    private void exchangeCodeForToken(String code) {
        statusText.setText("Completing Spotify login...");
        executor.execute(() -> {
            try {
                String verifier = prefs().getString("code_verifier", codeVerifier);
                if (verifier == null || verifier.isEmpty()) throw new Exception("Missing PKCE code verifier.");

                String body = "client_id=" + enc(CLIENT_ID)
                        + "&grant_type=authorization_code"
                        + "&code=" + enc(code)
                        + "&redirect_uri=" + enc(REDIRECT_URI)
                        + "&code_verifier=" + enc(verifier);

                JSONObject obj = new JSONObject(httpPost("https://accounts.spotify.com/api/token", body));
                String token = obj.optString("access_token", "");
                if (token.isEmpty()) throw new Exception("Spotify returned no access token.");

                accessToken = token;
                prefs().edit().putString("access_token", accessToken).apply();

                main.post(() -> {
                    updateLoginState();
                    statusText.setText("Spotify login complete.");
                    outputText.setText("Now use Spotify share link, manual search, or notification fallback.");
                });
            } catch (Exception e) {
                main.post(() -> {
                    statusText.setText("Spotify token exchange failed.");
                    outputText.setText(e.getMessage());
                });
            }
        });
    }

    private void getCurrentPlayingAndTranslate() {
        if (accessToken == null || accessToken.isEmpty()) {
            statusText.setText("Login with Spotify first.");
            return;
        }

        statusText.setText("Reading current Spotify track...");
        outputText.setText("");

        executor.execute(() -> {
            try {
                String json = spotifyGet("https://api.spotify.com/v1/me/player/currently-playing");
                if (json == null || json.trim().isEmpty()) throw new Exception("No current track. Play Spotify first.");

                JSONObject obj = new JSONObject(json);
                JSONObject item = obj.optJSONObject("item");
                if (item == null) throw new Exception("No track item found.");

                String song = item.optString("name", "");
                JSONArray artists = item.optJSONArray("artists");
                if (song.isEmpty() || artists == null || artists.length() == 0) throw new Exception("Could not parse track.");

                String artist = artists.getJSONObject(0).optString("name", "");
                if (artist.isEmpty()) throw new Exception("Could not parse artist.");

                main.post(() -> {
                    artistInput.setText(artist);
                    titleInput.setText(song);
                    statusText.setText("Track found: " + artist + " - " + song);
                });

                fetchAndTranslate(artist, song);
            } catch (Exception e) {
                main.post(() -> {
                    statusText.setText("Spotify track read failed.");
                    outputText.setText(e.getMessage());
                });
            }
        });
    }

    private void fetchAndTranslateFromFields() {
        String artist = artistInput.getText().toString().trim();
        String song = titleInput.getText().toString().trim();
        if (artist.isEmpty() || song.isEmpty()) {
            statusText.setText("Enter artist and song title first.");
            return;
        }
        fetchAndTranslate(artist, song);
    }

    private void fetchAndTranslate(String artist, String song) {
        int idx = languageSpinner.getSelectedItemPosition();
        String target = LANGS[idx][1];

        main.post(() -> {
            statusText.setText("Fetching lyrics...");
            outputText.setText("");
        });

        executor.execute(() -> {
            try {
                String lyrics = fetchLyrics(artist, song);
                main.post(() -> statusText.setText("Translating..."));
                String translated = translateLongText(lyrics, target);

                String result = "TRACK\n" + artist + " - " + song
                        + "\n\nORIGINAL LYRICS\n\n" + lyrics
                        + "\n\n\nTRANSLATED (" + LANGS[idx][0] + ")\n\n" + translated;

                main.post(() -> {
                    statusText.setText("Done.");
                    outputText.setText(result);
                });
            } catch (Exception e) {
                main.post(() -> {
                    statusText.setText("Lyrics/translation failed.");
                    outputText.setText("Error: " + e.getMessage());
                });
            }
        });
    }

    private String fetchLyrics(String artist, String song) throws Exception {
        String[] tries = buildTitleTries(song);

        for (String title : tries) {
            try {
                String lyrics = fetchLrcLibSearch(artist, title);
                if (!lyrics.trim().isEmpty()) return lyrics;
            } catch (Exception ignored) {}

            try {
                String lyrics = fetchLyricsOvh(artist, title);
                if (!lyrics.trim().isEmpty()) return lyrics;
            } catch (Exception ignored) {}
        }

        throw new Exception("No free lyrics source found. Paste lyrics manually below.");
    }

    private String fetchLrcLibSearch(String artist, String song) throws Exception {
        String url = "https://lrclib.net/api/search?artist_name=" + enc(artist) + "&track_name=" + enc(song);
        JSONArray arr = new JSONArray(httpGet(url));

        for (int i = 0; i < arr.length(); i++) {
            JSONObject obj = arr.getJSONObject(i);

            String plain = obj.optString("plainLyrics", "").trim();
            if (!plain.isEmpty() && !"null".equalsIgnoreCase(plain)) return plain;

            String synced = obj.optString("syncedLyrics", "").trim();
            if (!synced.isEmpty() && !"null".equalsIgnoreCase(synced)) {
                return synced
                        .replaceAll("\\[[0-9]{1,2}:[0-9]{2}(\\.[0-9]{1,3})?\\]", "")
                        .replaceAll("\\[[a-zA-Z]+:.*?\\]", "")
                        .trim();
            }
        }

        throw new Exception("No LRCLIB result.");
    }

    private String fetchLyricsOvh(String artist, String song) throws Exception {
        JSONObject obj = new JSONObject(httpGet("https://api.lyrics.ovh/v1/" + enc(artist) + "/" + enc(song)));
        String lyrics = obj.optString("lyrics", "").trim();
        if (lyrics.isEmpty()) throw new Exception("No lyrics returned.");
        return lyrics;
    }

    private String[] buildTitleTries(String song) {
        String cleaned = song
                .replaceAll("\\(.*?\\)", "")
                .replaceAll("\\[.*?\\]", "")
                .replace(" - Remastered", "")
                .replace(" - Explicit", "")
                .trim();

        if (song.equals("กรุงเทพมหานคร") || cleaned.equals("กรุงเทพมหานคร")) {
            return new String[]{
                    song,
                    cleaned,
                    "Bangkok",
                    "Krung Thep Maha Nakhon",
                    "Krung Thep",
                    "Bangkok City"
            };
        }

        if (!cleaned.equals(song)) return new String[]{song, cleaned};

        return new String[]{song};
    }

    private String translateLongText(String text, String targetLang) throws Exception {
        String clean = text == null ? "" : text.trim();
        if (clean.isEmpty()) throw new Exception("No lyrics to translate.");

        String[] lines = clean.split("\\n");
        StringBuilder chunk = new StringBuilder();
        StringBuilder out = new StringBuilder();

        for (String line : lines) {
            if (chunk.length() + line.length() + 1 > 450) {
                if (chunk.length() > 0) {
                    out.append(translateText(chunk.toString(), targetLang)).append("\n\n");
                    chunk.setLength(0);
                }
            }
            chunk.append(line).append("\n");
        }

        if (chunk.length() > 0) {
            out.append(translateText(chunk.toString(), targetLang));
        }

        return out.toString().trim();
    }

    private String translateText(String text, String targetLang) throws Exception {
        JSONObject obj = new JSONObject(httpGet("https://api.mymemory.translated.net/get?q=" + enc(text)
                + "&langpair=en|" + enc(targetLang)));
        JSONObject data = obj.optJSONObject("responseData");
        if (data == null) throw new Exception("No translation response.");
        String translated = data.optString("translatedText", "").trim();
        if (translated.isEmpty()) throw new Exception("No translated text returned.");
        return translated;
    }

    private String spotifyGet(String urlText) throws Exception {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(urlText).openConnection();
            conn.setConnectTimeout(20000);
            conn.setReadTimeout(30000);
            conn.setRequestProperty("Authorization", "Bearer " + accessToken);
            conn.setRequestProperty("User-Agent", "SpotifyTranslatorAndroid/0.4");
            int code = conn.getResponseCode();
            if (code == 204) return "";
            String body = readAll(code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream());
            if (code == 401) {
                accessToken = "";
                prefs().edit().remove("access_token").apply();
                throw new Exception("Spotify authorization expired. Log in again.");
            }
            if (code < 200 || code >= 300) throw new Exception("Spotify HTTP " + code + ": " + body);
            return body;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private String httpGet(String urlText) throws Exception {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(urlText).openConnection();
            conn.setConnectTimeout(20000);
            conn.setReadTimeout(30000);
            conn.setRequestProperty("User-Agent", "SpotifyTranslatorAndroid/0.4");
            int code = conn.getResponseCode();
            String body = readAll(code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream());
            if (code < 200 || code >= 300) throw new Exception("HTTP " + code + ": " + body);
            return body;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private String httpPost(String urlText, String body) throws Exception {
        HttpURLConnection conn = null;
        try {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            conn = (HttpURLConnection) new URL(urlText).openConnection();
            conn.setConnectTimeout(20000);
            conn.setReadTimeout(30000);
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            conn.setRequestProperty("Content-Length", String.valueOf(bytes.length));
            conn.setRequestProperty("User-Agent", "SpotifyTranslatorAndroid/0.4");
            OutputStream os = conn.getOutputStream();
            os.write(bytes);
            os.flush();
            os.close();

            int code = conn.getResponseCode();
            String response = readAll(code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream());
            if (code < 200 || code >= 300) throw new Exception("HTTP " + code + ": " + response);
            return response;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private String readAll(InputStream stream) throws Exception {
        if (stream == null) return "";
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) sb.append(line);
        return sb.toString();
    }

    private String enc(String s) throws Exception {
        return URLEncoder.encode(s, "UTF-8");
    }

    private String randomCodeVerifier() {
        byte[] bytes = new byte[64];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String codeChallenge(String verifier) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(verifier.getBytes(StandardCharsets.US_ASCII));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
    }

    private void openSpotify() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("spotify:")));
        } catch (Exception e) {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://open.spotify.com/")));
        }
    }
}
