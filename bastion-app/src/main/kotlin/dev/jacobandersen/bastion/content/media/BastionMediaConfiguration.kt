package dev.jacobandersen.bastion.content.media

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "bastion.media")
data class BastionMediaConfiguration(
    val baseUrl: String,
    val s3: S3Configuration,
    val orphanSweep: OrphanSweep = OrphanSweep(),
) {
    data class S3Configuration(
        val accessKeyId: String,
        val secretAccessKey: String,
        val endpoint: String,
        val region: String,
        val bucket: String,
    )

    /**
     * Deletes S3 objects not referenced by any stored post and older than
     * [ttlHours] (a grace window so an in-flight upload is never deleted).
     * Disabled by default; enable per environment.
     */
    data class OrphanSweep(
        val enabled: Boolean = false,
        val ttlHours: Long = 168,
    )
}
