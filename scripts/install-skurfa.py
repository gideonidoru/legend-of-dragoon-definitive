#!/usr/bin/env python3
"""Install a pinned, user-obtained Skurfa archive locally without executing it."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import tempfile
import zipfile


def digest(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def install(archive, mods):
    manifest = json.loads((Path(__file__).resolve().parents[1] / "delivery/mods/skurfa.json").read_text())
    if digest(archive) != manifest["archive_sha256"]:
        raise ValueError("Archive checksum does not match the pinned release; nothing installed")
    name = Path(manifest["jar_member"]).name
    if mods.is_symlink():
        raise ValueError("Choose a real mods directory, not a symbolic link")
    mods.mkdir(parents=True, exist_ok=True)
    destination = mods / name
    if destination.exists() or destination.is_symlink():
        if not destination.is_symlink() and digest(destination) == manifest["jar_sha256"]:
            print("Already installed and checksum verified:", destination)
            return
        raise ValueError("Destination contains a different file; preserve/remove it explicitly first")
    others = [p for p in mods.glob("*.jar") if "skurfa" in p.name.lower()]
    if others:
        raise ValueError("Another Skurfa version is present; preserve/remove it explicitly first")
    temporary = None
    try:
        with zipfile.ZipFile(archive) as package:
            matches = [item for item in package.infolist() if item.filename == manifest["jar_member"]]
            if len(matches) != 1:
                raise ValueError("Archive must contain exactly one expected mod JAR")
            with package.open(matches[0]) as source, tempfile.NamedTemporaryFile(dir=mods, delete=False) as output:
                temporary = Path(output.name)
                while chunk := source.read(8 * 1024 * 1024):
                    output.write(chunk)
            if digest(temporary) != manifest["jar_sha256"]:
                raise ValueError("Mod JAR checksum mismatch; nothing installed")
            # Exclusive creation preserves any file that appeared after preflight.
            os.link(temporary, destination)
        print("Installed and checksum verified:", destination)
    finally:
        if temporary is not None:
            temporary.unlink(missing_ok=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("archive", type=Path)
    parser.add_argument("--mods", type=Path, default=Path("mods"))
    args = parser.parse_args()
    try:
        install(args.archive, args.mods)
    except (OSError, ValueError, zipfile.BadZipFile) as error:
        parser.exit(1, str(error) + "\n")
