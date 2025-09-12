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
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class JavaTelemetryRetriever {
  private final WebClient client;

  public JavaTelemetryRetriever(int backendPort) {
    client = WebClient.of("http://localhost:" + backendPort);
  }

  public void clearTelemetry() {
    client.get("/clear").aggregate().join();
  }

  public List<SpanData> waitForTraces() {
    try {
      return TelemetryConverter.getSpanData(
          Collections.singletonList(waitForContent("get-traces")));
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  public Collection<MetricData> waitForMetrics() {
    try {
      return TelemetryConverter.getMetricsData(
          Collections.singletonList(waitForContent("get-metrics")));
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  public Collection<LogRecordData> waitForLogs() {
    try {
      return TelemetryConverter.getLogRecordData(
          Collections.singletonList(waitForContent("get-logs")));
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  private byte[] waitForContent(String path) throws InterruptedException {
    long previousSize = 0;
    long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(30);
    byte[] content = "[]".getBytes(StandardCharsets.UTF_8);
    while (System.currentTimeMillis() < deadline) {
      content = client.get(path).aggregate().join().content().array();
      if (content.length > 2 && content.length == previousSize) {
        break;
      }

      previousSize = content.length;
      System.out.println("Current content size " + previousSize);
      TimeUnit.MILLISECONDS.sleep(500);
    }

    // todo remove debug
    System.out.println(new String(content, StandardCharsets.UTF_8));

    return content;
  }

  public final WebClient getClient() {
    return client;
  }
}
