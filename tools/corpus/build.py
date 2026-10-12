"""build.py <source.txt> <picks.txt> <category> <author label>  -> prints category|text|author lines"""
import re, sys, json, os
sys.path.insert(0, os.path.dirname(__file__))
from cands import sentences

MANUAL = {'From the military school of life.\u2014': '', ' \u2014Fourth question of conscience.': '', 'is! amor fati': 'is: amor fati',
          'NEVER FORGET!\u2014': ''}

def decap(t):
    """Turn SHOUTING words (editorial emphasis or headings) into normal case."""
    def fix(m):
        w = m.group(0)
        return w if w == 'I' else w.lower()
    t2 = re.sub(r"\b[A-Z]{2,}(?:[-'\u2019][A-Z]+)*\b", fix, t)
    if t2 != t:
        t2 = re.sub(r'(?<=[a-z] )A(?= [a-z])', 'a', t2)
    if t2 != t and t2[:1].islower():
        t2 = t2[:1].upper() + t2[1:]
    return t2

def clean(t):
    for k, v in MANUAL.items():
        t = t.replace(k, v)
    t = t.replace('_', '')
    for k, v in MANUAL.items():
        t = t.replace(k, v)
    t = decap(t)
    t = t.replace('--', '—').replace('_', '')
    t = re.sub(r'\s+', ' ', t).strip()
    t = t.replace('|', '/')
    if t.count('"') % 2 == 1:
        if t.endswith('"') and not t.startswith('"'): t = t[:-1].rstrip()
        elif t.startswith('"') and not t.endswith('"'): t = t[1:].lstrip()
    if t.count('\u201c') != t.count('\u201d'):
        if t.endswith('\u201d') and t.count('\u201d') > t.count('\u201c'): t = t[:-1].rstrip()
        elif t.startswith('\u201c') and t.count('\u201c') > t.count('\u201d'): t = t[1:].lstrip()
    t = t.replace('\u2018', '')
    t = re.sub(r'([,;:]) (And|For|But|So|Let|Even|That|Which|Yet|Or|Nor|Then)\b', lambda m: m.group(1) + ' ' + m.group(2).lower(), t)
    t = re.sub(r'[,;:]$', '.', t)
    t = re.sub(r'\s+([,.;:!?])', r'\1', t)
    return t

def main(src, picks, cat, author):
    sents = sentences(src)
    for line in open(picks):
        if line.lstrip().startswith('#'): continue
        for spec in line.split():
            a, _, b = spec.replace('+', '-').partition('-')
            a = int(a); b = int(b) if b else a
            parts = [s for pi, s in sents[a:b + 1] if '*****' not in s and not s.startswith('[Illustration')]
            text = clean(' '.join(parts))
            print(f'{cat}|{text}|{author}')

if __name__ == '__main__':
    main(*sys.argv[1:5])
