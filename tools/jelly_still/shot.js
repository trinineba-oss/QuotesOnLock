const {chromium}=require('playwright');const fs=require('fs');
(async()=>{const b=await chromium.launch({args:['--use-gl=swiftshader','--enable-webgl','--ignore-gpu-blocklist']});
const p=await b.newPage({viewport:{width:+process.env.W||360,height:+process.env.H||780}});await p.goto('file://'+process.cwd()+'/page.html');
await p.evaluate(src=>window.setup(src),fs.readFileSync('jelly.frag','utf8'));
console.log(await p.title());
for(const t of process.argv.slice(2)){await p.evaluate(t=>window.frame(+t),t);await p.screenshot({path:'f'+t+'.png'});}
await b.close();})();
