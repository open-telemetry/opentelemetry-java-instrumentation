/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.protobuf.GeneratedMessageV3
import com.google.protobuf.util.JsonFormat
import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceRequest
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest
import io.opentelemetry.testing.internal.armeria.client.WebClient

import java.util.concurrent.TimeUnit
import java.util.function.Supplier

class TelemetryRetriever {
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()


  final WebClient client

  TelemetryRetriever(int backendPort) {
    client = WebClient.of("http://localhost:${backendPort}")
  }

  void clearTelemetry() {
    client.get("/clear").aggregate().join()
  }

  Collection<ExportTraceServiceRequest> waitForTraces() {
    return waitForTelemetry("get-traces", { ExportTraceServiceRequest.newBuilder() })
  }

  Collection<ExportMetricsServiceRequest> waitForMetrics() {
    return waitForTelemetry("get-metrics", { ExportMetricsServiceRequest.newBuilder() })
  }

  private <T extends GeneratedMessageV3, B extends GeneratedMessageV3.Builder> Collection<T> waitForTelemetry(String path, Supplier<B> builderConstructor) {
    def content = waitForContent(path)

    return OBJECT_MAPPER.readTree(content).collect {
      def builder = builderConstructor.get()
      // TODO(anuraaga): Register parser into object mapper to avoid de -> re -> deserialize.
      JsonFormat.parser().merge(OBJECT_MAPPER.writeValueAsString(it), builder)
      return (T) builder.build()
    }
  }

  private String waitForContent(String path) {
    long previousSize = 0
    long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(30)
    String content = "[]"
    while (System.currentTimeMillis() < deadline) {
      content = client.get(path).aggregate().join().contentUtf8()
      if (content.length() > 2 && content.length() == previousSize) {
        break
      }
      previousSize = content.length()
      println "Curent content size $previousSize"
      TimeUnit.MILLISECONDS.sleep(500)
    }

    return content
  }
}
