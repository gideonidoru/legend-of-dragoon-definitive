# CharHD shared texture ownership

This increment fixes resource ownership without selecting new character artwork or geometry. It is isolated from the unfinished party-head reconstruction branch.

## Requirements

- Immutable decoded CharHD images with the same checksum and dimensions share one CPU image. Each rigid model part keeps independent face coordinates.
- Parts using that image share one GPU upload while they coexist. Deleting one part must not invalidate another part's texture or a draw retained until renderer retirement.
- The final owner releases its texture at renderer retirement. Closing a lease twice must be harmless. Upload failure must leave no reusable failed entry.
- Forced renderer teardown may retire a texture before its old owners. A subsequent acquisition must create a new upload; retiring the old owners must not delete the new entry.
- Preserve the existing opaque character-paint contract, source checksums, geometry ownership priority and fail-closed loader behavior.
- Do not select experimental heads, hair, bodies, or any new runtime resources. All existing normal CharHD artwork and model payloads remain byte-for-byte unchanged.

## Implementation and scope

`TmdFaceDetail.Image` owns immutable pixel storage separately from per-part UVs. The CharHD loader caches this image by validated texture checksum and dimensions. `SharedFaceTexture` gives each rendered part a lease on one GPU upload. `Obj` releases the lease when it retires the mesh, after retained draws can use it.

The cache is confined to the existing renderer thread. This change introduces no background upload, disk cache, rendering setting or image-quality transformation. It does not include the draft alpha-cutout support or alter texture-unit binding behavior.

## Verification boundaries

Ownership tests use a simulated upload/delete backend and cover sharing, independent UVs, immutable pixels, retained object retirement, repeated close, upload failure and forced teardown. Loader tests cover the supported source and appearance routes. The existing selected resource inventory is compared directly with the accepted parent commit.

These tests establish source-level ownership behavior. They do not establish physical Steam Deck memory consumption, live GPU behavior, new visual acceptance, or readiness of the eight experimental character heads.
