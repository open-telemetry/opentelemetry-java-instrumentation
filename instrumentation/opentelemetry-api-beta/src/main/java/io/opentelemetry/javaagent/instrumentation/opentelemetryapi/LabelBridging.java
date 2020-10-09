/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.auto.opentelemetryapi;

import application.io.opentelemetry.common.LabelConsumer;
import io.opentelemetry.common.Labels;
import io.opentelemetry.common.Labels.Builder;

/**
 * This class converts between Labels class that application brings and Labels class that agent
 * uses.
 *
 * <p>TODO probably not the most performant solution...
 */
public class LabelBridging {

  public static Labels toAgent(application.io.opentelemetry.common.Labels applicationLabels) {
    io.opentelemetry.common.Labels.Builder builder = io.opentelemetry.common.Labels.newBuilder();
    applicationLabels.forEach(new Consumer(builder));
    return builder.build();
  }

  static class Consumer implements LabelConsumer {

    private final Builder builder;

    public Consumer(Builder builder) {
      this.builder = builder;
    }

    @Override
    public void consume(String key, String value) {
      builder.setLabel(key, value);
    }
  }
}
