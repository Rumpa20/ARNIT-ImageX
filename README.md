# ARNIT ImageX

A universal, private, fully offline image converter for Android, built with
**Kotlin** and **Jetpack Compose (Material 3)**. The project is structured for
on-device builds (e.g. CodeOnTheGo) - no Android Studio or desktop required.

## APK update and signing policy

**Important for every APK distributed to testers or users:** use the same persistent signing key for all updates. Do not distribute APKs signed with a newly generated, temporary CI/debug key if users are expected to install them over an existing version.

- Keep the Android signing keystore private and backed up securely.
- Never commit the keystore, passwords, or signing secrets to Git.
- Configure the stable signing key through GitHub Actions secrets before creating update-compatible APKs.
- Increment `versionCode` for each release/update.
- Until stable signing is configured, CI debug APKs may be incompatible with previously installed APKs signed by another key. Updating may require uninstalling the old app, which can delete local app data.
- Before publishing any APK, confirm its signature matches the previous distributed APK.

See [APK signing and update guide](docs/APK_SIGNING_AND_UPDATES.md).

## Highlights

- **100% offline.** The manifest contains no `INTERNET` permission; images
  physically cannot leave the device.
- **Honest format support.** The in-app Formats screen is generated from the
  codecs actually registered at runtime. Formats that this build cannot handle
  (JPEG 2000, DNG/CR2/CR3/NEF/ARW camera RAW) are listed as not available, with
  an explanation - never advertised as working.
- **Quality first.** Original resolution is always preserved unless you
  explicitly resize. Lossless encoding is used wherever the target supports it
  (PNG, WebP-lossless, TIFF, BMP, TGA, ...). Transparency is preserved and a
  warning is shown when a target format (JPEG) must flatten it. EXIF
  orientation is applied, with a pure-Kotlin EXIF reader.
- **Six quality modes.** Original, Lossless, High, Balanced, Smaller File,
  Custom (Premium).
- **Privacy by design.** Conversions run in app-private scratch storage
  (`cacheDir/imagex_tmp`), deleted after save/cancel/failure, on startup, and
  periodically by WorkManager. Saving writes a user-owned copy through the
  Storage Access Framework. History stores metadata only (names, formats,
  sizes, timestamps, status) and can be disabled or cleared.
- **Free / Premium.** Free tier: JPG/JPEG, PNG, WebP, BMP in and out, single
  image, full quality (no artificial degradation). Premium: batch conversion
  (up to 50), advanced formats, custom quality, larger images (8192px) and more.

## Supported formats (this build)

| Family | Decode | Encode |
| --- | --- | --- |
| JPEG, PNG, WebP, BMP, GIF | yes (platform) | yes (platform) |
| HEIC/HEIF | Android 9+ | decode-only |
| AVIF | Android 12+ | decode-only |
| TIFF / TIF | yes (bundled: strips, LZW/Deflate/PackBits, 1-16 bit, palette) | yes (baseline uncompressed RGB(A)) |
| SVG | yes (rasterized via AndroidSVG) | - |
| ICO | yes (embedded PNG/BMP entries) | yes (multi-size PNG entries) |
| ICNS | yes (PNG-representation entries) | - |
| TGA | yes (types 1/2/3/9/10/11, RLE, 8-32bpp) | yes (RLE 32bpp) |
| DDS | yes (raw RGB(A), DXT1/3/5) | - |
| WBMP | yes (type 0) | yes |
| PNM (PPM/PGM/PBM) | yes (P1-P6, 8/16-bit) | yes (P6/P5) |
| PSD | yes (flattened composite, raw/RLE/zip) | - |
| JPEG 2000, DNG, CR2, CR3, NEF, ARW | not available in this build | not available |

