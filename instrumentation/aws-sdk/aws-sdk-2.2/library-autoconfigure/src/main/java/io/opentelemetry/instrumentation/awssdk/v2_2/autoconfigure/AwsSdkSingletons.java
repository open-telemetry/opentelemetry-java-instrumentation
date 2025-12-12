/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2.autoconfigure;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.incubator.config.ConfigProvider;
import io.opentelemetry.api.incubator.config.InstrumentationConfigUtil;
import io.opentelemetry.instrumentation.api.internal.ConfigPropertiesUtil;
import io.opentelemetry.instrumentation.awssdk.v2_2.AwsSdkTelemetry;
import io.opentelemetry.instrumentation.awssdk.v2_2.internal.AbstractAwsSdkTelemetryFactory;
import java.util.List;
import java.util.Optional;

public final class AwsSdkSingletons {

  private static final AwsSdkTelemetry TELEMETRY = new AwsSdkTelemetryFactory().telemetry();

  public static AwsSdkTelemetry telemetry() {
    return TELEMETRY;
  }

  private static class AwsSdkTelemetryFactory extends AbstractAwsSdkTelemetryFactory {

    @Override
    protected List<String> getCapturedHeaders() {
      return ConfigPropertiesUtil.getList(
          GlobalOpenTelemetry.get(), "messaging", "capture_headers/development");
    }

    @Override
    protected boolean messagingReceiveInstrumentationEnabled() {
      return Optional.ofNullable(
              InstrumentationConfigUtil.getOrNull(
                  getConfigProvider(),
                  config -> config.getBoolean("messaging.receive_telemetry/development"),
                  "java",
                  "aws_sdk"))
          .orElse(false);
    }

    @Override
    protected ConfigProvider getConfigProvider() {
      return ConfigPropertiesUtil.getConfigProvider(GlobalOpenTelemetry.get());
    }
  }

  private AwsSdkSingletons() {}
}
