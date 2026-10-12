#!/usr/bin/env python3
"""Headless Java rasterizer controls; requires JDK25 and Pillow, no game assets."""
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]

class NativeSpriteAuthoringTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.workspace = tempfile.TemporaryDirectory(prefix='native-sprite-authoring-')
        cls.classes = Path(cls.workspace.name).resolve()
        cls.java = str(Path(os.environ['JAVA_HOME']) / 'bin/java') if os.environ.get('JAVA_HOME') else shutil.which('java')
        javac = str(Path(cls.java).with_name('javac'))
        helper = cls.classes / 'OutputFailureProbe.java'
        helper.write_text('''import java.nio.file.*;
public class OutputFailureProbe {
 public static void main(String[] args) throws Exception {
  var method=RenderNativeSpriteFont.class.getDeclaredMethod("outputs",Path.class,byte[].class,Path.class,byte[].class);method.setAccessible(true);
  Path first=Path.of(args[0]),second=Path.of(args[1]);
  try {method.invoke(null,first,new byte[]{1,2},second,new byte[]{3,4});throw new AssertionError("Expected output failure");}
  catch(java.lang.reflect.InvocationTargetException expected){if(!(expected.getCause() instanceof java.io.IOException))throw expected;}
  if(Files.exists(first))throw new AssertionError("Created partial output remains");
 }
}
''')
        subprocess.run([javac, '-d', str(cls.classes), str(ROOT / 'scripts/RenderNativeSpriteFont.java'), str(helper)], check=True, capture_output=True, text=True)

    @classmethod
    def tearDownClass(cls):
        cls.workspace.cleanup()

    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(dir=self.classes)
        self.directory = Path(self.temp.name)
        self.addCleanup(self.temp.cleanup)

    def render(self, attributes='', width='8', wrapped=False, palette=None):
        path = f'<path data-palette-index="7" {attributes} d="M1 1 L7 1 L7 7 L1 7 Z"/>'
        if wrapped:
            path = '<g transform="translate(1 0)">' + path + '</g>'
        source = self.directory / 'source.svg'
        source.write_text(f'<svg viewBox="0 0 {width} 8">{path}</svg>')
        encoded = self.directory / 'encoded.png'
        preview = getattr(self, 'preview', self.directory / 'preview.png')
        result = subprocess.run([self.java, '-Djava.awt.headless=true', '-cp', str(self.classes), 'RenderNativeSpriteFont', str(source), str(encoded), str(preview), '8', ','.join(palette or ['ffffff'] * 16)], capture_output=True, text=True)
        return result, encoded, preview

    def rejected(self, **arguments):
        result, encoded, _ = self.render(**arguments)
        self.assertNotEqual(0, result.returncode, result.stdout)
        self.assertFalse(encoded.exists(), result.stderr)

    def test_duplicate_rgb_keeps_role_and_decoded_preview(self):
        result, encoded, preview = self.render()
        self.assertEqual(0, result.returncode, result.stderr)
        with Image.open(encoded) as indexed, Image.open(preview) as visible:
            self.assertEqual((7, 0, 0, 255), indexed.convert('RGBA').getpixel((32, 32)))
            self.assertEqual((255, 255, 255, 255), visible.convert('RGBA').getpixel((32, 32)))

    def test_existing_preview_is_preserved(self):
        self.preview = self.directory / 'preview.png'
        self.preview.write_bytes(b'owner')
        self.rejected()
        self.assertEqual(b'owner', self.preview.read_bytes())

    def test_missing_parent_creates_no_partial_pair(self):
        self.preview = self.directory / 'missing/preview.png'
        self.rejected()

    def test_linked_parent_writes_nothing(self):
        external = self.directory / 'external'
        external.mkdir()
        (self.directory / 'link').symlink_to(external, target_is_directory=True)
        self.preview = self.directory / 'link/preview.png'
        self.rejected()
        self.assertEqual([], list(external.iterdir()))

    def test_dimensions_do_not_overflow(self):
        self.rejected(width='536870913')

    def test_group_transform_is_rejected(self):
        self.rejected(wrapped=True)

    def test_invisible_paint_is_rejected(self):
        self.rejected(attributes='fill="none" stroke="none"')

    def test_unhandled_color_is_rejected(self):
        self.rejected(attributes='fill="red"')

    def test_combined_fill_stroke_is_rejected(self):
        self.rejected(attributes='fill="#fff" stroke="#fff" stroke-width="1"')

    def test_empty_stroke_width_is_rejected(self):
        self.rejected(attributes='fill="none" stroke="#fff" stroke-width=""')

    def test_real_second_output_failure_rolls_back_only_created_file(self):
        for second in [self.directory / 'missing/preview.png', self.directory / 'existing.png']:
            if second.name == 'existing.png':
                second.write_bytes(b'owner')
            result = subprocess.run([self.java, '-cp', str(self.classes), 'OutputFailureProbe', str(self.directory / 'partial.png'), str(second)], capture_output=True, text=True)
            self.assertEqual(0, result.returncode, result.stderr)
            if second.name == 'existing.png':
                self.assertEqual(b'owner', second.read_bytes())

if __name__ == '__main__':
    unittest.main(verbosity=2)
