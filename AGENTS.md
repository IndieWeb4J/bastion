# Project agent memory

This file is the project's committed home for project-intrinsic agent knowledge: build, test, release, architecture, and
sharp-edge notes that should travel with the code.

- Add durable project-specific notes here as they are discovered through real work.

## Maintaining this file

Keep this file for knowledge useful to almost every future agent session in this project.
Do not repeat what the codebase already shows; point to the authoritative file or command instead.
Prefer rewriting or pruning existing entries over appending new ones.
When updating this file, preserve this bar for all agents and keep entries concise.

## Repository layout

Multi-module: `content-client/` (the published content contract: read/write/media
clients + `content.post.*` event schemas) and `bastion-app/` (the Spring Boot
server, `bootJar` -> `bastion-app/build/libs/bastion.jar`). Bastion owns the
content contract, so it is a module here (mirroring sigil-client/beacon-client),
not a separate repo. `bastion-app` depends on `project(":content-client")`.

## Build and test

- `./gradlew test` runs the full suite; the Spring Boot tests use Testcontainers (needs Docker).
- `./gradlew ktlintCheck` runs the linter (ktlint 1.8.0).
- `./gradlew :content-client:publishToMavenLocal` publishes the contract for
  local consumers (Forge/Beacon); CI publishes it to Bastion's GitHub Packages on release.
- Jackson 3 (`tools.jackson.*`) is used, not Jackson 2; `@JsonProperty` still comes from
  `com.fasterxml.jackson.annotation`.
- JobRunr: methods invoked from a scheduled/enqueued job lambda must not use Kotlin default parameter values - JobRunr
  fails to schedule them (`UnsupportedOperationException` at `JobDetailsBuilder`). Lambda args are also serialized once
  at registration and replayed on every fire, so never pass a computed time (e.g. `Instant.now()`) through the lambda -
  compute it inside the invoked method, as `IndieAuthRowPurgeService.purge()` does.

## Service boundaries

Bastion is the **content authority** only: it stores posts as mf2, owns media,
serves the public read API, and emits `content.post.*` events. The protocol
concerns were extracted and are no longer here:

- **Micropub** -> Forge (no storage).
- **IndieAuth** -> Sigil (Bastion no longer validates tokens; no sigil-client).
- **Webmention** -> Beacon.
- **WebSub + syndication** -> Conduit.

The `micropub/` package now holds only the post domain (`data/`, `media/`,
`type/`); the protocol code was removed in `25fb780`.

## Read model (projections)

Bastion projects the distribution services' events so the public read API stays a
single local query: `ProjectionConsumer` consumes Beacon's `WEBMENTION` stream
(`webmention.>`) and Conduit's `SYNDICATION` stream (`syndication.>`) into
`projected_webmentions` / `projected_syndications` (idempotent upserts, keyed by
source/post and post/target). `PublicPostController` reads those. Streams are
per-producer; consumers attach durably and share the same NATS connection as the
content publisher (`bastion.content.events.nats.*`, gate with `...enabled=true`).

## Data moves (V20)

Webmention/syndication tables (`received_webmentions`, `webmention_notifications`,
`webmention_endpoint_cache`, `post_syndications`) and the Micropub `tokens` table
are dropped by `V20__decommission_webmention_syndication.sql`; their state lives
in Beacon/Conduit now, and the projections rebuild from events (or the services'
reconciliation sweeps). Never drop tables permanently without a backfill plan.

