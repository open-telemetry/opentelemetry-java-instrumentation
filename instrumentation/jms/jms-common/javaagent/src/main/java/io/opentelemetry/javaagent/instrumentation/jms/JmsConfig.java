/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jms;

import io.opentelemetry.javaagent.bootstrap.internal.AgentInstrumentationConfig;

public final class JmsConfig {

  public static final boolean EXPERIMENTAL_CONSUMER_PROCESS_TELEMETRY_ENABLED =
      AgentInstrumentationConfig.get()
          .getBoolean(
              "otel.instrumentation.jms.experimental.consumer-process-telemetry.enabled", false);

  private JmsConfig() {}
}
