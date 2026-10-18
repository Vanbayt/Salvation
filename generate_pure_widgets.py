#!/usr/bin/env python3
import os
import subprocess

OUT_DIR = "/home/vanbayt/StudioProjects/Salvation/widget_concepts"
os.makedirs(OUT_DIR, exist_ok=True)

# Common SVG icons
ICON_PLAY = """<path d="M10 7.5v15l12-7.5-12-7.5z" fill="currentColor"/>"""
ICON_PAUSE = """<rect x="9" y="7" width="4" height="16" rx="2" fill="currentColor"/><rect x="17" y="7" width="4" height="16" rx="2" fill="currentColor"/>"""
ICON_PREV = """<path d="M7 7h3v16H7zm4 8l10 8V7z" fill="currentColor"/>"""
ICON_NEXT = """<path d="M20 7h-3v16h3zm-4 8L6 7v16z" fill="currentColor"/>"""
ICON_HEART_FILLED = """<path d="M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z" fill="#FF5370"/>"""
ICON_HEART_OUTLINE = """<path d="M16.5 3c-1.74 0-3.41.81-4.5 2.09C10.91 3.81 9.24 3 7.5 3 4.42 3 2 5.42 2 8.5c0 3.78 3.4 6.86 8.55 11.54L12 21.35l1.45-1.32C18.6 15.36 22 12.28 22 8.5 22 5.42 19.58 3 16.5 3zm-4.4 15.55l-.1.1-.1-.1C7.14 14.24 4 11.39 4 8.5 4 6.5 5.5 5 7.5 5c1.54 0 3.04.99 3.57 2.36h1.87C13.46 5.99 14.96 5 16.5 5c2 0 3.5 1.5 3.5 3.5 0 2.89-3.14 5.74-7.9 10.05z" fill="currentColor"/>"""
ICON_SHUFFLE = """<path d="M10.59 9.17L5.41 4 4 5.41l5.17 5.17 1.42-1.41zM14.5 4l2.04 2.04L4 18.59 5.41 20 17.96 7.46 20 9.5V4h-5.5zm.33 9.41l-1.41 1.41 3.13 3.13L14.5 20H20v-5.5l-2.04 2.04-3.13-3.13z" fill="currentColor"/>"""

# ==========================================
# 1. Compact Player (4x1) - 720 x 176 (28dp at 2x = 56px radius)
# ==========================================
def generate_compact_player_4x1():
    svg = f"""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 720 176" width="720" height="176">
  <defs>
    <linearGradient id="progressGrad1" x1="0%" y1="0%" x2="100%" y2="0%">
      <stop offset="0%" stop-color="#7CA8F8"/>
      <stop offset="100%" stop-color="#A8C7FA"/>
    </linearGradient>
    <clipPath id="albumClip1">
      <rect x="28" y="24" width="128" height="128" rx="36" ry="36"/>
    </clipPath>
  </defs>

  <style>
    .f-bold {{ font-family: -apple-system, BlinkMacSystemFont, "Roboto", "Segoe UI", sans-serif; font-weight: 700; }}
    .f-medium {{ font-family: -apple-system, BlinkMacSystemFont, "Roboto", "Segoe UI", sans-serif; font-weight: 500; }}
    .f-mono {{ font-family: "Roboto Mono", "SF Mono", monospace; font-weight: 600; }}
  </style>

  <!-- Widget Card Container (Pure Widget Bounds, rx=56 at 2x / 28dp, surfaceContainerHigh #242830, stroke #3E4552) -->
  <rect x="1" y="1" width="718" height="174" rx="56" fill="#242830" stroke="#3E4552" stroke-width="1.5"/>

  <!-- Squircle Album Art (18dp / 36px corner radius) -->
  <g clip-path="url(#albumClip1)">
    <rect x="28" y="24" width="128" height="128" fill="#1C1B19"/>
    <!-- Stylized NieR Album Cover Graphic -->
    <circle cx="92" cy="88" r="50" fill="#2E2C27"/>
    <circle cx="92" cy="88" r="32" fill="#444139"/>
    <!-- Lunar flower petals -->
    <g fill="#FFFDF2" opacity="0.95">
      <ellipse cx="92" cy="64" rx="7" ry="16"/>
      <ellipse cx="92" cy="112" rx="7" ry="16"/>
      <ellipse cx="68" cy="88" rx="16" ry="7"/>
      <ellipse cx="116" cy="88" rx="16" ry="7"/>
      <ellipse cx="75" cy="71" rx="7" ry="16" transform="rotate(-45 75 71)"/>
      <ellipse cx="109" cy="71" rx="7" ry="16" transform="rotate(45 109 71)"/>
      <ellipse cx="75" cy="105" rx="7" ry="16" transform="rotate(45 75 105)"/>
      <ellipse cx="109" cy="105" rx="7" ry="16" transform="rotate(-45 109 105)"/>
    </g>
    <circle cx="92" cy="88" r="10" fill="#F0E5CF"/>
    <circle cx="92" cy="88" r="4" fill="#6A5F48"/>
  </g>
  <!-- Inner border on art -->
  <rect x="28" y="24" width="128" height="128" rx="36" ry="36" fill="none" stroke="#FFFFFF" stroke-width="1.5" stroke-opacity="0.12"/>

  <!-- Metadata Column -->
  <!-- Track Title -->
  <text x="184" y="54" class="f-bold" font-size="20" fill="#E2E2E9">Alien Manifestation</text>
  
  <!-- FLAC Hi-Res Pill Badge -->
  <rect x="396" y="38" width="52" height="22" rx="11" fill="#182E4F" stroke="#375D94" stroke-width="1"/>
  <circle cx="406" cy="49" r="2.5" fill="#4ADE80"/>
  <text x="414" y="53" class="f-mono" font-size="10.5" fill="#A8C7FA" letter-spacing="0.5">FLAC</text>

  <!-- Artist & Album Subtitle -->
  <text x="184" y="80" class="f-medium" font-size="14.5" fill="#A4A8B5">Keiichi Okabe • NieR:Automata</text>

  <!-- Tactile Progress Bar with Scrub Head -->
  <rect x="184" y="104" width="264" height="7" rx="3.5" fill="#323640"/>
  <rect x="184" y="104" width="100" height="7" rx="3.5" fill="url(#progressGrad1)"/>
  <circle cx="284" cy="107.5" r="6.5" fill="#E2E2E9"/>
  <circle cx="284" cy="107.5" r="3.5" fill="#7CA8F8"/>

  <!-- Timestamps -->
  <text x="184" y="128" class="f-mono" font-size="11.5" fill="#8C919D">01:42</text>
  <text x="448" y="128" text-anchor="end" class="f-mono" font-size="11.5" fill="#8C919D">04:28</text>

  <!-- Interactive Controls Section -->
  <!-- Favorite (Heart) Button -->
  <g transform="translate(484, 88)">
    <circle cx="0" cy="0" r="20" fill="#323640" fill-opacity="0.6"/>
    <g transform="translate(-12, -12)">
      {ICON_HEART_FILLED}
    </g>
  </g>

  <!-- Previous Track Button -->
  <g transform="translate(544, 88)" fill="#E2E2E9">
    <circle cx="0" cy="0" r="20" fill="#282C34"/>
    <g transform="translate(-12, -12)">
      {ICON_PREV}
    </g>
  </g>

  <!-- Primary Circular FAB Play/Pause Button (#A8C7FA) -->
  <g transform="translate(608, 88)">
    <circle cx="0" cy="0" r="32" fill="#A8C7FA"/>
    <rect x="-6" y="-8" width="4" height="16" rx="2" fill="#041E49"/>
    <rect x="2" y="-8" width="4" height="16" rx="2" fill="#041E49"/>
  </g>

  <!-- Next Track Button -->
  <g transform="translate(672, 88)" fill="#E2E2E9">
    <circle cx="0" cy="0" r="20" fill="#282C34"/>
    <g transform="translate(-12, -12)">
      {ICON_NEXT}
    </g>
  </g>
</svg>"""
    return svg

