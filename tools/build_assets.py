#!/usr/bin/env python3
"""
Готовит компактные данные для приложения из открытых источников (скачайте файлы в папку SRC):
  * cross_references.txt   — OpenBible.info (CC BY), https://www.openbible.info/labs/cross-references/
  * TAGNT / TAHOT / TBESG / TBESH — STEPBible-Data (Tyndale House, CC BY 4.0), https://github.com/STEPBible/STEPBible-Data
Результат: app/src/main/assets/{xrefs,words,lexicon}/...
Запуск: python3 tools/build_assets.py SRC_DIR
"""
import collections, os, re, sys

SRC = sys.argv[1]
OUT = os.path.join(os.path.dirname(__file__), '..', 'app', 'src', 'main', 'assets')

OSIS = ["Gen","Exod","Lev","Num","Deut","Josh","Judg","Ruth","1Sam","2Sam","1Kgs","2Kgs","1Chr","2Chr","Ezra","Neh","Esth","Job","Ps","Prov","Eccl","Song","Isa","Jer","Lam","Ezek","Dan","Hos","Joel","Amos","Obad","Jonah","Mic","Nah","Hab","Zeph","Hag","Zech","Mal","Matt","Mark","Luke","John","Acts","Rom","1Cor","2Cor","Gal","Eph","Phil","Col","1Thess","2Thess","1Tim","2Tim","Titus","Phlm","Heb","Jas","1Pet","2Pet","1John","2John","3John","Jude","Rev"]
OSIS_NUM = {n: i + 1 for i, n in enumerate(OSIS)}
STEP = ["Gen","Exo","Lev","Num","Deu","Jos","Jdg","Rut","1Sa","2Sa","1Ki","2Ki","1Ch","2Ch","Ezr","Neh","Est","Job","Psa","Pro","Ecc","Sng","Isa","Jer","Lam","Ezk","Dan","Hos","Jol","Amo","Oba","Jon","Mic","Nam","Hab","Zep","Hag","Zec","Mal","Mat","Mrk","Luk","Jhn","Act","Rom","1Co","2Co","Gal","Eph","Php","Col","1Th","2Th","1Ti","2Ti","Tit","Phm","Heb","Jas","1Pe","2Pe","1Jn","2Jn","3Jn","Jud","Rev"]
STEP_NUM = {n: i + 1 for i, n in enumerate(STEP)}

def w(path, lines):
    path = os.path.join(OUT, path)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8', newline='\n') as f:
        f.write('\n'.join(lines))
        if lines: f.write('\n')

# ---------- cross references ----------
def parse_ref(r):
    a = r.split('-')[0].split('.')
    b = r.split('-')[-1].split('.')
    return OSIS_NUM[a[0]], int(a[1]), int(a[2]), (int(b[2]) if b[1] == a[1] else int(a[2]))

by = collections.defaultdict(list)
for line in open(os.path.join(SRC, 'cross_references.txt'), encoding='utf-8').read().splitlines()[1:]:
    p = line.split('\t')
    if len(p) < 3: continue
    votes = int(p[2])
    if votes <= 0: continue
    fb, fc, fv, _ = parse_ref(p[0])
    tb, tc, tv, te = parse_ref(p[1])
    by[(fb, fc, fv)].append((votes, f"{tb}.{tc}.{tv}" + (f"-{te}" if te > tv else "")))
per_book = collections.defaultdict(list)
total = 0
for (b, c, v), items in sorted(by.items()):
    items.sort(key=lambda x: -x[0])
    seen, out = set(), []
    for _, r in items:
        if r in seen: continue
        seen.add(r); out.append(r)
        if len(out) == 10: break
    per_book[b].append(f"{c}:{v}\t" + ';'.join(out))
    total += len(out)
for b, lines in per_book.items():
    w(f"xrefs/{b}.txt", lines)
print('xrefs', len(per_book), 'books', total, 'refs')

# ---------- lexicons ----------
TAG = re.compile(r'<[^>]+>')
def clean_def(s):
    s = re.sub(r'<BR\s*/?>', '\n', s, flags=re.I)
    s = re.sub(r"<ref='[^']*'>(.*?)</ref>", r'\1', s)
    s = TAG.sub('', s)
    s = re.sub(r'[ \t]+', ' ', s)
    s = re.sub(r'\n\s*\n+', '\n', s).strip()
    return s[:700].replace('\t', ' ').replace('\\', '/').replace('\n', '\\n')

