/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.properties;

import io.opentelemetry.api.internal.ConfigUtil;
import io.opentelemetry.exporter.otlp.internal.OtlpConfigUtil;
import io.opentelemetry.instrumentation.resources.ResourceProviderPropertiesCustomizer;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.internal.DefaultConfigProperties;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import javax.annotation.Nullable;
import org.springframework.core.env.Environment;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class SpringConfigProperties implements ConfigProperties {
  private final Environment environment;

  private final ExpressionParser parser;
  private final OtlpExporterProperties otlpExporterProperties;
  private final OtelResourceProperties resourceProperties;
  private final SpringOtelProperties springOtelProperties;
  private final ConfigProperties otelSdkProperties;
  private final ConfigProperties customizedResourceProviderProperties;

  private static final Map<String, Function<SpringOtelProperties, List<String>>> LIST_PROPERTIES =
      new HashMap<>();

  static final String DISABLED_KEY = "otel.java.disabled.resource.providers";
  static final String ENABLED_KEY = "otel.java.enabled.resource.providers";

  static {
    LIST_PROPERTIES.put(ENABLED_KEY, SpringOtelProperties::getJavaEnabledResourceProviders);
    LIST_PROPERTIES.put(DISABLED_KEY, SpringOtelProperties::getJavaDisabledResourceProviders);
    LIST_PROPERTIES.put(
        "otel.experimental.metrics.view.config",
        SpringOtelProperties::getExperimentalMetricsViewConfig);
    LIST_PROPERTIES.put(
        "otel.experimental.resource.disabled.keys",
        SpringOtelProperties::getExperimentalResourceDisabledKeys);
    LIST_PROPERTIES.put("otel.propagators", SpringOtelProperties::getPropagators);
  }

  public SpringConfigProperties(
      Environment environment,
      ExpressionParser parser,
      OtlpExporterProperties otlpExporterProperties,
      OtelResourceProperties resourceProperties,
      SpringOtelProperties springOtelProperties,
      ConfigProperties otelSdkProperties) {
    this.environment = environment;
    this.parser = parser;
    this.otlpExporterProperties = otlpExporterProperties;
    this.resourceProperties = resourceProperties;
    this.springOtelProperties = springOtelProperties;
    this.otelSdkProperties = otelSdkProperties;
    this.customizedResourceProviderProperties =
        createCustomizedProperties(otelSdkProperties, springOtelProperties);
  }

  private static void addList(
      Map<String, String> map, String key, List<String> list, ConfigProperties configProperties) {
    if (!list.isEmpty()) {
      map.put(key, String.join(",", list));
    } else {
      String defaultValue = configProperties.getString(key);
      if (defaultValue != null) {
        map.put(key, defaultValue);
      }
    }
  }

  private static ConfigProperties createCustomizedProperties(
      ConfigProperties configProperties, SpringOtelProperties springOtelProperties) {
    // find a better way to do this
    Map<String, String> map = new HashMap<>();
    addList(
        map, ENABLED_KEY, springOtelProperties.getJavaEnabledResourceProviders(), configProperties);
    addList(
        map,
        DISABLED_KEY,
        springOtelProperties.getJavaDisabledResourceProviders(),
        configProperties);

    return DefaultConfigProperties.createFromMap(
        new ResourceProviderPropertiesCustomizer()
            .customize(DefaultConfigProperties.createFromMap(map)));
  }

  // visible for testing
  public static ConfigProperties create(
      Environment env,
      OtlpExporterProperties otlpExporterProperties,
      OtelResourceProperties resourceProperties,
      SpringOtelProperties springOtelProperties,
      ConfigProperties fallback) {
    return new SpringConfigProperties(
        env,
        new SpelExpressionParser(),
        otlpExporterProperties,
        resourceProperties,
        springOtelProperties,
        fallback);
  }

  @Nullable
  @Override
  public String getString(String name) {
    String normalizedName = ConfigUtil.normalizeEnvironmentVariableKey(name);
    String value = environment.getProperty(normalizedName, String.class);
    if (value == null && normalizedName.equals("otel.exporter.otlp.protocol")) {
      // SDK autoconfigure module defaults to `grpc`, but this module aligns with recommendation
      // in specification to default to `http/protobuf`
      return OtlpConfigUtil.PROTOCOL_HTTP_PROTOBUF;
    }
    return or(value, otelSdkProperties.getString(name));
  }

  @Nullable
  @Override
  public Boolean getBoolean(String name) {
    return or(
        environment.getProperty(ConfigUtil.normalizeEnvironmentVariableKey(name), Boolean.class),
        otelSdkProperties.getBoolean(name));
  }

  @Nullable
  @Override
  public Integer getInt(String name) {
    return or(
        environment.getProperty(ConfigUtil.normalizeEnvironmentVariableKey(name), Integer.class),
        otelSdkProperties.getInt(name));
  }

  @Nullable
  @Override
  public Long getLong(String name) {
    return or(
        environment.getProperty(ConfigUtil.normalizeEnvironmentVariableKey(name), Long.class),
        otelSdkProperties.getLong(name));
  }

  @Nullable
  @Override
  public Double getDouble(String name) {
    return or(
        environment.getProperty(ConfigUtil.normalizeEnvironmentVariableKey(name), Double.class),
        otelSdkProperties.getDouble(name));
  }

  @SuppressWarnings("unchecked")
  @Override
  public List<String> getList(String name) {

    String normalizedName = ConfigUtil.normalizeEnvironmentVariableKey(name);

    Function<SpringOtelProperties, List<String>> get = LIST_PROPERTIES.get(normalizedName);
    if (get != null) {
      List<String> c = customizedResourceProviderProperties.getList(name);
      if (!c.isEmpty()) {
        return c;
      }
      return get.apply(springOtelProperties);
    }

    return or(environment.getProperty(normalizedName, List.class), otelSdkProperties.getList(name));
  }

  @Nullable
  @Override
  public Duration getDuration(String name) {
    String value = getString(name);
    if (value == null) {
      return otelSdkProperties.getDuration(name);
    }
    return DefaultConfigProperties.createFromMap(Collections.singletonMap(name, value))
        .getDuration(name);
  }

  @SuppressWarnings("unchecked")
  @Override
  public Map<String, String> getMap(String name) {
    Map<String, String> otelSdkMap = otelSdkProperties.getMap(name);

    String normalizedName = ConfigUtil.normalizeEnvironmentVariableKey(name);
    // maps from config properties are not supported by Environment, so we have to fake it
    switch (normalizedName) {
      case "otel.resource.attributes":
        return mergeWithOtel(resourceProperties.getAttributes(), otelSdkMap);
      case "otel.exporter.otlp.headers":
        return mergeWithOtel(otlpExporterProperties.getHeaders(), otelSdkMap);
      case "otel.exporter.otlp.logs.headers":
        return mergeWithOtel(otlpExporterProperties.getLogs().getHeaders(), otelSdkMap);
      case "otel.exporter.otlp.metrics.headers":
        return mergeWithOtel(otlpExporterProperties.getMetrics().getHeaders(), otelSdkMap);
      case "otel.exporter.otlp.traces.headers":
        return mergeWithOtel(otlpExporterProperties.getTraces().getHeaders(), otelSdkMap);
      default:
        break;
    }

    String value = environment.getProperty(normalizedName);
    if (value == null) {
      return otelSdkMap;
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
  private static Map<String, String> mergeWithOtel(
      Map<String, String> springMap, Map<String, String> otelSdkMap) {
    Map<String, String> merged = new HashMap<>(otelSdkMap);
    merged.putAll(springMap);
    return merged;
  }

  @Nullable
  private static <T> T or(@Nullable T first, @Nullable T second) {
    return first != null ? first : second;
  }
}
