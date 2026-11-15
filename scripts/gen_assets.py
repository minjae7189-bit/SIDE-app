"""SIDE 로고·그림 생성기.

사용법: python3 scripts/gen_assets.py .   (저장소 최상위에서, fonttools 필요)
: 같은 도형 목록으로 SVG(공유용)와 안드로이드 벡터 XML(앱용)을 함께 만든다."""
import os, sys

ROOT = sys.argv[1]
SVG_DIR = os.path.join(ROOT, 'design/assets')
RES = os.path.join(ROOT, 'app/src/main/res')
os.makedirs(SVG_DIR, exist_ok=True)

SUN = '#FFE38A'; LILAC = '#FFD3E0'; WHITE = '#FFFFFF'; INK = '#2A2353'; BODY = '#4A3577'; DEEP = '#1E1840'; CORAL = '#FF8E7A'  # 코랄 노을 테마


def circle_d(cx, cy, r):
    return f'M{cx - r},{cy} a{r},{r} 0 1,0 {2 * r},0 a{r},{r} 0 1,0 {-2 * r},0 Z'


def ellipse_d(cx, cy, rx, ry):
    return f'M{cx - rx},{cy} a{rx},{ry} 0 1,0 {2 * rx},0 a{rx},{ry} 0 1,0 {-2 * rx},0 Z'


def half_d(cx, cy, r):  # 지평선 위 반원
    return f'M{cx - r},{cy} A{r},{r} 0 0,1 {cx + r},{cy} Z'


def arc_d(cx, cy, r):  # 위쪽 반원 호(선)
    return f'M{cx - r},{cy} A{r},{r} 0 0,1 {cx + r},{cy}'


def rect_d(x, y, w, h, rx=0):
    if not rx:
        return f'M{x},{y} h{w} v{h} h{-w} Z'
    return (f'M{x + rx},{y} h{w - 2 * rx} q{rx},0 {rx},{rx} v{h - 2 * rx} q0,{rx} {-rx},{rx} '
            f'h{-(w - 2 * rx)} q{-rx},0 {-rx},{-rx} v{-(h - 2 * rx)} q0,{-rx} {rx},{-rx} Z')


def line_d(x1, y1, x2, y2):
    return f'M{x1},{y1} L{x2},{y2}'


def P(d, fill=None, stroke=None, sw=0, fa=1.0, sa=1.0):
    return dict(d=d, fill=fill, stroke=stroke, sw=sw, fa=fa, sa=sa)


def argb(hex_, a):
    return '#%02X%s' % (round(a * 255), hex_[1:].upper())


def write(name, w, h, shapes, comment, svg=True, res_dir='drawable', vw=None, vh=None, xml=True):
    vw = vw or w; vh = vh or h
    if svg:
        out = [f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {vw} {vh}" width="{vw}" height="{vh}">',
               f'  <!-- {comment} -->']
        for s in shapes:
            a = [f'd="{s["d"]}"']
            a.append(f'fill="{s["fill"]}"' + (f' fill-opacity="{s["fa"]}"' if s['fa'] != 1 else '') if s['fill'] else 'fill="none"')
            if s['stroke']:
                a.append(f'stroke="{s["stroke"]}" stroke-width="{s["sw"]}" stroke-linecap="round" stroke-linejoin="round"'
                         + (f' stroke-opacity="{s["sa"]}"' if s['sa'] != 1 else ''))
            out.append('  <path ' + ' '.join(a) + '/>')
        out.append('</svg>')
        open(os.path.join(SVG_DIR, name + '.svg'), 'w').write('\n'.join(out) + '\n')
    if not xml:
        return
    x = ['<?xml version="1.0" encoding="utf-8"?>', f'<!-- {comment} (design/assets/{name}.svg와 같은 그림) -->',
         '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
         f'    android:width="{w}dp"', f'    android:height="{h}dp"',
         f'    android:viewportWidth="{vw}"', f'    android:viewportHeight="{vh}">']
    for s in shapes:
        x.append('    <path')
        x.append(f'        android:pathData="{s["d"]}"')
        if s['fill']:
            x.append(f'        android:fillColor="{argb(s["fill"], s["fa"])}"')
        if s['stroke']:
            x.append(f'        android:strokeColor="{argb(s["stroke"], s["sa"])}"')
            x.append(f'        android:strokeWidth="{s["sw"]}"')
            x.append('        android:strokeLineCap="round"')
            x.append('        android:strokeLineJoin="round"')
        x[-1] += ' />'
    x.append('</vector>')
    d = os.path.join(RES, res_dir)
    os.makedirs(d, exist_ok=True)
    open(os.path.join(d, name + '.xml'), 'w').write('\n'.join(x) + '\n')


