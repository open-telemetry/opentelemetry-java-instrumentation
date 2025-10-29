/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2.autoconfigure;

import static java.util.Collections.emptyList;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.internal.ApiConfigProperties;
import io.opentelemetry.instrumentation.awssdk.v2_2.AwsSdkTelemetry;
import io.opentelemetry.instrumentation.awssdk.v2_2.internal.AbstractAwsSdkTelemetryFactory;
import java.util.List;

public final class AwsSdkSingletons {

  private static final AwsSdkTelemetry TELEMETRY = new AwsSdkTelemetryFactory().telemetry();

  public static AwsSdkTelemetry telemetry() {
    return TELEMETRY;
  }

  private static class AwsSdkTelemetryFactory extends AbstractAwsSdkTelemetryFactory {

    private final ApiConfigProperties config = new ApiConfigProperties(GlobalOpenTelemetry.get());

    @Override
    protected List<String> getCapturedHeaders() {
      return config.getList(
          "otel.instrumentation.messaging.experimental.capture-headers", emptyList());
    }

    @Override
    protected boolean messagingReceiveInstrumentationEnabled() {
      return config.getBoolean(
          "otel.instrumentation.messaging.experimental.receive-telemetry.enabled", false);
    }

    @Override
    protected boolean getBoolean(String name, boolean defaultValue) {
      return config.getBoolean(name, defaultValue);
    }
  }

  private AwsSdkSingletons() {}
}
