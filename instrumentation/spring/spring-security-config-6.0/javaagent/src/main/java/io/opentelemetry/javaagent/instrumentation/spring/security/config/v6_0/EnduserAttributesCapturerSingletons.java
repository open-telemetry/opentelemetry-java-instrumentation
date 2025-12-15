/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.security.config.v6_0;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.spring.security.config.v6_0.EnduserAttributesCapturer;

public class EnduserAttributesCapturerSingletons {

  private static final EnduserAttributesCapturer ENDUSER_ATTRIBUTES_CAPTURER =
      createEndUserAttributesCapturerFromConfig();

  private EnduserAttributesCapturerSingletons() {}

  public static EnduserAttributesCapturer enduserAttributesCapturer() {
    return ENDUSER_ATTRIBUTES_CAPTURER;
  }

  private static EnduserAttributesCapturer createEndUserAttributesCapturerFromConfig() {
    EnduserAttributesCapturer capturer = new EnduserAttributesCapturer();
    capturer.setEnduserIdEnabled(
        DeclarativeConfigUtil.getBoolean(
                GlobalOpenTelemetry.get(), "java", "common", "enduser", "id", "enabled")
            .orElse(false));
    capturer.setEnduserRoleEnabled(
        DeclarativeConfigUtil.getBoolean(
                GlobalOpenTelemetry.get(), "java", "common", "enduser", "role", "enabled")
            .orElse(false));
    capturer.setEnduserScopeEnabled(
        DeclarativeConfigUtil.getBoolean(
                GlobalOpenTelemetry.get(), "java", "common", "enduser", "scope", "enabled")
            .orElse(false));

    String rolePrefix =
        DeclarativeConfigUtil.getString(
                GlobalOpenTelemetry.get(),
                "java",
                "spring_security",
                "enduser",
                "role",
                "granted_authority_prefix")
            .orElse(null);
    if (rolePrefix != null) {
      capturer.setRoleGrantedAuthorityPrefix(rolePrefix);
    }

    String scopePrefix =
        DeclarativeConfigUtil.getString(
                GlobalOpenTelemetry.get(),
                "java",
                "spring_security",
                "enduser",
                "scope",
                "granted_authority_prefix")
            .orElse(null);
    if (scopePrefix != null) {
      capturer.setScopeGrantedAuthorityPrefix(scopePrefix);
    }
    return capturer;
  }
}
