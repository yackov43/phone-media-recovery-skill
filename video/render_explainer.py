#!/usr/bin/env python3
"""Render the Phone Media Recovery explainer video from local assets.

Creates 1920x1080 PNG slides, builds a video from stills, mixes narration with
background music, and burns Hebrew subtitles into the final MP4.
"""

from __future__ import annotations

import argparse
import subprocess
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont


ROOT = Path(__file__).resolve().parents[1]
VIDEO_DIR = ROOT / "video"
ASSETS = ROOT / "assets"
VIDEO_ASSETS = VIDEO_DIR / "assets"
BUILD = VIDEO_DIR / "build"
W, H = 1920, 1080


SCENES = [
    ("01-cover.png", 8.0),
    ("02-file-not-found.png", 10.0),
    ("03-workflow.png", 10.0),
    ("04-compare.png", 10.0),
    ("05-restore.png", 10.0),
    ("06-verify.png", 8.0),
    ("07-close.png", 2.65),
]
TOTAL_DURATION = sum(duration for _, duration in SCENES)


def font(size: int, bold: bool = False) -> ImageFont.FreeTypeFont:
    candidates = [
        Path("C:/Windows/Fonts/segoeuib.ttf" if bold else "C:/Windows/Fonts/segoeui.ttf"),
        Path("C:/Windows/Fonts/arialbd.ttf" if bold else "C:/Windows/Fonts/arial.ttf"),
    ]
    for candidate in candidates:
        if candidate.exists():
            return ImageFont.truetype(str(candidate), size)
    return ImageFont.load_default()


def cover_image(path: Path) -> Image.Image:
    img = Image.open(path).convert("RGB")
    scale = max(W / img.width, H / img.height)
    size = (round(img.width * scale), round(img.height * scale))
    img = img.resize(size, Image.Resampling.LANCZOS)
    left = (img.width - W) // 2
    top = (img.height - H) // 2
    return img.crop((left, top, left + W, top + H))


