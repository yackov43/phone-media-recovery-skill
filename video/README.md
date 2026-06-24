# 🎬 Explainer Video Kit · ערכת סרטון הסברה

Everything needed to produce a ~75-second explainer with **English voice-over** and **Hebrew subtitles** (e.g. on [effects.yuv.ai](https://effects.yuv.ai/) or any video editor).

## Contents

| File | Use |
|------|-----|
| [`narration-en.md`](narration-en.md) | English narration script → feed to the TTS / voice track (7 lines, in order). |
| [`subtitles-he.srt`](subtitles-he.srt) | Hebrew subtitles, pre-timed → load as the caption track. |
| [`storyboard.md`](storyboard.md) | Scene-by-scene map: timing · visual · narration · subtitle. |

## Visual assets (in order)

1. `../assets/recovery-cover.png`
2. *(“File not found” motif — create or use the cover)*
3. `../assets/recovery-workflow.svg`
4. `../assets/screenshots/01-compare.png`
5. `../assets/screenshots/02-restore.png`
6. `../assets/screenshots/03-verify.png` + `../assets/verification-loop.svg`
7. `../assets/recovery-cover.png` (close card)

## How to build it / איך מרכיבים

1. **Voice (English):** paste each numbered line from `narration-en.md` into the voice / TTS track, in order.
2. **Subtitles (Hebrew):** import `subtitles-he.srt` as the caption/subtitle track (right-to-left).
3. **Visuals:** drop the assets from the list above onto the timeline following `storyboard.md` timings.
4. **Style:** brand color `#0F766E`, 16:9, gentle zoom/fade transitions.
5. Export 1080p MP4 and drop it back into the repo root or `assets/` and embed it in the main `README.md`.

> Note: timings in the `.srt` are a starting point — nudge them to match your chosen voice pace.
