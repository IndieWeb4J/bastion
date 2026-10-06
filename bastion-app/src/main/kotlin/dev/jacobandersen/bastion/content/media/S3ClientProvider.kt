package dev.jacobandersen.bastion.content.media

import org.springframework.context.annotation.Bean
import org.springframework.stereotype.Component
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.S3Configuration
import java.net.URI

@Component
class S3ClientProvider(
    private val cfg: BastionMediaConfiguration,
) {
    @Bean
    fun amazonS3Client(): S3Client {
        val s3Config =
            S3Configuration
                .builder()
                .chunkedEncodingEnabled(false)
                .pathStyleAccessEnabled(true)
                .build()

        return S3Client
            .builder()
            .endpointOverride(URI.create(cfg.s3.endpoint))
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(
                        cfg.s3.accessKeyId,
                        cfg.s3.secretAccessKey,
                    ),
                ),
            ).region(Region.of(cfg.s3.region))
            .serviceConfiguration(s3Config)
            .build()
    }
}
