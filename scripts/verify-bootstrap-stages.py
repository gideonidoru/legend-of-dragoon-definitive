#!/usr/bin/env python3
"""Headless actual-shell forced-kill stage cleanup; no downloads, UI or game."""
import argparse,os,signal,subprocess,sys,tempfile,time
from pathlib import Path
ap=argparse.ArgumentParser();ap.add_argument('--source-ref');a=ap.parse_args();repo=Path(__file__).resolve().parents[1];failures=[]
for kind,path,prefix in [('portable','delivery/Install-Definitive.sh','.portable.'),('java','delivery/installer/bootstrap-java','.java-download.')]:
 with tempfile.TemporaryDirectory(prefix='definitive-stage-fixture-') as folder:
  root=Path(folder).resolve();cache=root/'cache';cache.mkdir();tools=root/'tools';tools.mkdir()
  def tool(name,code):
   p=tools/name;p.write_text('#!'+sys.executable+'\n'+code);p.chmod(0o755)
  tool('flock',"import fcntl,sys\ntry:fcntl.flock(int(sys.argv[-1]),fcntl.LOCK_EX|fcntl.LOCK_NB)\nexcept BlockingIOError:sys.exit(1)\n")
  tool('zenity','import sys\nsys.stdin.read()\n')
  tool('java','import sys\nsys.exit(1)\n')
  tool('curl',"import os,sys,time\nfrom pathlib import Path\na=sys.argv[1:];Path(a[a.index('--output')+1]).write_bytes(b'partial-download')\nPath(os.environ['FIXTURE_STARTED']).write_text('ready')\nif os.environ['FIXTURE_HOLD']=='yes':time.sleep(30)\nsys.exit(22)\n")
  source=subprocess.check_output(['git','show',f'{a.source_ref}:{path}'],cwd=repo,text=True) if a.source_ref else (repo/path).read_text()
  if kind=='portable':source=source.replace('CACHE="$HOME/.cache/legend-of-dragoon-definitive"',f'CACHE="{cache}"')
  entry=root/'entry.sh';entry.write_text(source);args=['/bin/bash',str(entry)]+([str(cache)] if kind=='java' else [])
  env=dict(os.environ,PATH=str(tools)+':'+os.environ['PATH'],JAVA_HOME='',DEFINITIVE_MANAGED_JAVA='1',FIXTURE_STARTED=str(root/'started'),FIXTURE_HOLD='yes')
  proc=subprocess.Popen(args,env=env,stdout=subprocess.PIPE,stderr=subprocess.PIPE,start_new_session=True)
  try:
   deadline=time.monotonic()+5
   while not (root/'started').exists() and time.monotonic()<deadline and proc.poll() is None:time.sleep(.01)
   assert (root/'started').exists(),'Download seam not reached'
   stale=list(cache.glob(prefix+'*'));assert len(stale)==1
   retryenv=dict(env,FIXTURE_HOLD='no',FIXTURE_STARTED=str(root/'retry-started'))
   active=subprocess.run(args,env=retryenv,capture_output=True,timeout=5)
   assert active.returncode!=0 and not (root/'retry-started').exists() and stale[0].exists(),'Live stage lock bypass'
   os.kill(proc.pid,signal.SIGKILL);proc.wait(timeout=5)
   orphan=subprocess.run(args,env=retryenv,capture_output=True,timeout=5)
   assert orphan.returncode!=0 and not (root/'retry-started').exists() and stale[0].exists(),'Live orphan download lost lock/stage protection'
   os.killpg(proc.pid,signal.SIGKILL);proc.communicate(timeout=5)
   retry=subprocess.run(args,env=retryenv,capture_output=True,timeout=5)
   assert (root/'retry-started').exists(),'Killed instance blocks restart'
   assert not stale[0].exists(),'SIGKILL partial stage survived next exclusive startup'
   print('PASS',kind,'SIGKILL owned-stage recovery/live-lock/orphan exclusion',flush=True)
   if kind=='java':
    old=cache/(prefix+'Z9y8X7');old.mkdir(mode=0o700);(old/'runtime.tar.gz').write_text('partial')
    home=root/'java-home';(home/'bin').mkdir(parents=True);java=home/'bin/java'
    java.write_text('#!/bin/bash\nprintf "java.specification.version = 25\\n"\n');java.chmod(0o755)
    cached=subprocess.run(args,env=dict(retryenv,JAVA_HOME=str(home),DEFINITIVE_MANAGED_JAVA='0'),capture_output=True,timeout=5)
    assert cached.returncode==0 and not old.exists(),'Usable Java short circuit skipped recovery'
    print('PASS java usable-JAVA_HOME startup recovery',flush=True)

   for scenario in ('legacy-download','unknown-file','owned-unknown-file','linked-stage','linked-receipt','wrong-receipt','public-mode','extracted-subtree','owned-extracted','owned-root-link','invalid-name'):
    candidate=cache/(prefix+('invalid-name' if scenario=='invalid-name' else 'A1b2C3'))
    victim=root/'protected';victim.write_text('personal-file')
    if scenario=='linked-stage':candidate.symlink_to(root,target_is_directory=True)
    else:
     candidate.mkdir(mode=0o700);(candidate/('installer.zip' if kind=='portable' else 'runtime.tar.gz')).write_text('partial')
     if scenario in ('unknown-file','owned-unknown-file'):(candidate/'save.dat').write_text('personal')
     if scenario in ('owned-unknown-file','owned-extracted','owned-root-link'):(candidate/'.definitive-stage-owner').write_text(f'definitive-stage-v1\n{kind}\n{os.getuid()}\n{candidate}\n')
     if scenario=='linked-receipt':(candidate/'.definitive-stage-owner').symlink_to(victim)
     if scenario=='wrong-receipt':(candidate/'.definitive-stage-owner').write_text('not-owned')
     if scenario=='public-mode':candidate.chmod(0o755)
     if scenario in ('extracted-subtree','owned-extracted'):
      unpacked=candidate/('installer' if kind=='portable' else 'jdk');unpacked.mkdir();(unpacked/'partial.bin').write_text('owned unpack')
      if scenario=='owned-extracted':(unpacked/'external-link').symlink_to(victim)
     if scenario=='owned-root-link':(candidate/('installer' if kind=='portable' else 'jdk')).symlink_to(root,target_is_directory=True)
    (root/'retry-started').unlink(missing_ok=True)
    r=subprocess.run(args,env=retryenv,capture_output=True,timeout=5)
    assert (root/'retry-started').exists(),'Preserved garbage blocked setup: '+scenario
    assert r.returncode!=0 and victim.read_text()=='personal-file'
    if scenario in ('legacy-download','owned-extracted'):assert not candidate.exists(),'Known historical partial not reclaimed'
    else:
     assert candidate.exists() or candidate.is_symlink(),'Unknown stage removed: '+scenario
     assert b'Preserved' in r.stderr,'Preservation not reported: '+scenario
     if candidate.is_symlink():candidate.unlink()
     else:
      import shutil;shutil.rmtree(candidate)
    print('PASS',kind,scenario,flush=True)
   blocked=cache/(prefix+'D4e5F6');blocked.mkdir(mode=0o700);(blocked/('installer.zip' if kind=='portable' else 'runtime.tar.gz')).write_text('partial')
   tool('rm', "import os,sys\nif sys.argv[-1].endswith('D4e5F6'):sys.exit(13)\nos.execv('/bin/rm',['/bin/rm',*sys.argv[1:]])\n")
   (root/'retry-started').unlink(missing_ok=True)
   failedcleanup=subprocess.run(args,env=retryenv,capture_output=True,timeout=5)
   assert blocked.exists() and (root/'retry-started').exists() and b'Could not remove' in failedcleanup.stderr,'Cleanup failure blocked normal setup'
   print('PASS',kind,'cleanup-failure preserves/report/continues',flush=True)
  except AssertionError as e:failures.append(kind+': '+str(e));print('FAIL',failures[-1],flush=True)
  finally:
   try:os.killpg(proc.pid,signal.SIGKILL)
   except ProcessLookupError:pass
   proc.communicate(timeout=5)
