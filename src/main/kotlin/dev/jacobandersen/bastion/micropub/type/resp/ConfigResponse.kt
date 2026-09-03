package dev.jacobandersen.bastion.micropub.type.resp

class ConfigResponse(
    val mediaEndpoint: String,
    val syndicateTo: List<SyndicateToResponse.SyndicateTo>
)