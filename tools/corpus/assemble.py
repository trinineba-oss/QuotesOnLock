import re,glob,collections,sys
KAFKA_TR=[
 ("For we are like tree trunks in the snow. They seem to lie smoothly on the surface, and with a little push one should be able to shove them away. No, one cannot, for they are firmly connected to the ground. But see, even that is only apparent.","Franz Kafka, Contemplation (tr. Claude)"),
 ("If one were only an Indian, instantly alert, and on the galloping horse, leaning into the wind, kept quivering briefly again and again over the quivering ground, until one let go of the spurs, for there were no spurs, until one threw away the reins, for there were no reins, and hardly saw the land before one as a smoothly mown heath, already without horse's neck and horse's head.","Franz Kafka, Contemplation (tr. Claude)"),
]
import os
BASE=os.path.join(os.path.dirname(os.path.abspath(__file__)), '')
base_rows=[l.rstrip('\n') for l in open(BASE+'base_quotes.txt',encoding='utf-8') if l.strip() and not l.startswith('#')]
rows=[]
for l in base_rows:
    c,rest=l.split('|',1); t,a=rest.rsplit('|',1); rows.append((c,t,a))
for f in sorted(glob.glob(BASE+'out/*.out')):
    for l in open(f,encoding='utf-8'):
        l=l.rstrip('\n')
        if not l: continue
        c,rest=l.split('|',1); t,a=rest.rsplit('|',1); rows.append((c,t,a))
for t,a in KAFKA_TR: rows.append(('kafka',t,a))
FIX={'Now I say that I know the meaning of my life: To live for God, for my soul.\u2019':'Now I say that I know the meaning of my life: "To live for God, for my soul."','"No," said the priest, "you don\'t need to accept everything as true, you only have to accept it as necessary."':"You don't need to accept everything as true, you only have to accept it as necessary.",
     'He is the realist, who turns his back on the whole he cannot grasp, and busies himself with a fragment."':'He is the realist, who turns his back on the whole he cannot grasp, and busies himself with a fragment.'}
seen=set(); final=[]
for c,t,a in rows:
    t=FIX.get(t,t); t=re.sub(r'\.{3,}$','.',t).strip(); t=re.sub(r'[ \t]+',' ',t).replace('|','/')
    if not t or len(t)>700: print('SKIP',c,len(t)); continue
    k=re.sub(r'\W+','',t.lower())
    if k in seen: print('DUP',t[:50]); continue
    seen.add(k); final.append((c,t,a))
order=['motivation','thought','stoic','kindness','gibran','rilke','nietzsche','dostoevsky','tolstoy','kafka']
final.sort(key=lambda r:order.index(r[0]))
cnt=collections.Counter(c for c,_,_ in final); print(cnt,sum(cnt.values()))
with open(BASE+'out/quotes_final.txt','w',encoding='utf-8') as f:
    f.write("# One quote per line: category|text|author   (a literal \\n in the text is a line break)\n")
    f.write("# Categories: motivation, thought, stoic, kindness, gibran, rilke, nietzsche, dostoevsky, tolstoy, kafka\n")
    for c,t,a in final: f.write(f"{c}|{t}|{a}\n")
