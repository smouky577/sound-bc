# SoundBC

Android приложение, което мери балистичния коефициент (**BC G1 и G7**) на сачма или слъг само по звука.

Телефонът записва изстрела и удара в гонга. От времето между двата звука, разстоянието, началната скорост и условията на въздуха приложението смята времето на полета и намира BC.

## Възможности

- Запис на живо или избор на звукови файлове (става и звукът от .mp4 видео)
- Вълна и спектрограма с маркери **SHOT** и **GONG**, които се местят с пръст
- Връзка на живо с Wi‑Fi хронограф **HT-X3006 / HT-50**: всеки изстрел пази своята скорост и енергия (J)
- Текущо време от барометъра на телефона и Open-Meteo, или ръчно въведени условия
- Резултати G1 и G7 за всеки изстрел, история на сесиите
- Експорт в Excel (.xlsx)
- Български и английски, сменят се с флагчето

## Инсталация

Свали **SoundBC-1.0.apk** от [Releases](https://github.com/smouky577/sound-bc/releases/tag/v1.0) и го инсталирай на телефона. Ако Android попита, разреши инсталация от този файл.

## Как се мери

1. Сложи метален гонг на известно разстояние и въведи разстоянието в метри.
2. Въведи V0 или свържи хронографа, за да се взима скоростта на всеки изстрел.
3. Натисни **Текущо време** или въведи температура, налягане и влажност.
4. Натисни **Запис**, стреляй, после **Стоп** и **Анализирай**.
5. Ако някой маркер е на грешно място, премести го в резултатите.

Записвай с бутона **Запис** в приложението. Видео от камерата или файл, пратен по Месенджър, често губи острите звуци и гонгът не се засича.

## Свързване с хронографа

1. Включи Wi‑Fi на HT-X3006 (задръж долния бутон, докато се появи Wi‑Fi).
2. В **SoundBC** натисни **HT3006** („Докосни за връзка“). Приложението само се свързва към хотспот **HT-X3000**, парола `88888888`.
3. Броят се само изстрелите след свързването. Връзката стои, докато не натиснеш пак („Докосни за прекъсване“).

## Компилация

Проектът е за `compileSdk 37`, `minSdk 26` и Gradle 9.6. Отвори папката в Android Studio или изпълни `gradlew.bat assembleRelease`. Подписаната версия иска файл `keystore.properties`, който не е в хранилището.

---

# SoundBC (English)

Android app that measures the ballistic coefficient (**BC G1 and G7**) of a pellet or slug from sound alone.

The phone records the shot and the gong hit. From the time between the two sounds, the distance, the muzzle velocity and the air conditions, the app works out the time of flight and solves for the BC.

## Features

- Live recording or audio files (the sound track of an .mp4 video works too)
- Waveform and spectrogram with **SHOT** and **GONG** markers you drag with a finger
- Live link to **HT-X3006 / HT-50** Wi‑Fi chronographs: each shot keeps its own speed and energy (J)
- Current weather from the phone barometer and Open-Meteo, or manual conditions
- G1 and G7 results for every shot, session history
- Excel (.xlsx) export
- Bulgarian and English, switched with the flag

## Install

Download **SoundBC-1.0.apk** from [Releases](https://github.com/smouky577/sound-bc/releases/tag/v1.0) and install it on the phone. Allow installation from this file if Android asks.

## How to measure

1. Put a steel gong at a known distance and enter the distance in metres.
2. Enter V0, or connect the chronograph so every shot gets its own speed.
3. Tap **Current weather**, or enter temperature, pressure and humidity.
4. Tap **Record**, shoot, then **Stop** and **Analyze**.
5. If a marker is in the wrong place, drag it on the results screen.

Record with the app's **Record** button. A camera video, or a file sent through Messenger, often loses the sharp sounds and the gong is not detected.

## Connect the chronograph

1. Turn on Wi‑Fi on the HT-X3006 (hold the down button until Wi‑Fi is on).
2. In **SoundBC** tap **HT3006** ("Tap to connect"). The app joins hotspot **HT-X3000**, password `88888888`, by itself.
3. Only shots fired after connecting count. The link stays up until you tap again ("Tap to disconnect").

## Build

The project uses `compileSdk 37`, `minSdk 26` and Gradle 9.6. Open the folder in Android Studio or run `gradlew.bat assembleRelease`. The signed build needs a `keystore.properties` file, which is not in the repository.
