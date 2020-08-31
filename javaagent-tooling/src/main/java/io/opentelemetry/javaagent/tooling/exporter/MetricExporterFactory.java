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

package io.opentelemetry.javaagent.tooling.exporter;

import io.opentelemetry.sdk.metrics.export.MetricExporter;

/**
 * A {@link MetricExporterFactory} acts as the bootstrap for a {@link MetricExporter}
 * implementation. An exporter must register its implementation of a {@link MetricExporterFactory}
 * through the Java SPI framework.
 */
public interface MetricExporterFactory {
  /**
   * Creates an instance of a {@link MetricExporter} based on the provided configuration.
   *
   * @param config The configuration
   * @return An implementation of a {@link MetricExporter}
   */
  MetricExporter fromConfig(ExporterConfig config);
}
