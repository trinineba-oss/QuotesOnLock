"""stanzas.py <gutenberg.txt> [min] [max] -> numbered stanza/paragraph candidates keeping line breaks (as \\n)"""
import re, sys, json
sys.path.insert(0, __import__('os').path.dirname(__file__))
from cands import body

def stanzas(path):
    out = []
    for blk in re.split(r'\n\s*\n', body(path)):
        lines = [re.sub(r'\s+', ' ', l).strip() for l in blk.split('\n')]
        lines = [l for l in lines if l]
        if lines: out.append(lines)
    return out

if __name__ == '__main__':
    path = sys.argv[1]; lo = int(sys.argv[2]) if len(sys.argv) > 2 else 40; hi = int(sys.argv[3]) if len(sys.argv) > 3 else 330
    S = stanzas(path)
    for i, ls in enumerate(S):
        t = ' / '.join(ls)
        if lo <= len(t) <= hi and not re.search(r'[\^<>{}\[\]=\\~]', t):
            print(f'{i}: {t}')
