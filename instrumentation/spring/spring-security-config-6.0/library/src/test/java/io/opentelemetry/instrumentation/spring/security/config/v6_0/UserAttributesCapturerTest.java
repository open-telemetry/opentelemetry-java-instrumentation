/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.security.config.v6_0;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_REQUEST_METHOD;
import static io.opentelemetry.semconv.incubating.UserIncubatingAttributes.USER_NAME;
import static io.opentelemetry.semconv.incubating.UserIncubatingAttributes.USER_ROLES;
import static java.util.Arrays.asList;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;

class UserAttributesCapturerTest {

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @Test
  void nothingEnabled() {
    UserAttributesCapturer capturer = new UserAttributesCapturer();

    Authentication authentication =
        new PreAuthenticatedAuthenticationToken(
            "principal",
            null,
            asList(
                new SimpleGrantedAuthority("ROLE_role1"),
                new SimpleGrantedAuthority("ROLE_role2"),
                new SimpleGrantedAuthority("SCOPE_scope1"),
                new SimpleGrantedAuthority("SCOPE_scope2")));

    test(
        capturer,
        authentication,
        span ->
            span.hasAttributesSatisfyingExactly(
                equalTo(ERROR_TYPE, "_OTHER"), equalTo(HTTP_REQUEST_METHOD, "GET")));
  }

  @Test
  void allEnabledButNoRoles() {
    UserAttributesCapturer capturer = new UserAttributesCapturer();
    capturer.setNameEnabled(true);
    capturer.setRolesEnabled(true);

    Authentication authentication =
        new PreAuthenticatedAuthenticationToken(
            "principal",
            null,
            asList(
                new SimpleGrantedAuthority("SCOPE_scope1"),
                new SimpleGrantedAuthority("SCOPE_scope2")));

    test(
        capturer,
        authentication,
        span ->
            span.hasAttributesSatisfyingExactly(
                equalTo(ERROR_TYPE, "_OTHER"),
                equalTo(HTTP_REQUEST_METHOD, "GET"),
                equalTo(USER_NAME, "principal")));
  }

  @Test
  void capturesRolesAsStringArray() {
    UserAttributesCapturer capturer = new UserAttributesCapturer();
    capturer.setNameEnabled(true);
    capturer.setRolesEnabled(true);

    Authentication authentication =
        new PreAuthenticatedAuthenticationToken(
            "principal",
            null,
            asList(
                new SimpleGrantedAuthority("ROLE_role1"),
                new SimpleGrantedAuthority("ROLE_role2")));

    test(
        capturer,
        authentication,
        span ->
            span.hasAttributesSatisfyingExactly(
                equalTo(ERROR_TYPE, "_OTHER"),
                equalTo(HTTP_REQUEST_METHOD, "GET"),
                equalTo(USER_NAME, "principal"),
                equalTo(USER_ROLES, asList("role1", "role2"))));
  }

  @Test
  void onlyNameEnabled() {
    UserAttributesCapturer capturer = new UserAttributesCapturer();
    capturer.setNameEnabled(true);

    Authentication authentication =
        new PreAuthenticatedAuthenticationToken(
            "principal",
            null,
            asList(
                new SimpleGrantedAuthority("ROLE_role1"),
                new SimpleGrantedAuthority("ROLE_role2"),
                new SimpleGrantedAuthority("SCOPE_scope1"),
                new SimpleGrantedAuthority("SCOPE_scope2")));

    test(
        capturer,
        authentication,
        span ->
            span.hasAttributesSatisfyingExactly(
                equalTo(ERROR_TYPE, "_OTHER"),
                equalTo(HTTP_REQUEST_METHOD, "GET"),
                equalTo(USER_NAME, "principal")));
  }

  @Test
  void onlyRolesEnabled() {
    UserAttributesCapturer capturer = new UserAttributesCapturer();
    capturer.setRolesEnabled(true);

    Authentication authentication =
        new PreAuthenticatedAuthenticationToken(
            "principal",
            null,
            asList(
                new SimpleGrantedAuthority("ROLE_role1"),
                new SimpleGrantedAuthority("ROLE_role2"),
                new SimpleGrantedAuthority("SCOPE_scope1"),
                new SimpleGrantedAuthority("SCOPE_scope2")));

    test(
        capturer,
        authentication,
        span ->
            span.hasAttributesSatisfyingExactly(
                equalTo(ERROR_TYPE, "_OTHER"),
                equalTo(HTTP_REQUEST_METHOD, "GET"),
                equalTo(USER_ROLES, asList("role1", "role2"))));
  }

  @Test
  void allEnabledAndAlternatePrefix() {
    UserAttributesCapturer capturer = new UserAttributesCapturer();
    capturer.setNameEnabled(true);
    capturer.setRolesEnabled(true);
    capturer.setRoleGrantedAuthorityPrefix("role_");

    Authentication authentication =
        new PreAuthenticatedAuthenticationToken(
            "principal",
            null,
            asList(
                new SimpleGrantedAuthority("role_role1"),
                new SimpleGrantedAuthority("role_role2")));

    test(
        capturer,
        authentication,
        span ->
            span.hasAttributesSatisfyingExactly(
                equalTo(ERROR_TYPE, "_OTHER"),
                equalTo(HTTP_REQUEST_METHOD, "GET"),
                equalTo(USER_NAME, "principal"),
                equalTo(USER_ROLES, asList("role1", "role2"))));
  }

  private static void test(
      UserAttributesCapturer capturer,
      Authentication authentication,
      Consumer<SpanDataAssert> assertions) {
    testing.runWithHttpServerSpan(
        () -> {
          Context otelContext = Context.current();
          capturer.captureUserAttributes(otelContext, authentication);
        });

    testing.waitAndAssertTraces(trace -> trace.hasSpansSatisfyingExactly(assertions));
  }
}
