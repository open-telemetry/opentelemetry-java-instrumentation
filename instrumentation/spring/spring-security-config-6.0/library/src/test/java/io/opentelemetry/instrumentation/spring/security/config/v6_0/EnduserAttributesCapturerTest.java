/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.security.config.v6_0;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.semconv.SemanticAttributes;
import java.util.Arrays;
import java.util.List;
import org.assertj.core.api.ThrowingConsumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;

public class EnduserAttributesCapturerTest {

  @RegisterExtension InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @Test
  void defaults() {
    EnduserAttributesCapturer capturer = new EnduserAttributesCapturer();

    Authentication authentication =
        new PreAuthenticatedAuthenticationToken(
            "principal",
            null,
            Arrays.asList(
                new SimpleGrantedAuthority("ROLE_role1"),
                new SimpleGrantedAuthority("ROLE_role2"),
                new SimpleGrantedAuthority("SCOPE_scope1"),
                new SimpleGrantedAuthority("SCOPE_scope2")));

    ThrowingConsumer<SpanData> assertions =
        span -> {
          assertThat(span.getAttributes().get(SemanticAttributes.ENDUSER_ID))
              .isEqualTo("principal");
          assertThat(span.getAttributes().get(SemanticAttributes.ENDUSER_ROLE))
              .isEqualTo("role1,role2");
          assertThat(span.getAttributes().get(SemanticAttributes.ENDUSER_SCOPE))
              .isEqualTo("scope1,scope2");
        };

    test(capturer, authentication, assertions);
  }

  @Test
  void noRoles() {
    EnduserAttributesCapturer capturer = new EnduserAttributesCapturer();

    Authentication authentication =
        new PreAuthenticatedAuthenticationToken(
            "principal",
            null,
            Arrays.asList(
                new SimpleGrantedAuthority("SCOPE_scope1"),
                new SimpleGrantedAuthority("SCOPE_scope2")));

    ThrowingConsumer<SpanData> assertions =
        span -> {
          assertThat(span.getAttributes().get(SemanticAttributes.ENDUSER_ID))
              .isEqualTo("principal");
          assertThat(span.getAttributes().get(SemanticAttributes.ENDUSER_ROLE)).isNull();
          assertThat(span.getAttributes().get(SemanticAttributes.ENDUSER_SCOPE))
              .isEqualTo("scope1,scope2");
        };

    test(capturer, authentication, assertions);
  }

  @Test
  void noScopes() {
    EnduserAttributesCapturer capturer = new EnduserAttributesCapturer();

    Authentication authentication =
        new PreAuthenticatedAuthenticationToken(
            "principal",
            null,
            Arrays.asList(
                new SimpleGrantedAuthority("ROLE_role1"),
                new SimpleGrantedAuthority("ROLE_role2")));

    ThrowingConsumer<SpanData> assertions =
        span -> {
          assertThat(span.getAttributes().get(SemanticAttributes.ENDUSER_ID))
              .isEqualTo("principal");
          assertThat(span.getAttributes().get(SemanticAttributes.ENDUSER_ROLE))
              .isEqualTo("role1,role2");
          assertThat(span.getAttributes().get(SemanticAttributes.ENDUSER_SCOPE)).isNull();
        };

    test(capturer, authentication, assertions);
  }

  @Test
  void disableEnduserId() {
    EnduserAttributesCapturer capturer = new EnduserAttributesCapturer();
    capturer.setEnduserIdEnabled(false);

    Authentication authentication =
        new PreAuthenticatedAuthenticationToken(
            "principal",
            null,
            Arrays.asList(
                new SimpleGrantedAuthority("ROLE_role1"),
                new SimpleGrantedAuthority("ROLE_role2"),
                new SimpleGrantedAuthority("SCOPE_scope1"),
                new SimpleGrantedAuthority("SCOPE_scope2")));

    ThrowingConsumer<SpanData> assertions =
        span -> {
          assertThat(span.getAttributes().get(SemanticAttributes.ENDUSER_ID)).isNull();
          assertThat(span.getAttributes().get(SemanticAttributes.ENDUSER_ROLE))
              .isEqualTo("role1,role2");
          assertThat(span.getAttributes().get(SemanticAttributes.ENDUSER_SCOPE))
              .isEqualTo("scope1,scope2");
        };

    test(capturer, authentication, assertions);
  }

