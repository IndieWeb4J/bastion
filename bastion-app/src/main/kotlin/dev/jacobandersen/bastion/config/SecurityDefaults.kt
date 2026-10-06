package dev.jacobandersen.bastion.config

import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy

/**
 * Shared baseline applied to every SecurityFilterChain.
 *
 * Ordering contract for future path-scoped chains:
 * - Order 1 .. n for chains with a securityMatcher (e.g. /internal)
 * - Ordered.LOWEST_PRECEDENCE for the catch-all chain with no matcher
 *   (GlobalSecurityConfig.defaultSecurityFilterChain)
 *
 * Each chain should call applyBastionDefaults and then add its own matcher, filters and
 * authorization rules. This keeps CORS, CSRF, stateless session and login method settings
 * consistent without coupling module-specific auth into the global config.
 */
fun HttpSecurity.applyBastionDefaults(): HttpSecurity =
    cors { }
        .csrf { it.disable() }
        .formLogin { it.disable() }
        .httpBasic { it.disable() }
        .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
