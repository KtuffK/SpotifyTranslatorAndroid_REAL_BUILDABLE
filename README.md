# Spotify Translator Android

This is a buildable native Android starter app for a Spotify lyric translator.

## What works now

- Opens as an Android app.
- Shows a Spotify Translator screen.
- Has an Open Spotify button.
- Builds a debug APK using GitHub Actions.

## What is not implemented yet

The original `atahanuz/spotify-translator` project is Python/Tkinter and uses a Spotify web cookie approach. That is not suitable for a proper Android APK.

Needed next:
1. Spotify OAuth PKCE login.
2. Spotify Web API currently-playing endpoint.
3. A lyrics backend/proxy.
4. Translation provider integration.

## Build APK on GitHub

Go to **Actions > Build Android APK > Run workflow**.

Download the APK from the workflow **Artifacts** section after the build finishes.