  @Test
  void disableEnduserRole() {
    EnduserAttributesCapturer capturer = new EnduserAttributesCapturer();
    capturer.setEnduserRoleEnabled(false);

    Authentication authentication =
        new PreAuthenticatedAuthenticationToken(
            "principal",
            null,
            Arrays.asList(
                new SimpleGrantedAuthority("ROLE_role1"),
                new SimpleGrantedAuthority("ROLE_role2"),
                new SimpleGrantedAuthority("SCOPE_scope1"),
                new SimpleGrantedAuthority("SCOPE_scope2")));

    ThrowingConsumer<SpanData> assertions =
        span -> {
          assertThat(span.getAttributes().get(SemanticAttributes.ENDUSER_ID))
              .isEqualTo("principal");
          assertThat(span.getAttributes().get(SemanticAttributes.ENDUSER_ROLE)).isNull();
          assertThat(span.getAttributes().get(SemanticAttributes.ENDUSER_SCOPE))
              .isEqualTo("scope1,scope2");
        };

    test(capturer, authentication, assertions);
  }

  @Test
  void disableEnduserScope() {
    EnduserAttributesCapturer capturer = new EnduserAttributesCapturer();
    capturer.setEnduserScopeEnabled(false);

    Authentication authentication =
        new PreAuthenticatedAuthenticationToken(
            "principal",
            null,
            Arrays.asList(
                new SimpleGrantedAuthority("ROLE_role1"),
                new SimpleGrantedAuthority("ROLE_role2"),
                new SimpleGrantedAuthority("SCOPE_scope1"),
                new SimpleGrantedAuthority("SCOPE_scope2")));

    ThrowingConsumer<SpanData> assertions =
        span -> {
          assertThat(span.getAttributes().get(SemanticAttributes.ENDUSER_ID))
              .isEqualTo("principal");
          assertThat(span.getAttributes().get(SemanticAttributes.ENDUSER_ROLE))
              .isEqualTo("role1,role2");
          assertThat(span.getAttributes().get(SemanticAttributes.ENDUSER_SCOPE)).isNull();
        };

    test(capturer, authentication, assertions);
  }

  @Test
  void alternatePrefix() {
    EnduserAttributesCapturer capturer = new EnduserAttributesCapturer();
    capturer.setRoleGrantedAuthorityPrefix("role_");
    capturer.setScopeGrantedAuthorityPrefix("scope_");

    Authentication authentication =
        new PreAuthenticatedAuthenticationToken(
            "principal",
            null,
            Arrays.asList(
                new SimpleGrantedAuthority("role_role1"),
                new SimpleGrantedAuthority("role_role2"),
                new SimpleGrantedAuthority("scope_scope1"),
                new SimpleGrantedAuthority("scope_scope2")));

    ThrowingConsumer<SpanData> assertions =
        span -> {
          assertThat(span.getAttributes().get(SemanticAttributes.ENDUSER_ID))
              .isEqualTo("principal");
          assertThat(span.getAttributes().get(SemanticAttributes.ENDUSER_ROLE))
              .isEqualTo("role1,role2");
          assertThat(span.getAttributes().get(SemanticAttributes.ENDUSER_SCOPE))
              .isEqualTo("scope1,scope2");
        };

    test(capturer, authentication, assertions);
  }

  void test(
      EnduserAttributesCapturer capturer,
      Authentication authentication,
      ThrowingConsumer<SpanData> assertions) {
    testing.runWithHttpServerSpan(
        () -> {
          Context otelContext = Context.current();
          capturer.captureEnduserAttributes(otelContext, authentication);
        });

    List<SpanData> spans = testing.spans();
    assertThat(spans).singleElement().satisfies(assertions);
  }
}
