"""build_paras.py <ocr.txt> <picks> <category> <author> -> category|text|author for OCR paragraph picks"""
import re, sys, os
sys.path.insert(0, os.path.dirname(__file__))
from paras import paras

FIX = {'saye': 'save', 'sav': 'say', 'yeil': 'veil', 'yery': 'very', 'sohe': 'solve', 'avay': 'away', 'awav': 'away',
       'vou': 'you', 'mav': 'may', 'davs': 'days', 'shvness': 'shyness', 'imagmation': 'imagination', 'tor': 'for',
       'sparkle': 'sparkle'}
COMPOUNDS = {'manmade': 'man-made', 'selfdefense': 'self-defense', 'slowfooted': 'slow-footed', 'slowminded': 'slow-minded',
             'blindeyed': 'blind-eyed', 'blindhearted': 'blind-hearted', 'snowwhite': 'snow-white'}
MANUAL = {'before you. you gazed': 'before you, you gazed', 'you do. but it is': 'you do, but it is',
          'that you do not know"?"': 'that you do not know?', 'for Jesus"?"': 'for Jesus?'}

def clean(t):
    t = t.replace("''", '"').replace('``', '"')
    t = re.sub(r"\s*/\s+", ' ', t)
    t = re.sub(r"(^|\s)'1\s", r'\1"I ', t)          # OCR: '1 for I
    t = re.sub(r"\*+'?", '"', t)                    # OCR: *' for an opening quote
    t = re.sub(r'(?<=\w)- (?=[a-z])', '', t)       # leftover hyphenation breaks "long- ing"
    t = re.sub(r"^[^A-Za-z\"“]+", '', t)
    t = re.sub(r"^[B-HJ-Z] (?=[A-Z])", '', t)       # stray OCR capital before a sentence
    t = t.replace(' --', '—')
    t = re.sub(r"\b(" + '|'.join(FIX) + r")\b", lambda m: FIX[m.group(1)], t)
    t = re.sub(r"[`^|]", '', t)
    for k, v in COMPOUNDS.items():
        t = re.sub(r'\b' + k + r'\b', v, t)
    for k, v in MANUAL.items():
        t = t.replace(k, v)
    t = re.sub(r'\s+[/)]\s*$', '', t)
    t = re.sub(r'\s+\)\s+', ' ', t)
    t = re.sub(r'\s+', ' ', t).strip()
    t = re.sub(r'\s+([:;,.!?])', r'\1', t)
    if t.count('"') % 2 == 1:
        if t.endswith('"') and not t.startswith('"'): t = t[:-1].rstrip()
        elif not t.startswith('"'): t = '"' + t if re.search(r'said, I |saying, [A-Z]', t) else t
        else: t += '"'
    t = re.sub(r'"\?$', '?', t)
    t = re.sub(r'[,;:]$', '.', t)
    if not re.search(r'[.!?"”)]$', t): t += '.'
    return t

def main(src, picks, cat, author):
    P = paras(src)
    for line in open(picks):
        for spec in line.split():
            a, _, b = spec.replace('+', '-').partition('-')
            a = int(a); b = int(b) if b else a
            print(f'{cat}|{clean(" ".join(P[a:b + 1]))}|{author}')

if __name__ == '__main__':
    main(*sys.argv[1:5])
