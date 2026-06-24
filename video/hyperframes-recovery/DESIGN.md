# Hyperframes Recovery Explainer Design

## Intent

Create a new visual explainer for the phone media recovery skill without reusing the repo's previous screenshots or cover art as the visual base.

## Style Rules

- Use a YUV-inspired dark stage: `#0c0e16`, warm off-white, hot pink, and yellow.
- Hebrew display and captions use local `Rubik-Black.ttf`.
- English labels use local `Anton-Regular.ttf`.
- Captions are one line only, centered in a bottom rail.
- Keep every scene faceless and abstract: phone silhouettes, path boards, file pills, archive capsule, checksum ring, restored media grid.
- Use Hyperframes timed clips and a paused GSAP timeline. Do not rebuild this version as static slide PNGs.

## Scene List

1. Recovery gap after migration.
2. WhatsApp item exists but file is missing.
3. Exact path/name matching.
4. Dual-phone scan and missing-file count.
5. Safe additive archive transfer.
6. Size/checksum/date verification.
7. Additive verified recovery result.

## Render Target

- Duration: 60 seconds.
- Resolution: 1920x1080.
- Frame rate: 30fps.
- Final output: `../explainer.mp4`.
