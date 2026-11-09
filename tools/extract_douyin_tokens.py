#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
从抖音原包提取可复用的前端设计令牌与字体资源。

用法:
    python tools/extract_douyin_tokens.py --apk "C:/path/aweme_xxx.apk" --out douyin-spec

产出:
    douyin-spec/tokens.json            颜色/间距/圆角/字阶统计
    douyin-spec/fonts/*.otf            包内 res/font/douyin_number_*.otf
    douyin-spec/layouts/*.xml.txt      aapt2 dump xmltree 解码的代表性布局
    app/src/main/res/font/*.otf        直接拷进 App 的字体

依赖: Android SDK build-tools 的 aapt2（脚本自动在 %LOCALAPPDATA%\\Android\\Sdk 下查找）。
APK 里的资源表条目名被混淆成 `_`，所以语义靠「出现次数 + 数值」而不是资源名恢复。
"""
import argparse
import collections
import glob
import json
import os
import re
import shutil
import subprocess
import sys
import zipfile

TARGET_LAYOUTS = [
    # 资源名 -> (r/ 路径)。资源表里少数未混淆的名字，可直接解码成可读布局树。
    ("common_feed_bottom_view_layout", "r/xem.xml"),
    ("tab_indicator", "r/2r.xml"),
]


def find_aapt2():
    base = os.path.join(os.environ.get("LOCALAPPDATA", ""), "Android", "Sdk", "build-tools")
    cands = sorted(glob.glob(os.path.join(base, "*", "aapt2.exe")))
    if not cands:
        sys.exit("aapt2 not found; install Android build-tools")
    return cands[-1]


def dump_resources(aapt2, apk, out_path):
    if os.path.isfile(out_path) and os.path.getsize(out_path) > 1024:
        return out_path
    with open(out_path, "w", encoding="utf-8", errors="replace") as f:
        subprocess.run([aapt2, "dump", "resources", apk], stdout=f, check=True)
    return out_path


def parse_resources(path):
    colors = collections.Counter()
    dimens = collections.Counter()
    styles = collections.Counter()
    files = {}
    cur = None
    resource_id = None
    with open(path, encoding="utf-8", errors="replace") as f:
        for line in f:
            m = re.match(r"^    resource (0x[0-9a-f]+) ([^/]+)/(\S*)\s*$", line)
            if m:
                resource_id, cur, _name = m.groups()
                continue
            m = re.match(r"^      \((.*?)\) (.*)$", line.rstrip("\n"))
            if not m or cur is None:
                continue
            _cfg, val = m.groups()
            if cur == "color" and val.startswith("#"):
                colors[val.lower()] += 1
            elif cur == "dimen":
                dm = re.match(r"([\d.]+)dp", val)
                if dm:
                    dimens[float(dm.group(1))] += 1
            elif cur == "style":
                for sm in re.finditer(r"textSize=([\d.]+)dp", val):
                    styles[round(float(sm.group(1)))] += 1
            if val.startswith('"r/') and resource_id:
                files[resource_id] = val.strip('"')
    return colors, dimens, styles, files


def extract_fonts(apk, out_dir):
    os.makedirs(out_dir, exist_ok=True)
    saved = []
    with zipfile.ZipFile(apk) as z:
        for n in z.namelist():
            if n.startswith("res/font/") and n.lower().endswith((".otf", ".ttf")):
                p = os.path.join(out_dir, os.path.basename(n))
                with open(p, "wb") as f:
                    f.write(z.read(n))
                saved.append(p)
    return saved


def decode_layouts(aapt2, apk, out_dir):
    os.makedirs(out_dir, exist_ok=True)
    made = []
    for name, path in TARGET_LAYOUTS:
        dst = os.path.join(out_dir, name + ".xml.txt")
        with open(dst, "w", encoding="utf-8", errors="replace") as f:
            subprocess.run([aapt2, "dump", "xmltree", "--file", path, apk],
                           stdout=f, stderr=subprocess.STDOUT, check=False)
        made.append(dst)
    return made


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--apk", required=True)
    ap.add_argument("--out", default="douyin-spec")
    ap.add_argument("--app-font-dir", default="app/src/main/res/font")
    args = ap.parse_args()

    os.makedirs(args.out, exist_ok=True)
    aapt2 = find_aapt2()
    dump = dump_resources(aapt2, args.apk, os.path.join(args.out, "resources_dump.txt"))

    colors, dimens, styles, files = parse_resources(dump)
    tokens = {
        "source_apk": os.path.abspath(args.apk),
        "colors_top": colors.most_common(80),
        "spacing_dp_top": dimens.most_common(40),
        "style_text_sizes": styles.most_common(20),
        "note": "资源名在包内已混淆为 '_'，语义由出现次数与数值推定",
    }
    with open(os.path.join(args.out, "tokens.json"), "w", encoding="utf-8") as f:
        json.dump(tokens, f, ensure_ascii=False, indent=1)

    fonts = extract_fonts(args.apk, os.path.join(args.out, "fonts"))
    os.makedirs(args.app_font_dir, exist_ok=True)
    for p in fonts:
        shutil.copy2(p, os.path.join(args.app_font_dir, os.path.basename(p)))

    layouts = decode_layouts(aapt2, args.apk, os.path.join(args.out, "layouts"))

    print("colors:", len(colors), "dimens:", len(dimens))
    print("fonts:", len(fonts), "->", args.app_font_dir)
    print("layouts:", len(layouts))
    print("tokens:", os.path.join(args.out, "tokens.json"))


if __name__ == "__main__":
    main()
