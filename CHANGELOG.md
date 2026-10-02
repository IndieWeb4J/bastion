# Changelog

## [1.3.28](https://github.com/jacobsandersen/bastion/compare/v1.3.27...v1.3.28) (2026-10-02)


### Features

* **indieauth:** allow empty-scope login-only flows ([#63](https://github.com/jacobsandersen/bastion/issues/63)) ([a0b7640](https://github.com/jacobsandersen/bastion/commit/a0b764068144af34229cff9dca57790e2acfbeb4))

## [1.3.27](https://github.com/jacobsandersen/bastion/compare/v1.3.26...v1.3.27) (2026-10-02)


### Features

* **api:** expose post syndications on single post lookup ([#61](https://github.com/jacobsandersen/bastion/issues/61)) ([d83c41c](https://github.com/jacobsandersen/bastion/commit/d83c41cca969a0070d9683c3a8fe1340177a6784))

## [1.3.26](https://github.com/jacobsandersen/bastion/compare/v1.3.25...v1.3.26) (2026-10-02)


### Features

* support adding and removing syndication targets via update ([#59](https://github.com/jacobsandersen/bastion/issues/59)) ([7d6513a](https://github.com/jacobsandersen/bastion/commit/7d6513a68420e4c52d7f63fbe929cae7d57685f5))

## [1.3.25](https://github.com/jacobsandersen/bastion/compare/v1.3.24...v1.3.25) (2026-10-02)


### Features

* block self-webmentions and syndicate excerpts with permalink ([#57](https://github.com/jacobsandersen/bastion/issues/57)) ([c893336](https://github.com/jacobsandersen/bastion/commit/c8933363c709a9fa60d6b20cd66e3a2c30376db3))

## [1.3.24](https://github.com/jacobsandersen/bastion/compare/v1.3.23...v1.3.24) (2026-10-01)


### Features

* support profile scope for quill ([190c7dc](https://github.com/jacobsandersen/bastion/commit/190c7dcee183bf049c016a7c300dd304c524e125))

## [1.3.23](https://github.com/jacobsandersen/bastion/compare/v1.3.22...v1.3.23) (2026-09-25)


### Features

* untagged post lookup ([#54](https://github.com/jacobsandersen/bastion/issues/54)) ([4d5b1f1](https://github.com/jacobsandersen/bastion/commit/4d5b1f1bd99c060fba7695d4dc330b2933a43702))

## [1.3.22](https://github.com/jacobsandersen/bastion/compare/v1.3.21...v1.3.22) (2026-09-25)


### Features

* add tag listing and tag-filtered post feed ([#52](https://github.com/jacobsandersen/bastion/issues/52)) ([fd11609](https://github.com/jacobsandersen/bastion/commit/fd11609ac790dbe73f0abae390c0e662be9bbd3b))

## [1.3.21](https://github.com/jacobsandersen/bastion/compare/v1.3.20...v1.3.21) (2026-09-25)


### Bug Fixes

* fix url service test to accept variable schemes ([6d0912e](https://github.com/jacobsandersen/bastion/commit/6d0912e7bdd3ce53768de667d6773034f6ef0557))

## [1.3.20](https://github.com/jacobsandersen/bastion/compare/v1.3.19...v1.3.20) (2026-09-25)


### Bug Fixes

* make schema lenient on URL checking to allow for http/https mixing ([f8b0b82](https://github.com/jacobsandersen/bastion/commit/f8b0b82c2f6fad0ce3ddbfdd98b9c6676a671ef4))

## [1.3.19](https://github.com/jacobsandersen/bastion/compare/v1.3.18...v1.3.19) (2026-09-24)


### Features

* add more filters for feed lookup, tighten post lookup, improve post response ([c1e6d45](https://github.com/jacobsandersen/bastion/commit/c1e6d45a76ae3547026fedf5254eeb4a3117db81))

## [1.3.18](https://github.com/jacobsandersen/bastion/compare/v1.3.17...v1.3.18) (2026-09-23)


### Features

* **api:** require non-null for generated post and webmention fields ([#46](https://github.com/jacobsandersen/bastion/issues/46)) ([1155ad1](https://github.com/jacobsandersen/bastion/commit/1155ad1dd5d1dfbf0eb2656be91fd1094109de0d))

## [1.3.17](https://github.com/jacobsandersen/bastion/compare/v1.3.16...v1.3.17) (2026-09-23)


### Features

* **webmention:** return all content entries as arrays ([#44](https://github.com/jacobsandersen/bastion/issues/44)) ([6a55b49](https://github.com/jacobsandersen/bastion/commit/6a55b49c58dab1ef2666d782c3871fe677c40a42))

## [1.3.16](https://github.com/jacobsandersen/bastion/compare/v1.3.15...v1.3.16) (2026-09-23)


### Features

* **api:** return all content entries as arrays in PostResponse ([#42](https://github.com/jacobsandersen/bastion/issues/42)) ([d5de37e](https://github.com/jacobsandersen/bastion/commit/d5de37e67a58a6106aac34385de514f76ca8c9c2))

## [1.3.15](https://github.com/jacobsandersen/bastion/compare/v1.3.14...v1.3.15) (2026-09-23)


### Features

* add tertiary type support ([#41](https://github.com/jacobsandersen/bastion/issues/41)) ([38cec13](https://github.com/jacobsandersen/bastion/commit/38cec1394e253717ade74cf33d3acb25850de337))
* enable lowercase post-type in feed query ([958f15e](https://github.com/jacobsandersen/bastion/commit/958f15e3f5a5efbdd8ac28a6d9b44b299584f23b))

## [1.3.14](https://github.com/jacobsandersen/bastion/compare/v1.3.13...v1.3.14) (2026-09-23)


### Features

* **api:** replace graphql with rest post feed and lookup ([#38](https://github.com/jacobsandersen/bastion/issues/38)) ([9fbb243](https://github.com/jacobsandersen/bastion/commit/9fbb243db5e2b596d7a6aeaaa0c822666cdfa97d))

## [1.3.13](https://github.com/jacobsandersen/bastion/compare/v1.3.12...v1.3.13) (2026-09-22)


### Features

* **graphql:** type Post.properties as mf2 union ([#36](https://github.com/jacobsandersen/bastion/issues/36)) ([dec9bd7](https://github.com/jacobsandersen/bastion/commit/dec9bd75f0da1a3cbbba13e9fa828de0f77721f2))

## [1.3.12](https://github.com/jacobsandersen/bastion/compare/v1.3.11...v1.3.12) (2026-09-22)


### Features

* **graphql:** add year/month/day filtering to posts feed ([#34](https://github.com/jacobsandersen/bastion/issues/34)) ([a6fe08e](https://github.com/jacobsandersen/bastion/commit/a6fe08ef2c94a07682b32ae8a20c51612ebf101c))

## [1.3.11](https://github.com/jacobsandersen/bastion/compare/v1.3.10...v1.3.11) (2026-09-21)


### Bug Fixes

* **micropub:** treat blank command values as absent ([#32](https://github.com/jacobsandersen/bastion/issues/32)) ([482d5ca](https://github.com/jacobsandersen/bastion/commit/482d5caf9fb9f82768d50d281895caaf15dbf055))

## [1.3.10](https://github.com/jacobsandersen/bastion/compare/v1.3.9...v1.3.10) (2026-09-21)


### Features

* **cors:** share public wildcard CORS source between micropub and indieauth ([#30](https://github.com/jacobsandersen/bastion/issues/30)) ([8b54c7a](https://github.com/jacobsandersen/bastion/commit/8b54c7a2d102f4d83f4dab2ec38e6067421cbf02))

## [1.3.9](https://github.com/jacobsandersen/bastion/compare/v1.3.8...v1.3.9) (2026-09-21)


### Features

* **indieauth:** allow any origin for token and discovery via dedicated CORS chain ([#28](https://github.com/jacobsandersen/bastion/issues/28)) ([c806fa5](https://github.com/jacobsandersen/bastion/commit/c806fa5573aedf0f712f1ae9d222397c58c76c62))

## [1.3.8](https://github.com/jacobsandersen/bastion/compare/v1.3.7...v1.3.8) (2026-09-21)


### Features

* **indieauth:** expose providers for Herald auth page ([#26](https://github.com/jacobsandersen/bastion/issues/26)) ([2c83a93](https://github.com/jacobsandersen/bastion/commit/2c83a939f5c68c60dc6baafe4776b1fdbd4b8145))

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
