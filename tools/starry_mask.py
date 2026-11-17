# Sky mask for The Starry Night: white where the sky may swirl, black for the cypress, hills and village.
# starry_mask.py <starry_night.jpg> <out.png> [preview.png]
import sys, numpy as np
from PIL import Image, ImageDraw, ImageFilter
im = Image.open(sys.argv[1]).convert('RGB'); W, H = im.size
s = W / 800.0
# The line of the hills (and the church steeple), traced on an 800-wide copy.
hills = [(0,462),(40,456),(100,452),(200,448),(280,446),(330,440),(380,426),(430,408),(446,404),(450,396),(453,398),(458,404),
         (480,398),(520,400),(560,404),(600,394),(650,380),(700,360),(718,338),(745,326),(770,322),(800,318)]
fg = Image.new('L', (W, H), 0); d = ImageDraw.Draw(fg)
d.polygon([(x*s, y*s) for x, y in hills] + [(W, H), (0, H)], fill=255)
a = np.asarray(im).astype(np.float32) / 255
r, g, b = a[..., 0], a[..., 1], a[..., 2]; l = a.mean(2)
# The cypress: dark, and green or brown rather than blue.
cyp = (l < 0.30) & (b < np.maximum(r, g) * 1.35)
box = np.zeros_like(cyp); box[int(25*s):, int(95*s):int(385*s)] = True
cm = Image.fromarray(((cyp & box) * 255).astype(np.uint8))
cm = cm.filter(ImageFilter.MaxFilter(7)).filter(ImageFilter.MinFilter(7)).filter(ImageFilter.MedianFilter(7))
cm = cm.filter(ImageFilter.MaxFilter(11)).filter(ImageFilter.MinFilter(11))
# The star just right of the cypress's tip is sky, not tree.
cd = ImageDraw.Draw(cm)
cd.ellipse([(172*s, 70*s), (232*s, 142*s)], fill=0)
m = np.maximum(np.asarray(fg), np.asarray(cm))
# Keep only foreground joined to the ground (stray dark strokes in the sky are dropped).
bm = Image.fromarray(np.where(m > 127, 255, 0).astype(np.uint8)).copy()
ImageDraw.floodfill(bm, (W // 2, H - 3), 128)
m = np.where(np.asarray(bm) == 128, 255, 0).astype(np.uint8)
# The sky is whatever is joined to the top edge; holes inside the tree stay still.
sk = Image.fromarray(np.where(m > 0, 0, 255).astype(np.uint8)).copy()
for x in range(0, W, max(1, W // 60)):
    if sk.getpixel((x, 1)) == 255: ImageDraw.floodfill(sk, (x, 1), 128)
sky = np.where(np.asarray(sk) == 128, 255, 0).astype(np.uint8)
# Pull the sky edge in a little and feather it, so moving strokes never smear the tree or hills.
mask = Image.fromarray(sky).filter(ImageFilter.MinFilter(5)).filter(ImageFilter.GaussianBlur(2.5 * s))
mask = mask.resize((W // 2, H // 2), Image.LANCZOS).convert("RGB")
mask.save(sys.argv[2])
if len(sys.argv) > 3:
    big = mask.resize((W, H))
    red = Image.new('RGB', (W, H), (255, 0, 0))
    Image.composite(im, Image.blend(im, red, 0.5), big).resize((800, int(800*H/W))).save(sys.argv[3])
