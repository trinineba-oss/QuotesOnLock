# Jellyfish stills

`jelly.frag` is the WebGL twin of the AGSL shader in `JellyArt.kt` (same maths; keep them in step).
To render the still pictures:

    cd tools/jelly_still
    W=720 H=1560 node shot.js 3     # writes f3.png: jelly_still.jpg, and its rows 700-1000 are jelly_frame.jpg

Rendering needs Playwright with Chromium (`--use-gl=swiftshader`).
