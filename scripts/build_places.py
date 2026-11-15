"""문화기반시설 자료 → 앱 장소 데이터(app/src/main/assets/places.json) 변환기.

사용법: python3 scripts/build_places.py <자료 폴더> [저장소 최상위]
  자료 폴더에 있어야 하는 파일
  - 2025 전국 문화기반시설 총람.xlsx                       박물관·미술관·문학관·도서관·문예회관·생활문화센터·지방문화원·문화의집
  - 한국문화정보원_전국 문화 여가 활동 시설(액티비티) 데이터_*.CSV   레저·체육 시설(위도·경도 있음)
  - 한국문화정보원_전국 문화 여가 활동 시설(클래스) 데이터_*.CSV     체험 클래스(읍면동까지만)
  - 전국박물관미술관정보표준데이터.json      박물관·미술관 정확한 좌표, 관람 시간, 휴관일, 어린이 관람료, 소개(있으면 총람보다 우선)
  - 전국공공시설개방정보표준데이터.json      공공시설 개방 정보 중 가족이 들를 만한 것(전시실, 캠핑장, 상시 개방 운동장)

총람에는 좌표가 없어서, 액티비티 자료의 좌표로 '같은 도로명 → 같은 읍면동 → 같은 시군구' 평균 위치를 만들어 대신 쓴다.
그래서 총람·클래스 장소의 거리는 대략값이며 앱에서 "약 ○km"로 보여 준다(approx=1).
(openpyxl 필요)
"""
import csv, json, os, re, sys, unicodedata
from collections import defaultdict

import openpyxl

SRC = sys.argv[1]
ROOT = sys.argv[2] if len(sys.argv) > 2 else os.path.join(os.path.dirname(__file__), '..')
OUT = os.path.join(ROOT, 'app/src/main/assets/places.json')

# 앱의 관심 주제(talk/Places.kt의 Theme과 같은 순서·이름)
THEMES = ['SCIENCE', 'NATURE', 'HISTORY', 'ART', 'PERFORM', 'BOOKS', 'MAKING', 'SPORTS', 'EXPLORE', 'COMMUNITY']
BIT = {t: 1 << i for i, t in enumerate(THEMES)}

SIDO = {'서울': '서울', '부산': '부산', '대구': '대구', '인천': '인천', '광주': '광주', '대전': '대전', '울산': '울산', '세종': '세종',
        '경기': '경기', '강원': '강원', '충청북': '충북', '충북': '충북', '충청남': '충남', '충남': '충남', '전라북': '전북', '전북': '전북',
        '전라남': '전남', '전남': '전남', '경상북': '경북', '경북': '경북', '경상남': '경남', '경남': '경남', '제주': '제주'}


def sido(s):
    s = (s or '').strip()
    for k in sorted(SIDO, key=len, reverse=True):
        if s.startswith(k):
            return SIDO[k]
    return s


def norm(s):
    return re.sub(r'\s+', '', s or '')


# ───── 1. 액티비티(좌표 있음) + 위치 색인 ─────
def find(part, ext):
    """macOS 파일 이름은 한글이 풀어 쓴 형태(NFD)라 글자 비교 전에 맞춘다."""
    for n in os.listdir(SRC):
        c = unicodedata.normalize('NFC', n)
        if part in c and c.lower().endswith(ext):
            return os.path.join(SRC, n)
    raise FileNotFoundError(f'{part}*{ext} 파일이 {SRC}에 없음')


def read_csv(part):
    path = find(part, '.csv')
    for enc in ('utf-8-sig', 'cp949'):
        try:
            with open(path, encoding=enc) as f:
                return list(csv.DictReader(f))
        except UnicodeDecodeError:
            continue
    raise RuntimeError(path)


acts = read_csv('(액티비티)')
acc = defaultdict(lambda: [0.0, 0.0, 0])
for r in acts:
    try:
        lat, lng = float(r['위도']), float(r['경도'])
    except ValueError:
        continue
    if not (33 < lat < 39 and 124 < lng < 132):
        continue
    sd, sg = sido(r['시도 명칭']), norm(r['시군구 명칭'])
    for key in ((sd, sg, 'road', norm(r['도로명 이름'])), (sd, sg, 'dong', norm(r['법정읍면동명칭'])), (sd, sg), (sd,)):
        a = acc[key]; a[0] += lat; a[1] += lng; a[2] += 1
