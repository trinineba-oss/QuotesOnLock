import sys,re
sys.path.insert(0,__import__('os').path.dirname(__file__))
from cands import sentences
path,lo,hi,cap=sys.argv[1],int(sys.argv[2]),int(sys.argv[3]),int(sys.argv[4])
start=int(sys.argv[5]) if len(sys.argv)>5 else 0
S=sentences(path)
st=re.compile(r'^(Remember|Do |Be |Let |Never|Nothing|No |Every|Whatever|What |The |A |An |To |Death|Life|Time|It is|Our |Such |You |Your |Take |Look |Consider|Think|Love|Live|Seek|Often|Always|If |When |Whoever|Where|Man|Men|Wise|Learn|Strive|Fear|Hope|Pleasure|Pain|Fortune|Fame|Glory|Truth|Reason|Nature|Virtue|Good|Evil|Happ|Real|True|Few|Much|Little|Great|Short|Long|All |Soon|Even|Those|Some|Others|Many|Most|We |He who|She who|They who|Who |Not |Only|Human|Genius|Art|Mind|Soul|Thought|Wisdom|Knowledge|Solitude|Silence|Friend|Habit|Custom|Youth|Age|Memory|Desire|Will|Power|Self|Wealth|Money|Work|Labour|Leisure|Books|Reading|Nobody|Everyone|Anyone|One |In |There is|There are|That )')
bad=re.compile(r'["“”]|[_*\d\[\]]|\b(said|replied|Mr\.|Mrs\.|Chapter|CHAPTER|Fig\.)\b')
out=[]
for i in range(start,len(S)):
    s=S[i][1]
    if lo<=len(s)<=hi and st.match(s) and not bad.search(s) and s[-1] in '.!?': out.append((i,s))
if len(out)>cap:
    step=len(out)/cap; out=[out[int(k*step)] for k in range(cap)]
for i,s in out: print(f'{i}: {s}')
