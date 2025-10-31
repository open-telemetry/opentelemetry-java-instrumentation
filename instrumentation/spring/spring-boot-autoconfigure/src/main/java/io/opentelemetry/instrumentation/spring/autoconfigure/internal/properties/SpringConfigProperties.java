/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.properties;

import io.opentelemetry.api.internal.ConfigUtil;
import io.opentelemetry.exporter.otlp.internal.OtlpConfigUtil;
import io.opentelemetry.instrumentation.resources.internal.ResourceProviderPropertiesCustomizer;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.internal.DefaultConfigProperties;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
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
  private final ConfigProperties otelSdkProperties;
  private final ConfigProperties customizedListProperties;
  private final Map<String, List<String>> listPropertyValues;

  private final ConcurrentHashMap<String, Optional<String>> cachedStringValues =
      new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, Optional<Boolean>> cachedBooleanValues =
      new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, Optional<Integer>> cachedIntValues =
      new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, Optional<Long>> cachedLongValues =
      new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, Optional<Double>> cachedDoubleValues =
      new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, Optional<List<String>>> cachedListValues =
      new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, Optional<Duration>> cachedDurationValues =
      new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, Optional<Map<String, String>>> cachedMapValues =
      new ConcurrentHashMap<>();

  static final String DISABLED_KEY = "otel.java.disabled.resource.providers";
  static final String ENABLED_KEY = "otel.java.enabled.resource.providers";

  public SpringConfigProperties(
      Environment environment,
      ExpressionParser parser,
      OtlpExporterProperties otlpExporterProperties,
      OtelResourceProperties resourceProperties,
      OtelSpringProperties otelSpringProperties,
      ConfigProperties otelSdkProperties) {
    this.environment = environment;
    this.parser = parser;
    this.otlpExporterProperties = otlpExporterProperties;
    this.resourceProperties = resourceProperties;
    this.otelSdkProperties = otelSdkProperties;
    this.customizedListProperties =
        createCustomizedListProperties(otelSdkProperties, otelSpringProperties);

    listPropertyValues = createListPropertyValues(otelSpringProperties);
  }

  private static Map<String, List<String>> createListPropertyValues(
      OtelSpringProperties otelSpringProperties) {
    Map<String, List<String>> values = new HashMap<>();

    // SDK
    values.put(ENABLED_KEY, otelSpringProperties.getJavaEnabledResourceProviders());
    values.put(DISABLED_KEY, otelSpringProperties.getJavaDisabledResourceProviders());
    values.put(
        "otel.experimental.metrics.view.config",
        otelSpringProperties.getExperimentalMetricsViewConfig());
    values.put(
        "otel.experimental.resource.disabled.keys",
        otelSpringProperties.getExperimentalResourceDisabledKeys());
    values.put("otel.propagators", otelSpringProperties.getPropagators());

    // exporters
    values.put("otel.logs.exporter", otelSpringProperties.getLogsExporter());
    values.put("otel.metrics.exporter", otelSpringProperties.getMetricsExporter());
    values.put("otel.traces.exporter", otelSpringProperties.getTracesExporter());

    // instrumentations
    values.put(
        "otel.instrumentation.http.client.capture-request-headers",
        otelSpringProperties.getHttpClientCaptureRequestHeaders());
    values.put(
        "otel.instrumentation.http.client.capture-response-headers",
        otelSpringProperties.getHttpClientCaptureResponseHeaders());
    values.put(
        "otel.instrumentation.http.server.capture-request-headers",
        otelSpringProperties.getHttpServerCaptureRequestHeaders());
    values.put(
        "otel.instrumentation.http.server.capture-response-headers",
        otelSpringProperties.getHttpServerCaptureResponseHeaders());
    values.put(
        "otel.instrumentation.http.known-methods", otelSpringProperties.getHttpKnownMethods());

    return values;
  }

  private static Map<String, String> createMapForListProperty(
      String key, List<String> springList, ConfigProperties configProperties) {
    if (!springList.isEmpty()) {
      return Collections.singletonMap(key, String.join(",", springList));
    } else {
      String otelList = configProperties.getString(key);
      if (otelList != null) {
        return Collections.singletonMap(key, otelList);
      }
    }
    return Collections.emptyMap();
  }

  private static ConfigProperties createCustomizedListProperties(
      ConfigProperties configProperties, OtelSpringProperties otelSpringProperties) {
    // io.opentelemetry.instrumentation.resources.ResourceProviderPropertiesCustomizer
    // has already been applied before this point, so we have to apply the same logic here
    // the logic is implemented here:
    // https://github.com/open-telemetry/opentelemetry-java/blob/325822ce8527b83a09274c86a5123a214db80c1d/sdk-extensions/autoconfigure/src/main/java/io/opentelemetry/sdk/autoconfigure/AutoConfiguredOpenTelemetrySdkBuilder.java#L634-L641
    // ResourceProviderPropertiesCustomizer gets applied by "propertiesCustomizers"
    // and spring properties by "configPropertiesCustomizer", which is later
    Map<String, String> map =
        new HashMap<>(
            createMapForListProperty(
                ENABLED_KEY,
                otelSpringProperties.getJavaEnabledResourceProviders(),
                configProperties));
    map.putAll(
        createMapForListProperty(
            DISABLED_KEY,
            otelSpringProperties.getJavaDisabledResourceProviders(),
            configProperties));

    return DefaultConfigProperties.createFromMap(
        new ResourceProviderPropertiesCustomizer()
            .customize(DefaultConfigProperties.createFromMap(map)));
  }

  // visible for testing
  public static ConfigProperties create(
      Environment env,
      OtlpExporterProperties otlpExporterProperties,
      OtelResourceProperties resourceProperties,
      OtelSpringProperties otelSpringProperties,
      ConfigProperties fallback) {
    return new SpringConfigProperties(
        env,
        new SpelExpressionParser(),
        otlpExporterProperties,
        resourceProperties,
        otelSpringProperties,
        fallback);
  }

  @Nullable
  private static <T> T getCachedValue(
      ConcurrentHashMap<String, Optional<T>> cache,
      String name,
      Function<String, T> valueFunction) {
    return cache
        .computeIfAbsent(name, key -> Optional.ofNullable(valueFunction.apply(key)))
        .orElse(null);
  }

  @Nullable
  @Override
  public String getString(String name) {
    return getCachedValue(
        cachedStringValues,
        name,
        key -> {
          String normalizedName = ConfigUtil.normalizeEnvironmentVariableKey(key);
          String value = environment.getProperty(normalizedName, String.class);
          if (value == null && normalizedName.equals("otel.exporter.otlp.protocol")) {
            // SDK autoconfigure module defaults to `grpc`, but this module aligns with
            // recommendation in specification to default to `http/protobuf`
            return OtlpConfigUtil.PROTOCOL_HTTP_PROTOBUF;
          }
          return or(value, otelSdkProperties.getString(key));
        });
  }

  @Nullable
  @Override
  public Boolean getBoolean(String name) {
    return getCachedValue(
        cachedBooleanValues,
        name,
        key ->
            or(
                environment.getProperty(
                    ConfigUtil.normalizeEnvironmentVariableKey(key), Boolean.class),
                otelSdkProperties.getBoolean(key)));
  }

  @Nullable
  @Override
  public Integer getInt(String name) {
    return getCachedValue(
        cachedIntValues,
        name,
        key ->
            or(
                environment.getProperty(
                    ConfigUtil.normalizeEnvironmentVariableKey(key), Integer.class),
                otelSdkProperties.getInt(key)));
  }

  @Nullable
  @Override
  public Long getLong(String name) {
    return getCachedValue(
        cachedLongValues,
        name,
        key ->
            or(
                environment.getProperty(
                    ConfigUtil.normalizeEnvironmentVariableKey(key), Long.class),
                otelSdkProperties.getLong(key)));
  }

  @Nullable
  @Override
  public Double getDouble(String name) {
    return getCachedValue(
        cachedDoubleValues,
        name,
        key ->
            or(
                environment.getProperty(
                    ConfigUtil.normalizeEnvironmentVariableKey(key), Double.class),
                otelSdkProperties.getDouble(key)));
  }

  @SuppressWarnings("unchecked")
  @Override
  public List<String> getList(String name) {
    return getCachedValue(
        cachedListValues,
        name,
        key -> {
          String normalizedName = ConfigUtil.normalizeEnvironmentVariableKey(key);

          List<String> list = listPropertyValues.get(normalizedName);
          if (list != null) {
            List<String> c = customizedListProperties.getList(key);
            if (!c.isEmpty()) {
              return c;
            }
            if (!list.isEmpty()) {
              return list;
            }
          }

          List<String> envValue =
              (List<String>) environment.getProperty(normalizedName, List.class);
          return or(envValue, otelSdkProperties.getList(key));
        });
  }

  @Nullable
  @Override
  public Duration getDuration(String name) {
    return getCachedValue(
        cachedDurationValues,
        name,
        key -> {
          String value = getString(key);
          if (value == null) {
            return otelSdkProperties.getDuration(key);
          }
          return DefaultConfigProperties.createFromMap(Collections.singletonMap(key, value))
              .getDuration(key);
        });
  }

  @SuppressWarnings("unchecked")
  @Override
  public Map<String, String> getMap(String name) {
    return getCachedValue(
        cachedMapValues,
        name,
        key -> {
          Map<String, String> otelSdkMap = otelSdkProperties.getMap(key);

          String normalizedName = ConfigUtil.normalizeEnvironmentVariableKey(key);
          // maps from config properties are not supported by Environment, so we have to fake it
          Map<String, String> specialMap = getSpecialMapProperty(normalizedName, otelSdkMap);
          if (specialMap != null) {
            return specialMap;
          }

          String value = environment.getProperty(normalizedName);
          if (value == null) {
            return otelSdkMap;
          }
          return (Map<String, String>) parser.parseExpression(value).getValue();
        });
  }

  @Nullable
  private Map<String, String> getSpecialMapProperty(
      String normalizedName, Map<String, String> otelSdkMap) {
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
        return null;
    }
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
