package com.krisreply.spotifytranslator;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
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
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    private String currentSyncedLyrics = "";
    private final Handler lyricHandler = new Handler(Looper.getMainLooper());

    private String accessToken = "";
    private String refreshToken = "";
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
        refreshToken = prefs().getString("refresh_token", "");
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

    @Override
    protected void onDestroy() {
        lyricHandler.removeCallbacksAndMessages(null);
        main.removeCallbacksAndMessages(null);
        executor.shutdownNow();
        super.onDestroy();
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

        Button loginButton = button("Connect");
        loginButton.setOnClickListener(v -> startSpotifyLogin());
        root.addView(loginButton);

        Button notifButton = button("Scan Playing Song");
        notifButton.setOnClickListener(v -> useSpotifyNotificationFallback());
        root.addView(notifButton);

        artistInput = input("Artist, e.g. Oasis");
        titleInput = input("Song title, e.g. Wonderwall");
        root.addView(artistInput);
        root.addView(titleInput);

        languageSpinner = new Spinner(this);
        String[] names = new String[LANGS.length];
        for (int i = 0; i < LANGS.length; i++) names[i] = LANGS[i][0];
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, names);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        languageSpinner.setAdapter(adapter);
        languageSpinner.setSelection(0);
        languageSpinner.setBackgroundColor(Color.rgb(30, 30, 30));
        languageSpinner.setPadding(18, 10, 18, 10);
        root.addView(languageSpinner);

        Button manualButton = button("Lyrics");
        manualButton.setOnClickListener(v -> fetchAndTranslateFromFields());
        root.addView(manualButton);

        Button spotifyButton = button("Open Spotify");
        spotifyButton.setOnClickListener(v -> openSpotify());
        root.addView(spotifyButton);

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
    b.setTextColor(Color.WHITE);
    b.setTextSize(16);

    int screenWidth = getResources().getDisplayMetrics().widthPixels;

    LinearLayout.LayoutParams params =
            new LinearLayout.LayoutParams(
                    (int)(screenWidth * 0.50f),
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );

    params.gravity = Gravity.CENTER_HORIZONTAL;
    params.topMargin = 12;
    params.bottomMargin = 12;

    b.setLayoutParams(params);

    GradientDrawable bg = new GradientDrawable();
    bg.setColor(Color.rgb(30, 185, 84));
    bg.setCornerRadius(9999f);

    b.setBackground(bg);

    int padH = (int)(16 * getResources().getDisplayMetrics().density);
    int padV = (int)(10 * getResources().getDisplayMetrics().density);

    b.setPadding(padH, padV, padH, padV);

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
        e.setTextSize(18);
        e.setBackgroundColor(Color.rgb(30, 30, 30));
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
            accessToken = "";
            refreshToken = "";
            codeVerifier = randomCodeVerifier();
            String challenge = codeChallenge(codeVerifier);

            prefs().edit()
                    .remove("access_token")
                    .remove("refresh_token")
                    .putString("code_verifier", codeVerifier)
                    .apply();

            updateLoginState();

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
            outputText.setText("Play a song in Spotify and make sure its notification is visible. If this still fails, Android notification access is not enabled for Spotify Translator.");
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
                String refresh = obj.optString("refresh_token", "");
                if (token.isEmpty()) throw new Exception("Spotify returned no access token.");

                accessToken = token;
                if (!refresh.isEmpty()) refreshToken = refresh;
                codeVerifier = "";

                SharedPreferences.Editor editor = prefs().edit().putString("access_token", accessToken);
                if (!refreshToken.isEmpty()) editor.putString("refresh_token", refreshToken);
                editor.remove("code_verifier");
                editor.apply();

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

    private void fetchAndTranslateFromFields() {
        String artist = artistInput.getText().toString().trim();
        String song = titleInput.getText().toString().trim();

        if (artist.isEmpty() || song.isEmpty()) {
            String notifArtist = prefs().getString("notif_artist", "").trim();
            String notifSong = prefs().getString("notif_song", "").trim();

            if (!notifArtist.isEmpty() && !notifSong.isEmpty()) {
                artistInput.setText(notifArtist);
                titleInput.setText(notifSong);
                statusText.setText("Using Spotify notification: " + notifArtist + " - " + notifSong);
                fetchAndTranslate(notifArtist, notifSong);
                return;
            }

            statusText.setText("No song detected.");
            outputText.setText("Play a song in Spotify, make sure the Spotify notification is visible, then tap Manual fetch again. Or type artist and song manually.");
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

                boolean sourceLooksThai = containsThai(lyrics);
                String actualTarget = target;
                String actualTargetName = LANGS[idx][0];

                if (sourceLooksThai && "th".equals(target)) {
                    actualTarget = "en";
                    actualTargetName = "English";
                }

                String sourceLang = sourceLooksThai ? "th" : "auto";
                final String displayTargetName = actualTargetName;

                main.post(() -> statusText.setText("Translating " + sourceLang.toUpperCase() + " → " + displayTargetName + "..."));
                String translatedTrack = translateText(song, sourceLooksThai ? "th" : "auto", "en");
                SpannableString result = buildLineByLineLyricsDisplay(artist, song, translatedTrack, lyrics, sourceLang, actualTarget, actualTargetName);

                main.post(() -> {
                    statusText.setText("Done.");
                    outputText.setText(result, TextView.BufferType.SPANNABLE);
                });
            } catch (Exception e) {
                main.post(() -> {
                    statusText.setText("Lyrics/translation failed.");
                    outputText.setText("Error: " + e.getMessage());
                });
            }
        });
    }

    private SpannableString buildLineByLineLyricsDisplay(String artist, String song, String translatedTrack, String lyrics, String sourceLang, String targetLang, String targetName) throws Exception {
        String cleanLyrics = normalizeNewlines(lyrics);
        if (cleanLyrics.isEmpty()) throw new Exception("No lyrics to display.");

        ArrayList<LyricSection> sections = parseLyricSections(cleanLyrics, currentSyncedLyrics);

        StringBuilder markedLyrics = new StringBuilder();
        HashMap<String, Integer> lineIds = new HashMap<>();
        int id = 0;

        for (int sIdx = 0; sIdx < sections.size(); sIdx++) {
            LyricSection section = sections.get(sIdx);
            for (int lIdx = 0; lIdx < section.lines.size(); lIdx++) {
                String line = section.lines.get(lIdx).trim();
                if (line.isEmpty()) continue;

                lineIds.put(sIdx + ":" + lIdx, id);
                markedLyrics.append("[")
                        .append(String.format("%03d", id))
                        .append("] ")
                        .append(line)
                        .append("\n");
                id++;
            }
        }

        String translatedMarked = normalizeNewlines(translateLongText(markedLyrics.toString(), sourceLang, targetLang));
        Map<Integer, String> translatedMap = parseMarkedTranslations(translatedMarked);

        StringBuilder out = new StringBuilder();
        ArrayList<int[]> headerRanges = new ArrayList<>();
        ArrayList<int[]> lyricRanges = new ArrayList<>();
        ArrayList<int[]> translationRanges = new ArrayList<>();

        out.append("TRACK\n")
                .append(artist).append(" - ").append(song).append("\n")
                .append(artist).append(" - ").append(translatedTrack).append("\n\n")
                .append("LYRICS + TRANSLATION (").append(sourceLang.toUpperCase()).append(" to ").append(targetName).append(")\n");

        for (int sIdx = 0; sIdx < sections.size(); sIdx++) {
            LyricSection section = sections.get(sIdx);
            if (section.lines.isEmpty()) continue;

            int headerStart = out.length();
            out.append("\n\n")
                    .append(section.label)
                    .append("\n")
                    .append("────────────────────")
                    .append("\n\n");
            headerRanges.add(new int[]{headerStart, out.length()});

            for (int lIdx = 0; lIdx < section.lines.size(); lIdx++) {
                String lyricLine = section.lines.get(lIdx).trim();
                if (lyricLine.isEmpty()) continue;

                int lyricStart = out.length();
                out.append(lyricLine).append("\n");
                lyricRanges.add(new int[]{lyricStart, lyricStart + lyricLine.length()});

                Integer lineId = lineIds.get(sIdx + ":" + lIdx);
                String translatedLine = lineId == null ? "" : translatedMap.getOrDefault(lineId, "");
                translatedLine = translatedLine.trim();

                int translationStart = out.length();
                out.append(translatedLine).append("\n\n");
                translationRanges.add(new int[]{translationStart, translationStart + translatedLine.length()});
            }
        }

        SpannableString span = new SpannableString(out.toString().trim());

        for (int[] range : headerRanges) {
            if (range[1] > range[0]) span.setSpan(new ForegroundColorSpan(Color.rgb(255, 215, 0)), range[0], range[1], Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        for (int[] range : lyricRanges) {
            if (range[1] > range[0]) span.setSpan(new ForegroundColorSpan(Color.rgb(30, 144, 255)), range[0], range[1], Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        for (int[] range : translationRanges) {
            if (range[1] > range[0]) span.setSpan(new ForegroundColorSpan(Color.rgb(50, 255, 90)), range[0], range[1], Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        return span;
    }

    private static class LyricSection {
        String label;
        ArrayList<String> lines = new ArrayList<>();

        LyricSection(String label) {
            this.label = label;
        }
    }

    private ArrayList<LyricSection> parseLyricSections(String lyrics, String syncedLyrics) {
        ArrayList<LyricSection> sections = parseTimedLyricSections(syncedLyrics);
        if (sections.size() <= 1) sections = parseTextLyricSections(lyrics);
        assignMissingSectionLabels(sections);
        if (sections.size() == 1) sections.get(0).label = "LYRICS";
        return sections;
    }

    private ArrayList<LyricSection> parseTimedLyricSections(String syncedLyrics) {
        ArrayList<LyricSection> sections = new ArrayList<>();
        if (syncedLyrics == null || syncedLyrics.trim().isEmpty()) return sections;

        LyricSection current = new LyricSection("");
        int previousMs = -1;

        for (String raw : syncedLyrics.split("\\n")) {
            String line = raw == null ? "" : raw.trim();
            if (!line.startsWith("[")) continue;

            int close = line.indexOf("]");
            if (close <= 0) continue;

            int ms = parseLrcTimeMs(line.substring(1, close));
            String text = line.substring(close + 1).trim();

            if (text.isEmpty()) {
                if (!current.lines.isEmpty()) {
                    sections.add(current);
                    current = new LyricSection("");
                }
                previousMs = ms;
                continue;
            }

            if (previousMs >= 0 && ms - previousMs >= 4500 && !current.lines.isEmpty()) {
                sections.add(current);
                current = new LyricSection("");
            }

            current.lines.add(stripSectionTag(text));
            previousMs = ms;
        }

        if (!current.lines.isEmpty()) sections.add(current);
        return sections;
    }

    private ArrayList<LyricSection> parseTextLyricSections(String lyrics) {
        ArrayList<LyricSection> sections = new ArrayList<>();
        LyricSection current = null;

        for (String raw : normalizeNewlines(lyrics).split("\\n", -1)) {
            String line = raw == null ? "" : raw.trim();

            if (line.isEmpty()) {
                if (current != null && !current.lines.isEmpty()) {
                    sections.add(current);
                    current = null;
                }
                continue;
            }

            if (isSectionTag(line)) {
                if (current != null && !current.lines.isEmpty()) sections.add(current);
                current = new LyricSection(cleanSectionLabel(line));
                continue;
            }

            if (current == null) current = new LyricSection("");
            current.lines.add(stripSectionTag(line));
        }

        if (current != null && !current.lines.isEmpty()) sections.add(current);
        return sections;
    }

    private void assignMissingSectionLabels(ArrayList<LyricSection> sections) {
        HashMap<String, Integer> repeatCounts = new HashMap<>();

        for (LyricSection section : sections) {
            String key = normalizeSectionKey(section);
            if (!key.isEmpty()) repeatCounts.put(key, repeatCounts.getOrDefault(key, 0) + 1);
        }

        HashMap<String, String> repeatedLabels = new HashMap<>();
        int verseNo = 1;
        int chorusNo = 1;
        int bridgeNo = 1;
        int sectionNo = 1;

        for (LyricSection section : sections) {
            if (section.label != null && !section.label.trim().isEmpty()) continue;

            String key = normalizeSectionKey(section);

            if (!key.isEmpty() && repeatCounts.getOrDefault(key, 0) > 1) {
                if (!repeatedLabels.containsKey(key)) repeatedLabels.put(key, "CHORUS " + chorusNo++);
                section.label = repeatedLabels.get(key);
            } else if (section.lines.size() <= 2 && sections.size() > 2) {
                section.label = bridgeNo == 1 ? "BRIDGE" : "BRIDGE " + bridgeNo;
                bridgeNo++;
            } else {
                section.label = "VERSE " + verseNo++;
            }

            if (section.label == null || section.label.trim().isEmpty()) {
                section.label = "SECTION " + sectionNo++;
            }
        }
    }

    private boolean isSectionTag(String line) {
        if (line == null) return false;
        String lower = line.trim().toLowerCase();
        return lower.matches("^\\[(verse|chorus|pre-chorus|pre chorus|bridge|intro|outro|hook|refrain|post-chorus|post chorus)[^\\]]*\\]$");
    }

    private String cleanSectionLabel(String tag) {
        String cleaned = tag == null ? "" : tag.replace("[", "").replace("]", "").trim();
        if (cleaned.isEmpty()) return "SECTION";
        return cleaned.toUpperCase();
    }

    private String normalizeSectionKey(LyricSection section) {
        if (section == null) return "";
        StringBuilder sb = new StringBuilder();

        for (String line : section.lines) {
            String cleaned = stripSectionTag(line)
                    .toLowerCase()
                    .replaceAll("[^\\p{L}\\p{N}]+", "")
                    .trim();

            if (!cleaned.isEmpty()) sb.append(cleaned).append("|");
        }

        return sb.toString();
    }

    private String stripSectionTag(String line) {
        if (line == null) return "";
        return line.replaceFirst("^\\s*\\[[^\\]]+\\]\\s*", "").trim();
    }

    private String normalizeNewlines(String text) {
        if (text == null) return "";
        return text
                .replace("\\r\\n", "\n")
                .replace("\\n", "\n")
                .replace("\\r", "\n")
                .replace("\r\n", "\n")
                .replace("\r", "\n")
                .trim();
    }

    private Map<Integer, String> parseMarkedTranslations(String text) {
        Map<Integer, String> map = new HashMap<>();
        if (text == null || text.trim().isEmpty()) return map;

        Pattern marker = Pattern.compile("\\[(\\d{3})\\]");
        Matcher matcher = marker.matcher(text);

        ArrayList<Integer> ids = new ArrayList<>();
        ArrayList<Integer> markerStarts = new ArrayList<>();
        ArrayList<Integer> contentStarts = new ArrayList<>();

        while (matcher.find()) {
            ids.add(Integer.parseInt(matcher.group(1)));
            markerStarts.add(matcher.start());
            contentStarts.add(matcher.end());
        }

        for (int i = 0; i < ids.size(); i++) {
            int start = contentStarts.get(i);
            int end = (i + 1 < markerStarts.size()) ? markerStarts.get(i + 1) : text.length();

            String value = text.substring(start, end)
                    .replaceAll("\\s+", " ")
                    .trim();

            map.put(ids.get(i), value);
        }

        return map;
    }

    private String fetchLyrics(String artist, String song) throws Exception {
        currentSyncedLyrics = "";
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

            String synced = obj.optString("syncedLyrics", "").trim();
            if (!synced.isEmpty() && !"null".equalsIgnoreCase(synced)) {
                currentSyncedLyrics = synced;
                return synced
                        .replaceAll("\\[[0-9]{1,2}:[0-9]{2}(\\.[0-9]{1,3})?\\]", "")
                        .replaceAll("\\[[a-zA-Z]+:.*?\\]", "")
                        .replaceAll("(?m)^\\s*$\\n?", "")
                        .trim();
            }

            String plain = obj.optString("plainLyrics", "").trim();
            if (!plain.isEmpty() && !"null".equalsIgnoreCase(plain)) {
                currentSyncedLyrics = "";
                return plain;
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

    private boolean containsThai(String text) {
        if (text == null || text.isEmpty()) return false;
        int thai = 0;
        int letters = 0;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isLetter(c)) letters++;
            if (c >= '\u0E00' && c <= '\u0E7F') thai++;
        }

        return thai > 5 && thai >= Math.max(3, letters / 5);
    }

    private String translateLongText(String text, String sourceLang, String targetLang) throws Exception {
        String clean = text == null ? "" : text.trim();
        if (clean.isEmpty()) throw new Exception("No lyrics to translate.");

        String[] lines = clean.split("\\n");
        StringBuilder chunk = new StringBuilder();
        StringBuilder out = new StringBuilder();

        for (String line : lines) {
            if (chunk.length() + line.length() + 1 > 450) {
                if (chunk.length() > 0) {
                    out.append(translateText(chunk.toString(), sourceLang, targetLang)).append("\n\n");
                    chunk.setLength(0);
                }
            }
            chunk.append(line).append("\n");
        }

        if (chunk.length() > 0) {
            out.append(translateText(chunk.toString(), sourceLang, targetLang));
        }

        return out.toString().trim();
    }

    private String translateText(String text, String sourceLang, String targetLang) throws Exception {
        if (sourceLang.equals(targetLang)) return text;

        if (!"auto".equals(sourceLang)) {
            try {
                JSONObject obj = new JSONObject(httpGet("https://api.mymemory.translated.net/get?q=" + enc(text)
                        + "&langpair=" + enc(sourceLang + "|" + targetLang)));
                JSONObject data = obj.optJSONObject("responseData");
                if (data != null) {
                    String translated = data.optString("translatedText", "").trim();
                    if (!translated.isEmpty()
                            && !translated.toLowerCase().contains("query length limit exceeded")
                            && !translated.toLowerCase().contains("quota")) {
                        return translated;
                    }
                }
            } catch (Exception ignored) {}
        }

        return translateTextGoogleFree(text, sourceLang, targetLang);
    }

    private String translateTextGoogleFree(String text, String sourceLang, String targetLang) throws Exception {
        String url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl="
                + enc(sourceLang) + "&tl=" + enc(targetLang) + "&dt=t&q=" + enc(text);

        JSONArray root = new JSONArray(httpGet(url));
        JSONArray sentences = root.getJSONArray(0);
        StringBuilder out = new StringBuilder();

        for (int i = 0; i < sentences.length(); i++) {
            JSONArray part = sentences.getJSONArray(i);
            if (!part.isNull(0)) out.append(part.getString(0));
        }

        String translated = out.toString().trim();
        if (translated.isEmpty()) throw new Exception("No translated text returned.");
        return translated;
    }

    private void startSyncedHighlight(String fullText, String syncedLyrics) {
        lyricHandler.removeCallbacksAndMessages(null);

        ArrayList<Integer> times = new ArrayList<>();
        ArrayList<String> lines = new ArrayList<>();
        ArrayList<Integer> starts = new ArrayList<>();

        String[] raw = syncedLyrics.split("\n");
        for (String r : raw) {
            if (!r.startsWith("[")) continue;
            int close = r.indexOf("]");
            if (close <= 0) continue;

            int ms = parseLrcTimeMs(r.substring(1, close));
            String line = r.substring(close + 1).trim();

            if (ms >= 0 && !line.isEmpty()) {
                times.add(ms);
                lines.add(line);
            }
        }

        if (times.isEmpty()) {
            outputText.setText(fullText);
            return;
        }

        int searchFrom = 0;
        for (String line : lines) {
            int pos = fullText.indexOf(line, searchFrom);
            if (pos < 0) pos = fullText.indexOf(line);
            starts.add(pos);
            if (pos >= 0) searchFrom = pos + line.length();
        }

        final long startMs = System.currentTimeMillis();

        Runnable runner = new Runnable() {
            @Override public void run() {
                int elapsed = (int)(System.currentTimeMillis() - startMs);
                int idx = 0;

                for (int i = 0; i < times.size(); i++) {
                    if (elapsed >= times.get(i)) idx = i;
                    else break;
                }

                int nextMs = idx + 1 < times.size() ? times.get(idx + 1) : times.get(idx) + 3500;
                int duration = Math.max(500, nextMs - times.get(idx));
                int lineElapsed = Math.max(0, elapsed - times.get(idx));

                renderHighlightedText(fullText, lines.get(idx), starts.get(idx), lineElapsed, duration);

                if (elapsed < times.get(times.size() - 1) + 10000) {
                    lyricHandler.postDelayed(this, 250);
                }
            }
        };

        lyricHandler.post(runner);
    }

    private int parseLrcTimeMs(String t) {
        try {
            String[] parts = t.split(":");
            if (parts.length != 2) return -1;
            int min = Integer.parseInt(parts[0]);
            double sec = Double.parseDouble(parts[1]);
            return (int)((min * 60 + sec) * 1000);
        } catch (Exception e) {
            return -1;
        }
    }

    private void renderHighlightedText(String fullText, String currentLine, int lineStart, int lineElapsed, int lineDuration) {
        if (lineStart < 0 || currentLine == null || currentLine.isEmpty()) {
            outputText.setText(fullText);
            return;
        }

        SpannableString span = new SpannableString(fullText);

        int lineEnd = Math.min(lineStart + currentLine.length(), fullText.length());
        span.setSpan(
                new ForegroundColorSpan(Color.rgb(144, 238, 144)),
                lineStart,
                lineEnd,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        );

        String[] words = currentLine.trim().split("\\s+");
        if (words.length > 0) {
            int wordIndex = Math.min(
                    words.length - 1,
                    Math.max(0, (int)((lineElapsed / (float)lineDuration) * words.length))
            );

            int scan = lineStart;
            int wordStart = -1;
            int wordEnd = -1;

            for (int i = 0; i <= wordIndex && scan < lineEnd; i++) {
                while (scan < lineEnd && Character.isWhitespace(fullText.charAt(scan))) scan++;
                int ws = scan;
                while (scan < lineEnd && !Character.isWhitespace(fullText.charAt(scan))) scan++;
                int we = scan;

                if (i == wordIndex) {
                    wordStart = ws;
                    wordEnd = we;
                    break;
                }
            }

            if (wordStart >= lineStart && wordEnd > wordStart && wordEnd <= lineEnd) {
                span.setSpan(
                        new ForegroundColorSpan(Color.rgb(0, 160, 70)),
                        wordStart,
                        wordEnd,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                );
            }
        }

        outputText.setText(span);
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
            if (code == 401 && refreshSpotifyAccessToken()) {
                conn.disconnect();
                conn = (HttpURLConnection) new URL(urlText).openConnection();
                conn.setConnectTimeout(20000);
                conn.setReadTimeout(30000);
                conn.setRequestProperty("Authorization", "Bearer " + accessToken);
                conn.setRequestProperty("User-Agent", "SpotifyTranslatorAndroid/0.4");
                code = conn.getResponseCode();
                body = readAll(code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream());
            }

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

    private boolean refreshSpotifyAccessToken() {
        try {
            if (refreshToken == null || refreshToken.isEmpty()) return false;

            String body = "client_id=" + enc(CLIENT_ID)
                    + "&grant_type=refresh_token"
                    + "&refresh_token=" + enc(refreshToken);

            JSONObject obj = new JSONObject(httpPost("https://accounts.spotify.com/api/token", body));
            String token = obj.optString("access_token", "");
            String refresh = obj.optString("refresh_token", "");

            if (token.isEmpty()) return false;

            accessToken = token;
            if (!refresh.isEmpty()) refreshToken = refresh;

            SharedPreferences.Editor editor = prefs().edit().putString("access_token", accessToken);
            if (!refreshToken.isEmpty()) editor.putString("refresh_token", refreshToken);
            editor.apply();

            return true;
        } catch (Exception ignored) {
            return false;
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
