/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.security.config.v6_0;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.LocalRootSpan;
import java.util.Objects;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

/**
 * Captures {@code enduser.*} semantic attributes from {@link Authentication} objects.
 *
 * <p>After construction, you must selectively enable which attributes you want captured by calling
 * the appropriate {@code setEnduser*Enabled(true)} method.
 */
public final class EnduserAttributesCapturer {

  // copied from EnduserIncubatingAttributes
  private static final AttributeKey<String> ENDUSER_ID = AttributeKey.stringKey("enduser.id");
  private static final AttributeKey<String> ENDUSER_ROLE = AttributeKey.stringKey("enduser.role");
  private static final AttributeKey<String> ENDUSER_SCOPE = AttributeKey.stringKey("enduser.scope");

  private static final String DEFAULT_ROLE_PREFIX = "ROLE_";
  private static final String DEFAULT_SCOPE_PREFIX = "SCOPE_";

  /** Determines if the {@code enduser.id} attribute should be captured. */
  private boolean enduserIdEnabled;

  /** Determines if the {@code enduser.role} attribute should be captured. */
  private boolean enduserRoleEnabled;

  /** Determines if the {@code enduser.scope} attribute should be captured. */
  private boolean enduserScopeEnabled;

  /** The prefix used to find {@link GrantedAuthority} objects for roles. */
  private String roleGrantedAuthorityPrefix = DEFAULT_ROLE_PREFIX;

  /** The prefix used to find {@link GrantedAuthority} objects for scopes. */
  private String scopeGrantedAuthorityPrefix = DEFAULT_SCOPE_PREFIX;

  /**
   * Captures the {@code enduser.*} semantic attributes from the given {@link Authentication} into
   * the {@link LocalRootSpan} of the given {@link Context}.
   *
   * <p>Only the attributes enabled via the {@code setEnduser*Enabled(true)} methods are captured.
   *
   * <p>The following attributes can be captured:
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
   * @param otelContext the context from which the {@link LocalRootSpan} in which to capture the
   *     attributes will be retrieved
   * @param authentication the authentication from which to determine the {@code enduser.*}
   *     attributes.
   */
  public void captureEnduserAttributes(Context otelContext, Authentication authentication) {
    if (authentication != null) {
      Span localRootSpan = LocalRootSpan.fromContext(otelContext);

      if (enduserIdEnabled && authentication.getName() != null) {
        localRootSpan.setAttribute(ENDUSER_ID, authentication.getName());
      }

      StringBuilder roleBuilder = null;
      StringBuilder scopeBuilder = null;
      if (enduserRoleEnabled || enduserScopeEnabled) {
        for (GrantedAuthority authority : authentication.getAuthorities()) {
          String authorityString = authority.getAuthority();
          if (enduserRoleEnabled && authorityString.startsWith(roleGrantedAuthorityPrefix)) {
            roleBuilder = appendSuffix(roleGrantedAuthorityPrefix, authorityString, roleBuilder);
          } else if (enduserScopeEnabled
              && authorityString.startsWith(scopeGrantedAuthorityPrefix)) {
            scopeBuilder = appendSuffix(scopeGrantedAuthorityPrefix, authorityString, scopeBuilder);
          }
        }
      }
      if (roleBuilder != null) {
        localRootSpan.setAttribute(ENDUSER_ROLE, roleBuilder.toString());
      }
      if (scopeBuilder != null) {
        localRootSpan.setAttribute(ENDUSER_SCOPE, scopeBuilder.toString());
      }
    }
  }

  private static StringBuilder appendSuffix(
      String prefix, String authorityString, StringBuilder builder) {
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
        Objects.requireNonNull(roleGrantedAuthorityPrefix, "rolePrefix must not be null");
  }

  public String getScopeGrantedAuthorityPrefix() {
    return scopeGrantedAuthorityPrefix;
  }

  public void setScopeGrantedAuthorityPrefix(String scopeGrantedAuthorityPrefix) {
    this.scopeGrantedAuthorityPrefix =
        Objects.requireNonNull(scopeGrantedAuthorityPrefix, "scopePrefix must not be null");
  }
}
