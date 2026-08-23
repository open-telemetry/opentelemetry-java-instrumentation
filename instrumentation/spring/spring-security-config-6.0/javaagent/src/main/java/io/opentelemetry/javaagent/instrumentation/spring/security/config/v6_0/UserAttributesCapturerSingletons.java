/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.security.config.v6_0;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.api.internal.SemconvStability;
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
    capturer.setScopeEnabled(AgentCommonConfig.get().getUserConfig().isScopeEnabled());

    DeclarativeConfigProperties springSecurityConfig =
        DeclarativeConfigUtil.getInstrumentationConfig(
            GlobalOpenTelemetry.get(), "spring_security");

    boolean v3Preview = SemconvStability.v3Preview();
    String rolePrefix =
        v3Preview
            ? springSecurityConfig.get("user").get("roles").getString("granted_authority_prefix")
            : springSecurityConfig.get("enduser").get("role").getString("granted_authority_prefix");
    if (rolePrefix != null) {
      capturer.setRoleGrantedAuthorityPrefix(rolePrefix);
    }

    // enduser.scope has no user.* equivalent and is not captured when v3 preview is enabled
    if (!v3Preview) {
      String scopePrefix =
          springSecurityConfig.get("enduser").get("scope").getString("granted_authority_prefix");
      if (scopePrefix != null) {
        capturer.setScopeGrantedAuthorityPrefix(scopePrefix);
      }
    }
    return capturer;
  }

  private UserAttributesCapturerSingletons() {}
}
