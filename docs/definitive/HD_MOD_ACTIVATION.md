# Managed HD module activation

The managed installation applies visual defaults at each player-visible mod boot,
including existing campaigns whose saved selection predates the HD modules.
Defaults do not rewrite an existing campaign's `enabled_mods` or save bytes.

Both launcher properties must be present:

- `definitive.artworkProfile`: `hd` or `original`.
- `definitive.hdModPreferences`: an absolute path ending in
  `definitive-hd-mods.properties` in the installation's owned data directory.

| Profile | Installed modules enabled by default |
| --- | --- |
| `hd` | EnvHD, CharHD, UIHD, FxHD, FMVHD, Skurfa backgrounds, ModelsHD |
| `original` | EnvHD, CharHD, UIHD, FxHD, FMVHD |

The existing launcher checkbox still controls Skurfa backgrounds and ModelsHD.
If those modules are present under `original`, their Mods checkboxes are disabled
with help explaining the launcher choice, rather than offering an ineffective edit.
Each profile honors explicit per-module opt-outs. Missing managed artifacts are
excluded from effective selections; requested custom mods, including missing
custom IDs, continue through the regular loader. Missing or invalid launcher
properties retain upstream selection behavior. `bootMods` stays the raw entry
point used by save conversion; player-visible flows use `bootVisibleMods`.

## Explicit menu choices

The Mods screen shows the effective choices without adding default-enabled IDs to
the stored campaign selection. Checkbox changes are staged locally. Returning a
checkbox to its initial displayed state also restores its original stored
membership, so merely opening the screen or toggling back is not a campaign edit.
Campaigns without the selection key stage the same all-installed fallback used by
loading, including their custom gameplay mods; the key stays absent on open or NO.

For an existing campaign, YES writes the accepted visual choices in one atomic
preferences replacement and applies the normal campaign edit. NO discards both
staged selections. For a new campaign, staged choices affect previews and remain
through reopening the Mods screen; they are persisted on Start Game. Leaving the
new-campaign screen without starting does not persist them.

A failed preferences write leaves the Mods/new-campaign screen available to retry
or cancel, shows a plain error, and does not proceed to the campaign save/start.
The preferences file is bounded to 8 KiB, validates known choices, refuses linked
or non-regular targets, and preserves unknown future keys. No defaults file is
created merely by booting. Accepted writes synchronize the file and its containing
directory after atomic replacement on the supported Linux/macOS platforms.
Campaign config saving otherwise retains upstream
behavior; the two separate files are not a cross-file crash transaction.

## Verification

`ManagedModProfileTest` covers defaults, profile filtering, durable opt-outs,
custom-mod preservation, invalid properties, malformed/oversized/linked files,
batch rejection, and concurrent choices. `ManagedModSelectionTest` exercises real
Checkbox handlers and the production existing-campaign confirmation seam,
including NO, retry after write failure, re-enable, and abandoned new campaigns.

`ManagedModBootProbe` runs actual discovery, constructors, events, registries and
`bootVisibleMods` in a separate headless JVM. Supply a directory containing exactly
the seven packaged mod JARs and a fresh preferences directory as its two arguments.
Use the packaged engine JAR, runtime dependencies and compiled probe on its
classpath: the loader's built-in locale discovery requires packaged resources.
The probe checks all-seven legacy activation, the five-module original profile,
custom gameplay selection, durable opt-outs, staged preview without writes,
unmanaged loading and the raw required-core-only save-conversion boot.

The loader probe passed with the seven actual module JARs from the `f5d6907f9`
source build, including CharHD 0.3; the earlier `617b221f8` probe remains a historical checkpoint. These checks establish activation and headless loading, not visual,
gameplay, physical Steam Deck performance or long-term stability acceptance.
