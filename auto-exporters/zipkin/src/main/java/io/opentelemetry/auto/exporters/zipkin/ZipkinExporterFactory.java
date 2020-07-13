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

package io.opentelemetry.auto.exporters.zipkin;

import io.opentelemetry.exporters.zipkin.ZipkinSpanExporter;
import io.opentelemetry.sdk.extensions.auto.config.Config;
import io.opentelemetry.sdk.extensions.auto.config.SpanExporterFactory;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import zipkin2.reporter.okhttp3.OkHttpSender;

public class ZipkinExporterFactory implements SpanExporterFactory {
  private static final String ZIPKIN_ENDPOINT_PROPERTY = "otel.zipkin.endpoint";
  private static final String ZIPKIN_ENDPOINT_ENV_VARIABLE = "OTEL_ZIPKIN_ENDPOINT";
  private static final String DEFAULT_ZIPKIN_ENDPOINT = "http://localhost:9411/api/v2/spans";

  @Override
  public SpanExporter fromConfig(Config config) {
    String zipkinEndpoint =
        System.getProperty(ZIPKIN_ENDPOINT_PROPERTY, System.getenv(ZIPKIN_ENDPOINT_ENV_VARIABLE));
    if (zipkinEndpoint == null) {
      zipkinEndpoint = DEFAULT_ZIPKIN_ENDPOINT;
    }

    return ZipkinSpanExporter.newBuilder()
        .readEnvironmentVariables()
        .readSystemProperties()
        .setSender(OkHttpSender.create(zipkinEndpoint))
        .build();
  }
}
