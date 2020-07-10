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

package io.opentelemetry.auto.exporters.jaeger;

import io.grpc.ManagedChannelBuilder;
import io.opentelemetry.exporters.jaeger.JaegerGrpcSpanExporter;
import io.opentelemetry.sdk.extensions.auto.config.Config;
import io.opentelemetry.sdk.extensions.auto.config.SpanExporterFactory;
import io.opentelemetry.sdk.trace.export.SpanExporter;

public class JaegerExporterFactory implements SpanExporterFactory {
  private static final String JAEGER_ENDPOINT = "jaeger.endpoint";
  private static final String DEFAULT_JAEGER_ENDPOINT = "localhost:14250";

  private static final String JAEGER_SERVICE_NAME = "jaeger.service.name";
  private static final String DEFAULT_JAEGER_SERVICE_NAME = "(unknown service)";

  @Override
  public SpanExporter fromConfig(final Config config) {
    final String jaegerEndpoint = config.getString(JAEGER_ENDPOINT, DEFAULT_JAEGER_ENDPOINT);
    final String serviceName = config.getString(JAEGER_SERVICE_NAME, DEFAULT_JAEGER_SERVICE_NAME);
    return JaegerGrpcSpanExporter.newBuilder()
        .setServiceName(serviceName)
        .setChannel(ManagedChannelBuilder.forTarget(jaegerEndpoint).usePlaintext().build())
        .build();
  }
}
