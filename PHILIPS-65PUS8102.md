# Olohuoneen Philips 65PUS8102/12 – mitä tehtiin

Tehty 20.10.2026. TV: Philips 65PUS8102/12 (2017), TPM171E-alusta, Android TV 8.0,
firmware TPM171E_R.107.001.143.000 (Philipsin viimeinen, 2021). Android-päivitystä
uudempaan ei ole saatavilla.

## Muutokset telkkarissa

**Käytöstä poistetut paketit (35 kpl)** – lista ja perustelut:
[`examples/philips-65pus8102/debloat.txt`](examples/philips-65pus8102/debloat.txt)

- Google/Philips suositus- ja mainosrivit, esittelyt, demo-tila
- Philipsin telemetria: `usagelogger`, `candeebug` (DAC), `gel`; Inscape ACR -asiakas (`inscape_fp`-daemon jää, vaatii rootin)
- NetTV-sovelluskauppa ja 13 NetTV-sovellusta
- Esiasennetut: Gameloft-peli, TED, Opera-testisovellus, Play Music, Play Elokuvat
- Google Assistant (`katniss`) ja Alexa – mikrofoninappi ei toimi tämän jälkeen

**Pidetty tarkoituksella:** `nettvbrowser` (HbbTV / punainen nappi), Chromecast,
Play Kauppa, firmware-päivitykset, Ambilight/Hue, YouTube.

**Launcher** (`com.gathin.tvhome`) – asetukset `launcher/app/src/main/res/values/config.xml`:
- Alapalkki: Netflix, Disney+, HBO Max, Apple TV, MTV Katsomo, Spotify (6 saraketta)
- Piilotettu: Live TV, TV-opas, Media, Play Pelit, käyttöopas, AirPlay
- Suorituskykykorjaus upstream-PR #150:stä (vain perf-commit, sovitettu nykyiseen mainiin)
- "Nyt soi" -kortti upstream-PR #173:sta (vain kortti; Bitcoin-rivi, itsepäivitys ja greeting_name jätetty pois).
  Kortti tarvitsee notification listenerin. Android 8.0:ssa `cmd notification allow_listener` ei ole
  olemassa (palauttaa silti onnistumisen), joten `build-and-install.sh` lisää listenerin suoraan
  `enabled_notification_listeners`-asetukseen.
- Suomenkielinen käännös (`values-fi`), tarjottu upstreamiin PR #301:nä; forkissa lisäksi Nyt soi -kortin teksti
- Ruska-teema (`themes/ruska/`): koodilla piirretty syksyinen taustakuva, ei valokuvia tai taideteoksia. Käytössä telkkarissa (`tools/theme.sh themes/ruska`, poisto `--reset`)
- Haara on synkronoitu upstream mainiin 20.10.2026 (mm. #248 palautustietue, #246/#247/#275/#277 launcher-korjaukset)

**Asennetut sovellukset:** Disney+, HBO Max, Apple TV, Spotify, MTV Katsomo (Play Kauppa),
AirPlay-vastaanotin `io.github.jqssun.airplay` (nimellä "Living Room TV", skripti `airplay/`).

**ADB-asetukset (eivät ole tiedostoissa):**
```
settings put global window_animation_scale 0.5
settings put global transition_animation_scale 0.5
settings put global animator_duration_scale 0.5
settings put secure screensaver_components com.gathin.tvhome/.ClockDream
settings put secure screensaver_enabled 1
settings put secure enabled_notification_listeners "<entinen arvo>:com.gathin.tvhome/com.gathin.tvhome.NowPlayingListener"
```

## Uudelleenasennus / muutokset

```
export TV_HOST=192.168.50.9
export JAVA_HOME=/opt/homebrew/opt/openjdk@17 ANDROID_HOME=$HOME/Library/Android/sdk
STOCK_LAUNCHER=none launcher/build-and-install.sh       # launcher
tools/disable.sh examples/philips-65pus8102/debloat.txt # debloat
AIRPLAY_SRC=$HOME/android-airplay-server airplay/build-and-install.sh
```
Vaatii: JDK 17, Android SDK platform 34 + build-tools 34, NDK 27 (AirPlay).

## Palautus

```
TV_HOST=192.168.50.9 tools/restore.sh examples/philips-65pus8102/debloat.txt com.google.android.tvlauncher
```
Palautus lukee tietueen `~/.cleanyourtv/recovery-192.168.50.9-<hash>.json` (upstream #248): kaikki listan
paketit olivat alun perin päällä, kotinäkymä oli `com.google.android.tvlauncher/.MainActivity` ja
animaatioasetukset olivat oletuksia (`null`). Tietue rakennettiin varmuuskopiosta, ja kopio on
[`examples/philips-65pus8102/recovery-record.json`](examples/philips-65pus8102/recovery-record.json).
Uudella koneella kopioi se `~/.cleanyourtv/`-kansioon; tiedostonimen hash lasketaan listan absoluuttisesta
polusta (`tools/recovery.py`, `record_path`).
Alkuperäisen tilan varmuuskopio on vain paikallisesti (`backup-philips-*/`, ei repossa).

## ADB

Telkkarin osoite 192.168.50.9. Kytke USB-vianetsintä pois kun ei tarvita (portti 5555 on
salaamaton). Uudelleen yhdistäessä hyväksyntäkysely ilmestyi vasta kun valtuutukset peruttiin,
vianetsintä kytkettiin pois/päälle ja virta katkaistiin seinästä.
