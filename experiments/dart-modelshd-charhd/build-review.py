#!/usr/bin/env python3
"""Private standalone visual review bundle, AGPL v3. Never copy controls into Git."""
import argparse
import base64
import json
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--study',type=Path,required=True)
parser.add_argument('--output',type=Path,required=True)
args=parser.parse_args()
if args.output.exists():parser.error('Use a new review directory')
root=Path(__file__).resolve().parents[2]
if args.output.resolve().is_relative_to(root):parser.error('Review controls must remain outside Git')
required={f'{form}-{view}-{variant}-{texture}{close}.png'
          for form in ('field','combat') for view in ('front','three-quarter','rear-side')
          for variant in ('current','experiment') for texture in ('stock','charhd','painted')
          for close in ('','-close')}
required.update(f'{form}-pose-{key}-{variant}.png' for form in ('field','combat') for key in (0,5,9) for variant in ('current','experiment'))
if any(not (args.study/name).is_file() for name in required):parser.error('Study comparison matrix is incomplete')
args.output.mkdir(parents=True)
images={p.name:'data:image/png;base64,'+base64.b64encode(p.read_bytes()).decode() for p in args.study.glob('*.png')}
font='/System/Library/Fonts/Supplemental/Arial.ttf'
# Contact sheets preserve matched dimensions and fit; transparent background becomes a neutral matte.
for form in ('field','combat'):
    for crop in ('full','face'):
        close='-close' if crop=='face' else ''
        pairs=(f'{form}-front-current-charhd{close}.png',f'{form}-front-experiment-painted{close}.png')
        source=[Image.open(args.study/name).convert('RGBA') for name in pairs]
        width=source[0].width;height=source[0].height
        canvas=Image.new('RGBA',(width*2+72,height+174),(18,20,22,255))
        draw=ImageDraw.Draw(canvas)
        title=ImageFont.truetype(font,32);label=ImageFont.truetype(font,22);note=ImageFont.truetype(font,17)
        draw.text((24,20),'DART  /  '+('FIELD' if form=='field' else 'BATTLE'),font=title,fill='#ede8df')
        draw.text((24,64),'Matched offline model comparison · Experimental, not approved',font=note,fill='#aaa8a3')
        for index,(image,text) in enumerate(zip(source,('CURRENT · ModelsHD 0.4 + CharHD','EXPERIMENT · Shape + CharHD + face paint'))):
            x=24+index*(width+24)
            draw.text((x,106),text,font=label,fill='#dab58b' if index else '#d6d5d1')
            canvas.alpha_composite(image,(x,146))
        canvas.convert('RGB').save(args.output/f'{form}-{crop}-comparison.jpg',quality=94)
