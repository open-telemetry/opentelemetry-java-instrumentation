/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.security.config.v6_0;

import static io.opentelemetry.semconv.incubating.EnduserIncubatingAttributes.ENDUSER_ID;
import static io.opentelemetry.semconv.incubating.EnduserIncubatingAttributes.ENDUSER_ROLE;
import static io.opentelemetry.semconv.incubating.EnduserIncubatingAttributes.ENDUSER_SCOPE;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.util.Arrays;
import java.util.function.Consumer;
import org.assertj.core.api.Condition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;

@SuppressWarnings("deprecation") // using deprecated semconv
class EnduserAttributesCapturerTest {

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @Test
  void nothingEnabled() {
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

    test(
        capturer,
        authentication,
        span ->
            span.doesNotHave(attribute(ENDUSER_ID))
                .doesNotHave(attribute(ENDUSER_ROLE))
                .doesNotHave(attribute(ENDUSER_SCOPE)));
  }

  @Test
  void allEnabledButNoRoles() {
    EnduserAttributesCapturer capturer = new EnduserAttributesCapturer();
    capturer.setEnduserIdEnabled(true);
    capturer.setEnduserRoleEnabled(true);
    capturer.setEnduserScopeEnabled(true);

    Authentication authentication =
        new PreAuthenticatedAuthenticationToken(
            "principal",
            null,
            Arrays.asList(
                new SimpleGrantedAuthority("SCOPE_scope1"),
                new SimpleGrantedAuthority("SCOPE_scope2")));

    test(
        capturer,
        authentication,
        span ->
            span.hasAttribute(ENDUSER_ID, "principal")
                .doesNotHave(attribute(ENDUSER_ROLE))
                .hasAttribute(ENDUSER_SCOPE, "scope1,scope2"));
  }

  @Test
  void allEnabledButNoScopes() {
    EnduserAttributesCapturer capturer = new EnduserAttributesCapturer();
    capturer.setEnduserIdEnabled(true);
    capturer.setEnduserRoleEnabled(true);
    capturer.setEnduserScopeEnabled(true);

    Authentication authentication =
        new PreAuthenticatedAuthenticationToken(
            "principal",
            null,
            Arrays.asList(
                new SimpleGrantedAuthority("ROLE_role1"),
                new SimpleGrantedAuthority("ROLE_role2")));

    test(
        capturer,
        authentication,
        span ->
            span.hasAttribute(ENDUSER_ID, "principal")
                .hasAttribute(ENDUSER_ROLE, "role1,role2")
                .doesNotHave(attribute(ENDUSER_SCOPE)));
  }

  @Test
  void onlyEnduserIdEnabled() {
    EnduserAttributesCapturer capturer = new EnduserAttributesCapturer();
    capturer.setEnduserIdEnabled(true);

    Authentication authentication =
        new PreAuthenticatedAuthenticationToken(
            "principal",
            null,
            Arrays.asList(
                new SimpleGrantedAuthority("ROLE_role1"),
                new SimpleGrantedAuthority("ROLE_role2"),
                new SimpleGrantedAuthority("SCOPE_scope1"),
                new SimpleGrantedAuthority("SCOPE_scope2")));

    test(
        capturer,
        authentication,
        span ->
            span.hasAttribute(ENDUSER_ID, "principal")
                .doesNotHave(attribute(ENDUSER_ROLE))
                .doesNotHave(attribute(ENDUSER_SCOPE)));
  }

  @Test
  void onlyEnduserRoleEnabled() {
    EnduserAttributesCapturer capturer = new EnduserAttributesCapturer();
    capturer.setEnduserRoleEnabled(true);

    Authentication authentication =
        new PreAuthenticatedAuthenticationToken(
            "principal",
            null,
            Arrays.asList(
                new SimpleGrantedAuthority("ROLE_role1"),
                new SimpleGrantedAuthority("ROLE_role2"),
                new SimpleGrantedAuthority("SCOPE_scope1"),
                new SimpleGrantedAuthority("SCOPE_scope2")));

    test(
        capturer,
        authentication,
        span ->
            span.doesNotHave(attribute(ENDUSER_ID))
                .hasAttribute(ENDUSER_ROLE, "role1,role2")
                .doesNotHave(attribute(ENDUSER_SCOPE)));
  }

  @Test
  void onlyEnduserScopeEnabled() {
    EnduserAttributesCapturer capturer = new EnduserAttributesCapturer();
    capturer.setEnduserScopeEnabled(true);

    Authentication authentication =
        new PreAuthenticatedAuthenticationToken(
            "principal",
            null,
            Arrays.asList(
                new SimpleGrantedAuthority("ROLE_role1"),
                new SimpleGrantedAuthority("ROLE_role2"),
                new SimpleGrantedAuthority("SCOPE_scope1"),
                new SimpleGrantedAuthority("SCOPE_scope2")));

    test(
        capturer,
        authentication,
        span ->
            span.doesNotHave(attribute(ENDUSER_ID))
                .doesNotHave(attribute(ENDUSER_ROLE))
                .hasAttribute(ENDUSER_SCOPE, "scope1,scope2"));
  }

  @Test
  void allEnabledAndAlternatePrefix() {
    EnduserAttributesCapturer capturer = new EnduserAttributesCapturer();
    capturer.setEnduserIdEnabled(true);
    capturer.setEnduserRoleEnabled(true);
    capturer.setEnduserScopeEnabled(true);
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

    test(
        capturer,
        authentication,
        span ->
            span.hasAttribute(ENDUSER_ID, "principal")
                .hasAttribute(ENDUSER_ROLE, "role1,role2")
                .hasAttribute(ENDUSER_SCOPE, "scope1,scope2"));
  }

  void test(
      EnduserAttributesCapturer capturer,
      Authentication authentication,
      Consumer<SpanDataAssert> assertions) {
    testing.runWithHttpServerSpan(
        () -> {
          Context otelContext = Context.current();
          capturer.captureEnduserAttributes(otelContext, authentication);
        });

    testing.waitAndAssertTraces(trace -> trace.hasSpansSatisfyingExactly(assertions));
  }

  private static Condition<SpanData> attribute(AttributeKey<?> attributeKey) {
    return new Condition<>(
        spanData -> spanData.getAttributes().get(attributeKey) != null,
        "attribute " + attributeKey);
  }
}
