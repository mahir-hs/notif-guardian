#!/usr/bin/env python3
"""Builds the multi-language GitHub Pages site into docs/.

    python site/build.py

Reads site/template.html and site/lang/<code>.json. English (en) is the master: any key a
translation leaves out falls back to English and is reported. Writes:

    docs/index.html            English (root)
    docs/<code>/index.html     every other language
    docs/sitemap.xml           with hreflang alternates

To add a language: copy site/lang/en.json to site/lang/<code>.json, translate the values,
and add the code to LANGS below.
"""
import html
import json
import os
import re
import sys
from datetime import date
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DOCS = ROOT / "docs"
LANG_DIR = Path(__file__).resolve().parent / "lang"
SITE = "https://mahir-hs.github.io/notif-guardian/"
FONTS = ("https://fonts.googleapis.com/css2?family=Bricolage+Grotesque:opsz,wght@12..96,500;12..96,700"
         "&family=IBM+Plex+Sans:wght@400;500;600&display=swap")

# GoatCounter site code (the "xxx" in https://xxx.goatcounter.com). Cookie-free page-view counter.
# Leave empty to build without any analytics. Can also be set with the GOATCOUNTER_CODE env var.
GOATCOUNTER_CODE = os.environ.get("GOATCOUNTER_CODE", "mahir")


def analytics_tag():
    if not GOATCOUNTER_CODE:
        return ""
    return (f'<script data-goatcounter="https://{GOATCOUNTER_CODE}.goatcounter.com/count" '
            'async src="https://gc.zgo.at/count.js"></script>' "\n")


# code (also the folder name), name shown in the picker, Open Graph locale, browser language tags that map here
LANGS = [
    ("en", "English", "en_US", ["en"]),
    ("bn", "বাংলা", "bn_BD", ["bn"]),
    ("hi", "हिन्दी", "hi_IN", ["hi"]),
    ("id", "Bahasa Indonesia", "id_ID", ["id", "in"]),
    ("zh", "简体中文", "zh_CN", ["zh"]),
    ("es", "Español", "es_ES", ["es"]),
    ("ru", "Русский", "ru_RU", ["ru"]),
]

# Keys that are plain text for the page script rather than template placeholders
SCRIPT_KEYS = ["release_meta", "dyn"]


def load(code):
    return json.loads((LANG_DIR / f"{code}.json").read_text(encoding="utf-8"))


def strip_tags(s):
    return html.unescape(re.sub(r"<[^>]+>", "", s)).strip()


def url_for(code):
    return SITE if code == "en" else f"{SITE}{code}/"


def jsonld(t):
    app = {
        "@context": "https://schema.org",
        "@type": "SoftwareApplication",
        "name": "Notif Guardian",
        "operatingSystem": "Android (Xiaomi HyperOS)",
        "applicationCategory": "UtilitiesApplication",
        "description": strip_tags(t["ld_desc"]),
        "offers": {"@type": "Offer", "price": "0", "priceCurrency": "USD"},
        "url": SITE,
        "downloadUrl": "https://github.com/mahir-hs/notif-guardian/releases/latest",
        "license": "https://opensource.org/licenses/MIT",
    }
    faq = {
        "@context": "https://schema.org",
        "@type": "FAQPage",
        "mainEntity": [
            {"@type": "Question", "name": strip_tags(t[f"faq_q{i}"]),
             "acceptedAnswer": {"@type": "Answer", "text": strip_tags(t[f"faq_a{i}"])}}
            for i in range(1, 7)
        ],
    }
    # "</" can't appear inside a script block; escape it
    dump = lambda o: json.dumps(o, ensure_ascii=False, indent=2).replace("</", "<\\/")
    return (f'<script type="application/ld+json">\n{dump(app)}\n</script>\n'
            f'<script type="application/ld+json">\n{dump(faq)}\n</script>')


