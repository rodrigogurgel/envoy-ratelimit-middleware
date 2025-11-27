package br.com.rodrigogurgel.envoy_ratelimit_middleware.framework.adapter.input.rest.filter

import com.github.michaelbull.result.andThen
import com.github.michaelbull.result.runCatching
import com.github.michaelbull.result.toErrorIf
import io.envoyproxy.envoy.extensions.common.ratelimit.v3.RateLimitDescriptor
import io.envoyproxy.envoy.service.ratelimit.v3.RateLimitRequest
import io.envoyproxy.envoy.service.ratelimit.v3.RateLimitResponse
import io.envoyproxy.envoy.service.ratelimit.v3.RateLimitServiceGrpc
import kotlinx.coroutines.reactor.mono
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.nio.charset.StandardCharsets
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.getOrThrow
import com.github.michaelbull.result.recoverIf

data class RateLimitExceededException(override val message: String? = null) : RuntimeException(message)

@Component
class ReactiveRateLimitingFilter(
    private val rateLimitClient: RateLimitServiceGrpc.RateLimitServiceBlockingStub
) : WebFilter {
    companion object {
        private val logger = LoggerFactory.getLogger(ReactiveRateLimitingFilter::class.java)
    }

    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        val apiKey = exchange.request.headers.getFirst("x-api-key") ?: "anonymous"
        val apiProduct = exchange.request.headers.getFirst("x-api-product") ?: "anonymous"

        val response = shouldRateLimit("api_key", apiKey)
            .toErrorIf({ isOverLimit(it) }) {
                RateLimitExceededException()
            }
            .andThen { shouldRateLimit("api_product", apiProduct) }
            .toErrorIf({ isOverLimit(it) }) {
                RateLimitExceededException()
            }

        return Mono.fromCallable {
            response.getOrThrow()
        }
            .subscribeOn(Schedulers.boundedElastic())
            .onErrorResume {
                logger.error("Error during rate limit", it)
                mono {
                    when (it) {
                        is RateLimitExceededException -> RateLimitResponse.newBuilder()
                            .setOverallCode(RateLimitResponse.Code.OVER_LIMIT).build()
                        else -> RateLimitResponse.newBuilder()
                            .setOverallCode(RateLimitResponse.Code.OK).build()
                    }
                }
            }
            .flatMap { response ->
                if (response.overallCode == RateLimitResponse.Code.OVER_LIMIT) {
                    val response = exchange.response
                    response.statusCode = HttpStatus.TOO_MANY_REQUESTS

                    val buffer = response.bufferFactory()
                        .wrap("Too Many Requests".toByteArray(StandardCharsets.UTF_8))

                    response.writeWith(Mono.just(buffer))
                } else {
                    chain.filter(exchange)
                }
            }
    }

    private fun shouldRateLimit(key: String, value: String) = runCatching {
        val descriptor = RateLimitDescriptor.newBuilder()
            .addEntries(
                RateLimitDescriptor.Entry.newBuilder()
                    .setKey(key)
                    .setValue(value),
            )
            .build()


        val request = RateLimitRequest.newBuilder()
            .setDomain("envoy")
            .addDescriptors(descriptor)
            .build()

        rateLimitClient.shouldRateLimit(request)
    }

    private fun isOverLimit(response: RateLimitResponse): Boolean =
        response.overallCode == RateLimitResponse.Code.OVER_LIMIT
}

