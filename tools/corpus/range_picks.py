"""range_picks.py <src.txt> <spec.txt> > picks.txt
spec line: 'start fragment => end fragment'  (fragments are substrings of source lines; the pick runs from the start
fragment (inside its line) through the end of the end fragment (inside its line), lines joined with literal \\n).
Optional prefix 'N@' on the spec line = start search after the Nth occurrence skip (default first). Verbatim by construction."""
import sys,re
lines=[l.rstrip('\n') for l in open(sys.argv[1],encoding='utf-8',errors='replace')]
for spec in open(sys.argv[2],encoding='utf-8'):
    spec=spec.rstrip('\n')
    if not spec.strip() or spec.startswith('#'): continue
    a,b=spec.split(' => ')
    n=0
    m=re.match(r'^(\d+)@(.*)$',a)
    if m: n=int(m.group(1))-1; a=m.group(2)
    idx=[i for i,l in enumerate(lines) if a in l]
    if len(idx)<=n: sys.stderr.write('START NOT FOUND: '+a+'\n'); continue
    if len(idx)>1 and not m: sys.stderr.write('WARN multiple starts (%d): %s\n'%(len(idx),a))
    i=idx[n]
    j=next((k for k in range(i,len(lines)) if b in lines[k] and (k>i or lines[k].find(b)>=lines[k].find(a)+0)),None)
    if j is None: sys.stderr.write('END NOT FOUND: '+b+'\n'); continue
    seg=lines[i:j+1]
    seg[0]=seg[0][seg[0].find(a):]
    seg[-1]=seg[-1][:seg[-1].find(b)+len(b)] if j>i else seg[-1][:seg[-1].find(b,0)+len(b)]
    out='\\n'.join(s.strip() for s in seg if s.strip())
    print(out)
