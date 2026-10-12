import re,json
t=open('src/pg3330.txt',encoding='utf-8',errors='replace').read().replace('\r','')
a=t.find('CONFUCIAN ANALECTS.'); t=t[a:t.find('*** END OF')]
parts=re.split(r'\n\s*(?=(?:CHAP\.|CHAPTER) [IVXLC]+\.)',t)
out=[]
book=''
for i,p in enumerate(parts):
    p=re.sub(r'\s+',' ',p).strip()
    m=re.search(r'BOOK ([IVXL]+)\.',p)
    if m: book=m.group(1)
    p2=re.sub(r'^(?:CHAP\.|CHAPTER) ([IVXLC]+)\. ','',p)
    out.append((i,book,p2))
json.dump(out,open('src/analects.json','w'))
for i,b,p in out:
    if 70<=len(p)<=420 and p.startswith(("The Master said","1. The Master said")) :
        print(f'{i}: {p}')
