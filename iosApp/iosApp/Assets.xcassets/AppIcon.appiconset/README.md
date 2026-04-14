# AppIcon PNGs

Drop the following PNGs into this directory so Xcode/XcodeGen can pick them up:

| Filename        | Size (px)   | Purpose                                   |
|-----------------|-------------|-------------------------------------------|
| `Icon-20.png`   | 20 × 20     | iPad notification @1x                     |
| `Icon-29.png`   | 29 × 29     | iPad settings @1x                         |
| `Icon-40.png`   | 40 × 40     | iPhone/iPad notif @2x, spotlight @1x      |
| `Icon-58.png`   | 58 × 58     | iPhone/iPad settings @2x                  |
| `Icon-60.png`   | 60 × 60     | iPhone notif @3x                          |
| `Icon-80.png`   | 80 × 80     | iPhone/iPad spotlight @2x                 |
| `Icon-87.png`   | 87 × 87     | iPhone settings @3x                       |
| `Icon-120.png`  | 120 × 120   | iPhone app @2x, spotlight @3x             |
| `Icon-152.png`  | 152 × 152   | iPad app @2x                              |
| `Icon-167.png`  | 167 × 167   | iPad Pro app @2x                          |
| `Icon-180.png`  | 180 × 180   | iPhone app @3x                            |
| `Icon-1024.png` | 1024 × 1024 | App Store marketing (no alpha channel!)   |

## Source

Use the same teal (#1A7D7A) + gold (#C49008) + cream (#FAF6EE) ionic-column design as Android — see `composeApp/src/androidMain/res/drawable/ic_launcher_foreground.xml`.

## Generating from Windows

1. Export `ic_launcher_foreground.xml` to a 1024×1024 PNG master (Android Studio → Asset Studio → Image Asset, or render the SVG with Inkscape / online tools).
2. Ensure the 1024 has **no alpha channel** — App Store Connect rejects transparency.
3. Generate the other sizes from the master with one of:
   - https://appicon.co (drag-and-drop, downloads the full set)
   - ImageMagick: `magick convert icon-1024.png -resize 180x180 Icon-180.png` (repeat per size)
   - GIMP / Photoshop batch export

Once the PNGs are in place, delete this README (it's excluded from the target via `project.yml`).