# ==========================================
# 2. Hi-Fi Canvas (4x2) - 720 x 360 (32dp at 2x = 64px radius)
# ==========================================
def generate_hifi_canvas_4x2():
    svg = f"""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 720 360" width="720" height="360">
  <defs>
    <linearGradient id="cardBg2" x1="0%" y1="0%" x2="0%" y2="100%">
      <stop offset="0%" stop-color="#21252D"/>
      <stop offset="100%" stop-color="#16181E"/>
    </linearGradient>
    <linearGradient id="primaryPlay2" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%" stop-color="#B2D3FF"/>
      <stop offset="100%" stop-color="#7CA8F8"/>
    </linearGradient>
    <linearGradient id="progressGrad2" x1="0%" y1="0%" x2="100%" y2="0%">
      <stop offset="0%" stop-color="#7CA8F8"/>
      <stop offset="100%" stop-color="#B2D3FF"/>
    </linearGradient>
    <clipPath id="albumClip2">
      <rect x="32" y="32" width="296" height="296" rx="48" ry="48"/>
    </clipPath>
  </defs>

  <style>
    .f-bold {{ font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; font-weight: 700; }}
    .f-medium {{ font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; font-weight: 500; }}
    .f-mono {{ font-family: "SF Mono", "Roboto Mono", monospace; font-weight: 600; }}
  </style>

  <!-- Pure Widget Card Body (32dp corner radius) -->
  <rect x="1" y="1" width="718" height="358" rx="62" fill="url(#cardBg2)" stroke="#3E4552" stroke-width="1.5" stroke-opacity="0.6"/>

  <!-- Left Side: Large Squircle Album Art (24dp = 48px at 2x) -->
  <g clip-path="url(#albumClip2)">
    <rect x="32" y="32" width="296" height="296" fill="#1C1A17"/>
    <!-- Stylized Atmospheric Vinyl & Lunar Flower Art -->
    <circle cx="180" cy="180" r="120" fill="#2B2721" stroke="#3D372D" stroke-width="2"/>
    <circle cx="180" cy="180" r="88" fill="#1F1D19" stroke="#3D372D" stroke-width="1"/>
    <circle cx="180" cy="180" r="54" fill="#3D372D"/>
    <!-- Floral center -->
    <g fill="#FFFDF5" opacity="0.95">
      <ellipse cx="180" cy="146" rx="10" ry="24"/>
      <ellipse cx="180" cy="214" rx="10" ry="24"/>
      <ellipse cx="146" cy="180" rx="24" ry="10"/>
      <ellipse cx="214" cy="180" rx="24" ry="10"/>
      <ellipse cx="156" cy="156" rx="10" ry="24" transform="rotate(-45 156 156)"/>
      <ellipse cx="204" cy="156" rx="10" ry="24" transform="rotate(45 204 156)"/>
      <ellipse cx="156" cy="204" rx="10" ry="24" transform="rotate(45 156 204)"/>
      <ellipse cx="204" cy="204" rx="10" ry="24" transform="rotate(-45 204 204)"/>
    </g>
    <circle cx="180" cy="180" r="16" fill="#EADBBE"/>
    <circle cx="180" cy="180" r="6" fill="#5E533C"/>

    <!-- Frosted Hi-Res Badge Overlay on Art -->
    <rect x="48" y="48" width="94" height="28" rx="8" fill="#000000" fill-opacity="0.65"/>
    <rect x="48" y="48" width="94" height="28" rx="8" fill="none" stroke="#E5C158" stroke-width="1" stroke-opacity="0.5"/>
    <text x="58" y="67" class="f-bold" font-size="11" fill="#F4D068" letter-spacing="0.5">Hi-Res AUDIO</text>
  </g>
  <rect x="32" y="32" width="296" height="296" rx="48" fill="none" stroke="#FFFFFF" stroke-width="1.5" stroke-opacity="0.1"/>

  <!-- Right Side: Track Metadata & Audiophile Controls -->
  <!-- Track Title -->
  <text x="356" y="74" class="f-bold" font-size="25" fill="#E2E2E9">Weight of the World</text>

  <!-- Artist & Album -->
  <text x="356" y="104" class="f-medium" font-size="17" fill="#A4A8B5">Keiichi Okabe • NieR:Automata OST</text>

  <!-- Technical Audiophile Pill Badge -->
  <rect x="356" y="126" width="276" height="36" rx="18" fill="#14243D" stroke="#2B5188" stroke-width="1.2"/>
  <circle cx="376" cy="144" r="5" fill="#4ADE80"/>
  <text x="390" y="150" class="f-mono" font-size="13" fill="#A8C7FA" letter-spacing="0.5">FLAC 24-bit / 96 kHz • Hi-Res</text>

  <!-- Tactile Progress Bar & Timestamps -->
  <text x="356" y="196" class="f-mono" font-size="14" fill="#A4A8B5">02:45</text>
  <text x="644" y="196" class="f-mono" font-size="14" fill="#8C919D">05:12</text>
  <rect x="356" y="210" width="330" height="8" rx="4" fill="#323640"/>
  <rect x="356" y="210" width="176" height="8" rx="4" fill="url(#progressGrad2)"/>
  <circle cx="532" cy="214" r="8" fill="#E2E2E9"/>

  <!-- Expressive 5-Button Control Bar -->
  <!-- Shuffle -->
  <g transform="translate(356, 252)" fill="#8C919D">
    <circle cx="28" cy="28" r="26" fill="#282C34"/>
    <g transform="translate(16, 16) scale(1)">
      {ICON_SHUFFLE}
    </g>
  </g>

  <!-- Previous -->
  <g transform="translate(426, 252)" fill="#E2E2E9">
    <circle cx="28" cy="28" r="26" fill="#282C34"/>
    <g transform="translate(16, 16) scale(1)">
      {ICON_PREV}
    </g>
  </g>

  <!-- Main Primary Circular Play/Pause FAB -->
  <g transform="translate(494, 244)">
    <circle cx="36" cy="36" r="36" fill="url(#primaryPlay2)"/>
    <g transform="translate(18, 18) scale(1.5)" fill="#06224E">
      {ICON_PAUSE}
    </g>
  </g>

  <!-- Next -->
  <g transform="translate(578, 252)" fill="#E2E2E9">
    <circle cx="28" cy="28" r="26" fill="#282C34"/>
    <g transform="translate(16, 16) scale(1)">
      {ICON_NEXT}
    </g>
  </g>

  <!-- Favorite Like -->
  <g transform="translate(648, 252)" fill="#FF5370">
    <circle cx="28" cy="28" r="26" fill="#323640" fill-opacity="0.8"/>
    <g transform="translate(14, 14) scale(1.2)">
      {ICON_HEART_FILLED}
    </g>
  </g>
</svg>"""
    return svg

