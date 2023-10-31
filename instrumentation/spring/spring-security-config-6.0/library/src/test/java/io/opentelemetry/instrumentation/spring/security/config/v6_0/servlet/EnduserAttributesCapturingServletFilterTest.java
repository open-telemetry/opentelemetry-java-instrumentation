/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.security.config.v6_0.servlet;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.spring.security.config.v6_0.EnduserAttributesCapturer;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.semconv.SemanticAttributes;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import java.util.Arrays;
import java.util.List;
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
  @Test
  void test() throws Exception {

    EnduserAttributesCapturer capturer = new EnduserAttributesCapturer();
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

    List<SpanData> spans = testing.spans();
    assertThat(spans)
        .singleElement()
        .satisfies(
            span -> {
              assertThat(span.getAttributes().get(SemanticAttributes.ENDUSER_ID))
                  .isEqualTo("principal");
              assertThat(span.getAttributes().get(SemanticAttributes.ENDUSER_ROLE))
                  .isEqualTo("role1,role2");
              assertThat(span.getAttributes().get(SemanticAttributes.ENDUSER_SCOPE))
                  .isEqualTo("scope1,scope2");
            });
  }
}
