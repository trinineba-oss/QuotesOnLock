import re,sys,html,urllib.request,time
def fetch(url):
    r=urllib.request.Request(url,headers={'User-Agent':'Mozilla/5.0'})
    return urllib.request.urlopen(r,timeout=30).read().decode('utf-8','replace')
def parse(h):
    out=[]
    for m in re.finditer(r'<div class="quoteText">(.*?)</div>.*?(?:class="smallText">.*?)?(?:(\d+) likes)',h,re.S):
        blk=m.group(1)
        t=re.split(r'<br\s*/?>\s*&#8213;',blk)[0]
        t=re.sub(r'<[^>]+>','',t); t=html.unescape(t).strip()
        t=re.sub(r'\s+',' ',t)
        out.append((int(m.group(2)),t))
    return out
if __name__=='__main__':
    slug,pages,outf=sys.argv[1],int(sys.argv[2]),sys.argv[3]
    rows=[]
    for p in range(1,pages+1):
        h=fetch(f'https://www.goodreads.com/author/quotes/{slug}?page={p}')
        rows+=parse(h); time.sleep(5)
    with open(outf,'w') as f:
        for l,t in rows: f.write(f'{l}\t{t}\n')
    print(len(rows))