page='''<!doctype html><html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Dart · ModelsHD + CharHD study</title>
<style>
:root{color-scheme:dark;font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif;background:#111315;color:#efede8}*{box-sizing:border-box}body{margin:0;padding:40px clamp(20px,5vw,80px) 70px}main{max-width:1680px;margin:auto}.eyebrow{font-size:11px;letter-spacing:.18em;text-transform:uppercase;color:#cba982}h1{font-weight:550;font-size:clamp(36px,4vw,62px);letter-spacing:-.05em;margin:18px 0 14px}p{line-height:1.6;color:#acaeb0;max-width:900px;font-size:15px}.status{border:1px solid #725d42;color:#dfba8c;font-size:12px;border-radius:20px;padding:6px 12px;display:inline-block;margin-top:8px}.controls{display:flex;flex-wrap:wrap;align-items:center;gap:14px;margin:32px 0 22px}.seg{display:flex;gap:4px;background:#1e2124;padding:4px;border:1px solid #33383d;border-radius:13px}button,select{min-height:44px;padding:0 18px;color:#bbbfc2;background:transparent;border:0;border-radius:9px;font:inherit;font-size:14px;cursor:pointer}button[aria-pressed=true]{background:#363c42;color:#fff}button:focus-visible,select:focus-visible{outline:2px solid #d9b58e;outline-offset:3px}select{background:#22262a;border:1px solid #343a40}label{display:flex;align-items:center;gap:9px;font-size:13px;color:#acaeb0;min-height:44px}input{width:20px;height:20px;accent-color:#d1ae86}.grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:14px}.card{border:1px solid #30353a;background:#191d20;border-radius:16px;overflow:hidden}.caption{padding:18px 20px 6px}.caption span{font-size:11px;text-transform:uppercase;letter-spacing:.11em;color:#979da1}.caption h2{font-size:17px;letter-spacing:-.02em;font-weight:500;margin:8px 0}.caption p{font-size:12px;margin:0;height:40px;color:#939b9f}.card img{width:100%;height:auto;display:block;background:#191d20}.card:last-child{border-color:#8e7556}.card:last-child .caption span{color:#dbb487}.foot{border-top:1px solid #30353a;margin-top:30px;padding-top:22px;display:grid;grid-template-columns:1fr 1fr;gap:26px}.foot h3{font-size:15px;font-weight:500;margin:0}.foot p{font-size:13px}.fine{font-size:12px;color:#878e93;margin-top:24px}@media(max-width:1100px){.grid{grid-template-columns:repeat(2,minmax(0,1fr))}}@media(max-width:560px){.grid,.foot{grid-template-columns:1fr}body{padding:26px 16px}.caption p{height:auto}}
</style><main><div class="eyebrow">Legend of Dragoon: Definitive / Character study 01</div><h1>A clearer Dart.</h1><p>Explore the contribution of texture restoration, controlled shape changes and a painted face. Every column shares its pose, camera and framing. These are actual offline model renders, not concept-art substitutions.</p><div class="status">Experimental · Owner approval required before main or release</div>
<div class="controls"><div class="seg" id="form"><button data-value="field" aria-pressed="true">Field</button><button data-value="combat" aria-pressed="false">Battle</button></div><div class="seg" id="crop"><button data-value="full" aria-pressed="true">Whole character</button><button data-value="face" aria-pressed="false">Face</button></div><select aria-label="Camera" id="view"><option value="front">Front camera</option><option value="three-quarter">Turned camera</option><option value="rear-side">Side / rear camera</option></select><label><input id="controls" type="checkbox">Show all six controls</label></div>
<div class="grid" id="gallery"></div><div class="foot"><section><h3>What improved</h3><p>Clearer facial features, preserved model attachments, a separate full-resolution face layer, and CharHD 4× restoration. Geometry is source-bound, animations remain original, and texture restoration retains source palette transparency.</p></section><section><h3>What still needs work</h3><p>Hair masses, hand construction, the side profile and ear transitions remain coarse. These offline views have no scene lighting. Native battle body-atlas integration, wider gameplay checks and Steam Deck performance remain unproven.</p></section></div><p class="fine">Baseline: ModelsHD 0.4 at 2b2e95cb5. Field 1,558 → 3,118 triangles; battle 2,838 → 5,508. No main/release defaults changed. No real-time neural inference.</p><details><summary>Stored-pose comparisons</summary><p>Keys 0, 5 and 9 from each inspected original animation, with shared framing. Current ModelsHD + CharHD versus experimental shape + CharHD + paint. This is a pose sample, not real gameplay timing.</p><div id="poses" class="grid"></div></details></main><script>
const images=__IMAGES__;let form='field',crop='full';
const variants=[['current-stock','Baseline','Current ModelsHD','Original character textures'],['current-charhd','Texture restoration','Current + CharHD','Shape unchanged · 4× texture restoration'],['experiment-charhd','Shape experiment','Shape + CharHD','Bounded facial/armor/glove/cloth changes'],['experiment-painted','Face detail','Shape + CharHD + paint','Full-resolution supplemental face texture']];
const extra=[['experiment-stock','Shape control','Shape + original textures','Separates geometry from texture restoration'],['current-painted','Paint control','Current + CharHD + paint','Separates painted detail from geometry']];
function card(name,row){const article=document.createElement('article');article.className='card';const caption=document.createElement('div');caption.className='caption';const small=document.createElement('span');small.textContent=row[1];const heading=document.createElement('h2');heading.textContent=row[2];const text=document.createElement('p');text.textContent=row[3];caption.append(small,heading,text);const image=document.createElement('img');image.src=images[name];image.alt=row[2]+' · '+form;image.loading='lazy';article.append(caption,image);return article}
function render(){const view=document.getElementById('view').value;const close=crop==='face'?'-close':'';const gallery=document.getElementById('gallery');gallery.replaceChildren();const rows=document.getElementById('controls').checked?[...variants.slice(0,3),...extra,variants[3]]:variants;for(const row of rows)gallery.append(card(`${form}-${view}-${row[0]}${close}.png`,row));const poses=document.getElementById('poses');poses.replaceChildren();for(const key of [0,5,9])for(const state of ['current','experiment'])poses.append(card(`${form}-pose-${key}-${state}.png`,['',`Stored key ${key}`,state==='current'?'Current + CharHD':'Experiment + CharHD + paint','Same original pose and shared frame']));}
for(const id of ['form','crop'])for(const button of document.getElementById(id).querySelectorAll('button'))button.onclick=()=>{if(id==='form')form=button.dataset.value;else crop=button.dataset.value;for(const sibling of button.parentElement.children)sibling.setAttribute('aria-pressed',sibling===button?'true':'false');render()};document.getElementById('view').onchange=render;document.getElementById('controls').onchange=render;render();
</script></html>'''
(args.output/'index.html').write_text(page.replace('__IMAGES__',json.dumps(images,separators=(',',':'))))
(args.output/'README.txt').write_text('Private visual review controls. Open index.html on any computer; all images are embedded. No local server is needed. Experimental, not approved for main/release. Images are offline renders, not gameplay screenshots.\n')
print(args.output/'index.html')
