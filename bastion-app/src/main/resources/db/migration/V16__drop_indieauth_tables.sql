-- IndieAuth has moved to the standalone Sigil service. Access tokens are no
-- longer stored or validated locally, so the provider tables are dropped and
-- existing tokens are invalidated (holders re-authenticate against Sigil).
drop table if exists indieauth_refresh_tokens;
drop table if exists indieauth_access_tokens;
drop table if exists indieauth_authorization_codes;
drop table if exists indieauth_authorization_requests;
