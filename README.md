# Kickset

Kickset is a paid, fully offline pipe fitter's calculator for Android that shows its working. It solves simple,
rolling and parallel offsets with the csc and cot multipliers printed under every answer; builds cut lengths after
ASME B16.9 butt-weld and B16.11 socket-weld takeouts and gaps, and cut elbows with arc marks; draws miter, saddle
and lateral wrap templates and exports them as tiled, true-scale PDFs with 100 mm and 4 inch calibration bars and an
ordinate table; shows pipe schedule data and ASME B16.5 flange bolt charts; and keeps saved cuts in jobs with CSV and
PDF cut sheet export and one-file backup and restore. It is for journeyman fitters, fabrication shop supervisors and
apprentices who work in millimetres, fractional inches or both.

Everything runs on the device. The app declares no network permission and sends nothing anywhere.

## Build

Requires JDK 17 and the Android SDK with platform 36.

```bash
./gradlew :app:testDebugUnitTest   # golden tests for the maths, tables, PDF geometry and backup codec
./gradlew assembleDebug
./gradlew bundleRelease       # needs keystore.properties, see below
```

`keystore.properties` and the `.jks` are not committed. Without them the release build stays
unsigned instead of failing:

```properties
storeFile=<slug>-upload.jks
storePassword=...
keyAlias=<slug>
keyPassword=...
```

## Layout

```
core/src/main/kotlin/com/mohdshayan/kickset/core/   pure Kotlin JVM module, no Android imports
  units/                    LengthParser (12 5/16, 1' 0 5/16, 313 mm), LengthFormatter (1/16 and 1/32 with carries)
  offset/                   simple, rolling (and angle from run) and parallel offsets, working lines
  fittings/                 table models, cut length, cut elbow
  template/                 miter, saddle, lateral wrap curves, header hole, sheet tiling, drawings
  pdf/                      a small PDF writer with uncompressed vector pages, so tests can measure its output
  pipe/                     schedule detail and weight
  jobs/                     saved input models, backup codec and validation, CSV, cut sheet pages
app/src/main/kotlin/com/mohdshayan/kickset/
  App.kt, MainActivity.kt   process and activity entry points
  di/                       ServiceLocator, the manual dependency container
  calc/                     solvers shared by screens and exports
  data/prefs/               DataStore settings (AppPrefs) and calculator drafts
  data/db/                  Room: Job and SavedCalc, save and restore transactions
  data/tables/              reads the bundled ASME JSON tables
  data/export/              document picker reads and writes
  data/session/             reopen hand-offs and the solved template for the sheet preview
  ui/theme/                 colour, type, shape and motion tokens, AppTheme
  ui/nav/                   AppNav, bottom bar or rail, type-safe routes
  ui/offsets, ui/cut, ui/templates, ui/pipe, ui/jobs, ui/settings   one package per destination
  ui/components/            sketch canvas, fraction text, fields, save-to-job sheet
app/src/main/assets/tables/ ASME B16.9, B16.11, B16.5 and B36.10M/B36.19M data with sources
app/src/test/               JVM unit tests (they exercise :core so build.sh counts them)
tools/tables/build_tables.py  transcribed catalogue columns and the two-source cross-check that writes the JSON
store/                      Play listing copy, icon, feature graphic, screenshots
docs/                       privacy policy, landing page, data sources and font licences
```

## Data sources

See `docs/DATA-SOURCES.md`. Every takeout, pipe wall and flange bolt value was checked against two published
manufacturer catalogues; values only one catalogue gave are left out.

## Licences

- Archivo and Archivo Narrow fonts: SIL Open Font License 1.1, `docs/OFL-Archivo.txt` and `docs/OFL-ArchivoNarrow.txt`.
- AndroidX, Jetpack Compose, Room, DataStore, Navigation, Kotlin, kotlinx.coroutines, kotlinx.serialization: Apache 2.0.
- Google Play In-App Review library: Play Core SDK terms. It adds no permission to the merged manifest.

Copyright SocialSure Private Limited.
