import json
R='/home/user/QuotesOnLock/app/src/main/res/raw/'
lines=[l.rstrip('\n') for l in open(R+'quotes.txt',encoding='utf-8') if l.strip() and not l.startswith('#')]
ideas=[l.rstrip('\n') for l in open(R+'ideas.txt',encoding='utf-8') if l.strip() and not l.startswith('#')]
def auth(a):
    if a.startswith('Alfred, Lord Tennyson'): return 'Alfred, Lord Tennyson'
    return a.split(', ')[0].split(' (')[0].strip()
tl={}
for l in open(R+'timeline.txt'):
    if l.startswith('#'): continue
    f=l.rstrip('\n').split('|'); tl[f[1]]=int(f[2])
tl['The Upanishads']=-700
places=dict(l.rstrip('\n').split('|') for l in open('embtools/places.txt') if l.strip())
arcs=dict(l.rstrip('\n').split('|',1) for l in open('embtools/themes_arcs.txt') if l.strip())
def era(born):
    y=born+35
    if born>=1800: return f"{(y//10)*10}s"
    if y<0:
        c=(-y-1)//100+1; return f"{c}{'st' if c%10==1 and c!=11 else 'nd' if c%10==2 and c!=12 else 'rd' if c%10==3 and c!=13 else 'th'} century BC"
    c=(y-1)//100+1; return f"{c}{'st' if c%10==1 and c!=11 else 'nd' if c%10==2 and c!=12 else 'rd' if c%10==3 and c!=13 else 'th'} century"
out=['# One idea, many hands: T|theme|note written for the app, then V|era|place|quote text|byline (quotes exactly as in quotes.txt). Built by tools/themes_build.py']
for l in open('embtools/themes_sel.txt'):
    name,ids=l.strip().split('|')
    out.append(f"T|{name}|{arcs[name]}")
    vs=[]
    for i in map(int,ids.split()):
        if i>=len(lines): raise SystemExit(f'idea index {i} in {name}')
        c,t,a=lines[i].split('|')[:3]
        n=auth(a); b=tl[n]
        vs.append((b,f"V|{era(b)}|{places[n]}|{t}|{a}"))
    vs.sort(key=lambda v:v[0])
    out+= [v for _,v in vs]
open(R+'themes.txt','w').write('\n'.join(out)+'\n')
print(len(out))
