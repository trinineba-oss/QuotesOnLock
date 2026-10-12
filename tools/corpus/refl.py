"""refl.py <file> <min> <max> [maxout] -> reflective, dialogue-free sentence candidates (index: text)"""
import re, sys
sys.path.insert(0, __import__('os').path.dirname(__file__))
from cands import sentences

ABSTRACT = re.compile(r"\b(life|truth|soul|suffering|pain|love|freedom|free|happiness|happy|death|time|joy|will|meaning|hope|fear|solitude|lonel\w*|spirit|power|wisdom|virtue|God|faith|good|evil|conscience|beauty|duty|nature|reason|knowledge|mankind|humanity|existence|heart|honest\w*|courage|despair|compassion|pity|forgive\w*|sin|grace|dignity|mystery|eternal|eternity|truth|lie|lies|justice|mercy|pride|vanity|desire|passion|meaning|purpose|dream\w*|illusion\w*)\b", re.I)
import os
LOOSE = os.environ.get('LOOSE') == '1'
BAD_STRICT = re.compile(r'["\u201c\u201d]|\b(said|replied|cried|asked|answered|exclaimed|whispered|shouted|muttered)\b|\b(he|she|they|his|her|their|him|them)\b', re.I)
BAD_LOOSE = re.compile(r'["\u201c\u201d]|\b(said|replied|cried|asked|answered|exclaimed|whispered|shouted|muttered|Zarathustra)\b')

def main(path, lo, hi, maxout=400):
    out = []
    for i, (pi, s) in enumerate(sentences(path)):
        if not (lo <= len(s) <= hi): continue
        if not s[0].isupper() or s[-1] not in '.!?': continue
        if (BAD_LOOSE if LOOSE else BAD_STRICT).search(s): continue
        if len(ABSTRACT.findall(s)) < 1: continue
        if re.search(r'[\^|<>{}\[\]_=\\~*]|CHAPTER|BOOK|\d', s): continue
        out.append((i, s))
    return out

if __name__ == '__main__':
    p, lo, hi = sys.argv[1], int(sys.argv[2]), int(sys.argv[3])
    res = main(p, lo, hi)
    mo = int(sys.argv[4]) if len(sys.argv) > 4 else 400
    if len(res) > mo:  # thin evenly
        step = len(res) / mo
        res = [res[int(k * step)] for k in range(mo)]
    for i, s in res: print(f'{i}: {s}')
