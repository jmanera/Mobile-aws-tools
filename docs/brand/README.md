# Brand

DevOps Mobile Tools has a provider-neutral identity: a phone, a cloud with
code brackets and metrics bars, and a gear. It carries no cloud provider's
logo or wordmark. Text may say which providers the app works with (for
example AWS), but the visuals must not suggest affiliation or endorsement.

| | |
|---|---|
| Name | DevOps Mobile Tools (on GitHub and in identifiers: `devops-mobile-tools`) |
| Tagline | Cloud operations on the go |
| Typography | Inter; Roboto as the Android fallback |

## Palette

| Colour | Hex | Use |
|---|---|---|
| Primary blue | `#0D6EFD` | Primary actions, light theme |
| Deep navy | `#0A2540` | Text, dark backgrounds, the cloud |
| Cyan | `#00B4FF` | Accents, primary colour in the dark theme |
| Secondary blue | `#3B82F6` | Secondary actions |
| Neutral | `#E5E7EB` | Surfaces, dividers |
| Light background | `#F8FAFC` | Light theme background |

In the app these live in `android/app/src/main/java/io/github/jmanera/devopsmobiletools/ui/BrandTheme.kt`.

## Files

| File | What it is |
|---|---|
| `logo-horizontal-light.svg`, `logo-horizontal-dark.svg` | Horizontal logo for light and dark backgrounds |
| `app-icon.svg` | The icon at 512 × 512, for docs and store listings |
| `adaptive-background.svg` | Background layer of the Android adaptive icon |
| `monochrome.svg` | Silhouette used for Android 13+ themed icons |

## The Android icon

The launcher icon is an adaptive icon made of three vector drawables in
`android/app/src/main/res/drawable/` (`ic_launcher_background`,
`ic_launcher_foreground`, `ic_launcher_monochrome`), referenced from
`mipmap-anydpi/`. With `minSdk` 26 no PNG launcher icons are needed.

Compared with the original brand pack, the Android layers were corrected:

- **Safe zone.** The drawing was off-centre and spread over 77 of the layer's
  108 dp, so launchers that crop to a circle cut it. It is now centred and
  scaled to fit the 66 dp safe zone.
- **Gear.** It had seven teeth (one missing at the top left); it now has eight.
- **Monochrome.** The outline version drew the cloud over the phone, so their
  lines crossed, and the gear had no teeth. It is now a solid silhouette where
  overlapping shapes are separated by a gap and the brackets are cut out of
  the cloud.
- **Logos.** They now show the same icon, gear included, as the app.
