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

## IndieAuth

- IndieAuth lives in the standalone **Sigil** service now, not Bastion. Bastion holds no IndieAuth state; it
  validates Micropub bearer tokens by calling Sigil's RFC 7662 introspection endpoint through the published
  `dev.jacobandersen:sigil-client` library (`TokenIntrospector`), wired by its Spring Boot auto-configuration via
  `sigil.client.base-url`. The library owns the cache and the wire types; the expected identity
  (`bastion.sigil.me`) is Bastion policy in `MicropubTokenValidator`.
- Introspection is self-authorized (bearer == subject token), so no service credential is configured.
- The old `indieauth_*` tables are dropped by `V16__drop_indieauth_tables.sql`; existing tokens are invalidated.
- Building Bastion needs the Sigil GitHub Packages Maven repo (`packages: read` in CI; a PAT with `read:packages`
  in `~/.gradle/gradle.properties` for local `.` builds). Pin the `sigilVersion` in `build.gradle.kts` to the
  released `sigil-client` version; `publishToMavenLocal` in the Sigil repo is the escape hatch during development.

## Micropub syndication

- Syndication targets are downstream micropub servers (e.g. Bridgy) configured under
  `bastion.micropub.syndication.targets` (`application.yaml`): each has `uid`, `name`, `endpoint`, optional bearer
  `token`, and supported `actions` (default CREATE + DELETE). `mp-syndicate-to` values must match a target `uid`, and
  the configured targets back the `q=syndicate-to` and `q=config` `syndicate-to` responses.
- Non-public posts are never sent to targets. Requested targets are recorded at create time even for a draft/private
  post, then dispatched on the later publish transition; the async create/rebase jobs re-check `Post.publiclyReachable`
  at job time because a post can be demoted or deleted between dispatch and send. A target only holds a copy once
  `post_syndications.syndicated_url` is recorded.
- Dispatch and the best-effort `@Job(retries = 0)` jobs live in `micropub/syndication/SyndicationService.kt`.
  Syndication is fire-and-forget: failures are logged and must never fail the originating create/update/delete request.
- The downstream copy is an excerpt plus permalink, never full content (`micropub/syndication/SyndicationContentMapper.kt`):
  notes send `{excerpt}\n\n{url}`, articles (`type == "article"`) send `{name}: {excerpt}\n\n{url}`. The
  `maxGraphemes` budget covers the total in grapheme clusters (`Graphemes.kt`, Bluesky counts graphemes not chars);
  the default is 300 via `defaultMaxGraphemes`, overridable per target with `max-graphemes`. The permalink is never
  dropped; `summary` is overwritten with the same text so full content cannot leak through it.
- Updates go through the same budget: `runUpdateJob` rewrites `content`/`summary` entries via
  `SyndicationHttpClient.mapUpdateToExcerpt`, and an article rename without a content change injects a
  `content` replace so the downstream title does not go stale.
- `mp-syndicate-to` is honored on update as well as create (`SyndicationService.diffTargets`): a `replace`
  entry is the desired target set, otherwise `add`/`delete` apply incrementally. Added targets are syndicated
  (or retained when non-public); removed targets are retracted via `retractTargets` (best-effort delete when a
  copy is held, then the record is forgotten).

## Webmention

- Self-webmentions are suppressed: `UrlService.isOwnContentUrl` (host + port match on `bastion.content.base-url`,
  lenient on scheme) filters targets in `WebmentionService.targetUrlsOf` plus a guard in `sendWebmention`, and
  `WebmentionController` rejects sources on the own content domain.
