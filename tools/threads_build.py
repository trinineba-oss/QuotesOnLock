import json,sys,numpy as np,onnxruntime as ort
from tokenizers import Tokenizer
R='/home/user/QuotesOnLock/app/src/main/res/raw/'
lines=[l.rstrip('\n') for l in open(R+'quotes.txt',encoding='utf-8') if l.strip() and not l.startswith('#')]
V=np.load('emb/bgeall.npy')
tok=Tokenizer.from_file('emb/bge/tokenizer.json'); tok.enable_truncation(256); tok.enable_padding()
sess=ort.InferenceSession('emb/bge/model.onnx')
def emb(q):
    e=tok.encode_batch(['Represent this sentence for searching relevant passages: '+q]); ids=np.array([x.ids for x in e]);m=np.array([x.attention_mask for x in e])
    o=sess.run(None,{'input_ids':ids,'attention_mask':m,'token_type_ids':np.zeros_like(ids)})[0][:,0,:]; return (o/np.linalg.norm(o))[0]
def auth(a):
    if a.startswith('Alfred, Lord Tennyson'): return 'Alfred, Lord Tennyson'
    return a.split(', ')[0].split(' (')[0].strip()
by={}
for i,l in enumerate(lines):
    c,t,a=l.split('|')[:3]; by.setdefault(auth(a),[]).append(i)
threads=[];cur=None
for l in open('embtools/threads.txt',encoding='utf-8'):
    l=l.rstrip('\n')
    if not l: continue
    k,rest=l.split('|',1)
    if k=='T': cur={'t':rest,'items':[]}; threads.append(cur)
    else: cur['items'].append((k,rest))
out=['# Influences: T|title|subtitle, then N|author|when|quote text|byline and E|why the next one follows (written for the app). Built by tools/threads_build.py']
used=set(); bad=0
for th in threads:
    out.append('T|'+th['t'])
    its=th['items']
    for n,(k,rest) in enumerate(its):
        if k=='E': out.append('E|'+rest); continue
        name,when,sub=rest.split('|')
        if name not in by: print('NO AUTHOR',name); bad+=1; continue
        cand=by[name]
        if sub:
            cand=[i for i in cand if sub.lower() in lines[i].split('|')[1].lower()]
            if not cand: print('NO MATCH',name,sub); bad+=1; continue
            pick=cand[0]
        else:
            ctx=' '.join(r for kk,r in its[max(0,n-1):n+2] if kk=='E')
            q=emb(ctx); cand=[i for i in cand if len(lines[i].split('|')[1])<280 and i not in used] or by[name]
            pick=max(cand,key=lambda i:float(V[i]@q))
        used.add(pick)
        c,t,a=lines[pick].split('|')[:3]
        out.append(f'N|{name}|{when}|{t}|{a}')
open(R+'influences.txt','w',encoding='utf-8').write('\n'.join(out)+'\n')
print('bad',bad,len(out))
