package dev.jacobandersen.bastion.micropub.type.resp

class SyndicateToResponse(val syndicateTo: List<SyndicateTo>) {
    data class SyndicateTo(val uid: String, val name: String, val service: ServiceInfo?) {
        data class ServiceInfo(val name: String, val url: String, val photo: String)
    }
}