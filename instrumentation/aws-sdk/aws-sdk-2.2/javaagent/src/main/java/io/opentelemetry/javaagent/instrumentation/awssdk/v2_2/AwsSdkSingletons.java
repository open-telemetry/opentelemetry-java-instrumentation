/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.awssdk.v2_2;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.awssdk.v2_2.AwsSdkTelemetry;
import io.opentelemetry.instrumentation.awssdk.v2_2.internal.AbstractAwsSdkTelemetryFactory;
import java.util.Collections;
import java.util.List;

public final class AwsSdkSingletons {

  private static final AwsSdkTelemetry TELEMETRY = new AwsSdkTelemetryFactory().telemetry();

  public static AwsSdkTelemetry telemetry() {
    return TELEMETRY;
  }

  private static class AwsSdkTelemetryFactory extends AbstractAwsSdkTelemetryFactory {

    @Override
    protected List<String> getCapturedHeaders() {
      return DeclarativeConfigUtil.getList(
              GlobalOpenTelemetry.get(), "java", "messaging", "capture_headers/development")
          .orElse(Collections.emptyList());
    }

    @Override
    protected boolean messagingReceiveInstrumentationEnabled() {
      return DeclarativeConfigUtil.getBoolean(
              GlobalOpenTelemetry.get(),
              "java",
              "messaging",
              "receive_telemetry/development",
              "enabled")
          .orElse(false);
    }

    @Override
    protected boolean getBoolean(String name, boolean defaultValue) {
      // Convert otel.instrumentation.xxx.yyy to java/xxx/yyy format
      // e.g. otel.instrumentation.aws-sdk.experimental-span-attributes -> java/aws_sdk/experimental_span_attributes
      String[] parts = name.replace("otel.instrumentation.", "java.").replace("-", "_").split("\\.");
      return DeclarativeConfigUtil.getBoolean(GlobalOpenTelemetry.get(), parts)
          .orElse(defaultValue);
    }
  }

  private AwsSdkSingletons() {}
}
