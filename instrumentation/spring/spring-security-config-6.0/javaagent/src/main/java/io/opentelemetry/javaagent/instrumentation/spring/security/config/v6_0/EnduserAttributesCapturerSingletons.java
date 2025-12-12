/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.security.config.v6_0;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.spring.security.config.v6_0.EnduserAttributesCapturer;
import io.opentelemetry.javaagent.bootstrap.internal.AgentInstrumentationConfig;

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
                GlobalOpenTelemetry.get(), "general", "enduser", "id", "enabled")
            .orElse(false));
    capturer.setEnduserRoleEnabled(
        DeclarativeConfigUtil.getBoolean(
                GlobalOpenTelemetry.get(), "general", "enduser", "role", "enabled")
            .orElse(false));
    capturer.setEnduserScopeEnabled(
        DeclarativeConfigUtil.getBoolean(
                GlobalOpenTelemetry.get(), "general", "enduser", "scope", "enabled")
            .orElse(false));

    String rolePrefix =
        AgentInstrumentationConfig.get()
            .getString(
                "otel.instrumentation.spring-security.enduser.role.granted-authority-prefix");
    if (rolePrefix != null) {
      capturer.setRoleGrantedAuthorityPrefix(rolePrefix);
    }

    String scopePrefix =
        AgentInstrumentationConfig.get()
            .getString(
                "otel.instrumentation.spring-security.enduser.scope.granted-authority-prefix");
    if (scopePrefix != null) {
      capturer.setScopeGrantedAuthorityPrefix(scopePrefix);
    }
    return capturer;
  }
}
