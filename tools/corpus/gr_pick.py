"""gr_pick.py <tsv> <prefixes> <category> <author> -> category|text|author (Goodreads text, quotes trimmed, apostrophes straightened)"""
import sys,re
tsv,pre,cat,au=sys.argv[1:5]
rows=[l.rstrip('\n').split('\t',1)[1] for l in open(tsv,encoding='utf-8') if '\t' in l]
norm=lambda s:s.replace('’',"'").replace('‘',"'")
for p in open(pre,encoding='utf-8'):
    p=p.rstrip('\n')
    if not p: continue
    m=[r for r in rows if norm(r).lstrip('“').startswith(norm(p))]
    if not m: sys.stderr.write('MISSING '+p[:50]+'\n'); continue
    t=norm(m[0]).strip('“” ').strip()
    t=re.sub(r'\s+',' ',t).replace('|','/').replace('…','...')
    t=re.sub(r'\.\.\.+',' ... ' if False else '...',t)
    print(f'{cat}|{t}|{au}')
