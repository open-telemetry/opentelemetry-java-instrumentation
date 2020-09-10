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

package io.opentelemetry.javaagent.spi.exporter;

import io.opentelemetry.sdk.trace.export.SpanExporter;

/**
 * A {@link SpanExporterFactory} acts as the bootstrap for a {@link SpanExporter} implementation. An
 * exporter must register its implementation of a {@link SpanExporterFactory} through the Java SPI
 * framework.
 */
public interface SpanExporterFactory {
  /**
   * Creates an instance of a {@link SpanExporter} based on the provided configuration.
   *
   * @param config The configuration
   * @return An implementation of a {@link SpanExporter}
   */
  SpanExporter fromConfig(ExporterConfig config);
}