center = {k: (v[0] / v[2], v[1] / v[2]) for k, v in acc.items()}


def locate(sd, sg, road=None, dong=None):
    """도로명 → 읍면동 → 시군구 순서로 평균 위치를 찾는다. 시군구 이름이 '수원시장안구'처럼 붙은 경우도 앞부분으로 다시 찾는다."""
    for g in (sg, re.sub(r'(시|군)[^시군]+구$', r'\1', sg)):
        for key in ((sd, g, 'road', road), (sd, g, 'dong', dong), (sd, g)):
            if None not in key and key in center:
                return center[key], key[2] if len(key) > 2 else 'sigungu'
    return None, None


KID_ACTIVITY = {
    '클라이밍': ['SPORTS', 'EXPLORE'], '볼링장': ['SPORTS'], '스케이트장': ['SPORTS'], '눈썰매': ['SPORTS', 'EXPLORE'],
    '야구연습장': ['SPORTS'], '농구장': ['SPORTS'], '풋살장': ['SPORTS'], '배드민턴장': ['SPORTS'],
    '방탈출': ['EXPLORE'], 'VR': ['EXPLORE', 'SCIENCE'], '서바이벌': ['EXPLORE', 'SPORTS'], '레일바이크': ['EXPLORE', 'NATURE'],
    '짚라인': ['EXPLORE', 'NATURE'], '래프팅': ['EXPLORE', 'NATURE'], '유람선': ['EXPLORE', 'NATURE'],
}

# 분류가 잘못 들어간 유흥·노래방 등은 이름으로 거른다(예: '유람선노래연습장'이 유람선으로 분류됨).
NOT_FOR_KIDS = ('노래연습장', '노래방', '노래클럽', '주점', '호프', '단란', '유흥', '모텔', '나이트', '포차', '술집')

places = []
seen = set()
for r in acts:
    cat = r['카테고리3']
    if cat not in KID_ACTIVITY or any(w in r['시설명'] for w in NOT_FOR_KIDS):
        continue
    try:
        lat, lng = float(r['위도']), float(r['경도'])
    except ValueError:
        continue
    key = (r['시설명'], round(lat, 4), round(lng, 4))
    if key in seen:
        continue
    seen.add(key)
    places.append([r['시설명'].strip(), cat, round(lat, 5), round(lng, 5), 0,
                   sum(BIT[t] for t in KID_ACTIVITY[cat]), (r['도로명주소'] or r['지번주소']).strip()])

# ───── 2. 문화기반시설 총람(주소 → 대략 위치) ─────
SCIENCE_WORDS = ['과학', '천문', '우주', '항공', '로봇', '철도', '자동차', '전기', '에너지', '발명', '기술', '반도체', '정보통신', '컴퓨터', '게임', '의학', '약학', '수학']
NATURE_WORDS = ['자연사', '생태', '곤충', '해양', '바다', '수족관', '식물', '수목', '숲', '동물', '공룡', '화석', '지질', '농업', '갯벌', '나비박물관', '조류']
ART_WORDS = ['미술', '갤러리', '디자인', '사진', '만화', '애니메이션', '그림', '공예', '도자', '아트']
PERFORM_WORDS = ['음악', '악기', '오디오', '축음기', '영화', '공연', '연극', '국악', '소리']
MAKING_WORDS = ['공예', '도자', '목공', '옹기', '한지', '짚풀', '전통', '민속', '체험']
SPORTS_WORDS = ['스포츠', '야구', '축구', '올림픽', '태권도', '씨름', '체육']

