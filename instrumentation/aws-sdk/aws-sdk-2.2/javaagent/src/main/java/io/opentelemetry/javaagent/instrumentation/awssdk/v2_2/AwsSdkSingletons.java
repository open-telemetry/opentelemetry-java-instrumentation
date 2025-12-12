/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.awssdk.v2_2;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.awssdk.v2_2.AwsSdkTelemetry;
import io.opentelemetry.instrumentation.awssdk.v2_2.internal.AbstractAwsSdkTelemetryFactory;
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
    protected boolean getBoolean(String name, boolean defaultValue) {
      // Convert otel.instrumentation.xxx.yyy to java/xxx/yyy format
      String converted =
          name.replace("otel.instrumentation.", "").replace(".", "_").replace("-", "_");
      String[] parts = converted.split("_", 2);
      if (parts.length == 2) {
        return DeclarativeConfigUtil.getBoolean(
                GlobalOpenTelemetry.get(), "java", parts[0].replace("_", "-"), parts[1])
            .orElse(defaultValue);
      }
      return defaultValue;
    }
  }

  private AwsSdkSingletons() {}
}
