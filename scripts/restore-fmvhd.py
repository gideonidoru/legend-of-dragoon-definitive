#!/usr/bin/env python3
"""Reproducible private extraction -> public FMVHD video assets. No game window."""
import argparse, hashlib, json, subprocess, time
from pathlib import Path

NAMES = 'OPENH DEMOH DEMO2 WAR1H TVRH GOAST ROZEH TREEH WAR2H BLACKH DRAGON1 DENIN DENIN2 DRAGON2 DEIASH MOONH ENDING1H ENDING2H'.split()

def run(argv, log, cwd=None):
    with log.open('a') as out:
        subprocess.run([str(a) for a in argv], stdout=out, stderr=out, check=True, cwd=cwd)

def sha(path):
    h = hashlib.sha256()
    with path.open('rb') as f:
        for b in iter(lambda: f.read(1048576), b''): h.update(b)
    return h.hexdigest()

def probe(path):
    return json.loads(subprocess.check_output(['ffprobe', '-v', 'error', '-count_frames', '-show_streams', '-show_format', '-of', 'json', str(path)]))

def main():
    p = argparse.ArgumentParser()
    for name in ('source', 'output', 'java', 'jpsxdec', 'upscaler', 'models'): p.add_argument('--'+name, type=Path, required=True)
    p.add_argument('--only', choices=NAMES)
    a = p.parse_args()
    for key in ('source', 'output', 'java', 'jpsxdec', 'upscaler', 'models'): setattr(a, key, getattr(a, key).resolve())
    a.output.mkdir(parents=True, exist_ok=True)
    tools = [a.jpsxdec, a.jpsxdec.with_name('jpsxdec-lib.jar'), a.upscaler, *a.models.glob('realesr-animevideov3-x4.*')]
    provenance = {'ffmpeg':subprocess.check_output(['ffmpeg', '-version'], text=True).splitlines()[0], 'files':{f.name:sha(f) for f in tools}}
    provenance['externalToolNotices'] = {'jPSXdec':'https://github.com/m35/jpsxdec/releases/tag/v2.1', 'RealESRGAN-ncnn':'https://github.com/xinntao/Real-ESRGAN-ncnn-vulkan/blob/master/LICENSE', 'RealESRGAN-model':'https://github.com/xinntao/Real-ESRGAN/blob/master/LICENSE'}
    toolchain = a.output/'toolchain.json'
    if toolchain.exists():
        old = json.loads(toolchain.read_text())
        if old['ffmpeg'] != provenance['ffmpeg'] or any(old['files'].get(k) != v for k,v in provenance['files'].items()):
            raise RuntimeError('Production toolchain changed; use a new output directory')
    else: toolchain.write_text(json.dumps(provenance, indent=2)+'\n')
    videos = a.output/'videos'; videos.mkdir(exist_ok=True)
    records = a.output/'records'; records.mkdir(exist_ok=True)
    for name in ([a.only] if a.only else NAMES):
        if (records/(name+'.json')).exists():
            r = json.loads((records/(name+'.json')).read_text())
            if (videos/(name+'.mp4')).is_file() and sha(videos/(name+'.mp4')) == r['sha256'] and sha(a.source/(name+'.IKI')) == r['sourceSha256']:
                print(json.dumps({'event':'complete', 'resumed':True, **r}), flush=True); continue
        t = time.monotonic()
        work = a.output/'work'/name; work.mkdir(parents=True, exist_ok=True)
        log = work/'production.log'; log.write_text('')
        original = a.source/(name+'.IKI')
        if not original.is_file(): raise RuntimeError('Missing original '+str(original))
        print(json.dumps({'event':'started','name':name}), flush=True)
        # Explicit -f overrides private paths stored in indexes; keep extraction outside Git.
        run([a.java, '-Djava.awt.headless=true', '-jar', a.jpsxdec, '-f', original, '-x', work/(name+'.idx'), '-i', '0', '-vf', 'avi:rgb', '-q', 'high', '-up', 'Lanczos3', '-psxav'], log, cwd=work)
        # Isolate decoder output and logs per video, independently of the caller directory.
        avi = work/(name+'.IKI[0].avi')
        if not avi.is_file(): raise RuntimeError('Expected decoded AVI '+str(avi))
        avi.rename(work/'source.avi'); avi = work/'source.avi'
        source = probe(avi)
        sv = next(s for s in source['streams'] if s['codec_type']=='video')
        if sv['r_frame_rate'] != '15/1' or sv['height'] != 192 or sv['width'] not in (320,640): raise RuntimeError('Unexpected original geometry/cadence')
        frames=work/'clean'; frames.mkdir(exist_ok=True)
        enhanced=work/'neural'; enhanced.mkdir(exist_ok=True)
        # Gentle temporal cleanup precedes framewise neural restoration; no invented motion.
        run(['ffmpeg','-y','-v','error','-i',avi,'-vf','hqdn3d=0.5:0.4:0.75:0.6','-fps_mode','passthrough',frames/'%08d.png'],log)
        run([a.upscaler,'-i',frames,'-o',enhanced,'-m',a.models,'-n','realesr-animevideov3','-s','4','-j','2:2:2'],log)
        expected=int(sv['nb_read_frames'])
        if len(list(enhanced.glob('*.png'))) != expected: raise RuntimeError('Neural frame count mismatch')
        # Blend a quarter of the conventional image to retain original texture and reduce a waxy appearance.
        filters='[0:v]scale=1280:768:flags=lanczos,setsar=1[n];[1:v]scale=1280:768:flags=lanczos,setsar=1[c];[n][c]blend=all_expr=A*0.75+B*0.25,format=yuv420p[v]'
        temp=videos/(name+'.partial.mp4')
        run(['ffmpeg','-y','-v','error','-framerate','15','-i',enhanced/'%08d.png','-i',avi,'-filter_complex',filters,'-map','[v]','-map','1:a:0','-c:v','libx264','-preset','medium','-crf','17','-color_primaries','bt709','-color_trc','bt709','-colorspace','bt709','-c:a','aac','-b:a','192k','-ar','48000','-ac','2','-af','volume=0.5','-movflags','+faststart',temp],log)
        result=probe(temp); v=next(s for s in result['streams'] if s['codec_type']=='video'); au=next(s for s in result['streams'] if s['codec_type']=='audio')
        if int(v['nb_read_frames']) != expected or (v['width'],v['height'])!=(1280,768) or au['sample_rate']!='48000' or au['channels']!=2: raise RuntimeError('Output format validation failed')
        # Full decode is necessary: metadata alone does not establish a playable output.
        run(['ffmpeg','-v','error','-xerror','-i',temp,'-f','null','-'],log)
        final=videos/(name+'.mp4'); temp.rename(final)
        r={'name':name,'sha256':sha(final),'sourceSha256':sha(original),'bytes':final.stat().st_size,'frames':expected,'duration':float(v['duration']),'width':1280,'height':768,'fps':15,'sourceWidth':sv['width'],'sourceHeight':sv['height'],'productionSeconds':round(time.monotonic()-t,1),'method':'Lanczos3 chroma / gentle temporal denoise / framewise RealESRGAN animevideov3 4x at 75 percent / Lanczos at 25 percent','extractionTimingWarnings':sum('WARNING' in line for line in (work/'save.log').read_text().splitlines())}
        (records/(name+'.json')).write_text(json.dumps(r,indent=2)+'\n')
        print(json.dumps({'event':'complete',**r}),flush=True)
        # Work intermediates are disposable and entirely outside the source tree.
        for folder in (frames,enhanced):
            for f in folder.glob('*.png'): f.unlink()
        avi.unlink()
    print(json.dumps({'event':'all_complete'}),flush=True)
if __name__=='__main__': main()
