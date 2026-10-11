#!/usr/bin/env python3
"""All battle producers preserve history and publish complete fresh candidates."""
import importlib.util
from pathlib import Path
import tempfile
import unittest
from unittest import mock

from PIL import Image


def module(name, filename):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).with_name(filename))
    result = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(result)
    return result


motif = module('safe_motif_fixture', 'test-envhd-painted-battle-motif.py')
circle = module('safe_circle_fixture', 'test-envhd-painted-battle-circle.py')
material = module('safe_material_fixture', 'test-envhd-painted-battle-material.py')
rectangle = module('safe_rectangle_fixture', 'test-envhd-painted-battle-rectangle.py')
CASES = [
    (motif.repair, motif.BattleMotifTests().source(), [motif.COLOUR, 3]),
    (circle.repair, circle.CircleRepairTests().source(), [circle.COLOUR, [7.5, 7.5, 3], 4]),
    (material.repair, material.PaintedMaterialTests().source(), []),
    (rectangle.repair, rectangle.PaintedRectangleTests().source(), [rectangle.PALETTE, [1, 1, 3, 3]]),
]


class CandidatePublicationTests(unittest.TestCase):
    def exercise(self, scenario):
        for recipe, image, arguments in CASES:
            with self.subTest(recipe=recipe.METHOD), tempfile.TemporaryDirectory() as temporary:
                root = Path(temporary).resolve()
                source, raw = root/'source.png', root/'raw.png'
                image.save(source)
                colour = (100, 20, 30) if recipe is rectangle.repair else (220, 245, 230)
                Image.new('RGB', (image.width*4, image.height*4), colour).save(raw)
                execute = lambda path: recipe.execute(source, raw, path,
                                                      recipe.terrain.fingerprint(image), *arguments)
                scenario(root, recipe, execute)

    def test_fresh_output_is_complete_and_repeat_requires_a_new_path(self):
        def scenario(root, recipe, execute):
            path = root/'new-parent'/'image-v1.png'
            first, _ = execute(path)
            before = path.read_bytes()
            with Image.open(path) as image:
                image.load()
                self.assertEqual(image.size, tuple(first['targetSize']))
            with self.assertRaisesRegex(ValueError, 'fresh candidate'):
                execute(path)
            self.assertEqual(before, path.read_bytes())
            second, _ = execute(path.with_name('image-v2.png'))
            self.assertEqual(first, second)
            self.assertEqual(before, path.with_name('image-v2.png').read_bytes())
            self.assertEqual(list(path.parent.glob('.painted-candidate-*')), [])
        self.exercise(scenario)

    def test_existing_output_bytes_are_never_overwritten(self):
        def scenario(root, recipe, execute):
            path = root/'image-v1.png'
            path.write_bytes(b'reviewed history')
            with self.assertRaisesRegex(ValueError, 'fresh candidate'):
                execute(path)
            self.assertEqual(path.read_bytes(), b'reviewed history')
            self.assertEqual(list(root.glob('.painted-candidate-*')), [])
        self.exercise(scenario)

    def test_output_symlink_and_dangling_symlink_are_not_followed(self):
        def scenario(root, recipe, execute):
            target = root/'reviewed.png'
            target.write_bytes(b'protected target')
            for destination in (target, root/'absent.png'):
                path = root/'image-v1.png'
                path.symlink_to(destination)
                with self.assertRaisesRegex(ValueError, 'fresh candidate'):
                    execute(path)
                self.assertTrue(path.is_symlink())
                self.assertEqual(target.read_bytes(), b'protected target')
                self.assertFalse((root/'absent.png').exists())
                path.unlink()
        self.exercise(scenario)

    def test_linked_parent_cannot_redirect_publication(self):
        def scenario(root, recipe, execute):
            target = root/'outside'
            target.mkdir()
            sentinel = target/'image-v1.png'
            sentinel.write_bytes(b'protected parent target')
            parent = root/'linked'
            parent.symlink_to(target, target_is_directory=True)
            with self.assertRaisesRegex(ValueError, 'non-symlink directory'):
                execute(parent/'image-v1.png')
            self.assertEqual(sentinel.read_bytes(), b'protected parent target')
            self.assertEqual(list(target.iterdir()), [sentinel])
        self.exercise(scenario)

    def test_failed_publish_leaves_no_partial_advertised_output_or_temporary(self):
        def scenario(root, recipe, execute):
            path = root/'image-v1.png'
            with mock.patch.object(recipe.writer.os, 'link', side_effect=OSError('interrupted publish')):
                with self.assertRaisesRegex(OSError, 'interrupted publish'):
                    execute(path)
            self.assertFalse(path.exists())
            self.assertEqual(list(root.glob('.painted-candidate-*')), [])
        self.exercise(scenario)

    def test_concurrent_output_creation_cannot_be_clobbered(self):
        def scenario(root, recipe, execute):
            path = root/'image-v1.png'
            original = recipe.writer.os.link
            def raced_link(*args, **kwargs):
                path.write_bytes(b'concurrent history')
                return original(*args, **kwargs)
            with mock.patch.object(recipe.writer.os, 'link', side_effect=raced_link):
                with self.assertRaisesRegex(ValueError, 'fresh candidate'):
                    execute(path)
            self.assertEqual(path.read_bytes(), b'concurrent history')
            self.assertEqual(list(root.glob('.painted-candidate-*')), [])
        self.exercise(scenario)

    def test_temporary_name_collision_does_not_remove_another_file(self):
        def scenario(root, recipe, execute):
            sentinel = root/'.painted-candidate-collision'
            sentinel.write_bytes(b'another transaction')
            with mock.patch.object(recipe.writer.secrets, 'token_hex', return_value='collision'):
                with self.assertRaises(FileExistsError):
                    execute(root/'image-v1.png')
            self.assertEqual(sentinel.read_bytes(), b'another transaction')
            self.assertFalse((root/'image-v1.png').exists())
        self.exercise(scenario)


if __name__ == '__main__':
    unittest.main()
