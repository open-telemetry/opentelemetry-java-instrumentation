/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.security.config.v6_0.servlet;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.v3Preview;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_REQUEST_METHOD;
import static io.opentelemetry.semconv.incubating.EnduserIncubatingAttributes.ENDUSER_ID;
import static io.opentelemetry.semconv.incubating.EnduserIncubatingAttributes.ENDUSER_ROLE;
import static io.opentelemetry.semconv.incubating.EnduserIncubatingAttributes.ENDUSER_SCOPE;
import static io.opentelemetry.semconv.incubating.UserIncubatingAttributes.USER_ID;
import static io.opentelemetry.semconv.incubating.UserIncubatingAttributes.USER_ROLES;
import static java.util.Arrays.asList;

import io.opentelemetry.instrumentation.spring.security.config.v6_0.EnduserAttributesCapturer;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;

@SuppressWarnings("deprecation") // using deprecated semconv
class EnduserAttributesCapturingServletFilterTest {

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  /**
   * Tests to ensure enduser attributes are captured.
   *
   * <p>This just tests one scenario of {@link EnduserAttributesCapturer} to ensure that it is
   * invoked properly by the filter. {@link
   * io.opentelemetry.instrumentation.spring.security.config.v6_0.EnduserAttributesCapturerTest}
   * tests many other scenarios.
   */
  @Test
  void test() throws Exception {

    EnduserAttributesCapturer capturer = new EnduserAttributesCapturer();
    capturer.setEnduserIdEnabled(true);
    capturer.setEnduserRoleEnabled(true);
    capturer.setEnduserScopeEnabled(true);
    EnduserAttributesCapturingServletFilter filter =
        new EnduserAttributesCapturingServletFilter(capturer);

    testing.runWithHttpServerSpan(
        () -> {
          ServletRequest request = new MockHttpServletRequest();
          ServletResponse response = new MockHttpServletResponse();
          FilterChain filterChain = new MockFilterChain();

          SecurityContext previousSecurityContext = SecurityContextHolder.getContext();
          try {
            SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
            securityContext.setAuthentication(
                new PreAuthenticatedAuthenticationToken(
                    "principal",
                    null,
                    asList(
                        new SimpleGrantedAuthority("ROLE_role1"),
                        new SimpleGrantedAuthority("ROLE_role2"),
                        new SimpleGrantedAuthority("SCOPE_scope1"),
                        new SimpleGrantedAuthority("SCOPE_scope2"))));
            SecurityContextHolder.setContext(securityContext);

            filter.doFilter(request, response, filterChain);
          } finally {
            SecurityContextHolder.setContext(previousSecurityContext);
          }
        });

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasAttributesSatisfyingExactly(
                        equalTo(ERROR_TYPE, "_OTHER"),
                        equalTo(HTTP_REQUEST_METHOD, "GET"),
                        equalTo(v3Preview() ? USER_ID : ENDUSER_ID, "principal"),
                        equalTo(USER_ROLES, v3Preview() ? asList("role1", "role2") : null),
                        equalTo(ENDUSER_ROLE, v3Preview() ? null : "role1,role2"),
                        equalTo(ENDUSER_SCOPE, v3Preview() ? null : "scope1,scope2"))));
  }
}
