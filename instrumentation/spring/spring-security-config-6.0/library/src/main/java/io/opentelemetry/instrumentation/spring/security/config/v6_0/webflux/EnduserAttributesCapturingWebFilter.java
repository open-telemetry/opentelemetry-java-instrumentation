/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.security.config.v6_0.webflux;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.reactor.v3_1.ContextPropagationOperator;
import io.opentelemetry.instrumentation.spring.security.config.v6_0.EnduserAttributesCapturer;
import java.util.Objects;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * A {@link WebFilter} that captures {@code endpoint.*} semantic attributes from the {@link
 * org.springframework.security.core.Authentication} in the current {@link
 * org.springframework.security.core.context.SecurityContext} retrieved from {@link
 * ReactiveSecurityContextHolder}.
 *
 * <p>Inserted into the filter chain by {@link EnduserAttributesServerHttpSecurityCustomizer} after
 * all the filters that populate the {@link
 * org.springframework.security.core.context.SecurityContext} in the {@link
 * org.springframework.security.core.context.ReactiveSecurityContextHolder}.
 */
public class EnduserAttributesCapturingWebFilter implements WebFilter {

  private final EnduserAttributesCapturer capturer;

  public EnduserAttributesCapturingWebFilter(EnduserAttributesCapturer capturer) {
    this.capturer = Objects.requireNonNull(capturer, "capturer must not be null");
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {

    Context threadLocalOtelContext = Context.current();

    return Mono.zip(ReactiveSecurityContextHolder.getContext(), Mono.deferContextual(Mono::just))
        .doOnNext(
            t2 ->
                capturer.captureEnduserAttributes(
                    ContextPropagationOperator.getOpenTelemetryContext(
                        reactor.util.context.Context.of(t2.getT2()), threadLocalOtelContext),
                    t2.getT1().getAuthentication()))
        .then(chain.filter(exchange));
  }
}