SHEETS = {
    # 시트: (종류 표시, 이름 열, 주소 열, 기본 주제)
    '박물관': ('박물관', 5, 6, ['HISTORY']),
    '미술관': ('미술관', 5, 6, ['ART']),
    '문학관': ('문학관', 9, 10, ['BOOKS']),
    '국립도서관': ('도서관', 4, 5, ['BOOKS']),
    '공공도서관': ('도서관', 4, 5, ['BOOKS']),
    '문예회관': ('공연장', 4, 5, ['PERFORM']),
    '생활문화센터': ('생활문화센터', 3, 8, ['MAKING', 'COMMUNITY']),
    '지방문화원': ('문화원', 3, 6, ['HISTORY', 'COMMUNITY']),
    '문화의집': ('문화의집', 3, 4, ['MAKING', 'COMMUNITY']),
}


def themes_for(kind, name, base):
    t = list(base)
    def has(words): return any(w in name for w in words)
    if kind in ('박물관', '미술관'):
        if has(SCIENCE_WORDS): t.append('SCIENCE')
        if has(NATURE_WORDS): t.append('NATURE')
        if has(ART_WORDS): t.append('ART')
        if has(PERFORM_WORDS): t.append('PERFORM')
        if has(MAKING_WORDS): t.append('MAKING')
        if has(SPORTS_WORDS): t.append('SPORTS')
        # 과학관·자연사 박물관은 역사 주제를 빼서 과학·자연 관심과 더 잘 맞게 한다.
        if kind == '박물관' and ('SCIENCE' in t or 'NATURE' in t) and '역사' not in name and '민속' not in name and 'HISTORY' in t:
            t.remove('HISTORY')
    return sorted(set(t), key=THEMES.index)


ROAD = re.compile(r'(\S+?(?:로|길))(?=\s|\d|$)')
wb = openpyxl.load_workbook(find('문화기반시설', '.xlsx'), read_only=True, data_only=True)
stats = defaultdict(lambda: defaultdict(int))
for sheet, (kind, ni, ai, base) in SHEETS.items():
    for r in wb[sheet].iter_rows(min_row=4, values_only=True):
        name = re.sub(r'\s+', ' ', str(r[ni] or '')).strip()
        if '휴관' in name or '폐관' in name:  # 총람에 '(휴관)'으로 표시된 곳은 지금 갈 수 없다
            continue
        addr = re.sub(r'\s+', ' ', str(r[ai] or '')).strip()
        if not name or not addr or name in ('도서관명', '박물관명', '미술관명', '문학관명', '시설명', '문화원명', '생활문화센터명', '문화의 집') or ' ' not in addr:
            continue
        toks = addr.split(' ')
        sd = sido(toks[0])
        sg_parts = []
        for tk in toks[1:3]:
            if re.search(r'(시|군|구)$', tk):
                sg_parts.append(tk)
            else:
                break
        sg = ''.join(sg_parts) or norm(str(r[2] or ''))
        rest = ' '.join(toks[1 + len(sg_parts):])
        m = ROAD.search(rest)
        dong = re.search(r'\(([^,)]+?(?:동|읍|면|가))', addr)
        pos, level = locate(sd, sg, m.group(1) if m else None, dong.group(1) if dong else None)
        stats[sheet][level] += 1
        if not pos:
            continue
        places.append([name, kind, round(pos[0], 5), round(pos[1], 5), 1,
                       sum(BIT[t] for t in themes_for(kind, name, base)), addr[:60]])

# 총람에 없는 국립과학관(과학관은 별도 등록 제도라 총람 박물관 목록에 거의 없음). 위치는 공개된 주소 기준 대략값.
for name, lat, lng, addr in [
    ('국립중앙과학관', 36.3762, 127.3760, '대전광역시 유성구 대덕대로 481'),
    ('국립과천과학관', 37.4364, 127.0057, '경기도 과천시 상하벌로 110'),
    ('국립부산과학관', 35.2049, 129.2119, '부산광역시 기장군 기장읍 동부산관광6로 59'),
    ('국립대구과학관', 35.6900, 128.4528, '대구광역시 달성군 유가읍 테크노대로6길 20'),
    ('국립광주과학관', 35.2280, 126.8510, '광주광역시 북구 첨단과기로 235'),
]:
    places.append([name, '과학관', lat, lng, 1, BIT['SCIENCE'] | BIT['NATURE'] | BIT['EXPLORE'], addr])

