# Embeds every quote and idea with BAAI/bge-base-en-v1.5 (ONNX, CLS pooling): similar_embed.py <model dir> <quotes.txt> <out prefix>
# then: similar_build.py <out prefix> quotes.txt ideas.txt res/raw/similar.txt
import sys,numpy as np,onnxruntime as ort
from tokenizers import Tokenizer
M=sys.argv[1]; Q=sys.argv[2]; OUT=sys.argv[3]
tok=Tokenizer.from_file(M+'/tokenizer.json'); tok.enable_truncation(256); tok.enable_padding()
sess=ort.InferenceSession(M+'/model.onnx')
items=[]
for l in open(Q,encoding='utf-8'):
    if l.startswith('#') or not l.strip(): continue
    p=l.rstrip('\n').split('|'); items.append((p[0],p[1].replace('\\n',' '),p[2]))
I='/home/user/QuotesOnLock/app/src/main/res/raw/ideas.txt'
for l in open(I,encoding='utf-8'):
    if l.startswith('#') or not l.strip(): continue
    p=l.rstrip('\n').split('|'); items.append((p[0],p[1]+'. '+p[2]+' '+p[4],p[3]))
vecs=[]
B=128
for i in range(0,len(items),B):
    enc=tok.encode_batch([t for _,t,_ in items[i:i+B]])
    ids=np.array([e.ids for e in enc],dtype=np.int64); m=np.array([e.attention_mask for e in enc],dtype=np.int64)
    out=sess.run(None,{'input_ids':ids,'attention_mask':m,'token_type_ids':np.zeros_like(ids)})[0]
    v=out[:,0,:].copy(); v/=np.linalg.norm(v,axis=1,keepdims=True); vecs.append(v.astype(np.float32))
    if i%2560==0: print(i,file=sys.stderr)
np.save(OUT+'.npy',np.concatenate(vecs))
import json; json.dump(items,open(OUT+'.json','w'))
print(len(items))
