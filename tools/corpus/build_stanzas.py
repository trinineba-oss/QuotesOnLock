"""build_stanzas.py <gutenberg.txt> <picks> <category> <author> -> verse picks with \\n line breaks (literal backslash-n)"""
import re, sys, os
sys.path.insert(0, os.path.dirname(__file__))
from stanzas import stanzas

def main(src, picks, cat, author):
    S = stanzas(src)
    for line in open(picks):
        for spec in line.split():
            a, _, b = spec.replace('+', '-').partition('-')
            a = int(a); b = int(b) if b else a
            blocks = []
            for ls in S[a:b + 1]:
                blocks.append('\\n'.join(ls))
            t = '\\n\\n'.join(blocks)
            t = t.replace('--', '—').replace('_', '').replace('|', '/')
            if len(blocks) == 1 and t.startswith('"') and t.endswith('"'): t = t[1:-1]
            t = re.sub(r'^"', '', t) if t.count('"') % 2 else t
            print(f'{cat}|{t}|{author}')

if __name__ == '__main__':
    main(*sys.argv[1:5])
