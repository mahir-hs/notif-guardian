"""Builds video/out/notif-guardian-install.mp4 from raw/full.mp4.

Cuts segments, speeds up the Danger countdowns, draws captions on top and tap rings at
hand-placed positions. Run:  python video/build.py   (needs ffmpeg, path below).
"""
import os, struct, subprocess, sys, textwrap, zlib

HERE = os.path.dirname(os.path.abspath(__file__))
FFMPEG = r"D:\DevData\downloads\ffmpeg\ffmpeg-release-essentials\ffmpeg-9.0.2-essentials_build\bin\ffmpeg.exe"
SRC = os.path.join(HERE, "raw", "full.mp4")
WORK = os.path.join(HERE, "work")
OUT = os.path.join(HERE, "out", "notif-guardian-install.mp4")
FONT = "C\\:/Windows/Fonts/arialbd.ttf"
W, H = 864, 1920


def t(s):  # "1:23.5" -> seconds
    parts = s.split(":")
    sec = 0.0
    for p in parts:
        sec = sec * 60 + float(p)
    return sec


# (start, end, speed, caption)
SEGS = [
    ("0:00", "0:10", 1, "Step 1: On the website, tap Download Notif Guardian"),
    ("0:16.5", "0:21", 1, "Step 2: Tap Install, then Launch"),
    ("0:21", "0:28.5", 1, "Step 3: Tap Give access and choose Notif Guardian"),
    ("0:28.5", "0:31.5", 1, "Android may block it. Tap How to unblock permissions"),
    ("0:36", "0:46", 1, "Open Settings > Apps > Manage apps and search Notif Guardian"),
    ("0:46", "0:51", 1, "Tap the three dots, then Unblock permissions"),
    ("0:54", "0:59", 1, "Go back and switch on Allow notification access"),
    ("0:59.5", "1:01", 1, "Read the warning. Tap OK when the timer ends"),
    ("1:01", "1:10", 4, "Read the warning. Tap OK when the timer ends"),
    ("1:15.5", "1:21.5", 1, "Step 4: Allow Guardian's alerts"),
    ("1:21.5", "1:28", 1, "Set Battery saver to No restrictions"),
    ("1:31", "1:39.9", 1, "Turn on Autostart for Notif Guardian"),
    ("1:41", "1:53", 1, "Step 5: Keep Google Play services awake. Tap Install"),
    ("1:53", "1:55", 1, "Tap Allow installs, then switch on Allow from this source"),
    ("1:55", "2:05", 4, "Wait for the timer, then tap OK"),
    ("2:05", "2:07.5", 1, "Guardian downloads the helper"),
    ("2:07.5", "2:15", 2, "Guardian downloads the helper"),
    ("2:20", "2:22", 1, "Tap Install, then OK"),
    ("2:22", "2:29", 1, "Allow Modify system settings for the helper"),
    ("2:30.8", "2:35.5", 1, "Turn on the helper's Autostart"),
    ("2:37", "2:44", 1, "Set the helper's battery saver to No restrictions"),
    ("2:44", "2:52.7", 1, "Tap Open helper, allow it, then tap OK"),
    ("2:53", "2:55.5", 1, "Tap Test helper"),
    ("2:55.5", "3:00", 1, "Every row is green: Google Play services are protected"),
    ("3:00", "3:04", 1, "Tap Add apps and pick the apps you want to protect"),
    ("3:04", "3:06.5", 1, "Done! All your apps can reach you"),
]

# (source time, x, y): where the finger touched, in source pixels (864x1920)
TAPS = [
    ("0:04.6", 300, 1336), ("0:05.9", 692, 1156), ("0:08.6", 740, 268),
    ("0:17.5", 432, 1668), ("0:19.7", 432, 1804),
    ("0:21.8", 208, 596), ("0:25.9", 300, 816), ("0:27.6", 748, 900),
    ("0:30.6", 272, 1624),
    ("0:38.4", 196, 1004), ("0:44.6", 304, 728), ("0:48.5", 776, 152),
    ("0:50.6", 600, 256),
    ("1:18.5", 708, 780), ("1:21.7", 624, 1256), ("1:38.6", 744, 704),
    ("1:53.5", 252, 692), ("1:54.5", 760, 400), ("2:05.1", 620, 1764),
    ("2:20.6", 432, 1668), ("2:21.7", 432, 1804),
    ("2:23.4", 708, 1072), ("2:25.6", 748, 484), ("2:34.2", 748, 432),
    ("2:38.6", 624, 1028), ("2:41.4", 180, 692),
    ("2:45.2", 664, 1140), ("2:48.0", 432, 1480), ("2:49.7", 744, 504),
    ("2:50.8", 748, 656), ("2:51.5", 636, 1804), ("2:53.3", 148, 1304),
    ("3:01.4", 792, 604), ("3:03.4", 760, 176),
]


# (source start, source end, x, y, w, h): lists of installed apps get blurred
BLURS = [
    ("0:23", "0:25.9", 0, 250, 864, 1630),     # notification-access app list
    ("0:25.9", "0:27.2", 0, 250, 864, 490),
    ("0:25.9", "0:27.2", 0, 890, 864, 990),
    ("0:39.5", "0:44.3", 0, 250, 864, 1630),   # Manage apps list + search results
    ("0:44.3", "0:45.2", 0, 250, 864, 440),
    ("0:44.3", "0:45.2", 0, 780, 864, 370),
    ("1:31", "1:38", 0, 230, 864, 890),        # Autostart search results
    ("1:38", "1:39.9", 0, 290, 864, 380),
    ("1:38", "1:39.9", 0, 900, 864, 200),
    ("3:00", "3:04", 0, 1250, 864, 630),       # "All apps" part of Choose apps
]


