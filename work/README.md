# My TV: Hisense HiSmartTV A4 (Android TV 9)

Cleaned on 23 October 2026 with cleanyourtv. Nothing was uninstalled and the TV is not rooted:
apps were only *disabled*, and the stock home screen was swapped for the cleanyourtv launcher.
Everything here can be undone.

## What changed

- **20 apps disabled**, listed with comments in [debloat.txt](debloat.txt): Hisense and Google
  telemetry, the recommendation/ad rows, store demo mode, the silent app auto-installer, the
  Hisense browser, Play Music/Movies/Games, Smart Connect, Lionsgate Play, discovery+, Zee5,
  MX Player and YouTube Music. (SonyLIV was disabled at first and turned back on.)
- **Kept**: Netflix, Prime Video, SonyLIV, YouTube, JioHotstar, Spotify, Airtel Xstream, Glance, RemoteNOW,
  the USB media player, Play Store, Assistant/voice, Chromecast, Screen Sharing and firmware updates.
- **Home screen**: the stock Google TV launcher (`com.google.android.tvlauncher`, with its sponsored
  banner) is disabled; `com.gathin.tvhome` is the new Home. Dock: Netflix, YouTube, Prime Video,
  JioHotstar.
- The backup taken before any change is in `../backup/` (package lists, getprop, all settings).

## Before running anything here

Every command needs ADB, which was switched off at the end. To turn it back on:
TV Settings > Device Preferences > Developer options > **USB debugging** on, then accept the
"Allow USB debugging?" prompt on the TV. Run the commands from **Git Bash** in the `cyt` folder.
If the TV's IP changed, pass the new one as the first argument.

## Your own values (kept out of git)

Two files hold what is personal to this TV and are listed in `.gitignore`, so `git push` never sends them:

- `work/local.env`: `TV_HOST=<tv ip>`. `work/build-and-install.sh` and `work/restore.sh` read it, so
  the address need not be typed. Without it, pass the address as the first argument.
- `launcher/app/src/release/res/values/my_personal.xml`: your name (`my_name`) and weather location
  (`my_weather_latitude`, `my_weather_longitude`). Release builds use these in place of the placeholders
  in `my_home.xml`:

  ```xml
  <resources>
      <string name="my_name" translatable="false">Your name</string>
      <string name="my_weather_latitude" translatable="false">28.61</string>
      <string name="my_weather_longitude" translatable="false">77.21</string>
  </resources>
  ```

Posters, backdrops, wallpapers and the white app logos stay local too (`.gitignore`); rebuild them with
`work/make_moods.py` and `work/make_logos.py`.

## Re-enable one app

```bash
TV_HOST=<tv ip> tools/venv/Scripts/python.exe tools/tv.py 'pm enable com.sonyliv'
```

Replace `com.sonyliv` with any package from [debloat.txt](debloat.txt). To keep it enabled next
time, comment its line out of the list with `#`.

## Undo everything

```bash
work/restore.sh
```

This re-enables all 20 apps and gives Home back to the stock launcher. Wait a minute before
rebooting: on this TV, a reboot within a few seconds of a change lost the change.

## Change the dock, the columns or the look

Edit `launcher/app/src/main/res/values/config.xml`:

- `dock_apps`: the dock apps, left to right (package names; list them with
  `tools/tv.py 'pm list packages'`).
- `columns`: tiles per row (4–7); the dock always has this many slots.
- `greeting_name`, `hidden_apps`, `app_labels` and the rest are explained in the file's comments.

Then rebuild and install:

```bash
work/build-and-install.sh
```

## My home screen (Iron Man vs Doom)

Two layers, both yours to change:

1. **The theme** in [theme-doom/](theme-doom/): the wallpaper (`wallpaper.jpg`, 1920×1080), how dark it is
   (`wallpaper_dim`, 0–90) and the dock size (`columns`). Push a change without rebuilding:
   `TV_HOST=<tv ip> tools/theme.sh work/theme-doom`. To try another wallpaper, replace
   `wallpaper.jpg` with a 1920×1080 image and push again.
