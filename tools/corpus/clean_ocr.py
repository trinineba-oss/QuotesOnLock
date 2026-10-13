import re,sys
t=open(sys.argv[1],encoding='utf-8',errors='replace').read()
t=t.replace('^','')
t=re.sub(r'([a-z])-\s*\n\s*([a-z])',r'\1\2',t)
t=re.sub(r'\s+',' ',t)
t=re.sub(r' ([;:?!,.])(?=\s|$|\'|")',r'\1',t)
# explicit OCR / footnote-marker repairs (each checked against the page text)
for a,b in [('grace of the Creator \'','grace of the Creator.\''),('know the Self I have heard','know the Self. I have heard'),('die same tree','the same tree'),('obj ects','objects'),
 ('led by the blind \'','led by the blind.\''),
 ('wealth. " This is','wealth. "This is'),
 ('of that creature, A man','of that creature. A man'),
 ('the mind the reins \'','the mind the reins.\''),
 ('all this is lighted 2.\'','all this is lighted.\''),
 ('without eating 2.','without eating.'),
 ('(devayana/)','(devayana)'),
 ('overcomes grief I am in grief','overcomes grief. I am in grief'),
 ('like that \\ according','like that, according'),
 ('this body * and dispelled','this body and dispelled'),
 ]:
    t=t.replace(a,b)
# page break inside the 'Lead me' verse: drop the interposed footnote + running head, fix OCR 'immortaHty'
t=re.sub(r'(Lead me from the unreal to the real! Lead me) The ascension is a ceremony.*?UPANISHAD\. (from darkness)',r'\1 \2',t)
t=t.replace('immortaHty','immortality')
open(sys.argv[2],'w',encoding='utf-8').write(t)