# ───── 3. 체험 클래스(읍면동 → 대략 위치) ─────
CLASS_THEMES = {
    '취미미술': ['ART'], '디지털드로잉': ['ART'], '캘리그래피': ['ART'], '사진': ['ART'],
    '악기': ['PERFORM'], '보컬': ['PERFORM'], '댄스': ['PERFORM', 'SPORTS'], '연기/무용/뮤지컬': ['PERFORM'],
    '향/초/비누': ['MAKING'], '가죽/목공/도예': ['MAKING'], '요리/베이킹': ['MAKING'], '플라워/가드닝': ['MAKING', 'NATURE'],
    '레저/액티비티': ['SPORTS', 'EXPLORE'], '반려동물': ['NATURE'],
}
for r in read_csv('(클래스)'):
    cat = (r['카테고리3'] or '').strip()
    if cat not in CLASS_THEMES:
        continue
    pos, level = locate(sido(r['시도 명칭']), norm(r['시군구 명칭']), dong=norm(r['법정읍면동명칭']))
    stats['클래스'][level] += 1
    if not pos:
        continue
    title = re.sub(r'\s+', ' ', r['클래스 타이틀']).strip()[:40]
    places.append([title, '체험 클래스', round(pos[0], 5), round(pos[1], 5), 1,
                   sum(BIT[t] for t in CLASS_THEMES[cat]), f"{cat} · {(r['시군구 주소'] or '').strip()}"[:60]])

# 여기까지는 [이름, 종류, 위도, 경도, 대략, 주제, 설명] 7칸. 아래 운영 정보 칸을 붙인다.
# 8 운영 시간, 9 휴관 안내, 10 요금 안내, 11 매주 쉬는 요일(비트: 월=1, 화=2, … 일=64), 12 소개
places = [r + ['', '', '', 0, ''] for r in places]

WEEKDAYS = '월화수목금토일'
WEEKLY = re.compile(r'(?<![^\s+,(·/])([월화수목금토일])(?:요일)?(?=$|[\s+,(·/])')


def closed_mask(text):
    """'매주 월요일', '월+1월 1일+설 당일', '토+일+공휴일' → 매주 쉬는 요일 비트. '1월 1일'·'당일' 같은 날짜는 빼고 요일만 센다."""
    if not text or '연중무휴' in text:
        return 0
    return sum(1 << WEEKDAYS.index(m.group(1)) for m in WEEKLY.finditer(text.replace('매주', ' ')))


def hours(a, b):
    a, b = (a or '').strip(), (b or '').strip()
    return f'{a}~{b}' if a and b else ''


def won(v):
    v = (v or '').strip()
    if v in ('', '0'):
        return '무료' if v == '0' else ''
    return f'{int(v):,}원' if v.isdigit() else v


def key(name):
    return re.sub(r'[^0-9A-Za-z가-힣]', '', name)


def find_any(part, ext):
    try:
        return find(part, ext)
    except FileNotFoundError:
        return None


