/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing.internal;

import io.opentelemetry.api.common.AttributeType;
import io.opentelemetry.api.internal.InternalAttributeKeyImpl;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.testing.internal.jackson.annotation.JsonProperty;
import io.opentelemetry.testing.internal.jackson.dataformat.yaml.YAMLFactory;
import io.opentelemetry.testing.internal.jackson.dataformat.yaml.YAMLGenerator;
import io.opentelemetry.testing.internal.jackson.dataformat.yaml.YAMLMapper;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Used in the {@link io.opentelemetry.instrumentation.testing.AgentTestRunner} to write telemetry
 * to metadata files within a .telemetry directory in each instrumentation module. This information
 * is then parsed and used to generate the instrumentation-list.yaml file.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class MetaDataCollector {

  private static final Logger logger = Logger.getLogger(MetaDataCollector.class.getName());

  private static final String TMP_DIR = ".telemetry";
  private static final Pattern MODULE_PATTERN =
      Pattern.compile("(.*?/instrumentation/.*?)(/javaagent|/library|/testing)");

  private static final YAMLMapper YAML =
      YAMLMapper.builder(
              YAMLFactory.builder()
                  .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
                  .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES)
                  .build())
          .build();

  public static void writeTelemetryToFiles(
      String path,
      Map<InstrumentationScopeInfo, Map<String, MetricData>> metricsByScope,
      Map<InstrumentationScopeInfo, Map<SpanKind, Map<InternalAttributeKeyImpl<?>, AttributeType>>>
          spansByScopeAndKind,
      java.util.Set<InstrumentationScopeInfo> instrumentationScopes)
      throws IOException {

    String moduleRoot = extractInstrumentationPath(path);
    writeMetricData(moduleRoot, metricsByScope);
    writeSpanData(moduleRoot, spansByScopeAndKind);
    writeScopeData(moduleRoot, instrumentationScopes);
  }

  private static String extractInstrumentationPath(String path) {
    Matcher matcher = MODULE_PATTERN.matcher(path);
    if (!matcher.find()) {
      throw new IllegalArgumentException("Invalid path: " + path);
    }

    String instrumentationPath = matcher.group(1);
    Path telemetryDir = Paths.get(instrumentationPath, TMP_DIR);

    try {
      Files.createDirectories(telemetryDir);
    } catch (FileAlreadyExistsException ignored) {
      // Directory already exists; nothing to do
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }

    return instrumentationPath;
  }

  private static void writeSpanData(
      String instrumentationPath,
      Map<InstrumentationScopeInfo, Map<SpanKind, Map<InternalAttributeKeyImpl<?>, AttributeType>>>
          spansByScopeAndKind)
      throws IOException {

    if (spansByScopeAndKind.isEmpty()) {
      return;
    }

    Path spansPath =
        Paths.get(instrumentationPath, TMP_DIR, "spans-" + UUID.randomUUID() + ".yaml");

    String config = System.getProperty("metadataConfig");
    String when = (config != null && !config.isEmpty()) ? config : "default";

    SpanData spanData = new SpanData();
    spanData.when = when;
    spanData.spansByScope = new ArrayList<>();

    for (Map.Entry<
            InstrumentationScopeInfo,
            Map<SpanKind, Map<InternalAttributeKeyImpl<?>, AttributeType>>>
        entry : spansByScopeAndKind.entrySet()) {
      InstrumentationScopeInfo scope = entry.getKey();
      Map<SpanKind, Map<InternalAttributeKeyImpl<?>, AttributeType>> spansByKind = entry.getValue();

      ScopeSpans scopeSpans = new ScopeSpans();
      scopeSpans.scope = scope.getName();
      scopeSpans.spans = new ArrayList<>();

      for (Map.Entry<SpanKind, Map<InternalAttributeKeyImpl<?>, AttributeType>> kindEntry :
          spansByKind.entrySet()) {
        SpanKind spanKind = kindEntry.getKey();
        Map<InternalAttributeKeyImpl<?>, AttributeType> attributes = kindEntry.getValue();

        Span span = new Span();
        span.spanKind = spanKind.toString();
        span.attributes = new ArrayList<>();

        attributes.forEach(
            (key, value) -> {
              AttributeInfo attr = new AttributeInfo();
              attr.name = key.getKey();
              attr.type = key.getType().toString();
              span.attributes.add(attr);
            });

        scopeSpans.spans.add(span);
      }

      spanData.spansByScope.add(scopeSpans);
    }

    YAML.writeValue(spansPath.toFile(), spanData);
  }

  private static void writeMetricData(
      String instrumentationPath,
      Map<InstrumentationScopeInfo, Map<String, MetricData>> metricsByScope) {

    if (metricsByScope.isEmpty()) {
      return;
    }

    Path metricsPath =
        Paths.get(instrumentationPath, TMP_DIR, "metrics-" + UUID.randomUUID() + ".yaml");

    try {
      String config = System.getProperty("metadataConfig");
      String when = (config != null && !config.isEmpty()) ? config : "default";

      MetricsData metricsData = new MetricsData();
      metricsData.when = when;
      metricsData.metricsByScope = new ArrayList<>();

      for (Map.Entry<InstrumentationScopeInfo, Map<String, MetricData>> entry :
          metricsByScope.entrySet()) {
        InstrumentationScopeInfo scope = entry.getKey();
        Map<String, MetricData> metrics = entry.getValue();

        ScopeMetrics scopeMetrics = new ScopeMetrics();
        scopeMetrics.scope = scope.getName();
        scopeMetrics.metrics = new ArrayList<>();

        for (MetricData metric : metrics.values()) {
          Metric metricInfo = new Metric();
          metricInfo.name = metric.getName();
          metricInfo.description = metric.getDescription();
          metricInfo.type = metric.getType().toString();
          metricInfo.unit = sanitizeUnit(metric.getUnit());
          metricInfo.attributes = new ArrayList<>();

          metric.getData().getPoints().stream()
              .findFirst()
              .ifPresent(
                  point ->
                      point
                          .getAttributes()
                          .forEach(
                              (key, value) -> {
                                AttributeInfo attr = new AttributeInfo();
                                attr.name = key.getKey();
                                attr.type = key.getType().toString();
                                metricInfo.attributes.add(attr);
                              }));

          scopeMetrics.metrics.add(metricInfo);
        }

        metricsData.metricsByScope.add(scopeMetrics);
      }

      YAML.writeValue(metricsPath.toFile(), metricsData);
    } catch (Exception e) {
      logger.warning("Failed to write metric data: " + e.getMessage());
    }
  }

  private static void writeScopeData(
      String instrumentationPath, Set<InstrumentationScopeInfo> instrumentationScopes)
      throws IOException {

    if (instrumentationScopes == null || instrumentationScopes.isEmpty()) {
      return;
    }

    Path outputPath =
        Paths.get(instrumentationPath, TMP_DIR, "scope-" + UUID.randomUUID() + ".yaml");

    ScopesData scopesData = new ScopesData();
    scopesData.scopes = new ArrayList<>();

    for (InstrumentationScopeInfo scope : instrumentationScopes) {
      ScopeInfo scopeInfo = new ScopeInfo();
      scopeInfo.name = scope.getName();
      scopeInfo.version = scope.getVersion();
      scopeInfo.schemaUrl = scope.getSchemaUrl();

      if (scope.getAttributes() != null && !scope.getAttributes().isEmpty()) {
        scopeInfo.attributes = new LinkedHashMap<>();
        scope
            .getAttributes()
            .forEach((key, value) -> scopeInfo.attributes.put(key.getKey(), value.toString()));
      }

      scopesData.scopes.add(scopeInfo);
    }

    YAML.writeValue(outputPath.toFile(), scopesData);
  }

  private static String sanitizeUnit(String unit) {
    return unit == null ? null : unit.replace("{", "").replace("}", "");
  }

  private MetaDataCollector() {}

  static class SpanData {
    public String when;

    @JsonProperty("spans_by_scope")
    public List<ScopeSpans> spansByScope;
  }

  static class ScopeSpans {
    public String scope;
    public List<Span> spans;
  }

  static class Span {
    @JsonProperty("span_kind")
    public String spanKind;

    public List<AttributeInfo> attributes;
  }

  static class MetricsData {
    public String when;

    @JsonProperty("metrics_by_scope")
    public List<ScopeMetrics> metricsByScope;
  }

  static class ScopeMetrics {
    public String scope;
    public List<Metric> metrics;
  }

  static class Metric {
    public String name;
    public String description;
    public String type;
    public String unit;
    public List<AttributeInfo> attributes;
  }

  static class ScopesData {
    public List<ScopeInfo> scopes;
  }

  static class ScopeInfo {
    public String name;
    public String version;
    public String schemaUrl;
    public Map<String, String> attributes;
  }

  static class AttributeInfo {
    public String name;
    public String type;
  }
}
