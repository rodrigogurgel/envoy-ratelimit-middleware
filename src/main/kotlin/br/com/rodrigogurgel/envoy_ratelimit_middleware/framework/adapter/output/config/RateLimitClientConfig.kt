package br.com.rodrigogurgel.envoy_ratelimit_middleware.framework.adapter.output.config

import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.envoyproxy.envoy.service.ratelimit.v3.RateLimitServiceGrpc
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class RateLimitClientConfig {

    @Bean(destroyMethod = "shutdown")
    fun rateLimitChannel(): ManagedChannel =
        ManagedChannelBuilder
            .forAddress("localhost", 8081)
            .usePlaintext()
            .build()

    @Bean
    fun rateLimitBlockingStub(
        channel: ManagedChannel
    ): RateLimitServiceGrpc.RateLimitServiceBlockingStub =
        RateLimitServiceGrpc.newBlockingStub(channel)
}
