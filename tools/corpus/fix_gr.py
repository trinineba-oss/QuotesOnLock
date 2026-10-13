import re
def norm(t):
    t=t.replace('“','"').replace('”','"').replace('’',"'").replace('‘',"'")
    t=re.sub(r'\s*--\s*','—',t)
    t=re.sub(r'\s+[–-]\s+',' — ',t)
    t=re.sub(r'\s+',' ',t).strip()
    return t
drop_start={'frankl':['Was Du erlebst'],
 'pessoa':['My soul is impatient with itself, as with a bothersome child; its restlessness keeps growing and is forever the same. Everything interests me, but nothing holds me. I attend'],
 'weil':['The world is the closed door. It is a barrier. And at the same time it is the way through. Two']}
subs={'frankl':[('unecessarily','unnecessarily')],
 'pessoa':[('eyes.Only','eyes. Only'),('himself.The','himself. The')],
 'borges':[(re.compile(r'\"\[As attributed.*$'),'')],
 'weil':[('"I am suffering," than to say, "This landscape is ugly.','"I am suffering," than to say, "This landscape is ugly."'),
         (re.compile(r'through\.Two.*$'),'through.')],
}
extra={'pessoa':[
 ('Fernando Pessoa (as Álvaro de Campos)','I am nothing.\\nI\'ll never be anything.\\nI couldn\'t want to be something.\\nApart from that, I have in me all the dreams in the world.'),
 ('Fernando Pessoa (as Álvaro de Campos)',"Look, there's no metaphysics on earth like chocolates."),
 ('Fernando Pessoa (as Ricardo Reis)','To be great, be whole;\\nExclude nothing, exaggerate nothing that is not you.\\nBe whole in everything. Put all you are\\nInto the smallest thing you do.\\nSo, in each lake, the moon shines with splendor\\nBecause it blooms up above.'),
]}
import sys
for k in ['frankl','pessoa','borges','cioran','weil']:
    out=[]
    for l in open(f'out/{k}.raw',encoding='utf-8'):
        cat,t,au=l.rstrip('\n').split('|')
        t=norm(t) if '\\n' not in t else t
        for s in subs.get(k,[]):
            if isinstance(s[0],str): t=t.replace(*s)
            else: t=s[0].sub(s[1],t)
        t=norm(t) if '\\n' not in t else t
        t=t.replace('...','…') if False else t
        if any(t.startswith(p) for p in drop_start.get(k,[])): continue
        out.append((cat,t,au))
    for au,t in extra.get(k,[]): out.append((k,t,au))
    seen=set()
    with open(f'out/{k}.out','w',encoding='utf-8') as f:
        for cat,t,au in out:
            assert 30<=len(t)<=700 and '|' not in t,(k,t)
            assert t not in seen; seen.add(t)
            f.write(f'{cat}|{t}|{au}\n')
    print(k,len(out))
