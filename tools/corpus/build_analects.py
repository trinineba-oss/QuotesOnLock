import re,json,sys
d={i:p for i,b,p in json.load(open('src/analects.json'))}
cat,au=sys.argv[2:4]
for tok in open(sys.argv[1]).read().split():
    p=d[int(tok)]
    p=p.split(' BOOK ')[0]
    p=re.sub(r"^\d+\. ","",p)
    p=re.sub(r"^The Master said[:,]? '?","",p)
    p=re.sub(r"\s\d+\. '?"," ",p)
    p=p.strip().rstrip("'").strip()
    p=p.replace('-- ','—').replace('--','—').replace('(','').replace(')','')
    p=re.sub(r'\s+',' ',p)
    p=re.sub(r"'(?=\s|$)","",p) if p.count("'")%2 else p
    if p[:1].islower(): p=p[0].upper()+p[1:]
    print(f'{cat}|{p}|{au}')
