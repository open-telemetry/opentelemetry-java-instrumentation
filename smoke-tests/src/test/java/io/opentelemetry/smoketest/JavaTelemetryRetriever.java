/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest;

import io.opentelemetry.instrumentation.testing.internal.TelemetryConverter;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.testing.internal.armeria.client.WebClient;
import io.opentelemetry.testing.internal.jackson.core.JsonProcessingException;
import io.opentelemetry.testing.internal.jackson.databind.ObjectMapper;
import io.opentelemetry.testing.internal.proto.collector.logs.v1.ExportLogsServiceRequest;
import io.opentelemetry.testing.internal.proto.collector.metrics.v1.ExportMetricsServiceRequest;
import io.opentelemetry.testing.internal.proto.collector.trace.v1.ExportTraceServiceRequest;
import io.opentelemetry.testing.internal.protobuf.GeneratedMessage;
import io.opentelemetry.testing.internal.protobuf.InvalidProtocolBufferException;
import io.opentelemetry.testing.internal.protobuf.util.JsonFormat;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class JavaTelemetryRetriever {
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private final WebClient client;

  public JavaTelemetryRetriever(int backendPort) {
    client = WebClient.of("http://localhost:" + backendPort);
  }

  public void clearTelemetry() {
    client.get("/clear").aggregate().join();
  }

  public List<SpanData> waitForTraces() {
    Collection<ExportTraceServiceRequest> requests =
        waitForTelemetry("get-traces", () -> ExportTraceServiceRequest.newBuilder());
    return TelemetryConverter.getSpanData(
        requests.stream()
            .flatMap(r -> r.getResourceSpansList().stream())
            .collect(Collectors.toList()));
  }

  public Collection<MetricData> waitForMetrics() {
    Collection<ExportMetricsServiceRequest> requests =
        waitForTelemetry("get-metrics", () -> ExportMetricsServiceRequest.newBuilder());
    return TelemetryConverter.getMetricsData(
        requests.stream()
            .flatMap(r -> r.getResourceMetricsList().stream())
            .collect(Collectors.toList()));
  }

  public Collection<LogRecordData> waitForLogs() {
    Collection<ExportLogsServiceRequest> requests =
        waitForTelemetry("get-logs", () -> ExportLogsServiceRequest.newBuilder());
    return TelemetryConverter.getLogRecordData(
        requests.stream()
            .flatMap(r -> r.getResourceLogsList().stream())
            .collect(Collectors.toList()));
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private <T extends GeneratedMessage, B extends GeneratedMessage.Builder>
      Collection<T> waitForTelemetry(String path, Supplier<B> builderConstructor) {
    try {
      return OBJECT_MAPPER
          .readTree(waitForContent(path))
          .valueStream()
          .map(
              it -> {
                B builder = builderConstructor.get();
                // TODO: Register parser into object mapper to avoid de -> re -> deserialize.
                try {
                  JsonFormat.parser().merge(OBJECT_MAPPER.writeValueAsString(it), builder);
                  return (T) builder.build();
                } catch (InvalidProtocolBufferException | JsonProcessingException e) {
                  throw new RuntimeException(e);
                }
              })
          .collect(Collectors.toList());
    } catch (InterruptedException
        | io.opentelemetry.testing.internal.jackson.core.JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  @SuppressWarnings("SystemOut")
  private String waitForContent(String path) throws InterruptedException {
    long previousSize = 0;
    long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(30);
    String content = "[]";
    while (System.currentTimeMillis() < deadline) {
      content = client.get(path).aggregate().join().contentUtf8();
      if (content.length() > 2 && content.length() == previousSize) {
        break;
      }

      previousSize = content.length();
      System.out.println("Current content size " + previousSize);
      TimeUnit.MILLISECONDS.sleep(500);
    }

    return content;
  }

  public final WebClient getClient() {
    return client;
  }
}
