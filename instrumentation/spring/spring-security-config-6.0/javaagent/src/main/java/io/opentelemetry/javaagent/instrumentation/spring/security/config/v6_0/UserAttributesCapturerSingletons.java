/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.security.config.v6_0;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.spring.security.config.v6_0.UserAttributesCapturer;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;

public class UserAttributesCapturerSingletons {

  private static final UserAttributesCapturer userAttributesCapturer =
      createUserAttributesCapturerFromConfig();

  public static UserAttributesCapturer userAttributesCapturer() {
    return userAttributesCapturer;
  }

  private static UserAttributesCapturer createUserAttributesCapturerFromConfig() {
    UserAttributesCapturer capturer = new UserAttributesCapturer();
    capturer.setNameEnabled(AgentCommonConfig.get().getUserConfig().isNameEnabled());
    capturer.setRolesEnabled(AgentCommonConfig.get().getUserConfig().isRolesEnabled());

    DeclarativeConfigProperties springSecurityConfig =
        DeclarativeConfigUtil.getInstrumentationConfig(
            GlobalOpenTelemetry.get(), "spring_security");

    String rolePrefix =
        springSecurityConfig.get("user").get("roles").getString("granted_authority_prefix");
    if (rolePrefix != null) {
      capturer.setRoleGrantedAuthorityPrefix(rolePrefix);
    }

    return capturer;
  }

  private UserAttributesCapturerSingletons() {}
}
