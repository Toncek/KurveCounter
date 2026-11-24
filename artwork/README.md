# KurveCounter app identity

`kurvecounter-mark.png` is the transparent master artwork generated with the built-in imagegen tool. It is shared by the Android launcher and launch screen. The app's existing orange, cyan, and charcoal palette is preserved.

Runtime assets:

- `../app/src/main/res/drawable-xxxhdpi/ic_launcher_foreground.png`: 432 px adaptive foreground (108 dp), with transparent padding for launcher masks.
- `../app/src/main/res/drawable-xxxhdpi/splash_logo.png`: 1152 px launch logo (288 dp), with visible artwork inside Android's central 192 dp circle.
- `../app/src/main/res/values/colors.xml`: charcoal background, `#0B0E14`.
- `play_store_feature_graphic.png`: 1024×500 Google Play Store feature graphic generated locally via `vega64-img` (dark `#0B0E14` motorsport telemetry banner with `#FF5F1F` orange and `#00E5FF` cyan accents).

The AndroidX SplashScreen integration displays the same artwork on Android 10 and later, without a separate splash activity or artificial startup delay. Version 1.0.1 is used to match the project's existing Kotlin and AndroidX toolchain.

Regenerate the packaged sizes from the repository root using ImageMagick:

```sh
convert artwork/kurvecounter-mark.png -resize 282x282 -background none -gravity center -extent 432x432 app/src/main/res/drawable-xxxhdpi/ic_launcher_foreground.png
convert artwork/kurvecounter-mark.png -resize 800x800 -background none -gravity center -extent 1152x1152 app/src/main/res/drawable-xxxhdpi/splash_logo.png
```

Sizing references: [Android adaptive icons](https://developer.android.com/develop/ui/compose/system/icon_design_adaptive), [SplashScreen dimensions](https://developer.android.com/reference/androidx/core/splashscreen/SplashScreen), and [launch integration](https://developer.android.com/develop/ui/views/launch/splash-screen/migrate).

## Generation prompt

Use case: logo-brand.
Asset type: production Android launcher foreground and matching splash-screen logo for KurveCounter, a motorcycle cornering telemetry and garage app.
Create ONE exceptional, finished emblem, no presentation board: a powerful geometric motorcycle and helmeted rider leaning into a sweeping corner, integrated with a bold open circular racing line / telemetry arc. A confident compact silhouette, instantly readable at tiny app-icon sizes. Premium motorsport identity, sharp purposeful geometry, elegant negative space, restrained two-tone highlights. Flat vector-like artwork with clean hard edges; no photographic rendering.
Palette: dominant vivid racing orange #FF5F1F, a small ice-cyan #00E5FF racing-line accent, optional off-white highlights. Designed to sit on near-black charcoal #0B0E14.
Composition: square canvas, emblem centered, entire mark contained inside central 60% of width and height, ample fully transparent padding on every side. Symbol only, NO text, NO letters, NO wordmark. Transparent background required. No enclosing app tile, no mockup, no drop shadow, no glow, no gradients, no texture, no tiny instrumentation, no thin hairlines, no watermark. The visual should feel fast, precise, and memorable, with a clear motorcycle identity.
