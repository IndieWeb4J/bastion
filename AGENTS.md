# Project agent memory

This file is the project's committed home for project-intrinsic agent knowledge: build, test, release, architecture, and sharp-edge notes that should travel with the code.

- Add durable project-specific notes here as they are discovered through real work.

## Maintaining this file

Keep this file for knowledge useful to almost every future agent session in this project.
Do not repeat what the codebase already shows; point to the authoritative file or command instead.
Prefer rewriting or pruning existing entries over appending new ones.
When updating this file, preserve this bar for all agents and keep entries concise.

## Build and test

- `./gradlew test` runs the full suite; the Spring Boot tests use Testcontainers (needs Docker).
- `./gradlew ktlintCheck` runs the linter (ktlint 1.8.0).
- Jackson 3 (`tools.jackson.*`) is used, not Jackson 2; `@JsonProperty` still comes from `com.fasterxml.jackson.annotation`.
- JobRunr: methods invoked from a scheduled/enqueued job lambda must not use Kotlin default parameter values - JobRunr fails to schedule them (`UnsupportedOperationException` at `JobDetailsBuilder`). Lambda args are also serialized once at registration and replayed on every fire, so never pass a computed time (e.g. `Instant.now()`) through the lambda - compute it inside the invoked method, as `IndieAuthRowPurgeService.purge()` does.

## IndieAuth

- Bastion is its own IndieAuth provider (`dev.jacobandersen.bastion.indieauth`): no local accounts, GitHub is the only identity provider, the browser UI is delegated to the "Herald" service via `bastion.indieauth.herald.*`.
- Micropub token validation (`MicropubTokenValidator`) resolves against Bastion-issued access tokens, not an external token endpoint.
- Raw `state`/`code`/`access_token` values are never persisted - only their SHA-256 digests (`indieauth/security/Tokens.kt`). PKCE is required and S256-only: every authorization request must carry an S256 `code_challenge`.
- Adding a provider = one new `IdentityProvider` implementation plus config.
