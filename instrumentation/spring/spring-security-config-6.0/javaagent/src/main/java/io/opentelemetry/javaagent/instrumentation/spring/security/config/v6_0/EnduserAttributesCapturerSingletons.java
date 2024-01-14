/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.security.config.v6_0;

import io.opentelemetry.instrumentation.spring.security.config.v6_0.EnduserAttributesCapturer;
import io.opentelemetry.javaagent.bootstrap.internal.CommonConfig;
import io.opentelemetry.javaagent.bootstrap.internal.InstrumentationConfig;

public class EnduserAttributesCapturerSingletons {

  private static final EnduserAttributesCapturer ENDUSER_ATTRIBUTES_CAPTURER =
      createEndUserAttributesCapturerFromConfig();

  private EnduserAttributesCapturerSingletons() {}

  public static EnduserAttributesCapturer enduserAttributesCapturer() {
    return ENDUSER_ATTRIBUTES_CAPTURER;
  }

  private static EnduserAttributesCapturer createEndUserAttributesCapturerFromConfig() {
    EnduserAttributesCapturer capturer = new EnduserAttributesCapturer();
    capturer.setEnduserIdEnabled(CommonConfig.get().getEnduserConfig().isIdEnabled());
    capturer.setEnduserRoleEnabled(CommonConfig.get().getEnduserConfig().isRoleEnabled());
    capturer.setEnduserScopeEnabled(CommonConfig.get().getEnduserConfig().isScopeEnabled());

    String rolePrefix =
        InstrumentationConfig.get()
            .getString(
                "otel.instrumentation.spring-security.enduser.role.granted-authority-prefix");
    if (rolePrefix != null) {
      capturer.setRoleGrantedAuthorityPrefix(rolePrefix);
    }

    String scopePrefix =
        InstrumentationConfig.get()
            .getString(
                "otel.instrumentation.spring-security.enduser.scope.granted-authority-prefix");
    if (scopePrefix != null) {
      capturer.setScopeGrantedAuthorityPrefix(rolePrefix);
    }
    return capturer;
  }
}
