"""Split a Gutenberg text into numbered sentence candidates: cands.py <file> [min] [max]"""
import re, sys, json

def body(path):
    t = open(path, encoding='utf-8', errors='replace').read().replace('\r', '')
    a = re.search(r'\*\*\* START OF .*?\*\*\*', t)
    b = re.search(r'\*\*\* END OF .*?\*\*\*', t)
    return t[a.end():b.start()] if a and b else t

def sentences(path):
    out = []
    for pi, para in enumerate(re.split(r'\n\s*\n', body(path))):
        p = re.sub(r'\s+', ' ', para).strip()
        if not p: continue
        parts = re.split(r'(?:(?<=[.!?])|(?<=[.!?]["\u201d\u2019]))\s+(?=["\u201c\u2018]?[A-Z])', p)
        for s in parts: out.append((pi, s.strip()))
    return out

if __name__ == '__main__':
    path = sys.argv[1]
    lo = int(sys.argv[2]) if len(sys.argv) > 2 else 45
    hi = int(sys.argv[3]) if len(sys.argv) > 3 else 300
    sents = sentences(path)
    json.dump(sents, open(path + '.sents.json', 'w'))
    for i, (pi, s) in enumerate(sents):
        if lo <= len(s) <= hi and not re.search(r'[\^|<>{}\[\]_=]', s):
            print(f'{i}: {s}')
