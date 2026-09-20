# Changelog

## [1.3.7](https://github.com/jacobsandersen/bastion/compare/v1.3.6...v1.3.7) (2026-09-20)


### Bug Fixes

* **db:** revert formatting of SQL migrations to restore Flyway checksums ([#24](https://github.com/jacobsandersen/bastion/issues/24)) ([115722d](https://github.com/jacobsandersen/bastion/commit/115722dccdd34a7fbd18e3724c9d7d0d4dc69e8d))

## [1.3.6](https://github.com/jacobsandersen/bastion/compare/v1.3.5...v1.3.6) (2026-09-20)


### Miscellaneous Chores

* fix formatting according to ktlint ([2e60b23](https://github.com/jacobsandersen/bastion/commit/2e60b23f2b81c4131fd1f748fe29689ce9034bbe))

## [1.3.5](https://github.com/jacobsandersen/bastion/compare/v1.3.4...v1.3.5) (2026-09-20)


### Miscellaneous Chores

* cleanup ([1b98bf7](https://github.com/jacobsandersen/bastion/commit/1b98bf7bf2d3f9628df6508971c4ac22e52f903a))

## [1.3.4](https://github.com/jacobsandersen/bastion/compare/v1.3.3...v1.3.4) (2026-09-18)

### Miscellaneous Chores

* externalize micropub syndication target config and clean up prod properties
  ([d737090](https://github.com/jacobsandersen/bastion/commit/d7370903b01ab80cd7dedfc2cb0173174e1bd63e))
* unnecessary non-null assertion
  ([ef8dfa3](https://github.com/jacobsandersen/bastion/commit/ef8dfa332afb07a97bd20fd2c41457572e1aadfc))

## [1.3.3](https://github.com/jacobsandersen/bastion/compare/v1.3.2...v1.3.3) (2026-09-09)

### Features

* **indieauth:** make Bastion its own IndieAuth provider ([#16](https://github.com/jacobsandersen/bastion/issues/16))
  ([2bc8936](https://github.com/jacobsandersen/bastion/commit/2bc89369ff90175d89846a376de16025f3f8a6bb))
* **micropub:** syndicate posts to downstream micropub targets
  ([#18](https://github.com/jacobsandersen/bastion/issues/18))
  ([c54a2da](https://github.com/jacobsandersen/bastion/commit/c54a2da93d12cfae66d6379d1eb7683ecddfc3e5))
* **websub:** publish posts over WebSub (PubSubHubbub)
  ([c48058d](https://github.com/jacobsandersen/bastion/commit/c48058d1cf6fb47914973abfd6d3edc1d747b3a5))
* **websub:** publish posts over WebSub (PubSubHubbub)
  ([2b41fa8](https://github.com/jacobsandersen/bastion/commit/2b41fa8bcf00204a10b9f02f8cc5a758a86bdb26))

### Bug Fixes

* **indieauth:** require PKCE, harden error handling, purge dead auth rows
  ([#17](https://github.com/jacobsandersen/bastion/issues/17))
  ([36d1a4a](https://github.com/jacobsandersen/bastion/commit/36d1a4a453df63c0878c9089012eb3b8a8b0dd77))

## [1.3.2](https://github.com/jacobsandersen/bastion/compare/v1.3.1...v1.3.2) (2026-09-07)

### Code Refactoring

* deslop the webmention, micropub and mf2 layers ([#11](https://github.com/jacobsandersen/bastion/issues/11))
  ([a478f24](https://github.com/jacobsandersen/bastion/commit/a478f24590d850da817bba0bcfca1b24a97a4482))

### Continuous Integration

* bump patch releases on every merge and surface all commit types in the changelog
  ([01b81f8](https://github.com/jacobsandersen/bastion/commit/01b81f898d5552aab40987cc185283e1b2aaa187))

## [1.3.1](https://github.com/jacobsandersen/bastion/compare/v1.3.0...v1.3.1) (2026-09-07)

### Bug Fixes

* update posts through the managed entity without touching generated columns
  ([5588056](https://github.com/jacobsandersen/bastion/commit/55880567bf0ca9e7994082262d91b112ac975424))

## [1.3.0](https://github.com/jacobsandersen/bastion/compare/v1.2.4...v1.3.0) (2026-09-07)

### Features

* return a gone result for deleted posts in graphql
  ([f43a589](https://github.com/jacobsandersen/bastion/commit/f43a589aebb413011bd01110ba34d6a369e652cb))

## [1.2.4](https://github.com/jacobsandersen/bastion/compare/v1.2.3...v1.2.4) (2026-09-07)

### Bug Fixes

* cache discovery only when advertised, rediscover on updates, and add cleanup job
  ([60be18e](https://github.com/jacobsandersen/bastion/commit/60be18e5b98bacfeda24f3528adaa9d2f6bc3a17))

## [1.2.3](https://github.com/jacobsandersen/bastion/compare/v1.2.2...v1.2.3) (2026-09-07)

### Bug Fixes

* skip webmention rel elements without an href during discovery
  ([237582b](https://github.com/jacobsandersen/bastion/commit/237582b0f1e9c97f50d798c8ef8dcdf56bb84609))

## [1.2.2](https://github.com/jacobsandersen/bastion/compare/v1.2.1...v1.2.2) (2026-09-07)

### Bug Fixes

* ignore unknown scopes when deserializing micropub tokens
  ([94b8186](https://github.com/jacobsandersen/bastion/commit/94b818639cee2354ee5c8a3774a5680652cf3787))
* reject micropub tokens issued for other identities
  ([49eebee](https://github.com/jacobsandersen/bastion/commit/49eebee7c5a2201e61ff5afcc59d4282a2616d1a))

## [1.2.1](https://github.com/jacobsandersen/bastion/compare/v1.2.0...v1.2.1) (2026-09-07)

### Bug Fixes

* allow graphql schema introspection
  ([f6e9e49](https://github.com/jacobsandersen/bastion/commit/f6e9e49af903045818fe1ce66728c597355a15f5))

## [1.2.0](https://github.com/jacobsandersen/bastion/compare/v1.1.0...v1.2.0) (2026-09-07)

### Features

* classify rsvp webmentions
  ([a7fd161](https://github.com/jacobsandersen/bastion/commit/a7fd161c69db7e8703ea255114a4c1dd162fd592))
* expose webmentions and counts over graphql
  ([d70bc78](https://github.com/jacobsandersen/bastion/commit/d70bc78c5565f5c5623ef67b48dba557998e3efa))

### Bug Fixes

* base webmention undelete dispatch on public content not deletion
  ([efbe6cc](https://github.com/jacobsandersen/bastion/commit/efbe6cc25190d9bc9ef317156b5b097f24e2c42e))
* implement the current graphql coercing api in the json scalar
  ([505569f](https://github.com/jacobsandersen/bastion/commit/505569f6e53362a1866eb629587e3a9ed631ab9e))
* make publiclyReachable account for deleted posts
  ([277e66a](https://github.com/jacobsandersen/bastion/commit/277e66ac8e85ff50579ae4f83300954cbae71edc))

## [1.1.0](https://github.com/jacobsandersen/bastion/compare/v1.0.1...v1.1.0) (2026-09-07)

### Features

* expose published posts over a public graphql api
  ([5c94376](https://github.com/jacobsandersen/bastion/commit/5c94376c99edbeb0fa2523d471f2a05fe4fbe0ad))

## [1.0.1](https://github.com/jacobsandersen/bastion/compare/v1.0.0...v1.0.1) (2026-09-06)

### Bug Fixes

* scope the micropub auth filter to the micropub security chain
  ([88a39a1](https://github.com/jacobsandersen/bastion/commit/88a39a18df8eac11bc83bdd17514ac71245806e0))

## 1.0.0 (2026-09-06)

### Features

* add extractors for webmention text and links
  ([8a5a2a5](https://github.com/jacobsandersen/bastion/commit/8a5a2a5789ece2c541a52703dc6d3b7a94a021e3))
* add generic microformats2 html parser
  ([382cb67](https://github.com/jacobsandersen/bastion/commit/382cb67772a69fe769a126e0f227a00d4bec17c8))
* add received webmention entity and repository
  ([72eac16](https://github.com/jacobsandersen/bastion/commit/72eac160b3030805c85028047995194c18f1f44b))
* add webmention http and discovery helpers
  ([0170cda](https://github.com/jacobsandersen/bastion/commit/0170cda65a37f093ac6e3cb567263f404beece96))
* add webmention http client
  ([5a30f92](https://github.com/jacobsandersen/bastion/commit/5a30f9273415ff122d65ccbb93081fe95d59bc45))
* add webmention receive controller with request verification
  ([60d6227](https://github.com/jacobsandersen/bastion/commit/60d6227e02ba73d3d13dced4869ffa5be4ec06ad))
* add webmention settings
  ([7782ab6](https://github.com/jacobsandersen/bastion/commit/7782ab6f60bd6063b7138d2b4d1fb27a1fb7fa8f))
* detect webmention interaction types from source documents
  ([2a546c3](https://github.com/jacobsandersen/bastion/commit/2a546c31699c44b3e5257295c5595ba7f33acb1f))
* dispatch webmentions on post lifecycle
  ([72e1e1e](https://github.com/jacobsandersen/bastion/commit/72e1e1e3981214bb746e0aead28becab90c7af7b))
* initial commit with working micropub
  ([26618d6](https://github.com/jacobsandersen/bastion/commit/26618d6f662f6437a7e77deb346d900dfc973ba0))
* send and retry webmentions
  ([d9a0ca0](https://github.com/jacobsandersen/bastion/commit/d9a0ca01381dd4f0eef3245dfa5f7e4680a4bf22))
* track webmention notifications
  ([d34d3be](https://github.com/jacobsandersen/bastion/commit/d34d3be0288a33b9ace5f9e941d4ff829d17d41c))
* verify received webmention sources asynchronously
  ([7efc7d4](https://github.com/jacobsandersen/bastion/commit/7efc7d4cd105729ea760082416e8aa954fad8afb))

### Bug Fixes

* classify post types per the post-type discovery algorithm
  ([c34b73d](https://github.com/jacobsandersen/bastion/commit/c34b73de7efbc386bb2ac7f093ad01f40bbf5d10))
* include indieauth response body in token validation logs
  ([f9783fe](https://github.com/jacobsandersen/bastion/commit/f9783febc8991fcd17600ca7501706ed8dc23319))
* include receiver response body in webmention failures
  ([05f42f0](https://github.com/jacobsandersen/bastion/commit/05f42f021514fd4aa888957bde1956f1f14ccd05))
* persist mf2 objects through hibernate json
  ([16e4eeb](https://github.com/jacobsandersen/bastion/commit/16e4eebca2f16642d941c2fbdc57ad5bdef96359))
