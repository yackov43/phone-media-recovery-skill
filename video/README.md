# 🎬 Explainer Video Kit · ערכת סרטון הסברה

Everything needed to produce the ~60-second explainer with **English voice-over**, background music, and **burned-in Hebrew subtitles**.

## Contents

| File | Use |
|------|-----|
| [`narration-en.md`](narration-en.md) | English narration script → feed to the TTS / voice track (7 lines, in order). |
| [`subtitles-he.srt`](subtitles-he.srt) | Hebrew subtitles, pre-timed → load as the caption track. |
| [`storyboard.md`](storyboard.md) | Scene-by-scene map: timing · visual · narration · subtitle. |
| [`render_explainer.py`](render_explainer.py) | Local renderer that builds slides, mixes audio, and burns subtitles into `explainer.mp4`. |
| [`explainer.mp4`](explainer.mp4) | Final rendered 1080p MP4. |

## Visual assets (in order)

1. `../assets/recovery-cover.png`
2. Generated “File not found” slide from `render_explainer.py`
3. Generated exact-path workflow slide from `render_explainer.py`
4. `../assets/screenshots/01-compare.png`
5. `../assets/screenshots/02-restore.png`
6. `../assets/screenshots/03-verify.png`
7. Generated close card from `../assets/recovery-cover.png`

## How to build it / איך מרכיבים

Run the renderer from the repository root:

```powershell
python .\video\render_explainer.py --ffmpeg "C:\path\to\ffmpeg.exe" --out .\video\explainer.mp4
```

The script creates temporary PNG slides under `video/build/`, mixes:

- `video/assets/narration.wav`
- `video/assets/bg-music.mp3`
- `video/subtitles-he.srt`

and writes the final MP4 to `video/explainer.mp4`.

Manual editors can still follow `storyboard.md` if they want to recreate or adapt the timeline.
