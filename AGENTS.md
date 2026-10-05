# Project agent memory

This file is the project's committed home for project-intrinsic agent knowledge: build, test, release, architecture, and
sharp-edge notes that should travel with the code.

- Add durable project-specific notes here as they are discovered through real work.

## Maintaining this file

Keep this file for knowledge useful to almost every future agent session in this project.
Do not repeat what the codebase already shows; point to the authoritative file or command instead.
Prefer rewriting or pruning existing entries over appending new ones.
When updating this file, preserve this bar for all agents and keep entries concise.

## Build and test

- `./gradlew test` runs the full suite; the Spring Boot tests use Testcontainers (needs Docker).
- `./gradlew ktlintCheck` runs the linter (ktlint 1.8.0).
- Jackson 3 (`tools.jackson.*`) is used, not Jackson 2; `@JsonProperty` still comes from
  `com.fasterxml.jackson.annotation`.
- JobRunr: methods invoked from a scheduled/enqueued job lambda must not use Kotlin default parameter values - JobRunr
  fails to schedule them (`UnsupportedOperationException` at `JobDetailsBuilder`). Lambda args are also serialized once
  at registration and replayed on every fire, so never pass a computed time (e.g. `Instant.now()`) through the lambda -
  compute it inside the invoked method, as `IndieAuthRowPurgeService.purge()` does.

## IndieAuth

- Bastion is its own IndieAuth provider (`dev.jacobandersen.bastion.indieauth`): no local accounts, GitHub is the only
  identity provider, the browser UI is delegated to the "Herald" service via `bastion.indieauth.herald.*`.
- Server metadata (`/.well-known/oauth-authorization-server`, `type/AuthorizationServerMetadata.kt`) advertises the
  `issuer` (trailing-slash normalized, https-only via `util/Issuers.kt`), token/introspection/revocation/userinfo
  endpoints, `service_documentation`, and `authorization_response_iss_parameter_supported=true`. The metadata URL
  itself is discovered via the `indieauth-metadata` link relation published manually on the external `me` site
  (Bastion serves no profile pages); see `DiscoveryController.kt`.
- The authorization redirect carries `code` + `state` + `iss` (`util/Redirects.kt`); `iss` must equal the metadata
  issuer for mix-up protection.
- Code redemption is split per spec: POST `/indieauth/auth` returns `{me}` only (`ProfileUrlController` +
  `ProfileUrlService`), POST `/indieauth/token` returns tokens (`TokenController` + `AccessTokenService`) and
  rejects empty-scope codes with `invalid_grant`. Token responses carry `expires_in` and rotate a `refresh_token`
  (`grant_type=refresh_token`, same-or-narrower scope, single-use rotation via `RefreshTokenService` and the
  `indieauth_refresh_tokens` table, TTLs `access-token-ttl` 30d / `refresh-token-ttl` 90d).
- Introspection (`/indieauth/introspect`, Bearer-gated, RFC 7662 plus `me`), revocation (`/indieauth/revocation`,
  always 200), and userinfo (`/indieauth/userinfo`, needs `profile`/`email` scope) are served and advertised.
  Profile claims come from static `bastion.indieauth.profile.*` config (`ProfileClaimService`); `email` needs both
  `profile` and `email` scopes. Default scopes include `email` (`IndieAuthConfig`).
- Micropub token validation (`MicropubTokenValidator`) resolves against Bastion-issued access tokens, not an external
  token endpoint.
- Raw `state`/`code`/`access_token`/`refresh_token` values are never persisted - only their SHA-256 digests
  (`indieauth/security/Tokens.kt`). PKCE is lenient for max client compat (deprecated): a missing `code_challenge`
  is accepted with a warning, S256-only when present, and the redemption enforces the conditional rule (no challenge
  means no verifier, challenge means a matching verifier is required).
- Client validation (`util/IndieAuthUrls.kt`, `service/ClientMetadataFetcher.kt`) enforces strict profile/client URL
  rules and the 4.2.2 cross-host redirect allowlist (JSON `redirect_uris`, `Link rel=redirect_uri`, HTML link tags).
  Loopback clients are never fetched; inconclusive fetches allow with a warning (fail-open), a fetched allowlist
  missing the target blocks.
- Adding a provider = one new `IdentityProvider` implementation plus config.

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
