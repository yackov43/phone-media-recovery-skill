# Explainer Video Kit

This folder contains the current 60-second explainer video and the source project used to render it.

## Current Output

| File | Purpose |
| --- | --- |
| `explainer.mp4` | Final 1920x1080 MP4, 30fps, English narration, Hebrew one-line burned-in captions. |
| `hyperframes-recovery/` | Hyperframes source project for the new motion-graphics version. |
| `narration-en.md` | Locked English narration script used by the voice-over. |
| `subtitles-he.srt` | Legacy subtitle timing reference. The current video renders captions directly in Hyperframes. |
| `storyboard.md` | Original explainer storyboard reference. |

## Visual Direction

The current video is no longer built from the repository screenshots or cover images. It uses a fresh Hyperframes composition with:

- abstract phone silhouettes and data-transfer motion,
- YUV-style dark stage, pink/yellow accent thread, glass cards, and scan-grid effects,
- local Rubik Black for Hebrew captions and Anton for English labels,
- one Hebrew subtitle line per scene,
- English TTS narration plus background music.

## Build

From the repository root:

```powershell
cd .\video\hyperframes-recovery
npx hyperframes lint
npx hyperframes validate
npx hyperframes inspect
npx hyperframes render --quality high --output renders\video.mp4 --strict
cd ..\..
ffmpeg -y -t 60 -i .\video\hyperframes-recovery\renders\video.mp4 -c copy .\video\explainer.mp4
```

The `renders/` and `snapshots/` folders are generated artifacts and are intentionally ignored by Git. Commit `video/explainer.mp4` as the publishable video.
