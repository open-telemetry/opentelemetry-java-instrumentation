/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.GeneratedMessage;
import com.google.protobuf.util.JsonFormat;
import io.opentelemetry.proto.collector.logs.v1.ExportLogsServiceRequest;
import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceRequest;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import io.opentelemetry.testing.internal.armeria.client.WebClient;
import java.util.Collection;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class TelemetryRetriever {
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  final WebClient client;

  TelemetryRetriever(int backendPort) {
    client = WebClient.of("http://localhost:" + backendPort);
  }

  void clearTelemetry() {
    client.get("/clear").aggregate().join();
  }

  Collection<ExportTraceServiceRequest> waitForTraces() {
    return waitForTelemetry("get-traces", ExportTraceServiceRequest::newBuilder);
  }

  Collection<ExportMetricsServiceRequest> waitForMetrics() {
    return waitForTelemetry("get-metrics", ExportMetricsServiceRequest::newBuilder);
  }

  Collection<ExportLogsServiceRequest> waitForLogs() {
    return waitForTelemetry("get-logs", ExportLogsServiceRequest::newBuilder);
  }

  @SuppressWarnings({"unchecked", "InterruptedExceptionSwallowed"})
  private <T extends GeneratedMessage, B extends GeneratedMessage.Builder<?>>
      Collection<T> waitForTelemetry(String path, Supplier<B> builderConstructor) {
    try {
      String content = waitForContent(path);

      return StreamSupport.stream(OBJECT_MAPPER.readTree(content).spliterator(), false)
          .map(
              jsonNode -> {
                B builder = builderConstructor.get();
                // TODO: Register parser into object mapper to avoid de -> re -> deserialize.
                try {
                  String json = OBJECT_MAPPER.writeValueAsString(jsonNode);
                  JsonFormat.parser().merge(json, builder);
                } catch (Exception e) {
                  throw new IllegalStateException(e);
                }
                return (T) builder.build();
              })
          .collect(Collectors.toList());
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

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
      System.out.println("Current content size $previousSize");
      TimeUnit.MILLISECONDS.sleep(500);
    }

    return content;
  }
}
