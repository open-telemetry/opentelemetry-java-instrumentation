/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.awssdk.v2_2;

import io.opentelemetry.api.incubator.config.ConfigProvider;
import io.opentelemetry.instrumentation.api.incubator.config.internal.InstrumentationConfig;
import io.opentelemetry.instrumentation.api.internal.ConfigPropertiesUtil;
import io.opentelemetry.instrumentation.awssdk.v2_2.AwsSdkTelemetry;
import io.opentelemetry.instrumentation.awssdk.v2_2.internal.AbstractAwsSdkTelemetryFactory;
import io.opentelemetry.javaagent.bootstrap.internal.AgentInstrumentationConfig;
import io.opentelemetry.javaagent.bootstrap.internal.ExperimentalConfig;
import java.util.List;

public final class AwsSdkSingletons {

  private static final AwsSdkTelemetry TELEMETRY = new AwsSdkTelemetryFactory().telemetry();

  public static AwsSdkTelemetry telemetry() {
    return TELEMETRY;
  }

  private static class AwsSdkTelemetryFactory extends AbstractAwsSdkTelemetryFactory {

    @Override
    protected List<String> getCapturedHeaders() {
      return ExperimentalConfig.get().getMessagingHeaders();
    }

    @Override
    protected boolean messagingReceiveInstrumentationEnabled() {
      return ExperimentalConfig.get().messagingReceiveInstrumentationEnabled();
    }

    @Override
    protected boolean getBoolean(boolean defaultValue, String... name) {
      InstrumentationConfig instrumentationConfig = AgentInstrumentationConfig.get();
      ConfigProvider configProvider = instrumentationConfig.getConfigProvider();
      if (configProvider != null) {
        // don't use to InstrumentationConfig, which would use a bridge back to declarative config
        return ConfigPropertiesUtil.getConfigProperties(configProvider, name)
            .getBoolean(name[name.length - 1], defaultValue);
      }

      return instrumentationConfig.getBoolean(
          ConfigPropertiesUtil.toSystemProperty(name), defaultValue);
    }
  }

  private AwsSdkSingletons() {}
}
