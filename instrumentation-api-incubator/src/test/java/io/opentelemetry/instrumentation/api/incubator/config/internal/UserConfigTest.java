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

class UserConfigTest {

  @Test
  void readsEnduserConfigWhenV3PreviewIsDisabled() {
    DeclarativeConfigProperties commonConfig =
        mock(DeclarativeConfigProperties.class, RETURNS_DEEP_STUBS);
    when(commonConfig.get("enduser").get("id").getBoolean("enabled", false)).thenReturn(false);
    when(commonConfig.get("enduser").get("role").getBoolean("enabled", false)).thenReturn(true);
    when(commonConfig.get("enduser").get("scope").getBoolean("enabled", false)).thenReturn(true);
    when(commonConfig.get("user").get("name").getBoolean("enabled", false)).thenReturn(true);
    when(commonConfig.get("user").get("roles").getBoolean("enabled", false)).thenReturn(false);

    UserConfig userConfig = new UserConfig(commonConfig, false);

    assertThat(userConfig.isIdEnabled()).isFalse();
    assertThat(userConfig.isRoleEnabled()).isTrue();
    assertThat(userConfig.isScopeEnabled()).isTrue();
  }

  @Test
  void readsUserConfigWhenV3PreviewIsEnabled() {
    DeclarativeConfigProperties commonConfig =
        mock(DeclarativeConfigProperties.class, RETURNS_DEEP_STUBS);
    when(commonConfig.get("enduser").get("id").getBoolean("enabled", false)).thenReturn(true);
    when(commonConfig.get("enduser").get("role").getBoolean("enabled", false)).thenReturn(true);
    when(commonConfig.get("enduser").get("scope").getBoolean("enabled", false)).thenReturn(true);
    when(commonConfig.get("user").get("name").getBoolean("enabled", false)).thenReturn(false);
    when(commonConfig.get("user").get("roles").getBoolean("enabled", false)).thenReturn(true);

    UserConfig userConfig = new UserConfig(commonConfig, true);

    assertThat(userConfig.isIdEnabled()).isFalse();
    assertThat(userConfig.isRoleEnabled()).isTrue();
    assertThat(userConfig.isScopeEnabled()).isFalse();
  }
}
