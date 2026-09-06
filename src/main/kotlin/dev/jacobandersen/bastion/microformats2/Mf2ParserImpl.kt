package dev.jacobandersen.bastion.microformats2

import org.springframework.stereotype.Service

/**
 * The default [Mf2Parser] implementation. Stateless: every parse runs in its
 * own [Mf2ParseSession].
 */
@Service
class Mf2ParserImpl : Mf2Parser {
    override fun parse(html: String, baseUrl: String): Mf2ParseResult {
        return Mf2ParseSession(html, baseUrl).parse()
    }
}
