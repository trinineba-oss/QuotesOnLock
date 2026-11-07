"""Builds res/raw/similar.txt: for each quote/idea, its 10 nearest neighbours (bge-base embeddings), one per author,
no death-themed match for a quote that isn't about death, a small boost for shared subject tags. Keys are Java
String.hashCode() of QuoteData.wordsKey(text), written in base 36."""
import sys,json,unicodedata,numpy as np
emb,quotes,ideas,out=sys.argv[1:5]
V=np.load(emb+'.npy'); items=json.load(open(emb+'.json'))
texts=[];tags=[]
for l in open(quotes,encoding='utf-8'):
    if l.startswith('#') or not l.strip(): continue
    p=l.rstrip('\n').split('|'); texts.append(p[1].replace('\\n','\n')); tags.append(set(x for x in (p[3] if len(p)>3 else '').split(',') if x))
for l in open(ideas,encoding='utf-8'):
    if l.startswith('#') or not l.strip(): continue
    p=l.rstrip('\n').split('|'); texts.append(p[2].replace('\\n','\n')); tags.append(set())
assert len(texts)==len(items)==len(V),(len(texts),len(items),len(V))
OK={'Lu','Ll','Lt','Lm','Lo','Nd'}
def words(t): return ''.join(c for c in t.lower() if unicodedata.category(c) in OK)
def jhash(s):
    h=0
    b=s.encode('utf-16-le')
    for i in range(0,len(b),2): h=(31*h+int.from_bytes(b[i:i+2],'little'))&0xFFFFFFFF
    return h-(1<<32) if h>=1<<31 else h
def b36(n):
    d='0123456789abcdefghijklmnopqrstuvwxyz'; s='-' if n<0 else ''; n=abs(n); r=''
    while True:
        r=d[n%36]+r; n//=36
        if n==0: break
    return s+r
keys=[jhash(words(t)) for t in texts]
def auth(a): return a.split(', ')[0].split(' (')[0]
K=10; lines=[]
S=V@V.T if len(V)<20000 else None
for i in range(len(V)):
    sims=S[i]; order=np.argpartition(-sims,300)[:300]; order=order[np.argsort(-sims[order])]
    cand=[]
    for j in order:
        if j==i or sims[j]>0.97 or keys[j]==keys[i]: continue
        if 'death' in tags[j] and 'death' not in tags[i]: continue
        cand.append((sims[j]+0.02*len(tags[i]&tags[j]),j))
    cand.sort(reverse=True); seen={auth(items[i][2])}; nb=[]
    for s,j in cand:
        a=auth(items[j][2])
        if a in seen: continue
        seen.add(a); nb.append(keys[j])
        if len(nb)==K: break
    lines.append(' '.join(b36(k) for k in [keys[i]]+nb))
with open(out,'w') as f:
    f.write('# More like this: each line is a quote key then its closest matches (Java hashCode of the quote\'s words, base 36). Built by embtools/similar_build.py\n')
    f.write('\n'.join(lines)+'\n')
print(len(lines), len(set(keys)))
