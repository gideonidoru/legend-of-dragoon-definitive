#!/usr/bin/env python3
"""Checks for categorical source visibility and generated alpha matte repair."""
import importlib.util
import io
import struct
import tempfile
import zlib
from functools import lru_cache
from pathlib import Path
import unittest
from unittest import mock

import numpy as np
from PIL import Image

spec = importlib.util.spec_from_file_location('painted_repair', Path(__file__).with_name('repair-envhd-painted-cutout.py'))
repair = importlib.util.module_from_spec(spec)
spec.loader.exec_module(repair)


@lru_cache(maxsize=4)
def compressed_png(width=4096, height=4096, colour_type=6):
    """Valid oversized uniform PNG built without allocating an image canvas."""
    def chunk(kind, data):
        return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind + data))
    row = b'\0' + bytes([35, 65, 20, 255][:3 if colour_type == 2 else 4]) * width
    encoder = zlib.compressobj()
    pieces = [encoder.compress(row) for _ in range(height)]
    pixels = b''.join(pieces) + encoder.flush()
    header = struct.pack('>2I5B', width, height, 8, colour_type, 0, 0, 0)
    return b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', header) + chunk(b'IDAT', pixels) + chunk(b'IEND', b'')


def duplicate_header_png(first=(2,2), second=(4096,4096), colour_type=6, first_colour_type=None):
    data=compressed_png(*second,colour_type)
    first_header=struct.pack('>2I5B',*first,8,colour_type if first_colour_type is None else first_colour_type,0,0,0)
    chunk=struct.pack('>I',13)+b'IHDR'+first_header+struct.pack('>I',zlib.crc32(b'IHDR'+first_header))
    return data[:8]+chunk+data[8:]


