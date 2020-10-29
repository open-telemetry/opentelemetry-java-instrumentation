/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi;

import application.io.opentelemetry.api.common.LabelConsumer;
import io.opentelemetry.api.common.Labels;

/**
 * This class converts between Labels class that application brings and Labels class that agent
 * uses.
 *
 * <p>TODO probably not the most performant solution...
 */
public class LabelBridging {

  public static Labels toAgent(application.io.opentelemetry.api.common.Labels applicationLabels) {
    Labels.Builder builder = Labels.builder();
    applicationLabels.forEach(new Consumer(builder));
    return builder.build();
  }

  static class Consumer implements LabelConsumer {

    private final Labels.Builder builder;

    public Consumer(Labels.Builder builder) {
      this.builder = builder;
    }

    @Override
    public void consume(String key, String value) {
      builder.put(key, value);
    }
  }
}
