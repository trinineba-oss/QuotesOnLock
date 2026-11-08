import sys,numpy as np
from PIL import Image
crop=Image.open(sys.argv[1]).convert('RGB'); mask=Image.open(sys.argv[2]).convert('L')
W=1600; H=int(crop.size[1]*W/crop.size[0])
c=crop.resize((W,H),Image.LANCZOS); m=mask.resize((W,H),Image.LANCZOS)
a=np.asarray(c).astype(np.float32); mm=np.asarray(m).astype(np.float32)/255
# 1. foreground: the painting with its sky cut out
fg=np.dstack([a,mm*255]).astype(np.uint8)
Image.fromarray(fg,'RGBA').save(sys.argv[3]+'_fg.webp',quality=82,method=6)
# 2. sky: the left 70% of the painting down to the lowest horizon, with hidden parts filled by
#    mirroring the sky above each column's horizon; then joined to its own mirror image so it loops.
x1=int(W*0.70); y1=int(H*0.72)
hard=mm>0.5
sky=a[:y1,:x1].copy()
for x in range(x1):
    col=hard[:y1,x]
    hy=np.argmax(col) if col.any() else y1
    hy=max(hy,int(H*0.25))
    for y in range(hy,y1):
        sy=2*hy-y-1
        if sy<0: sy=(-sy)%hy
        sky[y,x]=sky[sy,x]
# soften the seam rows a little
B=int(x1*0.35); L=x1-B
loop=sky[:,:L].copy()
al=np.linspace(1,0,B)[None,:,None]
loop[:,:B]=al*sky[:,L:L+B]+(1-al)*sky[:,:B]
loop=loop.astype(np.uint8)
Image.fromarray(loop).save(sys.argv[3]+'_sky.webp',quality=80,method=6)
# 3. still (widgets, frames): the original painting
c.save(sys.argv[3]+'_still.jpg',quality=82)
print(W,H,loop.shape)
