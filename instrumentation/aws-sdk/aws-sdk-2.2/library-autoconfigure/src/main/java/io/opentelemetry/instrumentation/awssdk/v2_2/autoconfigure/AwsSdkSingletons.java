/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2.autoconfigure;

import static java.util.Collections.emptyList;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.internal.ConfigPropertiesUtil;
import io.opentelemetry.instrumentation.awssdk.v2_2.AwsSdkTelemetry;
import io.opentelemetry.javaagent.bootstrap.internal.AgentInstrumentationConfig;
import io.opentelemetry.javaagent.bootstrap.internal.ExperimentalConfig;
import java.util.List;

public final class AwsSdkSingletons {

  private static final boolean HAS_INSTRUMENTATION_CONFIG = hasAgentConfiguration();
  private static final AwsSdkTelemetry TELEMETRY =
      AwsSdkTelemetry.builder(GlobalOpenTelemetry.get())
          .setCapturedHeaders(getCapturedHeaders())
          .setCaptureExperimentalSpanAttributes(captureExperimentalSpanAttributes())
          .setMessagingReceiveInstrumentationEnabled(messagingReceiveInstrumentationEnabled())
          .setUseConfiguredPropagatorForMessaging(useMessagingPropagator())
          .setRecordIndividualHttpError(recordIndividualHttpError())
          .build();

  private static boolean hasAgentConfiguration() {
    try {
      Class.forName("io.opentelemetry.javaagent.bootstrap.internal.AgentInstrumentationConfig");
      return true;
    } catch (ClassNotFoundException e) {
      return false;
    }
  }

  private static List<String> getCapturedHeaders() {
    if (HAS_INSTRUMENTATION_CONFIG) {
      return ExperimentalConfig.get().getMessagingHeaders();
    } else {
      return ConfigPropertiesUtil.getList(
          "otel.instrumentation.messaging.experimental.capture-headers", emptyList());
    }
  }

  private static boolean captureExperimentalSpanAttributes() {
    return getBoolean("otel.instrumentation.aws-sdk.experimental-span-attributes", false);
  }

  private static boolean messagingReceiveInstrumentationEnabled() {
    if (HAS_INSTRUMENTATION_CONFIG) {
      return ExperimentalConfig.get().messagingReceiveInstrumentationEnabled();
    } else {
      return ConfigPropertiesUtil.getBoolean(
          "otel.instrumentation.messaging.experimental.receive-telemetry.enabled", false);
    }
  }

  private static boolean useMessagingPropagator() {
    return getBoolean(
        "otel.instrumentation.aws-sdk.experimental-use-propagator-for-messaging", false);
  }

  private static boolean recordIndividualHttpError() {
    return getBoolean(
        "otel.instrumentation.aws-sdk.experimental-record-individual-http-error", false);
  }

  private static boolean getBoolean(String name, boolean defaultValue) {
    if (HAS_INSTRUMENTATION_CONFIG) {
      return AgentInstrumentationConfig.get().getBoolean(name, defaultValue);
    } else {
      return ConfigPropertiesUtil.getBoolean(name, defaultValue);
    }
  }

  public static AwsSdkTelemetry telemetry() {
    return TELEMETRY;
  }

  private AwsSdkSingletons() {}
}
