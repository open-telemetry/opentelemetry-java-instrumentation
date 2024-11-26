/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.security.config.v6_0;

import io.opentelemetry.instrumentation.spring.security.config.v6_0.EnduserAttributesCapturer;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
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
    capturer.setEnduserIdEnabled(AgentCommonConfig.get().getEnduserConfig().isIdEnabled());
    capturer.setEnduserRoleEnabled(AgentCommonConfig.get().getEnduserConfig().isRoleEnabled());
    capturer.setEnduserScopeEnabled(AgentCommonConfig.get().getEnduserConfig().isScopeEnabled());

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
      capturer.setScopeGrantedAuthorityPrefix(rolePrefix);
    }
    return capturer;
  }
}
