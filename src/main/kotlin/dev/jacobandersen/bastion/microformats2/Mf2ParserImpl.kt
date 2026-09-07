package dev.jacobandersen.bastion.microformats2

/**
 * The default [Mf2Parser] implementation. Stateless and safe for concurrent
 * reuse: every parse runs in its own [Mf2ParseSession].
 */
class Mf2ParserImpl : Mf2Parser {
    override fun parse(
        html: String,
        baseUrl: String,
    ): Mf2ParseResult = Mf2ParseSession(html, baseUrl).parse()
}
