#!/usr/bin/env python3
import os
import subprocess

OUT_DIR = "/home/vanbayt/StudioProjects/Salvation/widget_concepts"
os.makedirs(OUT_DIR, exist_ok=True)

BG_COLOR = "#211F1D"
NOTE_TILE_COLOR = "#4A4643"
BUTTON_COLOR = "#54453E"
ON_SURFACE = "#F5EFEA"
ON_SURFACE_VARIANT = "#C8C2BC"

D_NOTE = "M12,3v10.55c-0.59,-0.34 -1.27,-0.55 -2,-0.55 -2.21,0 -4,1.79 -4,4s1.79,4 4,4 4,-1.79 4,-4V7h4V3h-6z"
D_PLAY = "M8,19L8,5L19,12L8,19Z"
D_PREV = "M5.5,18L5.5,6L7.5,6L7.5,18L5.5,18ZM18.5,18L9.5,12L18.5,6L18.5,18Z"
D_NEXT = "M16.5,18L16.5,6L18.5,6L18.5,18L16.5,18ZM5.5,18L5.5,6L14.5,12L5.5,18Z"
D_SHUFFLE = "M10.59,9.17L5.41,4 4,5.41l5.17,5.17 1.42,-1.41zM14.5,4l2.04,2.04L4,18.59 5.41,20 17.96,7.46 20,9.5V4h-5.5zm.33,9.41l-1.41,1.41 3.13,3.13L14.5,20H20v-5.5l-2.04,2.04-3.13,-3.13z"
D_REPEAT = "M7,22L3,18L7,14L8.4,15.45L6.85,17H17V13H19V19H6.85L8.4,20.55L7,22ZM5,11V5H17.15L15.6,3.45L17,2L21,6L17,10L15.6,8.55L17.15,7H7V11H5Z"

def icon_note(cx, cy, s):
    return f"""<g transform="translate({cx}, {cy}) scale({s}) translate(-11, -12)"><path d="{D_NOTE}" fill="{ON_SURFACE}"/></g>"""

def icon_play(cx, cy, s):
    return f"""<g transform="translate({cx}, {cy}) scale({s}) translate(-13, -12)"><path d="{D_PLAY}" fill="{ON_SURFACE}"/></g>"""

def icon_prev(cx, cy, s):
    return f"""<g transform="translate({cx}, {cy}) scale({s}) translate(-12, -12)"><path d="{D_PREV}" fill="{ON_SURFACE}"/></g>"""

def icon_next(cx, cy, s):
    return f"""<g transform="translate({cx}, {cy}) scale({s}) translate(-12, -12)"><path d="{D_NEXT}" fill="{ON_SURFACE}"/></g>"""

def icon_shuffle(cx, cy, s):
    return f"""<g transform="translate({cx}, {cy}) scale({s}) translate(-12, -12)"><path d="{D_SHUFFLE}" fill="{ON_SURFACE}"/></g>"""

def icon_repeat(cx, cy, s):
    return f"""<g transform="translate({cx}, {cy}) scale({s}) translate(-12, -12)"><path d="{D_REPEAT}" fill="{ON_SURFACE}"/></g>"""

def gen_w1_720():
    return f"""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 720 720" width="720" height="720">
  <defs>
    <style>
      .widget-card {{ fill: {BG_COLOR}; }}
      .tile-note {{ fill: {NOTE_TILE_COLOR}; }}
      .tile-btn {{ fill: {BUTTON_COLOR}; }}
    </style>
  </defs>

  <rect width="720" height="720" rx="112" class="widget-card"/>

  <rect x="48" y="48" width="288" height="288" rx="72" class="tile-note"/>
  {icon_note(192, 192, 7.2)}

  <rect x="384" y="48" width="288" height="288" rx="72" class="tile-btn"/>
  {icon_play(528, 192, 4.4)}

  <rect x="48" y="384" width="288" height="288" rx="72" class="tile-btn"/>
  {icon_prev(192, 528, 4.0)}

  <rect x="384" y="384" width="288" height="288" rx="72" class="tile-btn"/>
  {icon_next(528, 528, 4.0)}
</svg>"""

def gen_w1_360():
    return f"""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 360 360" width="360" height="360">
  <defs>
    <style>
      .widget-card {{ fill: {BG_COLOR}; }}
      .tile-note {{ fill: {NOTE_TILE_COLOR}; }}
      .tile-btn {{ fill: {BUTTON_COLOR}; }}
    </style>
  </defs>

  <rect width="360" height="360" rx="56" class="widget-card"/>

  <rect x="24" y="24" width="144" height="144" rx="36" class="tile-note"/>
  {icon_note(96, 96, 3.6)}

  <rect x="192" y="24" width="144" height="144" rx="36" class="tile-btn"/>
  {icon_play(264, 96, 2.2)}

  <rect x="24" y="192" width="144" height="144" rx="36" class="tile-btn"/>
  {icon_prev(96, 264, 2.0)}

  <rect x="192" y="192" width="144" height="144" rx="36" class="tile-btn"/>
  {icon_next(264, 264, 2.0)}
</svg>"""

