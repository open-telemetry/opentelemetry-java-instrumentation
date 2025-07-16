/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.extension.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.incubator.config.ConfigProvider;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.internal.AutoConfigureUtil;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

@SuppressWarnings("DoNotMockAutoValue")
class ConfigPropertiesUtilTest {
  @Test
  void shouldUseConfigPropertiesForAutoConfiguration() {
    ConfigProperties configPropertiesMock = mock(ConfigProperties.class);
    AutoConfiguredOpenTelemetrySdk sdkMock = mock(AutoConfiguredOpenTelemetrySdk.class);
    try (MockedStatic<AutoConfigureUtil> autoConfigureUtilMock =
        Mockito.mockStatic(AutoConfigureUtil.class)) {
      autoConfigureUtilMock
          .when(() -> AutoConfigureUtil.getConfig(sdkMock))
          .thenReturn(configPropertiesMock);

      ConfigProperties configProperties = ConfigPropertiesUtil.resolveConfigProperties(sdkMock);

      assertThat(configProperties).isSameAs(configPropertiesMock);
    }
  }

  @Test
  void shouldUseConfigProviderForDeclarativeConfiguration() {
    String propertyName = "testProperty";
    String expectedValue = "the value";
    DeclarativeConfigProperties javaNodeMock = mock(DeclarativeConfigProperties.class);
    when(javaNodeMock.getString(propertyName)).thenReturn(expectedValue);

    DeclarativeConfigProperties instrumentationConfigMock = mock(DeclarativeConfigProperties.class);
    when(instrumentationConfigMock.getStructured(eq("java"), any())).thenReturn(javaNodeMock);

    ConfigProvider configProviderMock = mock(ConfigProvider.class);
    when(configProviderMock.getInstrumentationConfig()).thenReturn(instrumentationConfigMock);

    AutoConfiguredOpenTelemetrySdk sdkMock = mock(AutoConfiguredOpenTelemetrySdk.class);

    try (MockedStatic<AutoConfigureUtil> autoConfigureUtilMock =
        Mockito.mockStatic(AutoConfigureUtil.class)) {
      autoConfigureUtilMock.when(() -> AutoConfigureUtil.getConfig(sdkMock)).thenReturn(null);
      autoConfigureUtilMock
          .when(() -> AutoConfigureUtil.getConfigProvider(sdkMock))
          .thenReturn(configProviderMock);

      ConfigProperties configProperties = ConfigPropertiesUtil.resolveConfigProperties(sdkMock);

      assertThat(configProperties.getString(propertyName)).isEqualTo(expectedValue);
    }
  }

  @Test
  void shouldUseConfigProviderForDeclarativeConfiguration_noInstrumentationConfig() {
    AutoConfiguredOpenTelemetrySdk sdkMock = mock(AutoConfiguredOpenTelemetrySdk.class);
    ConfigProvider configProviderMock = mock(ConfigProvider.class);
    when(configProviderMock.getInstrumentationConfig()).thenReturn(null);

    try (MockedStatic<AutoConfigureUtil> autoConfigureUtilMock =
        Mockito.mockStatic(AutoConfigureUtil.class)) {
      autoConfigureUtilMock.when(() -> AutoConfigureUtil.getConfig(sdkMock)).thenReturn(null);
      autoConfigureUtilMock
          .when(() -> AutoConfigureUtil.getConfigProvider(sdkMock))
          .thenReturn(configProviderMock);

      ConfigProperties configProperties = ConfigPropertiesUtil.resolveConfigProperties(sdkMock);

      assertThat(configProperties.getString("testProperty")).isEqualTo(null);
    }
  }
}
