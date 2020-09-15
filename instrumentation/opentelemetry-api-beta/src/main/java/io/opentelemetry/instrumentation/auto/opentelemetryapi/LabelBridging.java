/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.instrumentation.auto.opentelemetryapi;

import application.io.opentelemetry.common.ReadableKeyValuePairs.KeyValueConsumer;
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

  static class Consumer implements KeyValueConsumer<String, String> {

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
