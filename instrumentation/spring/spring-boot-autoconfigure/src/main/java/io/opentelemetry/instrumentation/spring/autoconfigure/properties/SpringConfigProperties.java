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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import org.springframework.core.env.Environment;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;

public class SpringConfigProperties implements ConfigProperties {
  private final Environment environment;

  private final ExpressionParser parser;
  private final OtlpExporterProperties otlpExporterProperties;
  private final OtelResourceProperties resourceProperties;
  private final PropagationProperties propagationProperties;
  private final ConfigProperties fallback;

  public SpringConfigProperties(
      Environment environment,
      ExpressionParser parser,
      OtlpExporterProperties otlpExporterProperties,
      OtelResourceProperties resourceProperties,
      PropagationProperties propagationProperties,
      ConfigProperties fallback) {
    this.environment = environment;
    this.parser = parser;
    this.otlpExporterProperties = otlpExporterProperties;
    this.resourceProperties = resourceProperties;
    this.propagationProperties = propagationProperties;
    this.fallback = fallback;
  }

  // visible for testing
  public static ConfigProperties create(
      Environment env,
      OtlpExporterProperties otlpExporterProperties,
      OtelResourceProperties resourceProperties,
      PropagationProperties propagationProperties,
      ConfigProperties fallback) {
    return new SpringConfigProperties(
        env,
        new SpelExpressionParser(),
        otlpExporterProperties,
        resourceProperties,
        propagationProperties,
        fallback);
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
    return or(value, fallback.getString(name));
  }

  @Nullable
  @Override
  public Boolean getBoolean(String name) {
    return or(environment.getProperty(name, Boolean.class), fallback.getBoolean(name));
  }

  @Nullable
  @Override
  public Integer getInt(String name) {
    return or(environment.getProperty(name, Integer.class), fallback.getInt(name));
  }

  @Nullable
  @Override
  public Long getLong(String name) {
    return or(environment.getProperty(name, Long.class), fallback.getLong(name));
  }

  @Nullable
  @Override
  public Double getDouble(String name) {
    return or(environment.getProperty(name, Double.class), fallback.getDouble(name));
  }

  @SuppressWarnings("unchecked")
  @Override
  public List<String> getList(String name) {
    if (name.equals("otel.propagators")) {
      return propagationProperties.getPropagators();
    }

    return or(environment.getProperty(name, List.class), fallback.getList(name));
  }

  @Nullable
  @Override
  public Duration getDuration(String name) {
    String value = getString(name);
    if (value == null) {
      return fallback.getDuration(name);
    }
    return DefaultConfigProperties.createFromMap(Collections.singletonMap(name, value))
        .getDuration(name);
  }

  @SuppressWarnings("unchecked")
  @Override
  public Map<String, String> getMap(String name) {
    Map<String, String> fallbackMap = fallback.getMap(name);
    // maps from config properties are not supported by Environment, so we have to fake it
    switch (name) {
      case "otel.resource.attributes":
        return mergeWithFallback(resourceProperties.getAttributes(), fallbackMap);
      case "otel.exporter.otlp.headers":
        return mergeWithFallback(otlpExporterProperties.getHeaders(), fallbackMap);
      case "otel.exporter.otlp.logs.headers":
        return mergeWithFallback(otlpExporterProperties.getLogs().getHeaders(), fallbackMap);
      case "otel.exporter.otlp.metrics.headers":
        return mergeWithFallback(otlpExporterProperties.getMetrics().getHeaders(), fallbackMap);
      case "otel.exporter.otlp.traces.headers":
        return mergeWithFallback(otlpExporterProperties.getTraces().getHeaders(), fallbackMap);
      default:
        break;
    }

    String value = environment.getProperty(name);
    if (value == null) {
      return fallbackMap;
    }
    return (Map<String, String>) parser.parseExpression(value).getValue();
  }

  /**
   * If you specify the environment variable <code>OTEL_RESOURCE_ATTRIBUTES_POD_NAME</code>, then
   * Spring Boot will ignore <code>OTEL_RESOURCE_ATTRIBUTES</code>, which violates the principle of
   * least surprise. This method merges the two maps, giving precedence to <code>
   * OTEL_RESOURCE_ATTRIBUTES_POD_NAME</code>, which is more specific and which is also the value
   * that Spring Boot will use (and which will honor <a
   * href="https://docs.spring.io/spring-framework/docs/3.2.x/spring-framework-reference/html/expressions.html">SpEL</a>).
   */
  private static Map<String, String> mergeWithFallback(
      Map<String, String> attributes, Map<String, String> fallbackMap) {
    Map<String, String> merged = new HashMap<>(fallbackMap);
    merged.putAll(attributes);
    return merged;
  }

  @Nullable
  private static <T> T or(@Nullable T first, @Nullable T second) {
    return first != null ? first : second;
  }
}
