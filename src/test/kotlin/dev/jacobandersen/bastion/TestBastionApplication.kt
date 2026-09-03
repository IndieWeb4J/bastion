package dev.jacobandersen.bastion

import org.springframework.boot.fromApplication
import org.springframework.boot.with


fun main(args: Array<String>) {
    fromApplication<BastionApplication>().with(TestcontainersConfiguration::class).run(*args)
}