def png_ring(path, size=150):
    """RGBA PNG: white ring with a soft translucent fill (no external libs)."""
    c = (size - 1) / 2
    rows = []
    for y in range(size):
        row = bytearray([0])
        for x in range(size):
            d = ((x - c) ** 2 + (y - c) ** 2) ** 0.5
            if c - 9 <= d <= c - 2:
                px = (255, 255, 255, 235)
            elif d < c - 9:
                px = (255, 255, 255, 70)
            else:
                px = (0, 0, 0, 0)
            row += bytes(px)
        rows.append(bytes(row))
    raw = zlib.compress(b"".join(rows), 9)

    def chunk(tag, data):
        return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    with open(path, "wb") as f:
        f.write(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", size, size, 8, 6, 0, 0, 0))
                + chunk(b"IDAT", raw) + chunk(b"IEND", b""))


def main():
    os.makedirs(WORK, exist_ok=True)
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    ring = os.path.join(WORK, "ring.png")
    png_ring(ring)

    # timeline: output start of every segment
    out_starts, acc = [], 2.5  # 2.5 s title card first
    for s, e, sp, _ in SEGS:
        out_starts.append(acc)
        acc += (t(e) - t(s)) / sp
    total = acc

    def to_out(src):
        for (s, e, sp, _), o in zip(SEGS, out_starts):
            if t(s) <= src <= t(e):
                return o + (src - t(s)) / sp
        return None

    taps = []
    for ts, x, y in TAPS:
        o = to_out(t(ts))
        if o is not None:
            taps.append((o, x, y))
        else:
            print("tap outside kept segments, dropped:", ts)

    f = []
    f.append(f"color=c=0x0d1b3a:s={W}x{H}:d=2.5:r=30,format=yuv420p,"
             f"drawtext=fontfile='{FONT}':text='Fix late notifications':fontsize=62:fontcolor=white:x=(w-tw)/2:y=h/2-90,"
             f"drawtext=fontfile='{FONT}':text='on Xiaomi / HyperOS':fontsize=62:fontcolor=white:x=(w-tw)/2:y=h/2,"
             f"drawtext=fontfile='{FONT}':text='Notif Guardian setup':fontsize=40:fontcolor=0x9db4e8:x=(w-tw)/2:y=h/2+120[title]")
    labels = ["[title]"]
    for i, (s, e, sp, cap) in enumerate(SEGS):
        with open(os.path.join(WORK, f"cap{i}.txt"), "w", encoding="utf-8", newline=chr(10)) as fh:
            fh.write(chr(10).join(textwrap.wrap(cap, 28)))
        nlines = len(textwrap.wrap(cap, 28))
        bar_h = nlines * 58 + 36
        chain = f"[0:v]trim=start={t(s)}:end={t(e)},setpts=(PTS-STARTPTS)/{sp},fps=30,format=yuv420p"
        cur_l = f"[s{i}]"
        f.append(chain + cur_l)
        for j, (bs, be, bx, by, bw, bh) in enumerate(BLURS):
            lo, hi = max(t(bs), t(s)), min(t(be), t(e))
            if lo >= hi:
                continue
            ts, te = (lo - t(s)) / sp, (hi - t(s)) / sp
            f.append(f"{cur_l}split[a{i}_{j}][b{i}_{j}]")
            f.append(f"[b{i}_{j}]crop={bw}:{bh}:{bx}:{by},boxblur=28:4[c{i}_{j}]")
            nxt_l = f"[n{i}_{j}]"
            f.append(f"[a{i}_{j}][c{i}_{j}]overlay={bx}:{by}:enable='between(t,{ts:.3f},{te:.3f})'{nxt_l}")
            cur_l = nxt_l
        f.append(
            f"{cur_l}drawbox=x=0:y=100:w=iw:h={bar_h}:color=black@0.75:t=fill,"
            f"drawtext=fontfile='{FONT}':textfile=work/cap{i}.txt:fontsize=40:fontcolor=white:"
            f"line_spacing=6:text_align=center:x=(w-text_w)/2:y=100+20[v{i}]")
        labels.append(f"[v{i}]")
    f.append("".join(labels) + f"concat=n={len(labels)}:v=1:a=0[base]")

    cur = "[base]"
    for k, (o, x, y) in enumerate(taps):
        f.append(f"[{k + 1}:v]format=rgba,fade=t=out:st=0.22:d=0.23:alpha=1,setpts=PTS+{o:.3f}/TB[r{k}]")
        nxt = f"[o{k}]"
        f.append(f"{cur}[r{k}]overlay=x={x}-75:y={y}-75:enable='between(t,{o:.3f},{o + 0.45:.3f})':eof_action=pass{nxt}")
        cur = nxt
    f.append(f"{cur}format=yuv420p[outv]")

    with open(os.path.join(WORK, "filter.txt"), "w", encoding="utf-8") as fh:
        fh.write(";\n".join(f))

    cmd = [FFMPEG, "-y", "-hide_banner", "-loglevel", "error", "-stats", "-i", SRC]
    for _ in taps:
        cmd += ["-loop", "1", "-t", "0.5", "-framerate", "30", "-i", ring]
    cmd += ["-/filter_complex", "work/filter.txt", "-map", "[outv]", "-c:v", "libx264",
            "-preset", "medium", "-crf", "20", "-pix_fmt", "yuv420p", "-movflags", "+faststart", OUT]
    print(f"output length ~{total:.1f}s, {len(taps)} taps")
    subprocess.run(cmd, cwd=HERE, check=True)
    print("wrote", OUT)


if __name__ == "__main__":
    sys.exit(main())
