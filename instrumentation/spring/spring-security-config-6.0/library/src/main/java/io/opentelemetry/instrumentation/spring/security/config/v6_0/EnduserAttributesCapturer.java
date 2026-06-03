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
import io.opentelemetry.instrumentation.api.internal.SemconvStability;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

/**
 * Captures identity semantic attributes from {@link Authentication} objects.
 *
 * <p>By default, this captures {@code enduser.*} attributes. When the v3 preview is enabled (via
 * {@link SemconvStability#v3Preview()}), the corresponding {@code user.*} attributes are captured
 * instead; in that mode {@code enduser.scope} has no {@code user.*} equivalent and is never
 * captured.
 *
 * <p>After construction, you must selectively enable which attributes you want captured by calling
 * the appropriate {@code setEnduser*Enabled(true)} method.
 */
public final class EnduserAttributesCapturer {

  // copied from EnduserIncubatingAttributes
  private static final AttributeKey<String> ENDUSER_ID = AttributeKey.stringKey("enduser.id");
  private static final AttributeKey<String> ENDUSER_ROLE = AttributeKey.stringKey("enduser.role");
  private static final AttributeKey<String> ENDUSER_SCOPE = AttributeKey.stringKey("enduser.scope");
  // copied from UserIncubatingAttributes
  private static final AttributeKey<String> USER_ID = AttributeKey.stringKey("user.id");
  private static final AttributeKey<List<String>> USER_ROLES =
      AttributeKey.stringArrayKey("user.roles");

  private static final String DEFAULT_ROLE_PREFIX = "ROLE_";
  private static final String DEFAULT_SCOPE_PREFIX = "SCOPE_";

  /** Determines if {@code enduser.id}, or {@code user.id} in v3 preview, should be captured. */
  private boolean enduserIdEnabled;

  /**
   * Determines if {@code enduser.role}, or {@code user.roles} in v3 preview, should be captured.
   */
  private boolean enduserRoleEnabled;

  /** Determines if {@code enduser.scope} should be captured when v3 preview is disabled. */
  private boolean enduserScopeEnabled;

  /** The prefix used to find {@link GrantedAuthority} objects for roles. */
  private String roleGrantedAuthorityPrefix = DEFAULT_ROLE_PREFIX;

  /** The prefix used to find {@link GrantedAuthority} objects for scopes. */
  private String scopeGrantedAuthorityPrefix = DEFAULT_SCOPE_PREFIX;

  /**
   * Captures the identity semantic attributes from the given {@link Authentication} into the {@link
   * LocalRootSpan} of the given {@link Context}.
   *
   * <p>Only the attributes enabled via the {@code setEnduser*Enabled(true)} methods are captured.
   *
   * <p>By default, the following attributes can be captured:
   *
   * <ul>
   *   <li>{@code enduser.id} - from {@link Authentication#getName()}
   *   <li>{@code enduser.role} - a comma-separated list from the {@link
   *       Authentication#getAuthorities()} with the configured {@link
   *       #getRoleGrantedAuthorityPrefix() role prefix}
   *   <li>{@code enduser.scope} - a comma-separated list from the {@link
   *       Authentication#getAuthorities()} with the configured {@link
   *       #getScopeGrantedAuthorityPrefix() scope prefix}
   * </ul>
   *
   * <p>When the v3 preview is enabled, the following attributes are captured instead:
   *
   * <ul>
   *   <li>{@code user.id} - from {@link Authentication#getName()}
   *   <li>{@code user.roles} - a string array from the {@link Authentication#getAuthorities()} with
   *       the configured {@link #getRoleGrantedAuthorityPrefix() role prefix}
   * </ul>
   *
   * @param otelContext the context from which the {@link LocalRootSpan} in which to capture the
   *     attributes will be retrieved
   * @param authentication the authentication from which to determine the identity attributes.
   */
  public void captureEnduserAttributes(
      Context otelContext, @Nullable Authentication authentication) {
    if (authentication != null) {
      Span localRootSpan = LocalRootSpan.fromContext(otelContext);
      boolean v3Preview = SemconvStability.v3Preview();

      if (enduserIdEnabled) {
        localRootSpan.setAttribute(v3Preview ? USER_ID : ENDUSER_ID, authentication.getName());
      }

      List<String> roles = null;
      StringBuilder scopeBuilder = null;
      if (enduserRoleEnabled || enduserScopeEnabled) {
        for (GrantedAuthority authority : authentication.getAuthorities()) {
          String authorityString = authority.getAuthority();
          if (authorityString == null) {
            continue;
          }
          if (enduserRoleEnabled && authorityString.startsWith(roleGrantedAuthorityPrefix)) {
            roles = appendSuffix(roleGrantedAuthorityPrefix, authorityString, roles);
          } else if (enduserScopeEnabled
              && !v3Preview
              && authorityString.startsWith(scopeGrantedAuthorityPrefix)) {
            scopeBuilder = appendSuffix(scopeGrantedAuthorityPrefix, authorityString, scopeBuilder);
          }
        }
      }
      if (roles != null) {
        if (v3Preview) {
          localRootSpan.setAttribute(USER_ROLES, roles);
        } else {
          localRootSpan.setAttribute(ENDUSER_ROLE, String.join(",", roles));
        }
      }
      if (scopeBuilder != null) {
        localRootSpan.setAttribute(ENDUSER_SCOPE, scopeBuilder.toString());
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

  @Nullable
  private static StringBuilder appendSuffix(
      String prefix, String authorityString, @Nullable StringBuilder builder) {
    if (authorityString.length() > prefix.length()) {
      String suffix = authorityString.substring(prefix.length());
      if (builder == null) {
        builder = new StringBuilder();
        builder.append(suffix);
      } else {
        builder.append(",").append(suffix);
      }
    }
    return builder;
  }

  public boolean isEnduserIdEnabled() {
    return enduserIdEnabled;
  }

  public void setEnduserIdEnabled(boolean enduserIdEnabled) {
    this.enduserIdEnabled = enduserIdEnabled;
  }

  public boolean isEnduserRoleEnabled() {
    return enduserRoleEnabled;
  }

  public void setEnduserRoleEnabled(boolean enduserRoleEnabled) {
    this.enduserRoleEnabled = enduserRoleEnabled;
  }

  public boolean isEnduserScopeEnabled() {
    return enduserScopeEnabled;
  }

  public void setEnduserScopeEnabled(boolean enduserScopeEnabled) {
    this.enduserScopeEnabled = enduserScopeEnabled;
  }

  public String getRoleGrantedAuthorityPrefix() {
    return roleGrantedAuthorityPrefix;
  }

  public void setRoleGrantedAuthorityPrefix(String roleGrantedAuthorityPrefix) {
    this.roleGrantedAuthorityPrefix =
        requireNonNull(roleGrantedAuthorityPrefix, "roleGrantedAuthorityPrefix must not be null");
  }

  public String getScopeGrantedAuthorityPrefix() {
    return scopeGrantedAuthorityPrefix;
  }

  public void setScopeGrantedAuthorityPrefix(String scopeGrantedAuthorityPrefix) {
    this.scopeGrantedAuthorityPrefix =
        requireNonNull(scopeGrantedAuthorityPrefix, "scopeGrantedAuthorityPrefix must not be null");
  }
}
