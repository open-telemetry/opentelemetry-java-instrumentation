/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.security.config.v6_0;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.api.incubator.config.internal.ExtendedDeclarativeConfigProperties;
import io.opentelemetry.instrumentation.spring.security.config.v6_0.EnduserAttributesCapturer;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;

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

    ExtendedDeclarativeConfigProperties endUserConfig =
        DeclarativeConfigUtil.getInstrumentationConfig(GlobalOpenTelemetry.get(), "spring_security")
            .get("enduser");
    String rolePrefix = endUserConfig.get("role").getString("granted_authority_prefix");
    if (rolePrefix != null) {
      capturer.setRoleGrantedAuthorityPrefix(rolePrefix);
    }

    String scopePrefix = endUserConfig.get("scope").getString("granted_authority_prefix");
    if (scopePrefix != null) {
      capturer.setScopeGrantedAuthorityPrefix(scopePrefix);
    }
    return capturer;
  }
}
