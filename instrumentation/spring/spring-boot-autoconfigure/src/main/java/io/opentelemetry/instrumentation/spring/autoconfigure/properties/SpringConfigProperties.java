/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.properties;

import io.opentelemetry.exporter.otlp.internal.OtlpConfigUtil;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.internal.DefaultConfigProperties;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import org.springframework.core.env.Environment;
import org.springframework.expression.ExpressionParser;

public class SpringConfigProperties implements ConfigProperties {
  private final Environment environment;

  private final ExpressionParser parser;
  private final OtlpExporterProperties otlpExporterProperties;
  private final OtelResourceProperties resourceProperties;
  private final PropagationProperties propagationProperties;

  public SpringConfigProperties(
      Environment environment,
      ExpressionParser parser,
      OtlpExporterProperties otlpExporterProperties,
      OtelResourceProperties resourceProperties,
      PropagationProperties propagationProperties) {
    this.environment = environment;
    this.parser = parser;
    this.otlpExporterProperties = otlpExporterProperties;
    this.resourceProperties = resourceProperties;
    this.propagationProperties = propagationProperties;
  }

  @Nullable
  @Override
  public String getString(String name) {
    String value = environment.getProperty(name, String.class);
    if (value == null && name.equals("otel.exporter.otlp.protocol")) {
      // SDK autoconfigure module defaults to `grpc`, but this module aligns with recommendation
      // in specification to default to `http/protobuf`
      return OtlpConfigUtil.PROTOCOL_HTTP_PROTOBUF;
    }
    return value;
  }

  @Nullable
  @Override
  public Boolean getBoolean(String name) {
    return environment.getProperty(name, Boolean.class);
  }

  @Nullable
  @Override
  public Integer getInt(String name) {
    return environment.getProperty(name, Integer.class);
  }

  @Nullable
  @Override
  public Long getLong(String name) {
    return environment.getProperty(name, Long.class);
  }

  @Nullable
  @Override
  public Double getDouble(String name) {
    return environment.getProperty(name, Double.class);
  }

  @SuppressWarnings("unchecked")
  @Override
  public List<String> getList(String name) {
    if (name.equals("otel.propagators")) {
      return propagationProperties.getPropagators();
    }

    List<String> value = environment.getProperty(name, List.class);
    return value == null ? Collections.emptyList() : value;
  }

  @Nullable
  @Override
  public Duration getDuration(String name) {
    String value = getString(name);
    if (value == null) {
      return null;
    }
    return DefaultConfigProperties.createFromMap(Collections.singletonMap(name, value))
        .getDuration(name);
  }

  @SuppressWarnings("unchecked")
  @Override
  public Map<String, String> getMap(String name) {
    // maps from config properties are not supported by Environment, so we have to fake it
    switch (name) {
      case "otel.resource.attributes":
        return resourceProperties.getAttributes();
      case "otel.exporter.otlp.headers":
        return otlpExporterProperties.getHeaders();
      case "otel.exporter.otlp.logs.headers":
        return otlpExporterProperties.getLogs().getHeaders();
      case "otel.exporter.otlp.metrics.headers":
        return otlpExporterProperties.getMetrics().getHeaders();
      case "otel.exporter.otlp.traces.headers":
        return otlpExporterProperties.getTraces().getHeaders();
      default:
        break;
    }

    String value = environment.getProperty(name);
    if (value == null) {
      return Collections.emptyMap();
    }
    return (Map<String, String>) parser.parseExpression(value).getValue();
  }
}
