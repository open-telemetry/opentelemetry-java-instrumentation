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

package io.opentelemetry.auto.exporters.otlp;

import io.grpc.ManagedChannelBuilder;
import io.opentelemetry.exporters.otlp.OtlpGrpcMetricExporter;
import io.opentelemetry.sdk.contrib.auto.config.Config;
import io.opentelemetry.sdk.contrib.auto.config.MetricExporterFactory;
import io.opentelemetry.sdk.metrics.export.MetricExporter;

public class OtlpMetricExporterFactory implements MetricExporterFactory {
  private static final String OTLP_ENDPOINT = "otlp.endpoint";

  @Override
  public MetricExporter fromConfig(final Config config) {
    final String otlpEndpoint = config.getString(OTLP_ENDPOINT, "localhost:55680");
    if (otlpEndpoint.isEmpty()) {
      throw new IllegalStateException("ota.exporter.otlp.endpoint is required");
    }
    return OtlpGrpcMetricExporter.newBuilder()
        .setChannel(ManagedChannelBuilder.forTarget(otlpEndpoint).usePlaintext().build())
        .build();
  }
}
