# My Bad Apple

A terminal player for the classic **Bad Apple!!** shadow-art video — written in Java,
built to run anywhere with a single `java -jar`, and to look as good as your terminal allows.

## Highlights

- **Zero native dependencies at runtime.** The video is pre-rendered offline into a tiny
  embedded asset (1-bit frames + RLE/delta compression), so the player never needs ffmpeg
  or any native libs. Just a JRE.
- **Terminal-aware rendering.** Detects what your terminal supports (via env vars *and*
  active ANSI queries) and picks the best backend automatically: kitty/sixel/iTerm image
  protocols where available, Unicode half-blocks elsewhere, ASCII as the universal fallback.
- **Original procedural color.** Bad Apple has no source color, so color is *computed* in
  runtime by pluggable colorizers — mono, hue-cycle over time, spatial gradient, and a
  luminance LUT. Shape and color stay fully separate.
- **Solid A/V sync.** Audio is the master clock; video frames are selected by timestamp and
  dropped when behind.

## Running

```sh
./gradlew shadowJar
java -jar build/libs/my-bad-apple.jar
```

### Options (work in progress)

| Flag | Description |
|------|-------------|
| `--color <mode>` | `mono`, `hue`, `gradient`, `lut` |
| `--renderer <id>` | force a backend (`kitty`, `sixel`, `iterm`, `halfblock`, `ascii`) |
| `--force` | ignore capability detection |
| `--no-audio` | video only |

## Regenerating the asset

The embedded asset is committed, so you don't need this. To rebuild it from the source video
(requires `ffmpeg` on PATH):

```sh
./gradlew generateAsset
```

---

This is a from-scratch rewrite of an older ASCII-in-Swing version.
