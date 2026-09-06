package dev.jacobandersen.bastion.config

import org.springframework.aot.hint.RuntimeHints
import org.springframework.aot.hint.RuntimeHintsRegistrar

/**
 * Registers the resources the app needs at native-image runtime that cannot be
 * discovered by scanning, most notably the Flyway SQL migrations under
 * `db/migration`.
 */
class BastionNativeHints : RuntimeHintsRegistrar {
    override fun registerHints(hints: RuntimeHints, classLoader: ClassLoader?) {
        hints.resources().registerPattern("db/migration/*.sql")
    }
}
