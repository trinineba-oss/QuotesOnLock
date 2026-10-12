"""build_lines.py <source.txt> <picks.txt> <category> <author label>
Each pick line is verbatim text (literal \\n = line break). Editorial ( ) marks of the translator are dropped; text is
verified (after the same normalisation) to occur in the source. Prints category|text|author."""
import re, sys
def norm(t):
    t = re.sub(r'\[\d+\]', '', t).replace('_', '')
    t = t.replace('--', '—').replace('(', '').replace(')', '')
    return re.sub(r'\s+', ' ', t).strip()
src, picks, cat, author = sys.argv[1:5]
body = norm(open(src, encoding='utf-8', errors='replace').read())
seen = set()
for line in open(picks, encoding='utf-8'):
    line = line.rstrip('\n')
    if not line.strip() or line.startswith('#'): continue
    parts = line.split('\\n')
    if not all(norm(p) in body for p in parts):
        sys.stderr.write('NOT FOUND: ' + line[:90] + '\n'); continue
    t = '\\n'.join(norm(p) for p in parts).replace('|', '/')
    if t in seen: continue
    seen.add(t)
    print(f'{cat}|{t}|{author}')