# ───── 로고 표식: 지평선에 반쯤 떠오른 해 + 나란히 뻗은 두 줄의 길 ─────
def mark(ox=0, oy=0, k=1.0, ink=WHITE):
    t = lambda v: round(v * k, 2)
    X = lambda v: round(ox + v * k, 2)
    Y = lambda v: round(oy + v * k, 2)
    return [
        P(arc_d(X(48), Y(52), t(27)), stroke=SUN, sw=t(2.2), sa=0.55),
        P(half_d(X(48), Y(52), t(18)), fill=SUN),
        P(line_d(X(12), Y(52), X(84), Y(52)), stroke=ink, sw=t(3)),
        P(line_d(X(45), Y(52), X(22), Y(90)), stroke=ink, sw=t(4.4)),
        P(line_d(X(51), Y(52), X(74), Y(90)), stroke=ink, sw=t(4.4)),
        P(line_d(X(48), Y(59), X(48), Y(64)), stroke=SUN, sw=t(2.4)),
        P(line_d(X(48), Y(71), X(48), Y(86)), stroke=SUN, sw=t(3.6)),
    ]


write('side_logo_mark', 96, 96, mark(), 'SIDE 로고 표식: 지평선 위로 떠오르는 해와 나란히 뻗은 두 줄의 길')

# ───── 글자 로고: 표식 + 고운바탕 Bold "SIDE"(글자 윤곽을 도형으로 바꿈) ─────
from fontTools.ttLib import TTFont
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen

font = TTFont(os.path.join(RES, 'font/gowun_batang_bold.ttf'))
gs = font.getGlyphSet(); cmap = font.getBestCmap(); upm = font['head'].unitsPerEm
cap = 52  # 글자 높이(대문자 S 기준) 목표
s_glyph = gs[cmap[ord('S')]]
from fontTools.pens.boundsPen import BoundsPen
bp = BoundsPen(gs); s_glyph.draw(bp); capH = bp.bounds[3]
scale = cap / capH
x = 112.0; base = 74.0; track = 0.08 * cap
parts = []
for ch in 'SIDE':
    g = gs[cmap[ord(ch)]]
    pen = SVGPathPen(gs, ntos=lambda v: ('%.2f' % v).rstrip('0').rstrip('.'))
    g.draw(TransformPen(pen, (scale, 0, 0, -scale, x, base)))
    parts.append(pen.getCommands())
    x += g.width * scale + track
logo_w = round(x - track + 6)
write('side_logo', logo_w, 96, mark() + [P(' '.join(parts), fill=WHITE)], 'SIDE 글자 로고: 표식 + 고운바탕 Bold')

# ───── 앱 아이콘(적응형): 배경 + 전경 + 단색 ─────
bg = [P(rect_d(0, 0, 108, 108), fill='#352C66'),
      P(circle_d(54, 57, 46), fill='#FFB48A', fa=0.28),
      P(circle_d(54, 57, 30), fill='#FFB48A', fa=0.22),
      P(rect_d(0, 57, 108, 51), fill='#2A2353')]
write('ic_launcher_background', 108, 108, bg, '앱 아이콘 배경: 노을 하늘과 지평선 아래', svg=False)
write('ic_launcher_foreground', 108, 108, mark(24, 24, 0.625), '앱 아이콘 전경: 로고 표식(안전 영역 안)', svg=False)
mono = [dict(p, fill=('#FFFFFF' if p['fill'] else None), stroke=('#FFFFFF' if p['stroke'] else None), fa=1, sa=1) for p in mark(24, 24, 0.625)]
write('ic_launcher_monochrome', 108, 108, mono, '앱 아이콘 단색(테마 아이콘)', svg=False)
write('side_app_icon', 108, 108, bg + mark(24, 24, 0.625), '앱 아이콘 미리보기(배경+전경)', xml=False)

# ───── 홈 그림: 새벽 길 위의 차, 뒷유리 너머 나란히 앉은 어른과 아이 ─────
H = 250
hero = []
hero += [P(circle_d(x_, y_, r_), fill=WHITE, fa=0.55) for x_, y_, r_ in [(40, 60, 1.6), (96, 120, 1.2), (150, 40, 1.4), (300, 70, 1.6), (340, 150, 1.2), (250, 120, 1.0), (60, 190, 1.0)]]
hero += [P(arc_d(190, H, 128), stroke=SUN, sw=1.5, sa=0.22), P(arc_d(190, H, 98), stroke=SUN, sw=1.5, sa=0.38),
         P(half_d(190, H, 66), fill=SUN)]
