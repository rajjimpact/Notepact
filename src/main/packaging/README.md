# Notepact – Packaging Icon Assets

This directory holds optional app icon files used by `jpackage` during native installer creation.

## Files to place here

| File | Platform | Format | Recommended Size |
|---|---|---|---|
| `notepact.ico` | Windows (.msi) | ICO (multi-size) | 256×256 px minimum |
| `notepact.icns` | macOS (.dmg) | ICNS | 512×512 px minimum |

## How the workflow uses these files

The GitHub Actions workflow (`build.yml`) checks for the presence of these files before calling `jpackage`.
If an icon file **exists**, it passes `--icon <file>` to `jpackage`.
If an icon file **does not exist**, the flag is simply omitted and `jpackage` uses the default Java icon.

## Creating icon files

### Windows (.ico)

You can convert a PNG to ICO using free tools such as:
- https://convertico.com/
- GIMP (File → Export As → `.ico`)

### macOS (.icns)

On a Mac, use `iconutil`:

```bash
# From a 1024×1024 PNG
mkdir Notepact.iconset
sips -z 512 512   notepact.png --out Notepact.iconset/icon_512x512.png
sips -z 256 256   notepact.png --out Notepact.iconset/icon_256x256.png
sips -z 128 128   notepact.png --out Notepact.iconset/icon_128x128.png
sips -z 64 64     notepact.png --out Notepact.iconset/icon_64x64.png
sips -z 32 32     notepact.png --out Notepact.iconset/icon_32x32.png
sips -z 16 16     notepact.png --out Notepact.iconset/icon_16x16.png
iconutil -c icns Notepact.iconset -o notepact.icns
```

Or use an online converter like https://cloudconvert.com/png-to-icns
