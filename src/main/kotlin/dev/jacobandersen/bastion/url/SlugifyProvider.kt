package dev.jacobandersen.bastion.url

import com.github.slugify.Slugify
import org.springframework.context.annotation.Bean
import org.springframework.stereotype.Component
import java.util.Locale

@Component
class SlugifyProvider {
    @Bean
    fun slugify(): Slugify {
        return Slugify.builder()
            .lowerCase(true)
            .transliterator(true)
            .underscoreSeparator(false)
            .locale(Locale.ENGLISH)
            .build()
    }
}