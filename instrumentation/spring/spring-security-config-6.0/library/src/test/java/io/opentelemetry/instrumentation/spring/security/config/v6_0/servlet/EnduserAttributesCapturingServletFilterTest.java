/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.security.config.v6_0.servlet;

import io.opentelemetry.instrumentation.spring.security.config.v6_0.EnduserAttributesCapturer;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import io.opentelemetry.semconv.incubating.EnduserIncubatingAttributes;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;

class EnduserAttributesCapturingServletFilterTest {

  @RegisterExtension InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  /**
   * Tests to ensure enduser attributes are captured.
   *
   * <p>This just tests one scenario of {@link EnduserAttributesCapturer} to ensure that it is
   * invoked properly by the filter. {@link
   * io.opentelemetry.instrumentation.spring.security.config.v6_0.EnduserAttributesCapturerTest}
   * tests many other scenarios.
   */
  @SuppressWarnings("deprecation") // using deprecated semconv
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
                    Arrays.asList(
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
                    span.hasAttribute(EnduserIncubatingAttributes.ENDUSER_ID, "principal")
                        .hasAttribute(EnduserIncubatingAttributes.ENDUSER_ROLE, "role1,role2")
                        .hasAttribute(EnduserIncubatingAttributes.ENDUSER_SCOPE, "scope1,scope2")));
  }
}