# ───── 4. 박물관·미술관 표준데이터(정확한 좌표와 운영 정보) ─────
std_path = find_any('박물관미술관정보표준데이터', '.json')
if std_path:
    std = json.load(open(std_path, encoding='utf-8-sig'))['records']
    std_rows = []
    for r in std:
        try:
            lat, lng = float(r['위도']), float(r['경도'])
        except ValueError:
            continue
        name = r['시설명'].strip()
        kind = '미술관' if any(w in name for w in ('미술관', '갤러리', '아트')) else '과학관' if '과학관' in name else '박물관'
        base = ['ART'] if kind == '미술관' else ['SCIENCE', 'NATURE', 'EXPLORE'] if kind == '과학관' else ['HISTORY']
        child = won(r['어린이관람료'])
        fee = f'어린이 {child}' if child else ''
        intro = re.sub(r'\s+', ' ', r['박물관미술관소개']).strip()[:90]
        std_rows.append([name, kind, round(lat, 5), round(lng, 5), 0,
                         sum(BIT[t] for t in themes_for('박물관' if kind == '과학관' else kind, name, base)),
                         (r['소재지도로명주소'] or r['소재지지번주소']).strip()[:60],
                         hours(r['평일관람시작시각'], r['평일관람종료시각']),
                         re.sub(r'\s+', ' ', r['휴관정보']).strip()[:40],
                         fee, closed_mask(r['휴관정보']), intro])
    # 같은 시설이 두 번 들어 있는 경우(여러 기관이 함께 제공)가 있어 이름이 같으면 한 번만 쓴다.
    uniq = {}
    for r in std_rows:
        uniq.setdefault(key(r[0]), r)
    std_rows = list(uniq.values())
    names = set(uniq)
    before = len(places)

    def same_as_std(p):
        """이름이 같거나, 3km 안에서 한쪽 이름이 다른 쪽에 들어 있으면 같은 곳으로 본다
        (예: 총람 '서울대학교병원 의학박물관' = 표준 '의학박물관')."""
        k = key(p[0])
        if k in names:
            return True
        for r in std_rows:
            rk = key(r[0])
            if len(rk) >= 4 and (rk in k or k in rk) and abs(r[2] - p[2]) < 0.03 and abs(r[3] - p[3]) < 0.035:
                return True
        return False

    # 표준데이터에 같은 곳이 있으면 총람(대략 위치)·보충 과학관 줄은 버린다.
    places = [p for p in places if not (p[1] in ('박물관', '미술관', '과학관') and same_as_std(p))]
    places += std_rows
    print('박물관·미술관 표준데이터', len(std_rows), '곳, 총람·보충 목록에서 겹쳐 뺀 곳', before + len(std_rows) - len(places))

# ───── 5. 공공시설 개방 표준데이터(가족이 들를 만한 것만) ─────
open_path = find_any('공공시설개방정보표준데이터', '.json')
if open_path:
    SPORT_TYPES = ('축구장', '체육관', '다목적경기장', '풋살경기장', '야구장', '농구장', '배드맨턴장', '배드민턴장', '다목적구장', '배구장')
    seen_open, n_open = set(), 0
    for r in json.load(open(open_path, encoding='utf-8-sig'))['records']:
        t = r['개방시설유형구분']
        always = '상시개방' in (r['신청방법구분'] or '')
        if t.startswith('전시실'):
            kind, base = '전시관', ['HISTORY']
        elif t == '캠핑장':
            kind, base = '캠핑장', ['NATURE', 'EXPLORE']
        elif t in SPORT_TYPES and always:
            kind, base = '열린 운동장', ['SPORTS']
        else:
            continue
        try:
            lat, lng = float(r['위도']), float(r['경도'])
        except ValueError:
            continue
        name = r['개방시설명'].strip()
        if (key(name), round(lat, 3), round(lng, 3)) in seen_open:
            continue
        seen_open.add((key(name), round(lat, 3), round(lng, 3)))
        themes = themes_for('박물관', name, base) if kind == '전시관' else base
        fee = '' if kind == '전시관' else ('무료' if r['유료사용여부'] == 'N' else '유료')
        places.append([name, kind, round(lat, 5), round(lng, 5), 0, sum(BIT[x] for x in themes),
                       (r['소재지도로명주소'] or r['소재지지번주소']).strip()[:60],
                       hours(r['평일운영시작시각'], r['평일운영종료시각']),
                       re.sub(r'\s+', ' ', r['휴관일']).strip()[:40], fee, closed_mask(r['휴관일']), ''])
        n_open += 1
    print('공공시설 개방', n_open, '곳')

for k, v in stats.items():
    print(k, dict(v))
data = {'themes': THEMES, 'fields': ['name', 'kind', 'lat', 'lng', 'approx', 'themes', 'detail', 'hours', 'closed', 'fee', 'closedDays', 'intro'], 'places': places}
os.makedirs(os.path.dirname(OUT), exist_ok=True)
with open(OUT, 'w') as f:
    json.dump(data, f, ensure_ascii=False, separators=(',', ':'))
print('장소', len(places), '저장', os.path.normpath(OUT), os.path.getsize(OUT) // 1024, 'KB')
