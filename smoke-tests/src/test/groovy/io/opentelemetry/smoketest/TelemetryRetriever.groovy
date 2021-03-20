/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.protobuf.GeneratedMessageV3
import com.google.protobuf.util.JsonFormat
import io.opentelemetry.instrumentation.test.utils.OkHttpUtils
import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceRequest
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest
import java.util.concurrent.TimeUnit
import java.util.function.Supplier
import okhttp3.OkHttpClient
import okhttp3.Request

class TelemetryRetriever {
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
  protected static final OkHttpClient CLIENT = OkHttpUtils.client()

  final int backendPort

  TelemetryRetriever(int backendPort) {
    this.backendPort = backendPort
  }

  void clearTelemetry() {
    CLIENT.newCall(new Request.Builder()
      .url("http://localhost:${backendPort}/clear")
      .build())
      .execute()
      .close()
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
      def body = content = CLIENT.newCall(new Request.Builder()
        .url("http://localhost:${backendPort}/${path}")
        .build())
        .execute()
        .body()
      try {
        content = body.string()
      } finally {
        body.close()
      }
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
