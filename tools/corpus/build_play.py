"""build_play.py <src> <picks> <cat> <author>; pick = snippet|nlines[|start marker|end marker]"""
import sys,re
src,picks,cat,au=sys.argv[1:5]
L=[l.rstrip() for l in open(src,encoding='utf-8').read().replace('\r','').split('\n')]
norm=lambda s:s.replace('’',"'").replace('‘',"'").lower()
for spec in open(picks,encoding='utf-8'):
    spec=spec.rstrip('\n')
    if not spec.strip(): continue
    parts=spec.split('|'); snip=parts[0]; n=int(parts[1]); st=parts[2] if len(parts)>2 else ''; en=parts[3] if len(parts)>3 else ''; verse=len(parts)>4 and parts[4]=='v'
    idx=[i for i,l in enumerate(L) if norm(snip) in norm(l)]
    if not idx: sys.stderr.write('NOTFOUND '+snip+'\n'); continue
    if len(idx)>1: sys.stderr.write(f'MULTI({len(idx)}) {snip[:40]} lines {[x+1 for x in idx][:5]}\n')
    j=idx[0]; out=[]
    while len(out)<n and j<len(L):
        l=L[j].strip(); j+=1
        if not l: break
        out.append(l.strip('_ ').replace('_',''))
    if st or en:
        t=('\\n' if verse else ' ').join(out)
        if st: t=t[norm(t).index(norm(st)):]
        if en: t=t[:norm(t).index(norm(en))+len(en)]
        t=t.strip()
        if t[:1].islower(): t=t[0].upper()+t[1:]
    else:
        t='\\n'.join(out)
    t=t.replace('’',"'").replace('‘',"'").replace('|','/')
    print(f'{cat}|{t}|{au}')
