/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.exporters.otlp;

import java.time.Duration;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

class OtlpExporterUtil {
  private OtlpExporterUtil() {}

  static void applySignalProperties(
      OtlpExporterProperties properties,
      OtlpExporterProperties.SignalProperties signalProperties,
      Consumer<String> setEndpoint,
      BiConsumer<String, String> addHeader,
      Consumer<Duration> setTimeout) {
    String endpoint = properties.getLogs().getEndpoint();
    if (endpoint == null) {
      endpoint = properties.getEndpoint();
    }
    if (endpoint != null) {
      setEndpoint.accept(endpoint);
    }

    Map<String, String> headers = signalProperties.getHeaders();
    if (headers.isEmpty()) {
      headers = properties.getHeaders();
    }
    headers.forEach(addHeader);

    Duration timeout = signalProperties.getTimeout();
    if (timeout == null) {
      timeout = properties.getTimeout();
    }
    if (timeout != null) {
      setTimeout.accept(timeout);
    }
  }
}
