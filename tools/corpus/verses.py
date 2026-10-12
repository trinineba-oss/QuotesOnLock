import re,sys,json
t=open(sys.argv[1],encoding='utf-8',errors='replace').read().replace('\r','')
a=t.find('Chapter I.'); 
body=t[a:]
body=body[:body.find('*** END OF')]
paras=re.split(r'\n\s*\n',body)
verses={}
for p in paras:
    p=re.sub(r'\s+',' ',p).strip()
    m=re.match(r'^(\d{1,3})\. (.*)$',p)
    if m: verses[int(m.group(1))]=m.group(2)
json.dump(verses,open(sys.argv[1]+'.verses.json','w'))
lo,hi=int(sys.argv[2]),int(sys.argv[3])
for n in range(lo,hi+1):
    if n in verses: print(f'{n}: {verses[n][:330]}')