# ==========================================
# 3. Karaoke & Synced Lyrics 2.0 (4x3) - 720 x 540 (32dp at 2x = 64px radius)
# ==========================================
def generate_lyrics_karaoke_4x3():
    svg = f"""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 720 540" width="720" height="540">
  <defs>
    <linearGradient id="cardBg3" x1="0%" y1="0%" x2="0%" y2="100%">
      <stop offset="0%" stop-color="#1E222A"/>
      <stop offset="100%" stop-color="#14171D"/>
    </linearGradient>
    <linearGradient id="activePillGrad" x1="0%" y1="0%" x2="100%" y2="0%">
      <stop offset="0%" stop-color="#173562"/>
      <stop offset="100%" stop-color="#1E4785"/>
    </linearGradient>
    <clipPath id="albumClip3">
      <rect x="28" y="24" width="60" height="60" rx="18" ry="18"/>
    </clipPath>
  </defs>

  <style>
    .f-bold {{ font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; font-weight: 700; }}
    .f-medium {{ font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; font-weight: 500; }}
    .f-mono {{ font-family: "SF Mono", "Roboto Mono", monospace; font-weight: 600; }}
  </style>

  <!-- Pure Widget Card Body -->
  <rect x="1" y="1" width="718" height="538" rx="62" fill="url(#cardBg3)" stroke="#3E4552" stroke-width="1.5" stroke-opacity="0.6"/>

  <!-- Top Header Bar: Mini Album Art + Track Info + Mini Controls -->
  <g clip-path="url(#albumClip3)">
    <rect x="28" y="24" width="60" height="60" fill="#2C1B33"/>
    <circle cx="58" cy="54" r="24" fill="#E11D48"/>
    <circle cx="58" cy="54" r="10" fill="#FFE4E6"/>
  </g>
  <rect x="28" y="24" width="60" height="60" rx="18" fill="none" stroke="#FFFFFF" stroke-width="1" stroke-opacity="0.15"/>

  <text x="100" y="48" class="f-bold" font-size="20" fill="#E2E2E9">Blinding Lights</text>
  <rect x="250" y="34" width="56" height="20" rx="6" fill="#182E4F"/>
  <text x="258" y="48" class="f-mono" font-size="10" fill="#A8C7FA">24-96</text>
  <text x="100" y="72" class="f-medium" font-size="14" fill="#A4A8B5">The Weeknd • After Hours</text>

  <!-- Mini Playback Control Pill in Header -->
  <rect x="560" y="32" width="132" height="44" rx="22" fill="#282C34" stroke="#3A404D" stroke-width="1"/>
  <g transform="translate(574, 38)" fill="#E2E2E9">
    <circle cx="16" cy="16" r="16" fill="#A8C7FA"/>
    <g transform="translate(4, 4) scale(1)" fill="#0A2E66">
      {ICON_PAUSE}
    </g>
  </g>
  <g transform="translate(638, 42)" fill="#A4A8B5">
    {ICON_NEXT}
  </g>

  <!-- Hairline Header Divider -->
  <line x1="28" y1="100" x2="692" y2="100" stroke="#303540" stroke-width="1"/>

  <!-- Main Synced Lyrics Body -->
  <!-- Line 1 (Previous Line - Muted) -->
  <g transform="translate(28, 118)">
    <rect x="0" y="0" width="56" height="24" rx="12" fill="#232730"/>
    <text x="12" y="16" class="f-mono" font-size="11" fill="#717684">01:14</text>
    <text x="68" y="18" class="f-medium" font-size="17" fill="#717684">I've been on my own for long enough</text>
  </g>

  <!-- Line 2 (ACTIVE KARAOKE LINE - High Contrast M3 Pill) -->
  <g transform="translate(20, 168)">
    <rect x="0" y="0" width="680" height="136" rx="28" fill="url(#activePillGrad)" stroke="#4A75B5" stroke-width="1.5"/>
    
    <!-- Time Badge & Live Waveform -->
    <rect x="20" y="20" width="60" height="28" rx="14" fill="#0D2344"/>
    <text x="32" y="39" class="f-mono" font-size="12" fill="#A8C7FA">01:22</text>

    <!-- Wave bars -->
    <g transform="translate(90, 24)" fill="#4ADE80">
      <rect x="0" y="6" width="3" height="14" rx="1.5"/>
      <rect x="6" y="2" width="3" height="18" rx="1.5"/>
      <rect x="12" y="8" width="3" height="12" rx="1.5"/>
      <rect x="18" y="0" width="3" height="20" rx="1.5"/>
    </g>

    <!-- Active Lyric Text (With Word Highlight) -->
    <text x="20" y="82" class="f-bold" font-size="24" fill="#FFFFFF">I said, ooh, <tspan fill="#A8C7FA">I'm blinded</tspan> by the lights</text>
    
    <!-- Subtitle Translation Line -->
    <text x="20" y="114" class="f-medium" font-size="15" fill="#C2D8FF">RU «Я сказал: о, я ослеплен этим сиянием огней...»</text>
  </g>

  <!-- Line 3 (Upcoming Line +1) -->
  <g transform="translate(28, 332)">
    <rect x="0" y="0" width="56" height="24" rx="12" fill="#232730"/>
    <text x="12" y="16" class="f-mono" font-size="11" fill="#8C919D">01:28</text>
    <text x="68" y="18" class="f-medium" font-size="18" fill="#A4A8B5">No, I can't sleep until I feel your touch</text>
  </g>

  <!-- Line 4 (Upcoming Line +2) -->
  <g transform="translate(28, 384)">
    <rect x="0" y="0" width="56" height="24" rx="12" fill="#232730"/>
    <text x="12" y="16" class="f-mono" font-size="11" fill="#585D6B">01:35</text>
    <text x="68" y="18" class="f-medium" font-size="18" fill="#585D6B">I said, ooh, I'm drowning in the night</text>
  </g>

  <!-- Bottom Dock: Status Badges -->
  <g transform="translate(28, 462)">
    <rect x="0" y="0" width="168" height="36" rx="18" fill="#14243D" stroke="#2B5188" stroke-width="1"/>
    <circle cx="18" cy="18" r="4" fill="#4ADE80"/>
    <text x="32" y="23" class="f-bold" font-size="11" fill="#A8C7FA" letter-spacing="0.5">KARAOKE SYNC 2.0</text>

    <rect x="180" y="0" width="112" height="36" rx="18" fill="#252A34" stroke="#3A404D" stroke-width="1"/>
    <text x="194" y="23" class="f-mono" font-size="11" fill="#C5C7D2">EN ⇄ RU • ON</text>

    <!-- Progress Scrubber at very bottom of widget -->
    <rect x="312" y="14" width="380" height="6" rx="3" fill="#2C313C"/>
    <rect x="312" y="14" width="190" height="6" rx="3" fill="#7CA8F8"/>
  </g>
</svg>"""
    return svg