hero += [P('M0,250 C40,200 80,214 118,190 C150,172 172,206 196,222 C222,204 250,178 290,196 C330,214 352,200 380,206 L380,250 Z', fill=LILAC, fa=0.14),
         P('M0,250 C30,232 66,238 96,226 C120,218 140,240 160,250 Z', fill=INK, fa=0.9),
         P('M232,250 C262,230 300,222 334,234 C356,242 368,236 380,240 L380,250 Z', fill=INK, fa=0.9)]
hero += [P(rect_d(0, H, 380, 560 - H), fill=INK, fa=0.92),
         P(line_d(0, H, 380, H), stroke=WHITE, sw=1.5, sa=0.4),
         P('M184,250 L196,250 L340,560 L40,560 Z', fill=WHITE, fa=0.05),
         P(line_d(184, H, 40, 560), stroke=WHITE, sw=3, sa=0.7),
         P(line_d(196, H, 340, 560), stroke=WHITE, sw=3, sa=0.7)]
for i in range(5):
    t0, t1 = (i + 0.2) / 5, (i + 0.62) / 5
    y0, y1 = H + 150 * t0 * t0, H + 150 * t1 * t1
    hero.append(P(line_d(190, round(y0, 1), 190, round(y1, 1)), stroke=SUN, sw=round(1.5 + 4 * t1, 1), sa=0.9))
hero += [P(ellipse_d(190, 522, 118, 9), fill='#000000', fa=0.3),
         P(rect_d(100, 494, 36, 26, 5), fill=DEEP), P(rect_d(244, 494, 36, 26, 5), fill=DEEP),
         P('M120,420 L140,362 Q144,352 156,352 L224,352 Q236,352 240,362 L260,420 Z', fill=BODY, stroke=WHITE, sw=3, sa=0.85),
         P('M138,413 L152,371 Q154,364 162,364 L218,364 Q226,364 228,371 L242,413 Z', fill=SUN, fa=0.28),
         P(circle_d(166, 384, 13), fill=INK), P('M147,413 Q149,399 166,399 Q183,399 185,413 Z', fill=INK),
         P(circle_d(212, 391, 9.5), fill=INK), P('M197,413 Q199,403 212,403 Q225,403 227,413 Z', fill=INK),
         P('M96,420 L284,420 Q296,420 296,432 L296,486 Q296,498 284,498 L96,498 Q84,498 84,486 L84,432 Q84,420 96,420 Z', fill=BODY, stroke=WHITE, sw=3, sa=0.85),
         P(rect_d(96, 434, 42, 13, 5), fill=CORAL), P(rect_d(242, 434, 42, 13, 5), fill=CORAL),
         P(rect_d(164, 456, 52, 20, 4), stroke=WHITE, sw=2, sa=0.6)]
write('side_home_hero', 380, 560, hero, '홈 그림: 새벽 길 위의 차, 뒷유리 너머 나란히 앉은 어른과 아이')

# ───── 직업 분야 아이콘(선 그림) ─────
def icon(name, shapes, comment):
    write('side_field_' + name, 48, 48, shapes, comment)

W2 = dict(stroke=WHITE, sw=2.5)
icon('space', [P(ellipse_d(24, 26, 19, 6), stroke=SUN, sw=2.5), P(circle_d(24, 24, 9), fill='#2B2370', **W2), P(circle_d(38, 9, 1.8), fill=SUN), P(circle_d(9, 12, 1.2), fill=WHITE)], '우주·항공')
icon('it', [P(rect_d(14, 14, 20, 20, 3), **W2), P(rect_d(20, 20, 8, 8, 1.5), fill=SUN)] +
     [P(line_d(c, a, c, b), **W2) for c in (19, 24, 29) for a, b in ((8, 13), (35, 40))] +
     [P(line_d(a, c, b, c), **W2) for c in (19, 24, 29) for a, b in ((8, 13), (35, 40))], 'IT·AI')
icon('env', [P('M11,37 C11,19 23,10 38,10 C38,27 29,37 11,37 Z', **W2), P('M11,37 L29,19', stroke=SUN, sw=2.5)], '환경·에너지')
icon('health', [P(rect_d(10, 10, 28, 28, 8), **W2), P(line_d(24, 17, 24, 31), stroke=SUN, sw=3.2), P(line_d(17, 24, 31, 24), stroke=SUN, sw=3.2)], '보건·의료')
icon('media', [P(rect_d(8, 15, 32, 22, 4), **W2), P(line_d(12, 10, 36, 10), **W2), P('M21,21 L30,26 L21,31 Z', fill=SUN, stroke=SUN, sw=1.5)], '콘텐츠·미디어')
icon('public', [P('M8,18 L24,9 L40,18 Z', **W2), P(line_d(8, 39, 40, 39), **W2)] + [P(line_d(c, 22, c, 34), stroke=SUN, sw=2.8) for c in (14, 24, 34)], '사회·공공')
print('done')

