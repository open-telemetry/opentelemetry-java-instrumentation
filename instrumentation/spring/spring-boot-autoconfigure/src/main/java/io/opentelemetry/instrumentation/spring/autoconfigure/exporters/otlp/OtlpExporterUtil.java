/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.exporters.otlp;

import io.opentelemetry.exporter.otlp.internal.OtlpConfigUtil;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

class OtlpExporterUtil {
  private OtlpExporterUtil() {}

  static <GrpcBuilder, HttpBuilder, Exporter> Exporter applySignalProperties(
      String dataType,
      OtlpExporterProperties properties,
      OtlpExporterProperties.SignalProperties signalProperties,
      Supplier<GrpcBuilder> newGrpcBuilder,
      Supplier<HttpBuilder> newHttpBuilder,
      BiConsumer<GrpcBuilder, String> setGrpcEndpoint,
      BiConsumer<HttpBuilder, String> setHttpEndpoint,
      BiConsumer<GrpcBuilder, Map.Entry<String, String>> addGrpcHeader,
      BiConsumer<HttpBuilder, Map.Entry<String, String>> addHttpHeader,
      BiConsumer<GrpcBuilder, Duration> setGrpcTimeout,
      BiConsumer<HttpBuilder, Duration> setHttpTimeout,
      Function<GrpcBuilder, Exporter> buildGrpcExporter,
      Function<HttpBuilder, Exporter> buildHttpExporter) {

    String protocol = signalProperties.getProtocol();
    if (protocol == null) {
      protocol = properties.getProtocol();
    }

    GrpcBuilder grpcBuilder = newGrpcBuilder.get();
    HttpBuilder httpBuilder = newHttpBuilder.get();

    boolean isHttpProtobuf = Objects.equals(protocol, OtlpConfigUtil.PROTOCOL_HTTP_PROTOBUF);

    String endpoint = signalProperties.getEndpoint();
    if (endpoint == null) {
      endpoint = properties.getEndpoint();
    }
    if (endpoint != null) {
      if (isHttpProtobuf) {
        if (!endpoint.endsWith("/")) {
          endpoint += "/";
        }
        endpoint += signalPath(dataType);
      }

      if (isHttpProtobuf) {
        setHttpEndpoint.accept(httpBuilder, endpoint);
      } else {
        setGrpcEndpoint.accept(grpcBuilder, endpoint);
      }
    }

    Map<String, String> headers = signalProperties.getHeaders();
    if (headers.isEmpty()) {
      headers = properties.getHeaders();
    }
    for (Map.Entry<String, String> entry : headers.entrySet()) {
      if (isHttpProtobuf) {
        addHttpHeader.accept(httpBuilder, entry);
      } else {
        addGrpcHeader.accept(grpcBuilder, entry);
      }
    }

    Duration timeout = signalProperties.getTimeout();
    if (timeout == null) {
      timeout = properties.getTimeout();
    }
    if (timeout != null) {
      if (isHttpProtobuf) {
        setHttpTimeout.accept(httpBuilder, timeout);
      } else {
        setGrpcTimeout.accept(grpcBuilder, timeout);
      }
    }

    return isHttpProtobuf
        ? buildHttpExporter.apply(httpBuilder)
        : buildGrpcExporter.apply(grpcBuilder);
  }

  private static String signalPath(String dataType) {
    switch (dataType) {
      case OtlpConfigUtil.DATA_TYPE_METRICS:
        return "v1/metrics";
      case OtlpConfigUtil.DATA_TYPE_TRACES:
        return "v1/traces";
      case OtlpConfigUtil.DATA_TYPE_LOGS:
        return "v1/logs";
      default:
        throw new IllegalArgumentException(
            "Cannot determine signal path for unrecognized data type: " + dataType);
    }
  }
}
