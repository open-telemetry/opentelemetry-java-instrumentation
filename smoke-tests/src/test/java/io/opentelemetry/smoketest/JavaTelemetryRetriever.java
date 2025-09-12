/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.GeneratedMessage;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import io.opentelemetry.javaagent.testing.common.AgentTestingExporterAccess;
import io.opentelemetry.proto.collector.logs.v1.ExportLogsServiceRequest;
import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceRequest;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.testing.internal.armeria.client.WebClient;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
    Collection<ExportTraceServiceRequest> collection =
        waitForTelemetry("get-traces", () -> ExportTraceServiceRequest.newBuilder());
    Stream<io.opentelemetry.proto.trace.v1.Span> objectStream =
        collection.stream()
            .flatMap(
                req ->
                    req.getResourceSpansList().stream()
                        .flatMap(
                            rs ->
                                rs.getScopeSpansList().stream()
                                    .flatMap(ss -> ss.getSpansList().stream())));

    return AgentTestingExporterAccess.getSpanData(
        collection.stream().flatMap(req -> req.getResourceSpansList().stream()));
  }

  public Collection<ExportMetricsServiceRequest> waitForMetrics() {
    return waitForTelemetry("get-metrics", () -> ExportMetricsServiceRequest.newBuilder());
  }

  public Collection<ExportLogsServiceRequest> waitForLogs() {
    return waitForTelemetry("get-logs", () -> ExportLogsServiceRequest.newBuilder());
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
    } catch (JsonProcessingException | InterruptedException e) {
      throw new RuntimeException(e);
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
      System.out.println("Current content size " + previousSize);
      TimeUnit.MILLISECONDS.sleep(500);
    }

    return content;
  }

  public final WebClient getClient() {
    return client;
  }
}