def gen_w2():
    return f"""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 720 176" width="720" height="176">
  <defs>
    <style>
      .widget-card {{ fill: {BG_COLOR}; }}
      .tile-note {{ fill: {NOTE_TILE_COLOR}; }}
      .tile-btn {{ fill: {BUTTON_COLOR}; }}
      .txt-title {{ font-family: "Google Sans", "Product Sans", "Roboto", -apple-system, sans-serif; font-size: 30px; font-weight: 700; fill: {ON_SURFACE}; }}
      .txt-subtitle {{ font-family: "Google Sans", "Product Sans", "Roboto", -apple-system, sans-serif; font-size: 22px; font-weight: 500; fill: {ON_SURFACE_VARIANT}; }}
    </style>
  </defs>

  <rect width="720" height="176" rx="56" class="widget-card"/>

  <rect x="28" y="38" width="100" height="100" rx="36" class="tile-note"/>
  {icon_note(78, 88, 2.6)}

  <text x="152" y="80" class="txt-title">PixelPlayer</text>
  <text x="152" y="114" class="txt-subtitle">Tap to open</text>

  <rect x="396" y="44" width="88" height="88" rx="36" class="tile-btn"/>
  {icon_prev(440, 88, 1.7)}

  <rect x="500" y="44" width="88" height="88" rx="44" class="tile-btn"/>
  {icon_play(544, 88, 1.8)}

  <rect x="604" y="44" width="88" height="88" rx="36" class="tile-btn"/>
  {icon_next(648, 88, 1.7)}
</svg>"""

def gen_w3():
    return f"""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 720 360" width="720" height="360">
  <defs>
    <style>
      .widget-card {{ fill: {BG_COLOR}; }}
      .tile-note {{ fill: {NOTE_TILE_COLOR}; }}
      .tile-btn {{ fill: {BUTTON_COLOR}; }}
      .txt-title {{ font-family: "Google Sans", "Product Sans", "Roboto", -apple-system, sans-serif; font-size: 38px; font-weight: 700; fill: {ON_SURFACE}; }}
      .txt-subtitle {{ font-family: "Google Sans", "Product Sans", "Roboto", -apple-system, sans-serif; font-size: 26px; font-weight: 500; fill: {ON_SURFACE_VARIANT}; }}
    </style>
  </defs>

  <rect width="720" height="360" rx="56" class="widget-card"/>

  <rect x="32" y="32" width="156" height="156" rx="40" class="tile-note"/>
  {icon_note(110, 110, 4.4)}

  <text x="216" y="94" class="txt-title">PixelPlayer</text>
  <text x="216" y="138" class="txt-subtitle">Tap to open</text>

  <rect x="30" y="224" width="120" height="104" rx="36" class="tile-btn"/>
  {icon_shuffle(90, 276, 1.8)}

  <rect x="165" y="224" width="120" height="104" rx="36" class="tile-btn"/>
  {icon_prev(225, 276, 1.8)}

  <rect x="300" y="224" width="120" height="104" rx="42" class="tile-btn"/>
  {icon_play(360, 276, 1.9)}

  <rect x="435" y="224" width="120" height="104" rx="36" class="tile-btn"/>
  {icon_next(495, 276, 1.8)}

  <rect x="570" y="224" width="120" height="104" rx="36" class="tile-btn"/>
  {icon_repeat(630, 276, 1.8)}
</svg>"""

with open(f"{OUT_DIR}/concept_pixel_square_2x2.svg", "w") as f: f.write(gen_w1_720())
with open(f"{OUT_DIR}/concept_pixel_square_2x2_360.svg", "w") as f: f.write(gen_w1_360())
with open(f"{OUT_DIR}/concept_pixel_compact_4x1.svg", "w") as f: f.write(gen_w2())
with open(f"{OUT_DIR}/concept_pixel_standard_4x2.svg", "w") as f: f.write(gen_w3())

subprocess.run(["resvg", f"{OUT_DIR}/concept_pixel_square_2x2.svg", f"{OUT_DIR}/concept_pixel_square_2x2.png"], check=True)
subprocess.run(["resvg", f"{OUT_DIR}/concept_pixel_square_2x2_360.svg", f"{OUT_DIR}/concept_pixel_square_2x2_360.png"], check=True)
subprocess.run(["resvg", f"{OUT_DIR}/concept_pixel_compact_4x1.svg", f"{OUT_DIR}/concept_pixel_compact_4x1.png"], check=True)
subprocess.run(["resvg", f"{OUT_DIR}/concept_pixel_standard_4x2.svg", f"{OUT_DIR}/concept_pixel_standard_4x2.png"], check=True)

print("Generated refined pixel widgets successfully.")
