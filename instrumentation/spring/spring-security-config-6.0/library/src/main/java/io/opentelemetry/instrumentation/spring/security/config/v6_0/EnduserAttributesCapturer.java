/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.security.config.v6_0;

import io.opentelemetry.context.Context;
import javax.annotation.Nullable;
import org.springframework.security.core.Authentication;

/**
 * Captures identity semantic attributes from {@link Authentication} objects.
 *
 * @deprecated Use {@link UserAttributesCapturer} instead.
 */
@Deprecated
public final class EnduserAttributesCapturer extends UserAttributesCapturer {

  /**
   * @deprecated Use {@link UserAttributesCapturer#captureUserAttributes(Context, Authentication)}
   *     instead.
   */
  @Deprecated
  public void captureEnduserAttributes(
      Context otelContext, @Nullable Authentication authentication) {
    captureUserAttributes(otelContext, authentication);
  }

  /**
   * @deprecated Use {@link UserAttributesCapturer} instead.
   */
  @Deprecated
  public boolean isEnduserIdEnabled() {
    return isNameEnabled();
  }

  /**
   * @deprecated Use {@link UserAttributesCapturer#setNameEnabled(boolean)} instead.
   */
  @Deprecated
  public void setEnduserIdEnabled(boolean enduserIdEnabled) {
    setNameEnabled(enduserIdEnabled);
  }

  /**
   * @deprecated Use {@link UserAttributesCapturer} instead.
   */
  @Deprecated
  public boolean isEnduserRoleEnabled() {
    return isRolesEnabled();
  }

  /**
   * @deprecated Use {@link UserAttributesCapturer#setRolesEnabled(boolean)} instead.
   */
  @Deprecated
  public void setEnduserRoleEnabled(boolean enduserRoleEnabled) {
    setRolesEnabled(enduserRoleEnabled);
  }

  /**
   * @deprecated Use {@link UserAttributesCapturer} instead.
   */
  @Deprecated
  public boolean isEnduserScopeEnabled() {
    return isScopeEnabled();
  }

  /**
   * @deprecated Use {@link UserAttributesCapturer#setScopeEnabled(boolean)} instead.
   */
  @Deprecated
  public void setEnduserScopeEnabled(boolean enduserScopeEnabled) {
    setScopeEnabled(enduserScopeEnabled);
  }

  @Deprecated
  @Override
  public String getRoleGrantedAuthorityPrefix() {
    return super.getRoleGrantedAuthorityPrefix();
  }

  @Deprecated
  @Override
  public String getScopeGrantedAuthorityPrefix() {
    return super.getScopeGrantedAuthorityPrefix();
  }
}