The app shows exactly this table (adapted to the device's API level) on the
**Supported Formats** screen.

## Screens

Home, Image Selection (Photo Picker + SAF file browsing, single and batch),
Conversion (format/quality/resize + warnings), Processing (progress +
cancel), Result (preview, size comparison, Save / Share / Convert Another),
Batch queue (per-item progress, Save All to folder), Formats, History,
Premium, Settings.

## Architecture

```
app/src/main/java/com/arnit/imagex/
  core/       RasterImage model, byte helpers, EXIF, error taxonomy
  formats/    format catalog, magic-byte detector, codec factory, support table
  decoder/    ImageReader registry + pure codecs + platform (Bitmap) bridge
  encoder/    ImageWriter registry + pure codecs + platform (Bitmap) encoder
  converter/  ConversionEngine + BatchConverter (UI-independent pipeline)
  storage/    TempFileManager (scratch space) + DocumentStore (SAF I/O)
  cleanup/    WorkManager periodic cleanup
  premium/    entitlements and feature gating
  billing/    gateway contract + test-mode gateway (see docs/BILLING.md)
  history/    metadata-only JSON history
  settings/   DataStore-backed preferences
  di/         manual service locator
  ui/         Compose screens, components, theme, navigation
```

The conversion engine is fully separated from the UI. All hand-written codecs
are **pure Kotlin** operating on a `RasterImage` (ARGB `IntArray`) - they have
no Android imports and are covered by JVM unit tests
(`app/src/test/java/com/arnit/imagex/CodecsTest.kt`), including cross-checks
against the JDK's own TIFF and BMP readers.

New codec support = implement `ImageReader` or `ImageWriter` and register it in
`formats/CodecFactory.kt`. The engine, gating and format screens pick it up
automatically.

## Building on a phone (CodeOnTheGo)

- `settings.gradle.kts`, root + app `build.gradle.kts`, `gradle.properties`
  and `gradle/wrapper/gradle-wrapper.properties` are all included.
- Toolchain: **AGP 8.5.2, Kotlin 2.0.21, Gradle 8.9, JDK 17**, compileSdk 35,
  minSdk 26, targetSdk 35.
- `gradle/wrapper/gradle-wrapper.jar` is intentionally not committed: mobile
  IDEs ship their own Gradle distribution and generate/replace the wrapper
  themselves (or run `gradle wrapper` once). The wrapper properties already
  pin Gradle 8.9. If your environment needs the jar, run
  `gradle wrapper --gradle-version 8.9` inside the project or copy it from
  any Gradle 8.9 installation.
- No Room/Hilt/kapt/KSP is used, keeping annotation processing (a common
  source of pain on-device) out of the build entirely.

### Memory hints for phone builds

`gradle.properties` sets `-Xmx2560m`. If your device runs out of memory while
building, lower it; if you build a release APK, consider running R8 with
gradle's `--no-daemon`.

## Running the JVM codec tests

The pure codecs can be tested on any JVM:

```bash
kotlinc <pure sources> app/src/test/java/com/arnit/imagex/CodecsTest.kt \
  app/src/test/java/com/arnit/imagex/ImageIoPlatformRaster.kt \
  -cp junit-4.13.2.jar -d out
java -cp out:kotlin-stdlib.jar:junit-4.13.2.jar:hamcrest-core-1.3.jar \
  org.junit.runner.JUnitCore com.arnit.imagex.CodecsTest
```

(Or simply run `./gradlew :app:testDebugUnitTest` in an environment that has
the wrapper.)

## Privacy statement

- No INTERNET permission. Nothing is uploaded, ever.
- Temporary files live only in the app's private cache directory and are
  deleted after save/cancel/failure, on startup, and periodically (60 min TTL,
  WorkManager every 12 h).
- Images are never stored in a database, log, or analytics sink.
- History records metadata only and can be disabled or cleared at any time.
- Saving uses SAF to write a user-owned copy to the location you choose.

## License / branding

App name: **ARNIT ImageX**. Adaptive launcher icon included (foreground
vector + monochrome layer). Version 1.0.0.