# ==========================================
# 4. Contextual Flow & Smart Presets (4x2) - 720 x 360 (32dp at 2x = 64px radius)
# ==========================================
def generate_context_presets_4x2():
    svg = f"""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 720 360" width="720" height="360">
  <defs>
    <linearGradient id="cardBg4" x1="0%" y1="0%" x2="0%" y2="100%">
      <stop offset="0%" stop-color="#22262F"/>
      <stop offset="100%" stop-color="#16181E"/>
    </linearGradient>
    <linearGradient id="flowCtaGrad" x1="0%" y1="0%" x2="100%" y2="0%">
      <stop offset="0%" stop-color="#1D4B8F"/>
      <stop offset="100%" stop-color="#2563EB"/>
    </linearGradient>
    <linearGradient id="metalPillGrad" x1="0%" y1="0%" x2="100%" y2="0%">
      <stop offset="0%" stop-color="#6B1D2F"/>
      <stop offset="100%" stop-color="#991B1B"/>
    </linearGradient>
  </defs>

  <style>
    .f-bold {{ font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; font-weight: 700; }}
    .f-medium {{ font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; font-weight: 500; }}
    .f-mono {{ font-family: "SF Mono", "Roboto Mono", monospace; font-weight: 600; }}
  </style>

  <!-- Pure Widget Card Body -->
  <rect x="1" y="1" width="718" height="358" rx="62" fill="url(#cardBg4)" stroke="#3E4552" stroke-width="1.5" stroke-opacity="0.6"/>

  <!-- Context Header Bar with Signal Chips -->
  <g transform="translate(28, 26)">
    <!-- Time/Mood Chip -->
    <rect x="0" y="0" width="160" height="34" rx="17" fill="#272D3B" stroke="#3D4556" stroke-width="1"/>
    <text x="14" y="22" font-size="14">🌙</text>
    <text x="36" y="22" class="f-medium" font-size="12" fill="#D2D6E2">Ночной эмбиент</text>

    <!-- Weather Chip -->
    <rect x="172" y="0" width="128" height="34" rx="17" fill="#222D3D" stroke="#374863" stroke-width="1"/>
    <text x="184" y="22" font-size="14">🌧️</text>
    <text x="206" y="22" class="f-medium" font-size="12" fill="#A8C7FA">Дождь 14°C</text>

    <!-- Device / Audio Codec Chip -->
    <rect x="312" y="0" width="204" height="34" rx="17" fill="#242B38" stroke="#3D475C" stroke-width="1"/>
    <text x="324" y="22" font-size="14">🎧</text>
    <text x="346" y="22" class="f-mono" font-size="12" fill="#E2E2E9">Sony • LDAC 990k</text>

    <!-- BPM Badge -->
    <rect x="528" y="0" width="136" height="34" rx="17" fill="#1C2E24" stroke="#2B573D" stroke-width="1"/>
    <circle cx="544" cy="17" r="4" fill="#4ADE80"/>
    <text x="556" y="22" class="f-mono" font-size="12" fill="#86EFAC">72 BPM • Спокойно</text>
  </g>

  <!-- Hero CTA Button: Запустить Мою Волну (Expressive Soundwave Pill) -->
  <g transform="translate(28, 80)">
    <rect x="0" y="0" width="664" height="76" rx="38" fill="url(#flowCtaGrad)" stroke="#60A5FA" stroke-width="1.5"/>
    
    <!-- Soundwave Animated Bars Glyph -->
    <g transform="translate(28, 22)" fill="#FFFFFF">
      <rect x="0" y="10" width="5" height="16" rx="2.5"/>
      <rect x="8" y="4" width="5" height="28" rx="2.5"/>
      <rect x="16" y="0" width="5" height="36" rx="2.5"/>
      <rect x="24" y="8" width="5" height="20" rx="2.5"/>
      <rect x="32" y="14" width="5" height="8" rx="2.5"/>
      <rect x="40" y="2" width="5" height="32" rx="2.5"/>
      <rect x="48" y="6" width="5" height="24" rx="2.5"/>
    </g>

    <text x="96" y="46" class="f-bold" font-size="22" fill="#FFFFFF">Запустить Мою Волну</text>
    <text x="350" y="46" class="f-medium" font-size="15" fill="#BFDBFE">• Персональный умный поток</text>

    <!-- Play icon circle on right -->
    <circle cx="616" cy="38" r="24" fill="#FFFFFF"/>
    <g transform="translate(606, 26) scale(1)" fill="#1D4ED8">
      {ICON_PLAY}
    </g>
  </g>

  <!-- Salvation Recommendation Presets (75% Head / 25% Contrast Tail) -->
  <text x="32" y="186" class="f-bold" font-size="13" fill="#8C919D" letter-spacing="1">ДИНАМИЧЕСКИЕ ПРЕСЕТЫ (75/25 АРХИТЕКТУРА)</text>

  <g transform="translate(28, 202)">
    <!-- Preset 1 (Head 75%) -->
    <rect x="0" y="0" width="212" height="120" rx="24" fill="#242832" stroke="#3A404E" stroke-width="1.2"/>
    <rect x="14" y="14" width="40" height="40" rx="12" fill="#182E4F"/>
    <text x="24" y="40" font-size="18">🧠</text>
    <rect x="120" y="14" width="78" height="22" rx="11" fill="#15281E"/>
    <text x="128" y="29" class="f-mono" font-size="10" fill="#4ADE80">98% МАТЧ</text>
    <text x="14" y="78" class="f-bold" font-size="17" fill="#E2E2E9">Deep Focus</text>
    <text x="14" y="100" class="f-medium" font-size="12" fill="#A4A8B5">Ambient • 72 BPM</text>

    <!-- Preset 2 (Head 75%) -->
    <rect x="226" y="0" width="212" height="120" rx="24" fill="#242832" stroke="#3A404E" stroke-width="1.2"/>
    <rect x="240" y="14" width="40" height="40" rx="12" fill="#1E293B"/>
    <text x="250" y="40" font-size="18">🌧️</text>
    <rect x="346" y="14" width="78" height="22" rx="11" fill="#15281E"/>
    <text x="354" y="29" class="f-mono" font-size="10" fill="#4ADE80">95% МАТЧ</text>
    <text x="240" y="78" class="f-bold" font-size="17" fill="#E2E2E9">Rain Ambient</text>
    <text x="240" y="100" class="f-medium" font-size="12" fill="#A4A8B5">Piano Drone • 64 BPM</text>

    <!-- Preset 3 (Contrast Tail 25%) -->
    <rect x="452" y="0" width="212" height="120" rx="24" fill="url(#metalPillGrad)" stroke="#EF4444" stroke-width="1.2"/>
    <rect x="466" y="14" width="40" height="40" rx="12" fill="#450A0A"/>
    <text x="476" y="40" font-size="18">🔥</text>
    <rect x="572" y="14" width="78" height="22" rx="11" fill="#7F1D1D"/>
    <text x="580" y="29" class="f-mono" font-size="10" fill="#FCA5A5">25% ХВОСТ</text>
    <text x="466" y="78" class="f-bold" font-size="17" fill="#FFFFFF">Контраст: Метал</text>
    <text x="466" y="100" class="f-medium" font-size="12" fill="#FECACA">Symphonic • 145 BPM</text>
  </g>
</svg>"""
    return svg

