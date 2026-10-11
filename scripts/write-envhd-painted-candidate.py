#!/usr/bin/env python3
"""Publish a complete custom PNG at a fresh, non-linked candidate path."""
import io
import os
from pathlib import Path
import secrets


def write_candidate(image, path):
    """Reject every existing output; link complete bytes without replacing history.

    Directory descriptors and O_NOFOLLOW prevent linked parents from redirecting
    the write. The exclusive temporary is removed even if encoding or publication
    fails. A hard link advertises the completed file atomically, without clobbering
    an output created concurrently. This is not an idempotent overwrite operation.
    """
    path = Path(path).absolute()
    if '..' in path.parts:
        raise ValueError('Candidate path must not contain parent traversal')
    buffer = io.BytesIO()
    image.save(buffer, format='PNG')
    data = buffer.getvalue()
    flags = os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW
    directory = os.open(path.anchor, flags)
    temporary = None
    try:
        for component in path.parts[1:-1]:
            try:
                os.mkdir(component, dir_fd=directory)
            except FileExistsError:
                pass
            try:
                child = os.open(component, flags, dir_fd=directory)
            except OSError as error:
                raise ValueError('Candidate parent must be a non-symlink directory') from error
            os.close(directory)
            directory = child
        name = '.painted-candidate-' + secrets.token_hex(16)
        descriptor = os.open(name, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW,
                             0o644, dir_fd=directory)
        temporary = name
        with os.fdopen(descriptor, 'wb') as stream:
            stream.write(data)
            stream.flush()
            os.fsync(stream.fileno())
        try:
            os.link(temporary, path.name, src_dir_fd=directory, dst_dir_fd=directory,
                    follow_symlinks=False)
        except FileExistsError as error:
            raise ValueError('Use a fresh candidate output path') from error
        return data
    finally:
        if temporary is not None:
            try:
                os.unlink(temporary, dir_fd=directory)
            except FileNotFoundError:
                pass
        os.close(directory)
