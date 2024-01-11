/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.exporters.otlp;

import io.opentelemetry.exporter.otlp.internal.OtlpConfigUtil;
import java.time.Duration;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class OtlpExporterUtil {
  private OtlpExporterUtil() {}

  private static final Logger logger = LoggerFactory.getLogger(OtlpExporterUtil.class);

  static <G, H, E> E applySignalProperties(
      String dataType,
      OtlpExporterProperties properties,
      OtlpExporterProperties.SignalProperties signalProperties,
      Supplier<G> newGrpcBuilder,
      Supplier<H> newHttpBuilder,
      BiConsumer<G, String> setGrpcEndpoint,
      BiConsumer<H, String> setHttpEndpoint,
      BiConsumer<G, Map.Entry<String, String>> addGrpcHeader,
      BiConsumer<H, Map.Entry<String, String>> addHttpHeader,
      BiConsumer<G, Duration> setGrpcTimeout,
      BiConsumer<H, Duration> setHttpTimeout,
      Function<G, E> buildGrpcExporter,
      Function<H, E> buildHttpExporter) {

    String protocol = signalProperties.getProtocol();
    if (protocol == null) {
      protocol = properties.getProtocol();
    }

    G grpcBuilder = newGrpcBuilder.get();
    H httpBuilder = newHttpBuilder.get();

    boolean isHttpProtobuf = !"grpc".equals(protocol);

    if (protocol != null
        && !"grpc".equals(protocol)
        && !OtlpConfigUtil.PROTOCOL_HTTP_PROTOBUF.equals(protocol)) {
      logger.warn(
          protocol
              + " protocol is not managed. "
              + OtlpConfigUtil.PROTOCOL_HTTP_PROTOBUF
              + " will be used.");
    }

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
