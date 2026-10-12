import re,json
t=open('src/pg17405.txt',encoding='utf-8').read().replace('\r','')
a=t.find('I. LAYING PLANS'); t=t[a:t.find('*** END OF')]
paras=re.split(r'\n\s*\n',t)
out=[];ch=''
for p in paras:
    p=re.sub(r'\s+',' ',p).strip()
    m=re.match(r'^([IVX]+)\. [A-Z]',p)
    if m and p.isupper() or re.match(r'^[IVX]+\. [A-Z ŒÆ]+$',p): ch=p.split('.')[0]; continue
    m=re.match(r'^(\d+)\. (.*)$',p)
    if m: out.append((ch,int(m.group(1)),m.group(2)))
json.dump(out,open('src/suntzu.json','w'))
for i,(c,n,p) in enumerate(out):
    if 50<=len(p)<=340: print(f'{i}: {p}')
