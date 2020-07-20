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

package io.opentelemetry.auto.instrumentation.opentelemetryapi;

import io.opentelemetry.common.Labels;
import io.opentelemetry.common.Labels.Builder;
import unshaded.io.opentelemetry.common.ReadableKeyValuePairs.KeyValueConsumer;

/**
 * This class converts between Labels class that user brings and Labels class that agent has.
 *
 * <p>TODO probably not the most performant solution...
 */
public class LabelsShader {

  public static Labels shade(unshaded.io.opentelemetry.common.Labels labels) {
    io.opentelemetry.common.Labels.Builder builder = io.opentelemetry.common.Labels.newBuilder();
    labels.forEach(new Consumer(builder));
    return builder.build();
  }

  static class Consumer implements KeyValueConsumer<String> {

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
