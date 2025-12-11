/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2.autoconfigure;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.internal.ConfigPropertiesUtil;
import io.opentelemetry.instrumentation.awssdk.v2_2.AwsSdkTelemetry;
import io.opentelemetry.instrumentation.awssdk.v2_2.internal.AbstractAwsSdkTelemetryFactory;
import java.util.List;

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
      return ConfigPropertiesUtil.getBoolean(
              GlobalOpenTelemetry.get(), "messaging", "receive_telemetry/development", "enabled")
          .orElse(false);
    }

    @Override
    protected boolean getBoolean(String... name) {
      return ConfigPropertiesUtil.getBoolean(GlobalOpenTelemetry.get(), name).orElse(false);
    }
  }

  private AwsSdkSingletons() {}
}
