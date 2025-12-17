/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.config.bridge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.incubator.config.ConfigProvider;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.internal.AutoConfigureUtil;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.extension.incubator.ExtendedOpenTelemetrySdk;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

@SuppressWarnings("DoNotMockAutoValue")
class DeclarativeConfigPropertiesBridgeBuilderTest {
  @Test
  void shouldUseConfigPropertiesForAutoConfiguration() {
    ConfigProperties configPropertiesMock = mock(ConfigProperties.class);
    AutoConfiguredOpenTelemetrySdk sdkMock = mock(AutoConfiguredOpenTelemetrySdk.class);
    try (MockedStatic<AutoConfigureUtil> autoConfigureUtilMock =
        Mockito.mockStatic(AutoConfigureUtil.class)) {
      autoConfigureUtilMock
          .when(() -> AutoConfigureUtil.getConfig(sdkMock))
          .thenReturn(configPropertiesMock);

      ConfigProperties configProperties =
          new DeclarativeConfigPropertiesBridgeBuilder().build(sdkMock);

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
    when(instrumentationConfigMock.getStructured(eq("java"))).thenReturn(javaNodeMock);

    ConfigProvider configProviderMock = mock(ConfigProvider.class);
    when(configProviderMock.getInstrumentationConfig()).thenReturn(instrumentationConfigMock);

    AutoConfiguredOpenTelemetrySdk sdkMock = mock(AutoConfiguredOpenTelemetrySdk.class);
    ExtendedOpenTelemetrySdk extendedOpenTelemetrySdk = mock(ExtendedOpenTelemetrySdk.class);
    when(sdkMock.getOpenTelemetrySdk()).thenReturn(extendedOpenTelemetrySdk);
    when(extendedOpenTelemetrySdk.getConfigProvider()).thenReturn(configProviderMock);

    try (MockedStatic<AutoConfigureUtil> autoConfigureUtilMock =
        Mockito.mockStatic(AutoConfigureUtil.class)) {
      autoConfigureUtilMock.when(() -> AutoConfigureUtil.getConfig(sdkMock)).thenReturn(null);

      ConfigProperties configProperties =
          new DeclarativeConfigPropertiesBridgeBuilder().build(sdkMock);

      assertThat(configProperties.getString(propertyName)).isEqualTo(expectedValue);
    }
  }

  @Test
  void shouldUseConfigProviderForDeclarativeConfiguration_noInstrumentationConfig() {
    AutoConfiguredOpenTelemetrySdk sdkMock = mock(AutoConfiguredOpenTelemetrySdk.class);
    ExtendedOpenTelemetrySdk extendedOpenTelemetrySdk = mock(ExtendedOpenTelemetrySdk.class);
    when(sdkMock.getOpenTelemetrySdk()).thenReturn(extendedOpenTelemetrySdk);
    ConfigProvider configProviderMock = mock(ConfigProvider.class);
    when(extendedOpenTelemetrySdk.getConfigProvider()).thenReturn(configProviderMock);
    when(configProviderMock.getInstrumentationConfig()).thenReturn(null);

    try (MockedStatic<AutoConfigureUtil> autoConfigureUtilMock =
        Mockito.mockStatic(AutoConfigureUtil.class)) {
      autoConfigureUtilMock.when(() -> AutoConfigureUtil.getConfig(sdkMock)).thenReturn(null);

      ConfigProperties configProperties =
          new DeclarativeConfigPropertiesBridgeBuilder().build(sdkMock);

      assertThat(configProperties.getString("testProperty")).isNull();
    }
  }
}
