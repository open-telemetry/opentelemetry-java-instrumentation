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

package io.opentelemetry.javaagent.exporters.otlp;

import com.google.auto.service.AutoService;
import io.opentelemetry.exporters.otlp.OtlpGrpcMetricExporter;
import io.opentelemetry.javaagent.spi.exporter.ExporterConfig;
import io.opentelemetry.javaagent.spi.exporter.MetricExporterFactory;
import io.opentelemetry.sdk.metrics.export.MetricExporter;

@AutoService(MetricExporterFactory.class)
public class OtlpMetricExporterFactory implements MetricExporterFactory {

  @Override
  public MetricExporter fromConfig(ExporterConfig config) {
    return OtlpGrpcMetricExporter.newBuilder()
        .readEnvironmentVariables()
        .readSystemProperties()
        .build();
  }
}