if failures:raise SystemExit('\n'.join(failures))
# Generation preserves exactly the reviewed template plus digest/tag and current Desktop behavior.
with tempfile.TemporaryDirectory(prefix='definitive-assembler-stage-') as folder:
 import hashlib,re,shutil
 root=Path(folder).resolve();(root/'scripts').mkdir();(root/'delivery').mkdir();(root/'build/distributions').mkdir(parents=True)
 for name in ('assemble-installer.py','desktop_bootstrap.py'):shutil.copy2(repo/'scripts'/name,root/'scripts'/name)
 text=(repo/'delivery/Install-Definitive.sh').read_text();(root/'delivery/Install-Definitive.sh').write_text(text)
 payload=b'fixture portable package, never executed';(root/'build/distributions/Definitive-Installer.zip').write_bytes(payload)
 subprocess.run([sys.executable,str(root/'scripts/assemble-installer.py'),'--output-dir',str(root/'output'),'--tag','fixture-current'],check=True,capture_output=True)
 expected=re.sub(r'^EXPECTED=.*$','EXPECTED='+hashlib.sha256(payload).hexdigest(),text,flags=re.M);expected=re.sub(r'^TAG=.*$','TAG=fixture-current',expected,flags=re.M)
 assert (root/'output/Install-Definitive.sh').read_text()==expected,'Assembler lost reviewed stage recovery'
 subprocess.run(['/bin/bash','-n',str(root/'output/Install-Definitive.sh')],check=True)
 sys.path.insert(0,str(root/'scripts'));from desktop_bootstrap import desktop_entry
 assert (root/'output/Install-Definitive.desktop').read_text()==desktop_entry(),'Latest Desktop resolution changed'
 print('PASS actual assembler template preservation/current Desktop entry',flush=True)
print('PASS: actual bootstrap SIGKILL recovery, safe owned extraction, unknown/link preservation and generation')
