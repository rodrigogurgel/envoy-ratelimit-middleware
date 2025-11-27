package br.com.rodrigogurgel.envoy_ratelimit_middleware

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class EnvoyRatelimitMiddlewareApplication

fun main(args: Array<String>) {
	runApplication<EnvoyRatelimitMiddlewareApplication>(*args)
}
