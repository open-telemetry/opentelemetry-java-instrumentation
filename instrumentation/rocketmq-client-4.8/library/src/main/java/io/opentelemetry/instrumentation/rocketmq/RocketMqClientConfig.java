/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rocketmq;

import io.opentelemetry.instrumentation.api.config.Config;

public final class RocketMqClientConfig {

  public static boolean isPropagationEnabled() {
    return Config.get()
        .getBooleanProperty("otel.instrumentation.rocketmq.client-propagation", true);
  }

  private RocketMqClientConfig() {}
}