class PaintedCutoutTests(unittest.TestCase):
    def source(self):
        return Image.fromarray(np.array([
            [[20, 40, 15, 255], [0, 0, 0, 255]],
            [[5, 9, 12, 0], [40, 60, 25, 128]],
        ], dtype=np.uint8), 'RGBA')

    def test_near_transparent_matte_does_not_become_visible(self):
        painted = np.full((8, 8, 4), [35, 65, 20, 255], dtype=np.uint8)
        painted[1, 1] = [0, 255, 0, 1]
        result, operation = repair.repair(self.source(), Image.fromarray(painted, 'RGBA'))
        actual = np.asarray(result)
        native = np.repeat(np.repeat(np.asarray(self.source()), 4, axis=0), 4, axis=1)
        np.testing.assert_array_equal(actual[:, :, 3], native[:, :, 3])
        np.testing.assert_array_equal(actual[1, 1], [35, 65, 20, 255])
        np.testing.assert_array_equal(actual[0, 4], [0, 0, 0, 255])
        np.testing.assert_array_equal(actual[4, 0], [5, 9, 12, 0])
        self.assertEqual(operation['paintedGapPixelsExtended'], 1)

    def test_incomplete_painting_is_rejected(self):
        painted = np.zeros((8, 8, 4), dtype=np.uint8)
        painted[0, 0] = [35, 65, 20, 255]
        with self.assertRaisesRegex(ValueError, 'coverage differs'):
            repair.repair(self.source(), Image.fromarray(painted, 'RGBA'))

    def test_changed_canvas_aspect_is_rejected(self):
        with self.assertRaisesRegex(ValueError, 'aspect ratio'):
            repair.repair(self.source(), Image.new('RGBA', (8, 16), (35, 65, 20, 255)))

    def test_execute_reads_only_limit_plus_one_before_rejecting(self):
        class TrackedStream(io.BytesIO):
            requests = []
            def read(self, size=-1):
                self.requests.append(size)
                return super().read(size)
        stream = TrackedStream(b'x' * 100)
        source = mock.MagicMock()
        source.open.return_value.__enter__.return_value = stream
        source.read_bytes.side_effect = AssertionError('Unbounded read')
        painted = mock.MagicMock()
        with mock.patch.object(repair, 'MAX_IMAGE_BYTES', 64), mock.patch.object(Image, 'open', side_effect=AssertionError('Pixel allocation')):
            with self.assertRaisesRegex(ValueError, 'bounded payload'):
                repair.execute(source, painted, Path('unused.png'), 'unused', 'unused')
        self.assertEqual([65], stream.requests)
        source.read_bytes.assert_not_called()
        painted.open.assert_not_called()

    def execute_inputs(self, folder, source_data=None, painted_data=None):
        source_path = folder/'source.png';painted_path = folder/'painted.png'
        if source_data is None:self.source().save(source_path)
        else:source_path.write_bytes(source_data)
        if painted_data is None:Image.new('RGBA',(8,8),(35,65,20,255)).save(painted_path)
        else:painted_path.write_bytes(painted_data)
        return source_path, painted_path, folder/'output.png'

    def test_execute_limits_painted_file_reads_before_any_decode(self):
        with tempfile.TemporaryDirectory() as temporary:
            paths = self.execute_inputs(Path(temporary), painted_data=b'x' * 1000)
            with mock.patch.object(repair,'MAX_IMAGE_BYTES',128),mock.patch.object(Image,'open',side_effect=AssertionError('Pixel allocation')):
                with self.assertRaisesRegex(ValueError,'bounded payload'):
                    repair.execute(*paths, 'unused', 'unused')
            self.assertFalse(paths[2].exists())

    def test_execute_rejects_small_compressed_oversized_source_before_open(self):
        data = compressed_png();self.assertLess(len(data), 100000)
        with tempfile.TemporaryDirectory() as temporary:
            paths = self.execute_inputs(Path(temporary), source_data=data)
            with mock.patch.object(Image, 'open', side_effect=AssertionError('Pixel allocation')):
                with self.assertRaisesRegex(ValueError, 'bounded repair canvas'):
                    repair.execute(*paths, 'unused', 'unused')
            self.assertFalse(paths[2].exists())

    def test_execute_rejects_small_compressed_oversized_paint_before_open(self):
        with tempfile.TemporaryDirectory() as temporary:
            paths = self.execute_inputs(Path(temporary), painted_data=compressed_png())
            with mock.patch.object(Image, 'open', side_effect=AssertionError('Pixel allocation')):
                with self.assertRaisesRegex(ValueError, 'bounded painted canvas'):
                    repair.execute(*paths, 'unused', 'unused')
            self.assertFalse(paths[2].exists())

    def test_direct_repair_checks_lazy_painted_dimensions_before_load(self):
        with Image.open(io.BytesIO(compressed_png())) as painted:
            with mock.patch.object(painted, 'load', side_effect=AssertionError('Pixel allocation')) as load:
                with self.assertRaisesRegex(ValueError, 'bounded painted canvas'):
                    repair.repair(self.source(), painted)
                load.assert_not_called()

    def test_execute_valid_bounded_png_reproduces_the_reviewed_operation(self):
        with tempfile.TemporaryDirectory() as temporary:
            paths = self.execute_inputs(Path(temporary))
            source = self.source()
            key = repair.digest(struct.pack('<II', *source.size) + source.tobytes())
            record = repair.execute(*paths, key, repair.digest(paths[1].read_bytes()))
            expected, metrics = repair.repair(source, Image.open(paths[1]))
            with Image.open(paths[2]) as actual:np.testing.assert_array_equal(np.asarray(expected), np.asarray(actual))
            self.assertEqual(metrics, {k:record[k] for k in metrics})

    def test_duplicate_source_header_is_rejected_before_copy_or_load(self):
        with tempfile.TemporaryDirectory() as temporary:
            paths=self.execute_inputs(Path(temporary),source_data=duplicate_header_png())
            with mock.patch.object(Image.Image,'copy',side_effect=AssertionError('Pixel copy')),mock.patch.object(Image.Image,'load',side_effect=AssertionError('Pixel allocation')):
                with self.assertRaisesRegex(ValueError,'bounded repair canvas'):
                    repair.execute(*paths,'unused','unused')
            self.assertFalse(paths[2].exists())

    def test_duplicate_painted_header_is_rejected_before_load_or_resize(self):
        with tempfile.TemporaryDirectory() as temporary:
            paths=self.execute_inputs(Path(temporary),painted_data=duplicate_header_png(first=(8,8)))
            key=repair.digest(struct.pack('<II',*self.source().size)+self.source().tobytes())
            original_open=Image.open
            def guarded_open(stream):
                image=original_open(stream)
                if image.size==(4096,4096):
                    image.copy=mock.Mock(side_effect=AssertionError('Pixel copy'))
                    image.load=mock.Mock(side_effect=AssertionError('Pixel allocation'))
                    image.resize=mock.Mock(side_effect=AssertionError('Pixel resize'))
                return image
            with mock.patch.object(Image,'open',side_effect=guarded_open):
                with self.assertRaisesRegex(ValueError,'bounded painted canvas'):
                    repair.execute(*paths,key,repair.digest(paths[1].read_bytes()))
            self.assertFalse(paths[2].exists())

    def test_decoder_size_and_mode_must_match_first_header_even_below_caps(self):
        for data in [duplicate_header_png(second=(12,12)), duplicate_header_png(second=(2,2),colour_type=2,first_colour_type=6)]:
            with Image.open(io.BytesIO(data)) as image:
                with mock.patch.object(image,'load',side_effect=AssertionError('Pixel allocation')):
                    with self.assertRaisesRegex(ValueError,'differs from the validated header'):
                        repair.validate_opened_png(image,(2,2),'painted',(2,2))


if __name__ == '__main__':
    unittest.main()
