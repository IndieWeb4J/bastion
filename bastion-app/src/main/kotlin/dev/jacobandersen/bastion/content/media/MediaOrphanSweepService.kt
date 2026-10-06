package dev.jacobandersen.bastion.content.media

import dev.jacobandersen.bastion.content.PostRepository
import dev.jacobandersen.mf24j.Mf2Object
import dev.jacobandersen.mf24j.Mf2Value
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request
import java.time.Duration
import java.time.Instant

private val logger = KotlinLogging.logger {}

/**
 * Deletes media objects orphaned by post deletion: any object under the media
 * bucket not referenced by a stored post's mf2 and older than the grace window.
 * This is Bastion's compensation for media (it owns both the objects and the
 * posts), so it is a purely local sweep.
 */
@Service
class MediaOrphanSweepService(
    private val s3Client: S3Client,
    private val mediaConfiguration: BastionMediaConfiguration,
    private val postRepository: PostRepository,
) {
    fun sweep(): Int {
        val sweep = mediaConfiguration.orphanSweep
        if (!sweep.enabled) {
            logger.debug { "Media orphan sweep disabled" }
            return 0
        }

        val bucket = mediaConfiguration.s3.bucket
        val base = mediaConfiguration.baseUrl.trimEnd('/')
        val referenced = referencedKeys(base)
        val cutoff = Instant.now().minus(Duration.ofHours(sweep.ttlHours))

        val request = ListObjectsV2Request.builder().bucket(bucket).build()
        var deleted = 0
        for (page in s3Client.listObjectsV2Paginator(request)) {
            for (obj in page.contents()) {
                val key = obj.key()
                val lastModified = obj.lastModified()
                if (key !in referenced && lastModified != null && lastModified.isBefore(cutoff)) {
                    logger.info { "Deleting orphaned media object $key (last modified $lastModified)" }
                    s3Client.deleteObject(
                        DeleteObjectRequest
                            .builder()
                            .bucket(bucket)
                            .key(key)
                            .build(),
                    )
                    deleted++
                }
            }
        }
        if (deleted > 0) logger.info { "Media orphan sweep deleted $deleted object(s)" }
        return deleted
    }

    @Transactional(readOnly = true)
    fun referencedKeys(base: String): Set<String> =
        postRepository
            .findAll()
            .flatMap { entity -> mediaKeysIn(entity.post, base) }
            .toSet()

    private fun mediaKeysIn(
        obj: Mf2Object,
        base: String,
    ): List<String> {
        val out = mutableListOf<String>()
        obj.properties.values
            .flatten()
            .forEach { collect(it, base, out) }
        obj.children?.forEach { out += mediaKeysIn(it, base) }
        return out
    }

    private fun collect(
        value: Mf2Value,
        base: String,
        out: MutableList<String>,
    ) {
        when (value) {
            is Mf2Value.String -> {
                keyFor(value.value, base)?.let(out::add)
            }

            is Mf2Value.Object -> {
                out += mediaKeysIn(value.value, base)
            }

            is Mf2Value.Json -> {
                value.value
                    .get("value")
                    ?.takeIf { it.isString }
                    ?.asString()
                    ?.let { keyFor(it, base)?.let(out::add) }
                value.value
                    .get("html")
                    ?.takeIf { it.isString }
                    ?.asString()
                    ?.let { keyFor(it, base)?.let(out::add) }
            }

            else -> {
                Unit
            }
        }
    }

    private fun keyFor(
        url: String,
        base: String,
    ): String? =
        url
            .takeIf { it.startsWith("$base/") }
            ?.removePrefix("$base/")
            ?.substringBefore('?')
            ?.substringBefore('#')
}