# ==========================================
# 5. Queue & Up Next (4x3) - 720 x 540 (32dp at 2x = 64px radius)
# ==========================================
def generate_queue_upnext_4x3():
    svg = f"""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 720 540" width="720" height="540">
  <defs>
    <linearGradient id="cardBg5" x1="0%" y1="0%" x2="0%" y2="100%">
      <stop offset="0%" stop-color="#21252D"/>
      <stop offset="100%" stop-color="#16181E"/>
    </linearGradient>
    <clipPath id="thumbClipNow">
      <rect x="44" y="40" width="80" height="80" rx="22" ry="22"/>
    </clipPath>
    <clipPath id="thumbClip1"><rect x="44" y="214" width="60" height="60" rx="16" ry="16"/></clipPath>
    <clipPath id="thumbClip2"><rect x="44" y="292" width="60" height="60" rx="16" ry="16"/></clipPath>
    <clipPath id="thumbClip3"><rect x="44" y="370" width="60" height="60" rx="16" ry="16"/></clipPath>
  </defs>

  <style>
    .f-bold {{ font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; font-weight: 700; }}
    .f-medium {{ font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; font-weight: 500; }}
    .f-mono {{ font-family: "SF Mono", "Roboto Mono", monospace; font-weight: 600; }}
  </style>

  <!-- Pure Widget Card Body -->
  <rect x="1" y="1" width="718" height="538" rx="62" fill="url(#cardBg5)" stroke="#3E4552" stroke-width="1.5" stroke-opacity="0.6"/>

  <!-- Top Section: Elevated Mini-Player Card for Currently Playing -->
  <rect x="24" y="24" width="672" height="124" rx="36" fill="#282C36" stroke="#3D4452" stroke-width="1.2"/>
  
  <g clip-path="url(#thumbClipNow)">
    <rect x="44" y="40" width="80" height="80" fill="#31102A"/>
    <circle cx="84" cy="80" r="32" fill="#E11D48"/>
    <circle cx="84" cy="80" r="14" fill="#FFE4E6"/>
  </g>
  <rect x="44" y="40" width="80" height="80" rx="22" fill="none" stroke="#FFFFFF" stroke-width="1" stroke-opacity="0.2"/>

  <!-- Now Playing Titles -->
  <text x="140" y="68" class="f-bold" font-size="21" fill="#E2E2E9">Starboy</text>
  <text x="140" y="92" class="f-medium" font-size="15" fill="#A4A8B5">The Weeknd ft. Daft Punk</text>

  <!-- Progress Bar & Timestamps -->
  <rect x="140" y="112" width="380" height="6" rx="3" fill="#373D4A"/>
  <rect x="140" y="112" width="220" height="6" rx="3" fill="#A8C7FA"/>
  <circle cx="360" cy="115" r="5" fill="#FFFFFF"/>
  <text x="532" y="118" class="f-mono" font-size="12" fill="#8C919D">02:14 / 03:50</text>

  <!-- Play/Pause Button on Current Track -->
  <g transform="translate(620, 56)">
    <circle cx="26" cy="26" r="26" fill="#A8C7FA"/>
    <g transform="translate(14, 14) scale(1)" fill="#06224E">
      {ICON_PAUSE}
    </g>
  </g>

  <!-- Queue Header -->
  <text x="36" y="184" class="f-bold" font-size="13" fill="#8C919D" letter-spacing="1">ДАЛЕЕ В ОЧЕРЕДИ</text>
  <rect x="180" y="168" width="84" height="24" rx="12" fill="#1C2E24"/>
  <text x="194" y="184" class="f-mono" font-size="11" fill="#4ADE80">3 ТРЕКА</text>

  <!-- Queue Row 1 -->
  <g clip-path="url(#thumbClip1)">
    <rect x="44" y="214" width="60" height="60" fill="#1E293B"/>
    <circle cx="74" cy="244" r="20" fill="#3B82F6"/>
  </g>
  <rect x="44" y="214" width="60" height="60" rx="16" fill="none" stroke="#FFFFFF" stroke-width="1" stroke-opacity="0.1"/>
  <text x="120" y="242" class="f-bold" font-size="17" fill="#E2E2E9">Midnight City</text>
  <text x="120" y="262" class="f-medium" font-size="13" fill="#8C919D">M83 • Hurry Up, We're Dreaming</text>
  <text x="572" y="250" class="f-mono" font-size="14" fill="#8C919D">04:03</text>
  <g transform="translate(636, 230)" fill="#A8C7FA">
    <circle cx="16" cy="16" r="16" fill="#262A34"/>
    <g transform="translate(4, 4) scale(1)">{ICON_PLAY}</g>
  </g>
  <line x1="44" y1="284" x2="676" y2="284" stroke="#2B303C" stroke-width="1"/>

  <!-- Queue Row 2 -->
  <g clip-path="url(#thumbClip2)">
    <rect x="44" y="292" width="60" height="60" fill="#2E1065"/>
    <circle cx="74" cy="322" r="20" fill="#A855F7"/>
  </g>
  <rect x="44" y="292" width="60" height="60" rx="16" fill="none" stroke="#FFFFFF" stroke-width="1" stroke-opacity="0.1"/>
  <text x="120" y="320" class="f-bold" font-size="17" fill="#E2E2E9">Instant Crush</text>
  <text x="120" y="340" class="f-medium" font-size="13" fill="#8C919D">Daft Punk ft. Julian Casablancas</text>
  <text x="572" y="328" class="f-mono" font-size="14" fill="#8C919D">05:37</text>
  <g transform="translate(636, 308)" fill="#A8C7FA">
    <circle cx="16" cy="16" r="16" fill="#262A34"/>
    <g transform="translate(4, 4) scale(1)">{ICON_PLAY}</g>
  </g>
  <line x1="44" y1="362" x2="676" y2="362" stroke="#2B303C" stroke-width="1"/>

  <!-- Queue Row 3 -->
  <g clip-path="url(#thumbClip3)">
    <rect x="44" y="370" width="60" height="60" fill="#701A75"/>
    <circle cx="74" cy="400" r="20" fill="#EC4899"/>
  </g>
  <rect x="44" y="370" width="60" height="60" rx="16" fill="none" stroke="#FFFFFF" stroke-width="1" stroke-opacity="0.1"/>
  <text x="120" y="398" class="f-bold" font-size="17" fill="#E2E2E9">Nightcall</text>
  <text x="120" y="418" class="f-medium" font-size="13" fill="#8C919D">Kavinsky • Drive Original Soundtrack</text>
  <text x="572" y="406" class="f-mono" font-size="14" fill="#8C919D">04:19</text>
  <g transform="translate(636, 386)" fill="#A8C7FA">
    <circle cx="16" cy="16" r="16" fill="#262A34"/>
    <g transform="translate(4, 4) scale(1)">{ICON_PLAY}</g>
  </g>

  <!-- Footer Action Pills -->
  <g transform="translate(36, 458)">
    <rect x="0" y="0" width="220" height="50" rx="25" fill="#272C36" stroke="#3D4454" stroke-width="1.2"/>
    <text x="24" y="32" class="f-bold" font-size="14" fill="#E2E2E9">Открыть очередь (14)</text>

    <rect x="236" y="0" width="168" height="50" rx="25" fill="#272C36" stroke="#3D4454" stroke-width="1.2"/>
    <g transform="translate(254, 13) scale(1)" fill="#A8C7FA">{ICON_SHUFFLE}</g>
    <text x="286" y="32" class="f-bold" font-size="14" fill="#E2E2E9">Перемешать</text>
  </g>
</svg>"""
    return svg