# ───── 슬로건 로고: SIDE + "side by side, toward tomorrow" ─────
# "나란히 앉아, 내일을 향해"를 영어로 옮긴 슬로건. 글자는 Instrument Serif Italic(OFL) 윤곽을 도형으로 바꿔 넣어서
# 앱에 글꼴을 추가하지 않는다. 글꼴 파일은 design/fonts/에 둔다.
SLOGAN_FONT = os.path.join(ROOT, 'design/fonts/InstrumentSerif-Italic.ttf')
WORD_FONT = os.path.join(RES, 'font/gowun_batang_bold.ttf')


def text_d(font_path, text, x, base, size=None, cap=None, tracking=0.0):
    """글자 윤곽 경로와 폭. size는 em 크기, cap은 대문자 높이로 크기를 정할 때."""
    f = TTFont(font_path)
    gs, cm = f.getGlyphSet(), f.getBestCmap()
    if cap:
        bp = BoundsPen(gs); gs[cm[ord('H')]].draw(bp)
        sc = cap / bp.bounds[3]
    else:
        sc = size / f['head'].unitsPerEm
    cmds, cx = [], x
    for i, ch in enumerate(text):
        g = gs[cm[ord(ch)]]
        if ch != ' ':
            pen = SVGPathPen(gs, ntos=lambda v: ('%.2f' % v).rstrip('0').rstrip('.'))
            g.draw(TransformPen(pen, (sc, 0, 0, -sc, cx, base)))
            cmds.append(pen.getCommands())
        cx += g.width * sc + (tracking * (size or cap) if i < len(text) - 1 else 0)
    return ' '.join(cmds), cx - x


SLOGAN_A, SLOGAN_B = 'side by side, toward ', 'tomorrow'


def slogan(x, base, size):
    da, wa = text_d(SLOGAN_FONT, SLOGAN_A, x, base, size=size)
    db, wb = text_d(SLOGAN_FONT, SLOGAN_B, x + wa, base, size=size)
    return [P(da, fill=LILAC), P(db, fill=SUN)], wa + wb


# 가로형: 표식 | SIDE, 그 아래 슬로건
_, side_w = text_d(WORD_FONT, 'SIDE', 0, 0, cap=50, tracking=0.08)
word, _ = text_d(WORD_FONT, 'SIDE', 114, 64, cap=50, tracking=0.08)
sl, sl_w = slogan(116, 100, 23)
h_w = round(max(114 + side_w, 116 + sl_w) + 8)
write('side_logo_slogan', h_w, 118, mark(0, 4) + [P(word, fill=WHITE)] + sl,
      '슬로건 로고(가로): 표식 + SIDE + side by side, toward tomorrow')

# 세로형: 표식 위, SIDE, 슬로건 가운데 정렬
SW = 400
_, wv = text_d(WORD_FONT, 'SIDE', 0, 0, cap=64, tracking=0.12)
word_v, _ = text_d(WORD_FONT, 'SIDE', (SW - wv) / 2, 196, cap=64, tracking=0.12)
_, slw = slogan(0, 0, 28)
sl_v, _ = slogan((SW - slw) / 2, 246, 28)
rule = [P(line_d(SW / 2 - 18, 214, SW / 2 + 18, 214), stroke=SUN, sw=2, sa=0.8)]
write('side_logo_stacked', SW, 264, mark((SW - 96 * 1.15) / 2, 0, 1.15) + [P(word_v, fill=WHITE)] + rule + sl_v,
      '슬로건 로고(세로): 표식 + SIDE + 슬로건')

# 스토어·홍보용 아이콘(512): 런처 아이콘은 작아서 글자를 넣지 않고, 큰 아이콘에만 이름과 슬로건을 넣는다.
store = [P(rect_d(0, 0, 512, 512), fill='#352C66'),
         P(circle_d(256, 250, 230), fill='#FFB48A', fa=0.24),
         P(circle_d(256, 250, 150), fill='#FFB48A', fa=0.2),
         P(rect_d(0, 250, 512, 262), fill='#2A2353')]
store += mark(256 - 96 * 1.6 / 2, 250 - 52 * 1.6, 1.6)
_, ws = text_d(WORD_FONT, 'SIDE', 0, 0, cap=72, tracking=0.14)
word_s, _ = text_d(WORD_FONT, 'SIDE', (512 - ws) / 2, 420, cap=72, tracking=0.14)
_, sls = slogan(0, 0, 30)
sl_s, _ = slogan((512 - sls) / 2, 466, 30)
write('side_store_icon', 512, 512, store + [P(word_s, fill=WHITE)] + sl_s, '스토어·홍보용 아이콘(512): 표식 + SIDE + 슬로건', xml=False)
print('slogan done')
