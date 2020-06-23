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

import static io.grpc.Metadata.ASCII_STRING_MARSHALLER;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall.SimpleForwardingClientCall;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.Metadata.Key;
import io.grpc.MethodDescriptor;
import io.opentelemetry.exporters.otlp.OtlpGrpcSpanExporter;
import io.opentelemetry.exporters.otlp.OtlpGrpcSpanExporter.Builder;
import io.opentelemetry.sdk.contrib.auto.config.Config;
import io.opentelemetry.sdk.contrib.auto.config.SpanExporterFactory;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

public class OtlpSpanExporterFactory implements SpanExporterFactory {
  private static final String OTLP_ENDPOINT = "otlp.endpoint";
  private static final String OTLP_DEADLINE_MS = "otlp.deadline.ms";
  private static final String OTLP_USE_TLS = "otlp.use.tls";
  private static final String OTLP_METADATA = "otlp.metadata";

  @Override
  public SpanExporter fromConfig(final Config config) {
    final String otlpEndpoint = config.getString(OTLP_ENDPOINT, "localhost:55680");
    if (otlpEndpoint.isEmpty()) {
      throw new IllegalStateException("ota.exporter.otlp.endpoint is required");
    }

    final Builder builder = OtlpGrpcSpanExporter.newBuilder();

    final long deadlineMs = config.getLong(OTLP_DEADLINE_MS, -1);
    if (deadlineMs > 0) {
      builder.setDeadlineMs(deadlineMs);
    }

    final ManagedChannelBuilder<?> managedChannelBuilder =
        ManagedChannelBuilder.forTarget(otlpEndpoint);

    if (config.getBoolean(OTLP_USE_TLS, false)) {
      managedChannelBuilder.useTransportSecurity();
    } else {
      managedChannelBuilder.usePlaintext();
    }

    final String metadata = config.getString(OTLP_METADATA, null);
    if (metadata != null) {
      final Map<Key<String>, String> customMetadata = new HashMap<>();
      for (String keyValueString : metadata.split(";")) {
        final String[] keyValue = keyValueString.split("=");
        if (keyValue.length == 2) {
          customMetadata.put(Key.of(keyValue[0], ASCII_STRING_MARSHALLER), keyValue[1]);
        }
      }
      if (!customMetadata.isEmpty()) {
        managedChannelBuilder.intercept(
            new ClientInterceptor() {
              @Override
              public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
                  MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {
                return new SimpleForwardingClientCall<ReqT, RespT>(
                    next.newCall(method, callOptions)) {
                  @Override
                  public void start(final Listener<RespT> responseListener, Metadata headers) {
                    for (Entry<Key<String>, String> entry : customMetadata.entrySet()) {
                      headers.put(entry.getKey(), entry.getValue());
                    }
                    super.start(responseListener, headers);
                  }
                };
              }
            });
      }
    }

    return builder.setChannel(managedChannelBuilder.build()).build();
  }
}
