/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.config.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import org.junit.jupiter.api.Test;

class EnduserConfigTest {

  @Test
  void readsEnduserConfigWhenV3PreviewIsDisabled() {
    DeclarativeConfigProperties commonConfig =
        mock(DeclarativeConfigProperties.class, RETURNS_DEEP_STUBS);
    when(commonConfig.get("enduser").get("id").getBoolean("enabled", false)).thenReturn(false);
    when(commonConfig.get("enduser").get("role").getBoolean("enabled", false)).thenReturn(true);
    when(commonConfig.get("enduser").get("scope").getBoolean("enabled", false)).thenReturn(true);
    when(commonConfig.get("user").get("id").getBoolean("enabled", false)).thenReturn(true);
    when(commonConfig.get("user").get("roles").getBoolean("enabled", false)).thenReturn(false);

    EnduserConfig enduserConfig = new EnduserConfig(commonConfig, false);

    assertThat(enduserConfig.isIdEnabled()).isFalse();
    assertThat(enduserConfig.isRoleEnabled()).isTrue();
    assertThat(enduserConfig.isScopeEnabled()).isTrue();
  }

  @Test
  void readsUserConfigWhenV3PreviewIsEnabled() {
    DeclarativeConfigProperties commonConfig =
        mock(DeclarativeConfigProperties.class, RETURNS_DEEP_STUBS);
    when(commonConfig.get("enduser").get("id").getBoolean("enabled", false)).thenReturn(true);
    when(commonConfig.get("enduser").get("role").getBoolean("enabled", false)).thenReturn(true);
    when(commonConfig.get("enduser").get("scope").getBoolean("enabled", false)).thenReturn(true);
    when(commonConfig.get("user").get("id").getBoolean("enabled", false)).thenReturn(false);
    when(commonConfig.get("user").get("roles").getBoolean("enabled", false)).thenReturn(true);

    EnduserConfig enduserConfig = new EnduserConfig(commonConfig, true);

    assertThat(enduserConfig.isIdEnabled()).isFalse();
    assertThat(enduserConfig.isRoleEnabled()).isTrue();
    assertThat(enduserConfig.isScopeEnabled()).isFalse();
  }
}
