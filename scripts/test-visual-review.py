#!/usr/bin/env python3
"""Original review-viewer identity and injection fixtures. AGPL v3; see LICENSE."""
import hashlib
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from PIL import Image

spec=importlib.util.spec_from_file_location('review',Path(__file__).with_name('build-visual-review.py'))
review=importlib.util.module_from_spec(spec);spec.loader.exec_module(review)


def fixture(folder):
    outputs=[]
    for column in range(2):
        name=f'pose-0-candidate-{column}.png';path=folder/name
        Image.new('RGBA',(384,448),(255,0,column*255,255)).save(path)
        outputs.append({'path':name,'sha256':hashlib.sha256(path.read_bytes()).hexdigest()})
    report={'pipeline':'definitive-private-pose-inspection-1','modelSha256':'a'*64,'timSha256':'b'*64,'animationSha256':'c'*64,'keyframes':[0],'cameraYawRadians':[0],'candidates':[{'label':'</script><script>alert(1)</script>','sha256':'d'*64}],'outputs':outputs}
    (folder/'manifest.json').write_text(json.dumps(report))
    return report


class ReviewTest(unittest.TestCase):
    def test_allocation_dimensions_and_map_are_bound_to_the_inspected_atlas(self):
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp);source=root/'inspection';source.mkdir();report=fixture(source)
            pack=root/'pack';pack.mkdir();path=pack/'atlas-engine-stp.png'
            Image.new('RGBA',(8,8),(255,0,0,0)).save(path)
            entries=[{'palette':0,'sourceCrop':[0,0,4,4],'atlasRect':[0,0,8,8]}]
            report['candidates'][0].update(sha256=hashlib.sha256(path.read_bytes()).hexdigest(),materialMapSha256=hashlib.sha256(json.dumps({0:entries[0]},sort_keys=True).encode()).hexdigest())
            (source/'manifest.json').write_text(json.dumps(report))
            meta={'pipeline':'definitive-private-material-pack-1','modelSha256':report['modelSha256'],'timSha256':report['timSha256'],'atlasEngineSha256':report['candidates'][0]['sha256'],'atlasSize':[8,8],'materials':entries}
            manifest=pack/'manifest.json';manifest.write_text(json.dumps(meta))
            review.build_review(source,root/'valid',[pack])
            meta['atlasSize']=[4096,4096];manifest.write_text(json.dumps(meta))
            with self.assertRaisesRegex(ValueError,'atlas image'):review.build_review(source,root/'bad-size',[pack])
            meta['atlasSize']=[8,8];meta['materials'][0]['sourceCrop'][0]=-1;manifest.write_text(json.dumps(meta))
            with self.assertRaisesRegex(ValueError,'representation'):review.build_review(source,root/'bad-map',[pack])

    def test_embeds_verified_views_and_keeps_labels_inert(self):
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp);source=root/'inspection';source.mkdir();fixture(source)
            html=review.build_review(source,root/'review').read_text()
            self.assertIn('data:image/png;base64,',html)
            self.assertNotIn('</script><script>alert',html)
            self.assertIn('\\u003c/script>',html)
            self.assertEqual(html.count('<script>'),1)
            with self.assertRaises(FileExistsError):review.build_review(source,root/'review')

    def test_rejects_changed_images_source_ids_and_unmatched_allocation(self):
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp);source=root/'inspection';source.mkdir();report=fixture(source)
            Image.new('RGBA',(384,448),(0,255,0,255)).save(source/'pose-0-candidate-1.png')
            with self.assertRaisesRegex(ValueError,'identity'):review.build_review(source,root/'bad-image')
            report=fixture(source);report['timSha256']='not a hash';(source/'manifest.json').write_text(json.dumps(report))
            with self.assertRaisesRegex(ValueError,'identity'):review.build_review(source,root/'bad-source')
            fixture(source);pack=root/'pack';pack.mkdir();(pack/'manifest.json').write_text(json.dumps({'pipeline':'definitive-private-material-pack-1','atlasEngineSha256':'e'*64}))
            with self.assertRaisesRegex(ValueError,'metadata'):review.build_review(source,root/'bad-pack',[pack])


if __name__=='__main__':unittest.main()
