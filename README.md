# SoundBC

Android app that measures the ballistic coefficient (BC, G1 and G7) of a pellet or slug from sound alone.

The phone records the shot and the gong hit. The time between them, the distance, the muzzle velocity
and the air conditions (temperature, pressure, humidity) give the time of flight, and a point-mass drag
model solves for the BC.

- Record live or pick audio files (also the sound track of .mp4 videos)
- Waveform and spectrogram views with draggable SHOT / GONG markers
- Live link to HT-X3006 / HT-50 Wi-Fi chronographs: each shot keeps its own speed and energy (J)
- Current weather from the phone barometer and Open-Meteo
- Excel (.xlsx) export, Bulgarian and English

## Build

```
gradlew.bat assembleRelease
```

The signed release needs a `keystore.properties` file (not in the repository). The latest APK is on the
Releases page.
