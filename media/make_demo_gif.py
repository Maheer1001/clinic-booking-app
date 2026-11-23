"""Builds media/demo.gif from the screenshot harnesses' PNGs.

1. Load docs/sample-data.sql and docs/demo-data.sql into a throwaway database, create a clinic
   admin with app.StaffSeed, then run
     mvn -Pscreenshots test-compile exec:exec          (target/screenshots)
     mvn -Pstaff-screenshots test-compile exec:exec    (target/staff-shots)
2. python media/make_demo_gif.py   (needs Pillow; run from the repo root)

Each slide is a caption on the left and the app in a window frame on the right, with a short
crossfade between slides.
"""
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parent.parent
SHOTS = ROOT / "target" / "screenshots"
STAFF = ROOT / "target" / "staff-shots"
FONTS = ROOT / "src" / "main" / "resources" / "fonts"
OUT = ROOT / "media" / "demo.gif"

S = 2                      # draw at 2x, then downscale for smooth text and edges
W, H = 720, 640
BG = (247, 243, 251)
INK = (30, 21, 48)
BODY = (88, 80, 104)
PURPLE = (91, 44, 140)
CHIP_EDGE = (221, 211, 236)
DOT_OFF = (216, 206, 232)

SLIDES = [
    ("auth-01-login", "Clinic booking app", "Book a sexual-health clinic appointment in under a minute",
     "A JavaFX desktop app to find a clinic, book a slot, and keep track of results and prescriptions.", []),
    ("home-01-upcoming", "Home", "Everything at a glance",
     "Your next appointment, counted down, with everything you have booked.", []),
    ("book-19-default-clinic-offer", "Step 1 · Clinic", "Your usual clinic first",
     "A saved clinic is offered straight away. Or search for another one.", []),
    ("book-04-map-popup", "Step 1 · Clinic", "Find the nearest clinic",
     "Search by postcode. Clinics are sorted by distance and pinned on an OpenStreetMap map.",
     ["postcodes.io", "Leaflet"]),
    ("book-08-service-chosen", "Step 2 · Service", "Choose a service",
     "Tick one or more services and open each to see what it involves.", []),
    ("book-11-time-list", "Step 3 · Time", "Pick a free time",
     "Slots come from each clinic's opening hours and the service's length, and can't be double-booked.", []),
    ("book-14-review", "Step 5 · Review", "Check and confirm",
     "Your details are saved for the clinic. Review everything before you book.", []),
    ("book-16-confirmed", "Confirmed", "Booked, with a confirmation email",
     "An HTML email and an in-app notification with a booking reference.", ["JavaMail", "Thymeleaf"]),
    ("reminders-02-time-expanded", "Reminders", "Reminders your way",
     "Choose how far ahead, repeats, quiet hours and discreet wording. Popups show one at a time.", []),
    ("tracking-03-test-detail", "Tracking", "Test results, clearly",
     "Results appear once the clinic releases them, with treatment notes and a shortcut to book a retest.", []),
    ("tracking-05-prescriptions", "Tracking", "Prescriptions",
     "Current and past courses with dose and dates. Dose reminders end when the course does.", []),
    ("staff-16-today", "Staff · Today", "The clinic's day",
     "Staff use the same app. Open an appointment to mark it attended or a no-show and keep staff notes.", []),
    ("staff-24-record", "Staff · Patients", "Patient records",
     "Find a patient with a booking at the clinic, then add a test result or prescribe.", []),
    ("staff-31-release-confirm", "Staff · Results", "Released, never leaked",
     "Results are drafts until staff release them. A positive result asks first; the email never says it.", []),
    ("staff-39b-rx-shortened", "Staff · Prescriptions", "Shorten or stop a course",
     "Change the last day or stop early, and the patient's dose reminders follow.", []),
    ("staff-04-clinic", "Staff · Clinic admin", "Approve staff, per clinic",
     "Clinicians sign up with a staff code and wait for approval. Roles are set per clinic.", []),
    ("a11y-02-high-contrast", "Accessibility", "High contrast and larger text",
     "Keyboard focus, screen-reader labels, and per-user contrast, bold and text size settings.", []),
    ("home-01-upcoming", "Open source on GitHub", "Maheer1001/\nclinic-booking-app",
     "Group project for Southampton COMP2300, continued and polished by Maheer.",
     ["Java 21", "JavaFX", "PostgreSQL", "Leaflet + OSM", "postcodes.io", "JavaMail", "Thymeleaf", "JUnit 5",
      "Maven"]),
]
HOLD_MS = [3200] + [2600] * (len(SLIDES) - 2) + [3800]
FADE_MS, FADE_STEPS = 70, 2


def font(weight, size):
    return ImageFont.truetype(str(FONTS / f"Nunito-{weight}.ttf"), size * S)


def wrap(draw, text, fnt, width):
    lines = []
    for para in text.split("\n"):
        line = ""
        for word in para.split():
            trial = (line + " " + word).strip()
            if draw.textlength(trial, font=fnt) <= width:
                line = trial
            else:
                lines.append(line)
                line = word
        lines.append(line)
    return lines


def shot(name):
    for folder in (SHOTS, STAFF):
        p = folder / f"{name}.png"
        if p.exists():
            return Image.open(p).convert("RGB")
    raise FileNotFoundError(name)