def fit_on_canvas(path: Path, bg=(248, 250, 252), pad=70) -> Image.Image:
    canvas = Image.new("RGB", (W, H), bg)
    img = Image.open(path).convert("RGB")
    scale = min((W - pad * 2) / img.width, (H - pad * 2) / img.height)
    size = (round(img.width * scale), round(img.height * scale))
    img = img.resize(size, Image.Resampling.LANCZOS)
    canvas.paste(img, ((W - size[0]) // 2, (H - size[1]) // 2))
    return canvas


def draw_rounded(draw: ImageDraw.ImageDraw, box, radius, fill, outline=None, width=1):
    draw.rounded_rectangle(box, radius=radius, fill=fill, outline=outline, width=width)


def file_not_found_slide() -> Image.Image:
    img = Image.new("RGB", (W, H), (248, 250, 252))
    d = ImageDraw.Draw(img)
    title = font(58, True)
    body = font(34)
    small = font(28)
    mono = font(26)

    d.text((90, 82), "The chat survived. The file did not.", fill=(15, 23, 42), font=title)
    d.text((92, 158), "A strict manifest comparison finds what the transfer missed.", fill=(71, 85, 105), font=body)

    phone = (120, 240, 700, 920)
    draw_rounded(d, phone, 42, (15, 118, 110), outline=(19, 78, 74), width=8)
    draw_rounded(d, (165, 305, 655, 850), 22, (240, 253, 250), None)

    bubbles = [
        ((205, 355, 585, 425), "IMG-2020...jpg"),
        ((205, 455, 585, 525), "VID-2019...mp4"),
        ((205, 555, 585, 625), "DOC-2021...pdf"),
    ]
    for box, text in bubbles:
        draw_rounded(d, box, 18, (255, 255, 255), outline=(148, 163, 184), width=2)
        d.text((box[0] + 34, box[1] + 18), text, fill=(15, 23, 42), font=small)

    alert = (860, 345, 1640, 735)
    draw_rounded(d, alert, 28, (255, 247, 237), outline=(249, 115, 22), width=5)
    d.text((920, 410), "File not found", fill=(124, 45, 18), font=font(62, True))
    d.text((920, 505), "The app points to a path,", fill=(124, 45, 18), font=body)
    d.text((920, 555), "but the media file is absent.", fill=(124, 45, 18), font=body)
    d.text((920, 650), "Action: restore exact path only", fill=(15, 118, 110), font=mono)
    return img


def workflow_slide() -> Image.Image:
    img = Image.new("RGB", (W, H), (248, 250, 252))
    d = ImageDraw.Draw(img)
    d.text((90, 78), "Exact-path recovery pipeline", fill=(15, 23, 42), font=font(58, True))
    d.text((92, 150), "No guesses. No visual matching. Every restored file is verified.", fill=(71, 85, 105), font=font(34))
    steps = [
        ("1", "Manifest", "epoch | size | path"),
        ("2", "Compare", "missing + partial"),
        ("3", "Restore", "archive-safe transfer"),
        ("4", "Verify", "checksum + bytes"),
    ]
    x = 130
    for num, head, desc in steps:
        draw_rounded(d, (x, 325, x + 340, 675), 30, (255, 255, 255), outline=(15, 118, 110), width=5)
        d.ellipse((x + 120, 365, x + 220, 465), fill=(15, 118, 110))
        d.text((x + 150, 385), num, fill=(255, 255, 255), font=font(48, True), anchor="ma")
        d.text((x + 170, 525), head, fill=(15, 23, 42), font=font(42, True), anchor="mm")
        d.text((x + 170, 590), desc, fill=(71, 85, 105), font=font(26), anchor="mm")
        if num != "4":
            d.line((x + 360, 500, x + 450, 500), fill=(15, 118, 110), width=8)
            d.polygon([(x + 450, 500), (x + 428, 486), (x + 428, 514)], fill=(15, 118, 110))
        x += 440
    d.text((W // 2, 880), "If a file is absent from every source, local recovery cannot recreate it.", fill=(100, 116, 139), font=font(30), anchor="mm")
    return img


def close_slide() -> Image.Image:
    img = cover_image(ASSETS / "recovery-cover.png")
    overlay = Image.new("RGBA", (W, H), (15, 23, 42, 130))
    img = Image.alpha_composite(img.convert("RGBA"), overlay).convert("RGB")
    d = ImageDraw.Draw(img)
    d.text((W // 2, 410), "Phone Media Recovery", fill=(255, 255, 255), font=font(72, True), anchor="mm")
    d.text((W // 2, 500), "Recovered. Verified. Back where it belongs.", fill=(204, 251, 241), font=font(40), anchor="mm")
    return img


def write_slides() -> None:
    BUILD.mkdir(parents=True, exist_ok=True)
    cover_image(ASSETS / "recovery-cover.png").save(BUILD / "01-cover.png")
    file_not_found_slide().save(BUILD / "02-file-not-found.png")
    workflow_slide().save(BUILD / "03-workflow.png")
    fit_on_canvas(ASSETS / "screenshots" / "01-compare.png").save(BUILD / "04-compare.png")
    fit_on_canvas(ASSETS / "screenshots" / "02-restore.png").save(BUILD / "05-restore.png")
    fit_on_canvas(ASSETS / "screenshots" / "03-verify.png").save(BUILD / "06-verify.png")
    close_slide().save(BUILD / "07-close.png")

    lines: list[str] = []
    for name, duration in SCENES:
        lines.append(f"file '{(BUILD / name).as_posix()}'")
        lines.append(f"duration {duration:.2f}")
    lines.append(f"file '{(BUILD / SCENES[-1][0]).as_posix()}'")
    (BUILD / "slides.ffconcat").write_text("\n".join(lines) + "\n", encoding="utf-8")


def run(cmd: list[str]) -> None:
    print(" ".join(cmd))
    subprocess.run(cmd, check=True)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--ffmpeg", required=True)
    parser.add_argument("--out", default=str(VIDEO_DIR / "explainer.mp4"))
    args = parser.parse_args()

    write_slides()
    temp_video = BUILD / "silent.mp4"
    mixed_audio = BUILD / "mixed-audio.m4a"
    subtitles = (VIDEO_DIR / "subtitles-he.srt").as_posix().replace(":", "\\:")

    run([
        args.ffmpeg, "-y", "-f", "concat", "-safe", "0", "-i", str(BUILD / "slides.ffconcat"),
        "-t", f"{TOTAL_DURATION:.2f}",
        "-vf", "scale=1920:1080:force_original_aspect_ratio=decrease,pad=1920:1080:(ow-iw)/2:(oh-ih)/2,format=yuv420p",
        "-r", "30", "-c:v", "libx264", "-preset", "medium", "-crf", "18", "-pix_fmt", "yuv420p",
        str(temp_video),
    ])
    run([
        args.ffmpeg, "-y", "-i", str(VIDEO_ASSETS / "narration.wav"), "-stream_loop", "-1", "-i", str(VIDEO_ASSETS / "bg-music.mp3"),
        "-filter_complex",
        "[1:a]volume=0.12,atrim=0:58.65,afade=t=in:st=0:d=2,afade=t=out:st=55.65:d=3[m];"
        "[0:a]volume=1.0[n];[n][m]amix=inputs=2:duration=first:dropout_transition=0[a]",
        "-map", "[a]", "-c:a", "aac", "-b:a", "192k", str(mixed_audio),
    ])
    run([
        args.ffmpeg, "-y", "-i", str(temp_video), "-i", str(mixed_audio),
        "-vf",
        "subtitles='" + subtitles + "':force_style='FontName=Arial,FontSize=28,PrimaryColour=&H00FFFFFF,OutlineColour=&H80000000,BorderStyle=3,BackColour=&H80000000,Outline=1,Shadow=0,Alignment=2,MarginV=48'",
        "-map", "0:v:0", "-map", "1:a:0", "-c:v", "libx264", "-preset", "medium", "-crf", "18",
        "-c:a", "aac", "-b:a", "192k", "-shortest", "-movflags", "+faststart", str(args.out),
    ])
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