# ==========================================
# 6. Quick Actions & Micro-Widgets (2x1) - 720 x 176 (28dp at 2x = 56px radius)
# ==========================================
def generate_quick_actions_2x1():
    svg = f"""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 720 176" width="720" height="176">
  <defs>
    <linearGradient id="cardBg6" x1="0%" y1="0%" x2="0%" y2="100%">
      <stop offset="0%" stop-color="#242831"/>
      <stop offset="100%" stop-color="#1A1C22"/>
    </linearGradient>
    <linearGradient id="favTileGrad" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%" stop-color="#3B1C23"/>
      <stop offset="100%" stop-color="#541B28"/>
    </linearGradient>
    <linearGradient id="waveTileGrad" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%" stop-color="#1E2A44"/>
      <stop offset="100%" stop-color="#253A68"/>
    </linearGradient>
    <linearGradient id="searchTileGrad" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%" stop-color="#133333"/>
      <stop offset="100%" stop-color="#1B4D4D"/>
    </linearGradient>
    <linearGradient id="albumTileGrad" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%" stop-color="#382C18"/>
      <stop offset="100%" stop-color="#4D3B1D"/>
    </linearGradient>
  </defs>

  <style>
    .f-bold {{ font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; font-weight: 700; }}
    .f-medium {{ font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; font-weight: 500; }}
    .f-mono {{ font-family: "SF Mono", "Roboto Mono", monospace; font-weight: 600; }}
  </style>

  <!-- Pure Widget Card Body (28dp corner radius) -->
  <rect x="1" y="1" width="718" height="174" rx="54" fill="url(#cardBg6)" stroke="#3E4552" stroke-width="1.5" stroke-opacity="0.6"/>

  <!-- 4 Asymmetric Squircle Tiles (Material Expressive) -->
  
  <!-- Tile 1: Любимые треки (Favorites) -->
  <g transform="translate(18, 18)">
    <rect x="0" y="0" width="160" height="140" rx="32" fill="url(#favTileGrad)" stroke="#FF5370" stroke-width="1.2" stroke-opacity="0.6"/>
    <!-- Heart Icon Circle -->
    <circle cx="44" cy="44" r="24" fill="#FF5370" fill-opacity="0.2"/>
    <g transform="translate(24, 24) scale(1.65)">
      {ICON_HEART_FILLED}
    </g>
    <!-- Label -->
    <text x="20" y="100" class="f-bold" font-size="17" fill="#FFE4E8">Любимое</text>
    <text x="20" y="122" class="f-mono" font-size="11" fill="#FF94A6">1 420 треков</text>
  </g>

  <!-- Tile 2: Моя волна (My Wave / Smart Flow) -->
  <g transform="translate(190, 18)">
    <rect x="0" y="0" width="160" height="140" rx="32" fill="url(#waveTileGrad)" stroke="#60A5FA" stroke-width="1.2" stroke-opacity="0.6"/>
    <!-- Wave Icon Circle -->
    <circle cx="44" cy="44" r="24" fill="#3B82F6" fill-opacity="0.25"/>
    <g transform="translate(26, 26)" fill="#93C5FD">
      <rect x="0" y="10" width="4" height="16" rx="2"/>
      <rect x="8" y="4" width="4" height="28" rx="2"/>
      <rect x="16" y="0" width="4" height="36" rx="2"/>
      <rect x="24" y="8" width="4" height="20" rx="2"/>
      <rect x="32" y="14" width="4" height="8" rx="2"/>
    </g>
    <text x="20" y="100" class="f-bold" font-size="17" fill="#DBEAFE">Моя волна</text>
    <text x="20" y="122" class="f-medium" font-size="11" fill="#93C5FD">Умный поток</text>
  </g>

  <!-- Tile 3: Поиск музыки (Music Search) -->
  <g transform="translate(362, 18)">
    <rect x="0" y="0" width="160" height="140" rx="32" fill="url(#searchTileGrad)" stroke="#2DD4BF" stroke-width="1.2" stroke-opacity="0.6"/>
    <!-- Search Magnifier Icon Circle -->
    <circle cx="44" cy="44" r="24" fill="#0D9488" fill-opacity="0.25"/>
    <g transform="translate(30, 30)" fill="#5EEAD4">
      <circle cx="11" cy="11" r="8" fill="none" stroke="#5EEAD4" stroke-width="3"/>
      <line x1="17" y1="17" x2="26" y2="26" stroke="#5EEAD4" stroke-width="3" stroke-linecap="round"/>
    </g>
    <text x="20" y="100" class="f-bold" font-size="17" fill="#CCFBF1">Поиск</text>
    <text x="20" y="122" class="f-medium" font-size="11" fill="#5EEAD4">Радар + голос</text>
  </g>

  <!-- Tile 4: Недавний альбом (Recent Album) -->
  <g transform="translate(534, 18)">
    <rect x="0" y="0" width="168" height="140" rx="32" fill="url(#albumTileGrad)" stroke="#F59E0B" stroke-width="1.2" stroke-opacity="0.6"/>
    <!-- Vinyl Disc Graphic -->
    <circle cx="44" cy="44" r="24" fill="#1C1917" stroke="#78350F" stroke-width="2"/>
    <circle cx="44" cy="44" r="14" fill="#292524"/>
    <circle cx="44" cy="44" r="6" fill="#F59E0B"/>
    <text x="20" y="100" class="f-bold" font-size="17" fill="#FEF3C7">Недавнее</text>
    <text x="20" y="122" class="f-medium" font-size="11" fill="#FCD34D">Продолжить</text>
  </g>
</svg>"""
    return svg

def main():
    generators = [
        ("concept_compact_player_4x1.svg", generate_compact_player_4x1),
        ("concept_hifi_canvas_4x2.svg", generate_hifi_canvas_4x2),
        ("concept_lyrics_karaoke_4x3.svg", generate_lyrics_karaoke_4x3),
        ("concept_context_presets_4x2.svg", generate_context_presets_4x2),
        ("concept_queue_upnext_4x3.svg", generate_queue_upnext_4x3),
        ("concept_quick_actions_2x1.svg", generate_quick_actions_2x1),
    ]

    for filename, gen in generators:
        svg_path = os.path.join(OUT_DIR, filename)
        png_path = os.path.join(OUT_DIR, filename.replace(".svg", ".png"))
        
        svg_content = gen()
        with open(svg_path, "w", encoding="utf-8") as f:
            f.write(svg_content)
        print(f"Generated {svg_path}")

        # Render PNG via resvg
        res = subprocess.run(["resvg", svg_path, png_path], capture_output=True, text=True)
        if res.returncode == 0:
            print(f"Rendered {png_path}")
        else:
            print(f"resvg error for {filename}: {res.stderr}")

if __name__ == "__main__":
    main()