def background():
    img = Image.new("RGB", (W * S, H * S), BG)
    d = ImageDraw.Draw(img)
    d.ellipse((-170 * S, 500 * S, 220 * S, 900 * S), fill=(238, 231, 247))
    glow = Image.new("L", img.size, 0)
    ImageDraw.Draw(glow).rounded_rectangle((350 * S, 4 * S, 720 * S, 640 * S), 60 * S, fill=110)
    glow = glow.filter(ImageFilter.GaussianBlur(28 * S))
    return Image.composite(Image.new("RGB", img.size, (232, 222, 245)), img, glow)


def window(img, screenshot):
    x, y, w = 370 * S, 24 * S, 330 * S
    bar = 26 * S
    content = screenshot.resize((w, round(w * screenshot.height / screenshot.width)), Image.Resampling.LANCZOS)
    h = bar + content.height
    shadow = Image.new("L", img.size, 0)
    ImageDraw.Draw(shadow).rounded_rectangle((x, y + 6 * S, x + w, y + h + 6 * S), 14 * S, fill=70)
    shadow = shadow.filter(ImageFilter.GaussianBlur(12 * S))
    img.paste((190, 175, 215), mask=shadow)
    frame = Image.new("RGB", (w, h), (246, 244, 249))
    frame.paste(content, (0, bar))
    fd = ImageDraw.Draw(frame)
    for i, c in enumerate([(237, 106, 94), (245, 191, 79), (98, 197, 84)]):
        cx = (16 + i * 16) * S
        fd.ellipse((cx - 5 * S, 13 * S - 5 * S, cx + 5 * S, 13 * S + 5 * S), fill=c)
    title = "Health Tracking and Appointments"
    tf = font("Medium", 11)
    fd.text((w / 2, 13 * S), title, font=tf, fill=(90, 84, 100), anchor="mm")
    fd.line((0, bar - 1, w, bar - 1), fill=(228, 222, 236), width=S)
    mask = Image.new("L", (w, h), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, w - 1, h - 1), 14 * S, fill=255)
    img.paste(frame, (x, y), mask)


def caption(img, index, eyebrow, title, body, chips):
    d = ImageDraw.Draw(img)
    left, width = 38 * S, 300 * S
    ef, tf, bf, cf = font("ExtraBold", 13), font("ExtraBold", 28), font("Regular", 16), font("Bold", 13)
    t_lines = wrap(d, title, tf, width)
    b_lines = wrap(d, body, bf, width)
    rows = []
    x = 0
    for c in chips:
        cw = d.textlength(c, font=cf) + 24 * S
        if rows and x + cw <= width:
            rows[-1].append((c, cw))
            x += cw + 8 * S
        else:
            rows.append([(c, cw)])
            x = cw + 8 * S
    height = 20 * S + 14 * S + len(t_lines) * 36 * S + 10 * S + len(b_lines) * 25 * S \
        + (16 * S + len(rows) * 37 * S if rows else 0)
    y = (H * S - height) / 2 - 20 * S
    d.text((left, y), eyebrow.upper(), font=ef, fill=PURPLE)
    y += 34 * S
    for line in t_lines:
        d.text((left, y), line, font=tf, fill=INK)
        y += 36 * S
    y += 10 * S
    for line in b_lines:
        d.text((left, y), line, font=bf, fill=BODY)
        y += 25 * S
    y += 16 * S
    for row in rows:
        x = left
        for c, cw in row:
            d.rounded_rectangle((x, y, x + cw, y + 27 * S), 14 * S, fill=(252, 250, 254), outline=CHIP_EDGE,
                                width=S)
            d.text((x + cw / 2, y + 13.5 * S), c, font=cf, fill=(58, 34, 92), anchor="mm")
            x += cw + 8 * S
        y += 37 * S
    for i in range(len(SLIDES)):
        cx = (42 + i * 13) * S
        d.ellipse((cx - 4 * S, 594 * S - 4 * S, cx + 4 * S, 594 * S + 4 * S),
                  fill=PURPLE if i <= index else DOT_OFF)


def main():
    base = background()
    slides = []
    for i, (name, eyebrow, title, body, chips) in enumerate(SLIDES):
        img = base.copy()
        window(img, shot(name))
        caption(img, i, eyebrow, title, body, chips)
        slides.append(img.resize((W, H), Image.Resampling.LANCZOS))
    frames, durations = [], []
    for i, slide in enumerate(slides):
        frames.append(slide)
        durations.append(HOLD_MS[i])
        if i + 1 < len(slides):
            for k in range(1, FADE_STEPS + 1):
                frames.append(Image.blend(slide, slides[i + 1], k / (FADE_STEPS + 1)))
                durations.append(FADE_MS)
    montage = Image.new("RGB", (W, H * len(slides)))
    for i, slide in enumerate(slides):
        montage.paste(slide, (0, H * i))
    palette = montage.quantize(colors=255, method=Image.Quantize.MEDIANCUT)
    quantized = [f.quantize(palette=palette, dither=Image.Dither.NONE) for f in frames]
    quantized[0].save(OUT, save_all=True, append_images=quantized[1:], duration=durations, loop=0, optimize=True)
    print(f"{OUT} - {len(frames)} frames, {OUT.stat().st_size / 1e6:.1f} MB")


if __name__ == "__main__":
    main()
