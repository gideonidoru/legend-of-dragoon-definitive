# Exact raw archive assembly

`scripts/archive_assembly.py` plans byte-for-byte ZIP/JAR reconstruction using
immutable whole-source ranges. It does not inflate entries, recompress archives,
upload assets, change a release manifest, install files or activate an update.
Publisher and manager integration remain separate work.

## Contract

The external format has exactly these fields:

```json
{
  "format": 1,
  "sourceRevision": "<40 lowercase hexadecimal characters>",
  "platform": "macos-arm64",
  "releaseTag": "definitive-current",
  "sources": {
    "<whole-source SHA-256>": {"id": "<positive decimal asset ID>", "bytes": 123}
  },
  "files": {
    "bundled-mods/ExampleHD.zip": [
      {"sha256": "<raw-span SHA-256>", "bytes": 123,
       "source": "<whole-source SHA-256>", "offset": 0}
    ]
  }
}
```

Examples use placeholders; these are not valid hash or ID values. Platforms are
`linux-x64` and `macos-arm64`. Source IDs are unique positive decimal strings,
without leading zeroes, at most `2**53 - 1`. Every source is referenced. Spans are
concatenated in listed order; an offset refers to bytes of the immutable source,
not an entry's inflated contents. Exact duplicate ranges may be shared; other
overlapping ranges are rejected. A managed path is a canonical printable ASCII
relative POSIX path without traversal, control characters, backslashes or colons.
A file and its directory cannot both appear as managed files.

The existing managed inventory supplies each final file's SHA-256 and size.
Those final identities are essential: the recipe alone cannot authorize different
output bytes or a different managed file set. The publisher must bind the exact
revision, platform, carrier, source asset IDs and whole-source size/hash identities
against a fresh immutable release inventory. Schema validation alone does not
establish server ownership or freshness.

## Helper seam

`FileIdentity(path, sha256, bytes)` supplies an expected whole-file identity.
`plan_files(targets, baselines, bundle_directory, baseline_ranges=None)` accepts
managed target paths and verified old whole-file sources indexed by SHA. It hashes
all inputs, matches raw spans by hash and size, and deterministically packs changed
spans into `file-SHA` bundles. Identical changed bytes are stored once across files.
The returned `AssemblyPlan` contains ID-free source sizes, ordered file spans,
new upload identities and the expected final file identities.

For subsequent delta generations, call
`verified_recipe_ranges(previous_recipe, source_files, fresh_asset_ids)` and pass
its result as `baseline_ranges`. This checks the prior recipe's full source
bindings, whole-file hashes and every range hash. It allows reuse from prior raw
bundles that are not complete ZIPs. Planning validates and rehashes the catalog
again. Extra baseline whole files may be supplied alongside that catalog.

`bind_recipe(plan, revision, platform, tag, asset_ids)` requires an exact source-ID
closure. `load_recipe(bytes)` rejects duplicate JSON keys and malformed or
oversized recipes. `prove_plan(plan, source_files)` reads the actual ordered
mixed-source concatenation, checks every span, and checks final SHA and size
against the managed inventory without writing an installation.

Use a dedicated trusted output directory. Input files must be regular files with
the expected size; source symlinks are rejected. Bundles are created exclusively,
and a retry accepts an existing bundle only after verifying its bytes. Failure
removes only temporary and newly created bundle files owned by that attempt;
existing files and input archives are preserved.

## Bounds and archive validation

Sources and assembled files must be positive-sized and smaller than 2 GiB.
Each span and changed bundle is at most 16 MiB; I/O reads are at most 1 MiB.
Limits are 30,000 files, 1,000 sources, 100,000 planned spans, 100,000 aggregate
baseline spans, 100,000 ZIP entries, 32 MiB central-directory metadata and a
16 MiB serialized recipe. Metadata count and directory geometry are checked
before Python's ZIP parser allocates entry objects.

ZIP local records are bounded by their physical header offsets. Headers, central
directory sizes, names, CRC identities and signed/unsigned data descriptors must
agree. Duplicate names or offsets, overlapping payloads, missing/truncated tails,
unaccounted local padding, encryption, archive prefixes and multi-disk archives
are rejected. Standard fixed-size ZIP64 end records and local ZIP64 size fields
are supported within the same limits; extensible ZIP64 end records are rejected.
ZIP comments, including embedded end-record signatures, remain exact wire bytes.
Oversized records and the central-directory/end tail are split into bounded raw
spans. Ordinary non-archive files use fixed 16 MiB raw blocks. Required `.zip` or
`.jar` targets cannot silently fall back when their header is malformed.

## Headless evidence and integration limits

Run `python3 scripts/test-archive-assembly.py` for synthetic behavior controls.
The optional `--real-env EVIDENCE_ROOT --baseline PRESERVED_RELEASE --output
PRIVATE_OUTPUT` replay reads private release receipts and four Env pack pairs.
Keep original game data and private diagnostics outside public deliverables.

The verified old0af-to-fb94 replay reconstructs all four packs exactly:

| Measure | Bytes/count |
| --- | ---: |
| Full current packs | 2,368,751,813 bytes |
| Reused bytes | 2,366,333,533 bytes |
| Changed upload bundle | 2,418,280 bytes; one source |
| Ordered spans | 9,846 |
| Reused spans | 9,792 |

This is an offline byte proof using synthetic asset-ID bindings, not a published
recipe or a client update. The private recipe is 1,831,678 bytes. Fine record spans
reduce uploads but can increase request counts; the assembler must coalesce
verified adjacent ranges or use verified local files where appropriate. HTTP
range behavior, CDN retries, asset limits, transfer performance and real client
activation are not established by this helper.

Publication must retain every whole source referenced by active recipes, even
when it is an older full pack or an earlier bundle. Commit the new release closure
only after verifying its complete immutable sources and final managed inventory.
Clients must assemble in temporary storage, verify span and final identities, and
activate only after complete success. Failed range retrieval or missing sources
must leave the current installation intact; complete installation fallback is a
separate installer policy.
