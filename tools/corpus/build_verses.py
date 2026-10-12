"""build_verses.py <verses.json> <picks> <category> <author>  (picks: verse numbers, '+' joins)"""
import sys,json,re
v={int(k):x for k,x in json.load(open(sys.argv[1])).items()}
cat,au=sys.argv[3:5]
for l in open(sys.argv[2]):
    l=l.strip()
    if not l or l.startswith('#'): continue
    t=' '.join(v[int(n)] for n in l.split('+'))
    t=t.replace('--','—').replace('`',"'")
    t=re.sub(r'\s+',' ',t).strip()
    print(f'{cat}|{t}|{au}')
