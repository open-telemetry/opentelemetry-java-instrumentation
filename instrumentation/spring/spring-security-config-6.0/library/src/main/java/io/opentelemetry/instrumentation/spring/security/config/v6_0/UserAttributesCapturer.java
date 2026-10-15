/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.security.config.v6_0;

import static java.util.Objects.requireNonNull;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.LocalRootSpan;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

/**
 * Captures identity semantic attributes from {@link Authentication} objects.
 *
 * <p>When enabled, this captures {@code user.name} and {@code user.roles} attributes.
 *
 * <p>After construction, you must selectively enable which attributes you want captured by calling
 * the appropriate {@code set*Enabled(true)} method.
 */
public class UserAttributesCapturer {

  // copied from UserIncubatingAttributes
  private static final AttributeKey<String> USER_NAME = AttributeKey.stringKey("user.name");
  private static final AttributeKey<List<String>> USER_ROLES =
      AttributeKey.stringArrayKey("user.roles");

  private static final String DEFAULT_ROLE_PREFIX = "ROLE_";

  /** Determines if {@code user.name} should be captured. */
  private boolean nameEnabled;

  /** Determines if {@code user.roles} should be captured. */
  private boolean rolesEnabled;

  /** The prefix used to find {@link GrantedAuthority} objects for roles. */
  private String roleGrantedAuthorityPrefix = DEFAULT_ROLE_PREFIX;

  /**
   * Captures the identity semantic attributes from the given {@link Authentication} into the {@link
   * LocalRootSpan} of the given {@link Context}.
   *
   * <p>Only the attributes enabled via the {@code set*Enabled(true)} methods are captured.
   *
   * <p>The following attributes can be captured:
   *
   * <ul>
   *   <li>{@code user.name} - from {@link Authentication#getName()}
   *   <li>{@code user.roles} - a string array from the {@link Authentication#getAuthorities()} with
   *       the configured {@link #setRoleGrantedAuthorityPrefix(String) role prefix}
   * </ul>
   *
   * @param otelContext the context from which the {@link LocalRootSpan} in which to capture the
   *     attributes will be retrieved
   * @param authentication the authentication from which to determine the identity attributes.
   */
  public void captureUserAttributes(Context otelContext, @Nullable Authentication authentication) {
    if (authentication != null) {
      Span localRootSpan = LocalRootSpan.fromContext(otelContext);

      if (nameEnabled) {
        localRootSpan.setAttribute(USER_NAME, authentication.getName());
      }

      List<String> roles = null;
      if (rolesEnabled) {
        for (GrantedAuthority authority : authentication.getAuthorities()) {
          String authorityString = authority.getAuthority();
          if (authorityString == null) {
            continue;
          }
          if (authorityString.startsWith(roleGrantedAuthorityPrefix)) {
            roles = appendSuffix(roleGrantedAuthorityPrefix, authorityString, roles);
          }
        }
      }
      if (roles != null) {
        localRootSpan.setAttribute(USER_ROLES, roles);
      }
    }
  }

  @Nullable
  private static List<String> appendSuffix(
      String prefix, String authorityString, @Nullable List<String> values) {
    if (authorityString.length() > prefix.length()) {
      if (values == null) {
        values = new ArrayList<>();
      }
      values.add(authorityString.substring(prefix.length()));
    }
    return values;
  }

  public void setNameEnabled(boolean nameEnabled) {
    this.nameEnabled = nameEnabled;
  }

  public void setRolesEnabled(boolean rolesEnabled) {
    this.rolesEnabled = rolesEnabled;
  }

  public void setRoleGrantedAuthorityPrefix(String roleGrantedAuthorityPrefix) {
    this.roleGrantedAuthorityPrefix =
        requireNonNull(roleGrantedAuthorityPrefix, "roleGrantedAuthorityPrefix must not be null");
  }
}
