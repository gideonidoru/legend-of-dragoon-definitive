#!/usr/bin/env python3
"""Stage approved films from exact immutable current-carrier asset identities."""
from pathlib import Path
import argparse
import importlib.util
import os
import tempfile
from current_publication import (REPO,TAG,parse_body,index_assets,check_asset,
    fmv_contract,canonical,descriptor)
from member_publication import fetch_asset


def metadata():
    spec=importlib.util.spec_from_file_location('delta_fmv',Path(__file__).with_name('prepare-publication-delta.py'))
    delta=importlib.util.module_from_spec(spec);spec.loader.exec_module(delta)
    return delta.fetch_current(REPO)


def sources(contract, current):
    release=current['release']
    if release is None or release['tag_name']!=TAG:raise ValueError('FMV current carrier unavailable')
    pointer=parse_body(release.get('body') or '',release['id'])
    assets=index_assets(current['assets']);result={}
    # The committed runtime revision remains closed while ROOT preseeds a future
    # Git-pinned candidate. Its film identity must not become the candidate pin.
    if pointer['format']==2:
        for declared in pointer['fmvSources'].values():
            asset=check_asset(assets.get(declared['name'],{}),declared['name'],declared['bytes'],declared['sha256'])
            if str(asset['id'])!=declared['id']:
                raise ValueError('Committed film source asset ID differs')
    for film in fmv_contract.NAMES:
        sha=contract[film+'.sha256'];size=int(contract[film+'.bytes']);name='file-'+sha
        result[film]=check_asset(assets.get(name,{}),name,size,sha)
    return result


def stage(manifest, output, read=metadata, fetch=fetch_asset):
    contract=fmv_contract.contract(manifest);current=read();bound=sources(contract,current)
    output=Path(output).absolute()
    if any(parent.is_symlink() for parent in (output,*output.parents)):raise ValueError('Linked film output')
    output.parent.mkdir(parents=True,exist_ok=True)
    # Cache-free owned staging keeps identity filenames out of the authoritative
    # source namespace and disappears on both success and interruption.
    with tempfile.TemporaryDirectory(prefix='.current-film-inputs-',dir=output.parent) as directory:
        directory=Path(directory)
        for asset in {asset['name']:asset for asset in bound.values()}.values():
            path=fetch(asset,directory)
            os.link(path,directory/(asset['name'][5:]+'.mp4'))
        def guard():
            fresh=read();again=sources(contract,fresh)
            release=lambda value:(value['release']['id'],value['release']['tag_name'],value['release'].get('body'))
            if release(fresh)!=release(current) or {film:asset['id'] for film,asset in again.items()}!={film:asset['id'] for film,asset in bound.items()}:
                raise ValueError('Current film carrier changed during build staging')
        # Recheck only after the owned candidate has copied/verified every byte,
        # immediately before the staging helper can rename any active output.
        result=fmv_contract.stage(manifest,output,directory,before_promote=guard)
        result.update(sourceReleaseId=str(current['release']['id']),sourceAssets={film:descriptor(asset,'movie') for film,asset in bound.items()})
        return result


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--manifest',type=Path,required=True);parser.add_argument('--output',type=Path,required=True)
    args=parser.parse_args();print(canonical(stage(args.manifest,args.output)).decode())
