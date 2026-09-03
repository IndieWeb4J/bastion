package dev.jacobandersen.bastion.micropub.security.auth

import dev.jacobandersen.bastion.micropub.security.MicropubToken
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.service.annotation.GetExchange
import org.springframework.web.service.annotation.PostExchange

interface IndieAuthService {
    @PostExchange(
        headers = ["Content-Type=application/x-www-form-urlencoded", "Accept=application/json"]
    )
    fun modernValidation(@RequestParam("token") token: String): MicropubToken

    @GetExchange(
        accept = ["application/json"]
    )
    fun legacyValidation(@RequestHeader("Authorization") bearerToken: String): MicropubToken
}