2. **The launcher changes** from the "Your TV, Your Way" guide, set in
   `launcher/app/src/main/res/values/my_home.xml`, then `work/build-and-install.sh`:
   - `my_taglines`: the lines above the dock, `package=line|line`. With several lines, the next one
     shows each time you come back to Home.
   - `my_dock_logos` / `my_dock_colors`: the white logo and glow colour per app. The logos are made by
     `work/make_logos.py` from a screenshot of the stock tiles (`shots/11-down.png`).
   - `my_dock_inputs`: the PS5 item (HDMI 2) and Airtel TV, the set-top box (HDMI 1).
   - `my_screensaver_minutes`: idle minutes before everything fades away but the wallpaper (3; the TV's own Glance screensaver starts at 5).
   - `my_weather_latitude` / `longitude`: the weather by the clock (your city). Empty turns it off.
   - `my_home`: `false` puts back the stock cleanyourtv look.

### Welcome, voice and switching themes

- Under the clock: "Good evening, <my_name>. What do you want to enjoy today?" (morning / afternoon /
  evening by the hour). `my_name` and `my_welcome_question` in `my_home.xml`; an empty name turns it off.
- Spoken welcome: the TV's own text-to-speech voice (Indian English) says the same line once each time the
  TV wakes up or Home first starts, never when coming back from an app. `my_speak_welcome` turns it off.
- The wallpaper switches every hour between the pictures in [theme-doom/wallpapers/](theme-doom/wallpapers/)
  (`wallpaper_rotation_minutes`: 60, `wallpaper_order`: sequential). Add a picture there and push the theme
  to add a third look. Taglines follow the picture on screen: lines written `look/<app>=…` in `my_taglines`
  (e.g. `batman-superman/com.netflix.ninja=I'm Batman.`) apply while `2-batman-superman.jpg` is showing.

Still reporting: `com.hisense.tv.smartsense` logs which apps you open to a Hisense report database. The tested
list for this model keeps it enabled on purpose (on a sister Hisense model disabling it led to a recovery-mode
boot), so it was left alone.

### Mood cards ("Don't have time to browse?")

Under More apps (press Down twice from the dock). Left and Right flip back and forth through the moods;
OK spins the top card's posters and opens the title it lands on after a 3-second countdown (OK opens it
now; Left, Right or Back cancels). Eight moods plus "Anything at all", 41 titles across Netflix, Prime Video, JioHotstar and SonyLIV: Capes and chaos, Comfort sitcom, Edge of your seat, Desi mass, Mind games, Ghar ki kahaniyan (family dramas, with Oh Humnava Tum Dena Saath Mera), K-drama nights and Reality rumble (Bigg Boss, Roadies, Khatron Ke Khiladi and more).

- The list is [moods/titles.json](moods/titles.json): one entry per title with its service, its id or
  link, its TMDB images and its mood. Moods and colours are at the top of [make_moods.py](make_moods.py).
- After a change: `tools/venv/Scripts/python.exe -I work/make_moods.py` (downloads any new poster and
  backdrop from TMDB, makes the backdrops black and white), then `work/build-and-install.sh`.
- `work/test_mood_links.py` opens every title on the TV and screenshots it.
- Prime Video and JioHotstar first ask "Who's watching?"; the title opens after you pick a profile.
- SonyLIV links must be show pages: a `/season/N` link (Bade Achhe Lagte Hain 2, Shark Tank India) gave "Content not found" in the TV app.
- Spider-Man: Across the Spider-Verse was left out: in India it is only on a paid add-on channel
  inside Prime Video. Andhadhun is on Prime Video, not Netflix.

## Ad-blocking DNS

The TV looks up every address through AdGuard DNS (Private DNS, `dns.adguard-dns.com`), which blocks ad
and tracking servers for the whole TV. It does not block YouTube ads (they come from the same servers as
the videos). Checked on 24 October 2026: videos play normally with it on. Before: both settings unset, i.e. Android's automatic mode (`work/private-dns-before.txt`).

If an app stops loading something, undo it:

```bash
TV_HOST=<tv ip> tools/venv/Scripts/python.exe tools/tv.py 'settings put global private_dns_mode opportunistic'
```

or TV Settings > Network & Internet > Private DNS > Automatic.

The TV's own screensaver (Glance) is switched off so the wallpaper screensaver stays up. To bring it
back: `TV_HOST=<tv ip> tools/venv/Scripts/python.exe tools/tv.py 'settings put secure screensaver_enabled 1'`,
or TV Settings > Device Preferences > Screen saver.

The weather is the one place the launcher goes online: Open-Meteo, at most every 30 minutes, only
while Home is showing. Remove the INTERNET line from `AndroidManifest.xml` and empty the latitude
to make it fully offline again.

## Wallpaper or theme

Pick a theme from `themes/` (each has a `screenshot.jpg`), or copy one and put your own image in it,
then push it without rebuilding:

```bash
TV_HOST=<tv ip> tools/theme.sh themes/<name>
```

`themes/README.md` lists every key a theme's `config.json` can set, including the wallpaper.

## Notes for this TV

- Firmware PTMR.190127.037. The same model already has a tested list in
  `examples/hisense-hismarttv-a4/`.
- A firmware update (OTA) may re-enable some apps or the stock launcher. If the old home screen comes
  back, switch USB debugging on and run `work/build-and-install.sh` again, then re-apply the list with
  `tools/disable.sh work/debloat.txt` and wait a minute before any reboot.
- Build tools live in `~/.cleanyourtv/toolchain` (JDK 17 and the Android SDK), and the Python
  venv lives in `tools/venv`. The ADB key in `tools/adbkey/` is what the TV trusts; keep it private.
