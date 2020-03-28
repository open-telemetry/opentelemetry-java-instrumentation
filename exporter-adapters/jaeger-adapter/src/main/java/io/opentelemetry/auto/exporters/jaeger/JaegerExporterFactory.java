/*
 * Copyright 2020, OpenTelemetry Authors
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

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.opentelemetry.exporters.jaeger.JaegerGrpcSpanExporter;
import io.opentelemetry.sdk.contrib.auto.config.Config;
import io.opentelemetry.sdk.contrib.auto.config.SpanExporterFactory;
import io.opentelemetry.sdk.trace.export.SpanExporter;

public class JaegerExporterFactory implements SpanExporterFactory {
  private static final String HOST_CONFIG = "jaeger.host";

  private static final String PORT_CONFIG = "jaeger.port";

  private static final String SERVICE_CONFIG = "service";

  private static final int DEFAULT_PORT = 14250;

  private static final String DEFAULT_SERVICE = "(unknown service)";

  @Override
  public SpanExporter fromConfig(final Config config) {
    final String host = config.getString(HOST_CONFIG, null);
    if (host == null) {
      throw new IllegalArgumentException(HOST_CONFIG + " must be specified");
    }
    final int port = config.getInt(PORT_CONFIG, DEFAULT_PORT);
    final String service = config.getString(SERVICE_CONFIG, DEFAULT_SERVICE);
    final ManagedChannel jaegerChannel =
        ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();
    return JaegerGrpcSpanExporter.newBuilder()
        .setServiceName(service)
        .setChannel(jaegerChannel)
        .build();
  }
}
