"""paras.py <file> [min] [max] -> numbered paragraph candidates from OCR text; also saves <file>.paras.json"""
import re, sys, json

_VOCAB = None
def vocab():
    global _VOCAB
    if _VOCAB is None:
        import glob, os
        v = set()
        here = os.path.dirname(os.path.abspath(__file__))
        for f in glob.glob(os.path.join(here, '..', 'src', 'pg*.txt')):
            v |= set(re.findall(r"[a-z]+", open(f, encoding='utf-8', errors='replace').read().lower()))
        _VOCAB = v
    return _VOCAB

def dehyphenate(t):
    """Join words broken at line ends (compounds are restored by build_paras.COMPOUNDS)."""
    return re.sub(r'([A-Za-z]+)-[ \t]*\n\s*([a-z]+)', r'\1\2', t)

def paras(path):
    t = open(path, encoding='utf-8', errors='replace').read().replace('\r', '')
    t = dehyphenate(t)
    out = []
    for para in re.split(r'\n\s*\n', t):
        p = re.sub(r'\s+', ' ', para).strip()
        if p: out.append(p)
    return out

if __name__ == '__main__':
    path = sys.argv[1]; lo = int(sys.argv[2]) if len(sys.argv) > 2 else 35; hi = int(sys.argv[3]) if len(sys.argv) > 3 else 320
    P = paras(path)
    json.dump(P, open(path + '.paras.json', 'w'))
    for i, p in enumerate(P):
        if lo <= len(p) <= hi and not re.search(r'[\^|<>{}\[\]_=\\~]', p):
            print(f'{i}: {p}')
