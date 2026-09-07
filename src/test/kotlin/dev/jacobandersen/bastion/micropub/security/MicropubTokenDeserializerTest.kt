package dev.jacobandersen.bastion.micropub.security

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper

class MicropubTokenDeserializerTest {
    private val mapper = JsonMapper.builder().build()

    private fun deserialize(scope: String): MicropubToken {
        val json = """{"me":"https://me.example","client_id":"https://client.example","scope":"$scope"}"""
        return mapper.readValue(json, MicropubToken::class.java)
    }

    @Test
    fun ignoresUnknownScopesAndKeepsKnownOnes() {
        val token = deserialize("create media bogus draft")
        assertEquals(listOf(MicropubTokenScope.CREATE, MicropubTokenScope.MEDIA), token.scope)
    }

    @Test
    fun ignoresBlankTokens() {
        val token = deserialize(" create   update ")
        assertEquals(listOf(MicropubTokenScope.CREATE, MicropubTokenScope.UPDATE), token.scope)
    }

    @Test
    fun unknownOnlyOrBlankScopeYieldsEmptyList() {
        assertEquals(emptyList<MicropubTokenScope>(), deserialize("bogus draft").scope)
        assertEquals(emptyList<MicropubTokenScope>(), deserialize("   ").scope)
    }

    @Test
    fun knownScopesMatchCaseInsensitively() {
        val token = deserialize("Create MeDiA")
        assertEquals(listOf(MicropubTokenScope.CREATE, MicropubTokenScope.MEDIA), token.scope)
    }
}
