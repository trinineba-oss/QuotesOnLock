import sys,numpy as np
from PIL import Image, ImageDraw, ImageFilter
im=Image.open(sys.argv[1]).convert('RGB'); W,H=im.size
s=W/1000.0
pts=[(0,545),(100,538),(118,470),(150,442),(200,430),(250,424),(300,430),(332,446),(352,462),(400,440),(450,415),(500,402),(550,396),(600,382),(640,376),(700,386),(735,400),(760,420),(930,420),(945,300),(970,272),(1000,262)]
mask=Image.new('L',(W,H),0); d=ImageDraw.Draw(mask)
poly=[(x*s,y*s) for x,y in pts]+[(W,H),(0,H)]
d.polygon(poly,fill=255)
a=np.asarray(im).astype(np.float32)/255
r,g,b=a[...,0],a[...,1],a[...,2]; l=a.mean(2)
cyp=(g>=b*0.9)&(g>r*1.0)&(l<0.36)
box=np.zeros_like(cyp); box[int(0*s):int(470*s),int(715*s):int(950*s)]=True; box[:int(300*s),int(908*s):]=False
cm=Image.fromarray(((cyp&box)*255).astype(np.uint8))
cm=cm.filter(ImageFilter.MaxFilter(9)).filter(ImageFilter.MinFilter(9)).filter(ImageFilter.MedianFilter(9))
# keep only the big connected blobs: crude, by closing more
cm=cm.filter(ImageFilter.MaxFilter(15)).filter(ImageFilter.MinFilter(15))
m=np.maximum(np.asarray(mask),np.asarray(cm))
# keep only what is joined to the ground (stray dark strokes in the sky are dropped)
bm=Image.fromarray(np.where(m>127,255,0).astype(np.uint8)).copy()
ImageDraw.floodfill(bm,(W//2,H-5),128)
m=np.where(np.asarray(bm)==128,255,0).astype(np.uint8)
# fill holes: the sky is only what joins the top edge; enclosed light strokes in the trees stay foreground
sk=Image.fromarray(np.where(m>0,0,255).astype(np.uint8)).copy()
for x in range(0,W,max(1,W//50)):
    if sk.getpixel((x,2))==255: ImageDraw.floodfill(sk,(x,2),128)
m=np.where(np.asarray(sk)==128,0,255).astype(np.uint8)
mask=Image.fromarray(m).filter(ImageFilter.GaussianBlur(3*s))
mask.save(sys.argv[2]+'_fgmask.png')
red=Image.new('RGB',(W,H),(255,0,0))
prev=Image.composite(Image.blend(im,red,0.45),im,mask).resize((1000,int(1000*H/W)))
prev.save(sys.argv[2]+'_maskprev.jpg',quality=85)
