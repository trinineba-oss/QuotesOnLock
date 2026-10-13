"""diag.py src picks : for each NOT FOUND line, show longest matching prefix and source continuation"""
import re,sys
def norm(t):
    t = re.sub(r'\[\d+\]', '', t).replace('_', '')
    t = t.replace('--', '—').replace('(', '').replace(')', '')
    return re.sub(r'\s+', ' ', t).strip()
body=norm(open(sys.argv[1],encoding='utf-8',errors='replace').read())
for line in open(sys.argv[2],encoding='utf-8'):
    line=line.rstrip('\n')
    if not line.strip() or line.startswith('#'): continue
    for p in line.split('\\n'):
        p=norm(p)
        if p in body: continue
        lo,hi=0,len(p)
        while lo<hi:
            m=(lo+hi+1)//2
            if p[:m] in body: lo=m
            else: hi=m-1
        i=body.find(p[:lo])
        print('PICK :',p[:lo+40].replace('\n',' '))
        print('  ok to',lo,'| mine next:',repr(p[lo:lo+50]),'| src next:',repr(body[i+lo:i+lo+70]))
        break
