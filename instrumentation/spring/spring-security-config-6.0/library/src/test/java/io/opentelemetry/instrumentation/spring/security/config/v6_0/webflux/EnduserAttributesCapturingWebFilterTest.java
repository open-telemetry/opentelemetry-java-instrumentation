/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.security.config.v6_0.webflux;

import static io.opentelemetry.semconv.incubating.EnduserIncubatingAttributes.ENDUSER_ID;
import static io.opentelemetry.semconv.incubating.EnduserIncubatingAttributes.ENDUSER_ROLE;
import static io.opentelemetry.semconv.incubating.EnduserIncubatingAttributes.ENDUSER_SCOPE;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.reactor.v3_1.ContextPropagationOperator;
import io.opentelemetry.instrumentation.spring.security.config.v6_0.EnduserAttributesCapturer;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.web.server.handler.DefaultWebFilterChain;
import reactor.core.publisher.Mono;

@SuppressWarnings("deprecation") // using deprecated semconv
class EnduserAttributesCapturingWebFilterTest {

  @RegisterExtension
  static InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  /**
   * Tests to ensure enduser attributes are captured.
   *
   * <p>This just tests one scenario of {@link EnduserAttributesCapturer} to ensure that it is
   * invoked properly by the filter. {@link
   * io.opentelemetry.instrumentation.spring.security.config.v6_0.EnduserAttributesCapturerTest}
   * tests many other scenarios.
   */
  @Test
  void test() {
    EnduserAttributesCapturer capturer = new EnduserAttributesCapturer();
    capturer.setEnduserIdEnabled(true);
    capturer.setEnduserRoleEnabled(true);
    capturer.setEnduserScopeEnabled(true);
    EnduserAttributesCapturingWebFilter filter = new EnduserAttributesCapturingWebFilter(capturer);

    testing.runWithHttpServerSpan(
        () -> {
          MockServerHttpRequest request = MockServerHttpRequest.get("/").build();
          MockServerWebExchange exchange = MockServerWebExchange.from(request);
          DefaultWebFilterChain filterChain =
              new DefaultWebFilterChain(exch -> Mono.empty(), emptyList());
          Context otelContext = Context.current();
          filter
              .filter(exchange, filterChain)
              .contextWrite(
                  ReactiveSecurityContextHolder.withAuthentication(
                      new PreAuthenticatedAuthenticationToken(
                          "principal",
                          null,
                          asList(
                              new SimpleGrantedAuthority("ROLE_role1"),
                              new SimpleGrantedAuthority("ROLE_role2"),
                              new SimpleGrantedAuthority("SCOPE_scope1"),
                              new SimpleGrantedAuthority("SCOPE_scope2")))))
              .contextWrite(
                  context ->
                      ContextPropagationOperator.storeOpenTelemetryContext(context, otelContext))
              .block();
        });

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasAttribute(ENDUSER_ID, "principal")
                        .hasAttribute(ENDUSER_ROLE, "role1,role2")
                        .hasAttribute(ENDUSER_SCOPE, "scope1,scope2")));
  }
}