def redirect_script():
    """Root page only: first-time visitors whose browser prefers another supported language
    are sent there once. Picking a language in the picker stores the choice and stops this."""
    table = {tag: code for code, _, _, tags in LANGS if code != "en" for tag in tags}
    return (
        "<script>\n(function () {\n  try {\n"
        "    if (localStorage.getItem('ng-lang')) return;\n"
        f"    var map = {json.dumps(table)};\n"
        "    var prefs = navigator.languages && navigator.languages.length ? navigator.languages : [navigator.language || 'en'];\n"
        "    var first = String(prefs[0] || 'en').toLowerCase().split('-')[0];\n"
        "    if (first === 'en') return;\n"
        "    for (var i = 0; i < prefs.length; i++) {\n"
        "      var tag = String(prefs[i]).toLowerCase().split('-')[0];\n"
        "      if (tag === 'en') return;\n"
        "      if (map[tag]) { location.replace(map[tag] + '/' + location.hash); return; }\n"
        "    }\n"
        "  } catch (e) {}\n})();\n</script>"
    )


def render(template, code, name, og_locale, en, missing):
    t = dict(en)
    if code != "en":
        own = load(code)
        for k in en:
            if k in own:
                t[k] = own[k]
            else:
                missing.append(f"{code}: {k}")
        for k in own:
            if k not in en:
                print(f"warning: {code}.json has unknown key {k!r}")
    base = "" if code == "en" else "../"
    langs = {c: (base or "./") if c == "en" else f"{base}{c}/" for c, *_ in LANGS}

    hreflang = "\n".join(
        [f'<link rel="alternate" hreflang="{c}" href="{url_for(c)}">' for c, *_ in LANGS]
        + [f'<link rel="alternate" hreflang="x-default" href="{SITE}">'])
    options = "\n".join(
        f'            <option value="{c}"{" selected" if c == code else ""}>{html.escape(n)}</option>'
        for c, n, *_ in LANGS)

    script_t = {k: t[k] for k in SCRIPT_KEYS if k in t}
    values = dict(t)
    values.update(
        lang=code, url=url_for(code), base=base, home=base or "./", fonts=FONTS, og_locale=og_locale,
        analytics=analytics_tag(), hreflang=hreflang, options=options, jsonld=jsonld(t),
        redirect=redirect_script() if code == "en" else "",
        release_meta_static=t["release_meta_static"],
        page_json=json.dumps({"lang": code, "base": base, "langs": langs}, ensure_ascii=False),
        t_json=json.dumps(script_t, ensure_ascii=False).replace("</", "<\\/"),
    )

    def sub(m):
        key, attr = m.group(1), m.group(2)
        if key not in values:
            sys.exit(f"template uses unknown key {key!r}")
        v = values[key]
        return html.escape(v, quote=True) if attr else v

    return re.sub(r"\{\{(\w+)(\|attr)?\}\}", sub, template)


def main():
    template = (Path(__file__).resolve().parent / "template.html").read_text(encoding="utf-8")
    en = load("en")
    missing = []
    for code, name, og_locale, _ in LANGS:
        out = DOCS / "index.html" if code == "en" else DOCS / code / "index.html"
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_text(render(template, code, name, og_locale, en, missing), encoding="utf-8", newline="\n")
        print("wrote", out.relative_to(ROOT))

    today = date.today().isoformat()
    urls = []
    for code, *_ in LANGS:
        alts = "\n".join(
            f'    <xhtml:link rel="alternate" hreflang="{c}" href="{url_for(c)}"/>' for c, *_ in LANGS)
        alts += f'\n    <xhtml:link rel="alternate" hreflang="x-default" href="{SITE}"/>'
        urls.append(f"  <url>\n    <loc>{url_for(code)}</loc>\n    <lastmod>{today}</lastmod>\n{alts}\n  </url>")
    (DOCS / "sitemap.xml").write_text(
        '<?xml version="1.0" encoding="UTF-8"?>\n'
        '<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9" xmlns:xhtml="http://www.w3.org/1999/xhtml">\n'
        + "\n".join(urls) + "\n</urlset>\n", encoding="utf-8", newline="\n")
    print("wrote docs/sitemap.xml")

    if missing:
        print("\nMissing translations (fell back to English):")
        print("\n".join("  " + m for m in missing))
        sys.exit(1)


if __name__ == "__main__":
    main()
