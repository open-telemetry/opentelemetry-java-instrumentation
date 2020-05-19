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

import io.opentelemetry.exporters.zipkin.ZipkinExporterConfiguration;
import io.opentelemetry.exporters.zipkin.ZipkinSpanExporter;
import io.opentelemetry.sdk.contrib.auto.config.Config;
import io.opentelemetry.sdk.contrib.auto.config.SpanExporterFactory;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import zipkin2.reporter.okhttp3.OkHttpSender;

public class ZipkinExporterFactory implements SpanExporterFactory {
  private static final String ZIPKIN_ENDPOINT = "zipkin.endpoint";
  private static final String DEFAULT_ZIPKIN_ENDPOINT = "http://localhost:9411/api/v2/spans";

  private static final String ZIPKIN_SERVICE_NAME = "zipkin.service.name";
  private static final String DEFAULT_ZIPKIN_SERVICE_NAME = "(unknown service)";

  @Override
  public SpanExporter fromConfig(Config config) {
    final String zipkinEndpoint = config.getString(ZIPKIN_ENDPOINT, DEFAULT_ZIPKIN_ENDPOINT);
    final String serviceName = config.getString(ZIPKIN_SERVICE_NAME, DEFAULT_ZIPKIN_SERVICE_NAME);
    return ZipkinSpanExporter.create(
        ZipkinExporterConfiguration.builder()
            .setSender(OkHttpSender.create(zipkinEndpoint))
            .setServiceName(serviceName)
            .build());
  }
}
