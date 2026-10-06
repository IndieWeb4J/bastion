package dev.jacobandersen.bastion.content.event

/**
 * Publishes a content event to the bus. Implemented over NATS JetStream when a
 * broker is configured; the no-op implementation keeps local deployments (and
 * tests) working before the bus exists.
 */
fun interface ContentEventPublisher {
    fun publish(
        subject: String,
        partitionKey: String?,
        payload: String,
    )
}

/** No-op publisher used when no event bus is configured. */
object NoopContentEventPublisher : ContentEventPublisher {
    override fun publish(
        subject: String,
        partitionKey: String?,
        payload: String,
    ) = Unit
}
