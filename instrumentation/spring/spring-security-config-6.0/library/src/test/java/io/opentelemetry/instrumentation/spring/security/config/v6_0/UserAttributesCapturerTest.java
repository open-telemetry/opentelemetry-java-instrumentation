/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.security.config.v6_0;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.v3Preview;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_REQUEST_METHOD;
import static io.opentelemetry.semconv.incubating.EnduserIncubatingAttributes.ENDUSER_ID;
import static io.opentelemetry.semconv.incubating.EnduserIncubatingAttributes.ENDUSER_ROLE;
import static io.opentelemetry.semconv.incubating.EnduserIncubatingAttributes.ENDUSER_SCOPE;
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

@SuppressWarnings("deprecation") // using deprecated semconv
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
    capturer.setScopeEnabled(true);

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
                equalTo(v3Preview() ? USER_NAME : ENDUSER_ID, "principal"),
                equalTo(ENDUSER_SCOPE, v3Preview() ? null : "scope1,scope2")));
  }

  @Test
  void allEnabledButNoScopes() {
    UserAttributesCapturer capturer = new UserAttributesCapturer();
    capturer.setNameEnabled(true);
    capturer.setRolesEnabled(true);
    capturer.setScopeEnabled(true);

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
                equalTo(v3Preview() ? USER_NAME : ENDUSER_ID, "principal"),
                equalTo(USER_ROLES, v3Preview() ? asList("role1", "role2") : null),
                equalTo(ENDUSER_ROLE, v3Preview() ? null : "role1,role2")));
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
                equalTo(v3Preview() ? USER_NAME : ENDUSER_ID, "principal")));
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
                equalTo(USER_ROLES, v3Preview() ? asList("role1", "role2") : null),
                equalTo(ENDUSER_ROLE, v3Preview() ? null : "role1,role2")));
  }

  @Test
  void onlyScopeEnabled() {
    UserAttributesCapturer capturer = new UserAttributesCapturer();
    capturer.setScopeEnabled(true);

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
                equalTo(ENDUSER_SCOPE, v3Preview() ? null : "scope1,scope2")));
  }

  @Test
  void allEnabledAndAlternatePrefix() {
    UserAttributesCapturer capturer = new UserAttributesCapturer();
    capturer.setNameEnabled(true);
    capturer.setRolesEnabled(true);
    capturer.setScopeEnabled(true);
    capturer.setRoleGrantedAuthorityPrefix("role_");
    capturer.setScopeGrantedAuthorityPrefix("scope_");

    Authentication authentication =
        new PreAuthenticatedAuthenticationToken(
            "principal",
            null,
            asList(
                new SimpleGrantedAuthority("role_role1"),
                new SimpleGrantedAuthority("role_role2"),
                new SimpleGrantedAuthority("scope_scope1"),
                new SimpleGrantedAuthority("scope_scope2")));

    test(
        capturer,
        authentication,
        span ->
            span.hasAttributesSatisfyingExactly(
                equalTo(ERROR_TYPE, "_OTHER"),
                equalTo(HTTP_REQUEST_METHOD, "GET"),
                equalTo(v3Preview() ? USER_NAME : ENDUSER_ID, "principal"),
                equalTo(USER_ROLES, v3Preview() ? asList("role1", "role2") : null),
                equalTo(ENDUSER_ROLE, v3Preview() ? null : "role1,role2"),
                equalTo(ENDUSER_SCOPE, v3Preview() ? null : "scope1,scope2")));
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