def lexicon(fname, prefix):
    rows = {}
    started = False
    for line in open(os.path.join(SRC, fname), encoding='utf-8-sig'):
        p = line.rstrip('\n').split('\t')
        if not started:
            if p and p[0].startswith('eStrong') and len(p) > 6 and 'Gloss' in p[6]:
                started = True
            continue
        if len(p) < 8 or not p[0].startswith(prefix): continue
        e = p[0].strip(); d = p[1].split(' ')[0].strip(); u = p[2].strip()
        rec = (p[3].strip(), p[4].strip(), p[6].strip(), clean_def(p[7]))
        for k in {e, d, u}:
            if k and k not in rows: rows[k] = rec
    return rows

lex = {}
lex.update(lexicon('tbesg.txt', 'G'))
lex.update(lexicon('tbesh.txt', 'H'))
print('lexicon keys', len(lex))

# ---------- words ----------
def main_strong(s, prefix):
    m = re.findall(r'\{(' + prefix + r'\d+[A-Za-z]?)\}', s)
    if m: return m[0]
    m = re.findall(prefix + r'\d+[A-Za-z]?', s)
    return m[-1] if m else ''

words = collections.defaultdict(lambda: collections.OrderedDict())
used = set()
hits = miss = 0
def add(b, c, v, rec):
    words[b].setdefault((c, v), []).append(rec)

for fn in ['tagnt1.txt', 'tagnt2.txt']:
    for line in open(os.path.join(SRC, fn), encoding='utf-8-sig'):
        m = re.match(r'^([0-9A-Za-z]+)\.(\d+)\.(\d+)#\d+=(\S+)\t', line)
        if not m or m.group(1) not in STEP_NUM: continue
        if 'N' not in m.group(4): continue          # NA27/28 — критический текст
        p = line.rstrip('\n').split('\t')
        if len(p) < 5: continue
        gm = re.match(r'^(.*?)\s*\((.*?)\)$', p[1])
        orig, tr = (gm.group(1), gm.group(2)) if gm else (p[1], '')
        strong = main_strong(p[3].split('=')[0], 'G')
        lemma = p[4].replace('|', '/')
        add(STEP_NUM[m.group(1)], int(m.group(2)), int(m.group(3)),
            '|'.join([orig, tr, p[2].replace('|', '/'), strong, lemma]))
        if strong: used.add(strong)

for fn in ['TAHOT_Gen-Deu.txt', 'TAHOT_Jos-Est.txt', 'TAHOT_Job-Sng.txt', 'TAHOT_Isa-Mal.txt']:
    for line in open(os.path.join(SRC, fn), encoding='utf-8-sig'):
        m = re.match(r'^([0-9A-Za-z]+)\.(\d+)\.(\d+)#\d+=(\S+)\t', line)
        if not m or m.group(1) not in STEP_NUM: continue
        if 'L' not in m.group(4): continue          # Ленинградский кодекс
        p = line.rstrip('\n').split('\t')
        if len(p) < 5: continue
        strong = main_strong(p[4], 'H')
        add(STEP_NUM[m.group(1)], int(m.group(2)), int(m.group(3)),
            '|'.join([p[1].replace('|', '/'), p[2].replace('|', '/'), p[3].replace('|', '/'), strong, '']))
        if strong: used.add(strong)

nwords = 0
for b, vs in words.items():
    lines = []
    for (c, v), recs in vs.items():
        lines.append(f"{c}:{v}\t" + '\t'.join(recs)); nwords += len(recs)
    w(f"words/{b}.txt", lines)
print('words books', len(words), 'words', nwords)

def base(k): return re.sub(r'[A-Za-z]$', '', k)
outlex = []
for k in sorted(used):
    rec = lex.get(k) or lex.get(base(k))
    if rec: hits += 1; outlex.append(f"{k}\t" + '\t'.join(rec))
    else: miss += 1
print('lexicon hits', hits, 'miss', miss)
w('lexicon/lexicon.txt', outlex)
