/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs.utils;

import io.opentelemetry.instrumentation.docs.internal.EmittedMetrics;
import io.opentelemetry.instrumentation.docs.internal.EmittedScope;
import io.opentelemetry.instrumentation.docs.internal.EmittedSpans;
import io.opentelemetry.instrumentation.docs.internal.InstrumentationEntity;
import io.opentelemetry.instrumentation.docs.internal.InstrumentationMetaData;
import java.io.BufferedWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

public class YamlHelper {

  public static EmittedScope emittedScopeParser(String input) {
    return new Yaml().loadAs(input, EmittedScope.class);
  }

  public static EmittedMetrics emittedMetricsParser(String input) {
    return new Yaml().loadAs(input, EmittedMetrics.class);
  }

  public static EmittedSpans emittedSpansParser(String input) {
    return new Yaml().loadAs(input, EmittedSpans.class);
  }

  public static void printInstrumentationList(
      List<InstrumentationEntity> list, BufferedWriter writer) {
    Map<String, List<InstrumentationEntity>> groupedByGroup =
        list.stream()
            .collect(
                Collectors.groupingBy(
                    InstrumentationEntity::getGroup, TreeMap::new, Collectors.toList()));

    Map<String, Object> output = new TreeMap<>();
    groupedByGroup.forEach(
        (group, entities) -> {
          Map<String, Object> groupMap = new LinkedHashMap<>();
          List<Map<String, Object>> instrumentations = new ArrayList<>();
          for (InstrumentationEntity entity : entities) {
            Map<String, Object> entityMap = new LinkedHashMap<>();
            entityMap.put("name", entity.getInstrumentationName());

            if (entity.getMetadata() != null && entity.getMetadata().getDescription() != null) {
              entityMap.put("description", entity.getMetadata().getDescription());
            }

            entityMap.put("srcPath", entity.getSrcPath());

            if (entity.getScope() != null) {
              Map<String, Object> scopeMap = new LinkedHashMap<>();
              scopeMap.put("name", entity.getScope().getName());
              scopeMap.put("version", entity.getScope().getVersion());
              scopeMap.put("schemaUrl", entity.getScope().getSchemaUrl());

              if (entity.getScope().getAttributes() != null
                  && !entity.getScope().getAttributes().isEmpty()) {

                Map<String, Object> attributesMap = new LinkedHashMap<>();
                entity
                    .getScope()
                    .getAttributes()
                    .forEach((key, value) -> attributesMap.put(String.valueOf(key), value));
                scopeMap.put("attributes", attributesMap);
              }

              entityMap.put("scope", scopeMap);
            }

            Map<String, Object> targetVersions = new TreeMap<>();
            if (entity.getTargetVersions() != null && !entity.getTargetVersions().isEmpty()) {
              entity
                  .getTargetVersions()
                  .forEach(
                      (type, versions) ->
                          targetVersions.put(type.toString(), new ArrayList<>(versions)));
            }
            entityMap.put("target_versions", targetVersions);

            if (entity.getMetrics() != null) {
              List<Map<String, Object>> metricsList = getMetricsList(entity);
              entityMap.put("metrics", metricsList);
            }

            // Add span_data section
            Map<String, Object> spanDataMap = new LinkedHashMap<>();
            if (entity.getSpanKinds() != null && !entity.getSpanKinds().isEmpty()) {
              spanDataMap.put("span_kinds", entity.getSpanKinds());
            }
            if (entity.getSpanAttributes() != null && !entity.getSpanAttributes().isEmpty()) {
              List<Map<String, Object>> attributesList = new ArrayList<>();
              for (EmittedSpans.EmittedSpanAttribute attribute : entity.getSpanAttributes()) {
                Map<String, Object> attributeMap = new LinkedHashMap<>();
                attributeMap.put("name", attribute.getName());
                attributeMap.put("type", attribute.getType());
                attributesList.add(attributeMap);
              }
              spanDataMap.put("attributes", attributesList);
            }
            if (!spanDataMap.isEmpty()) {
              entityMap.put("span_data", spanDataMap);
            }

            instrumentations.add(entityMap);
          }
          groupMap.put("instrumentations", instrumentations);
          output.put(group, groupMap);
        });

    System.out.println(output);
    DumperOptions options = new DumperOptions();
    options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);

    Yaml yaml = new Yaml(options);
    yaml.dump(output, writer);
  }

  private static List<Map<String, Object>> getMetricsList(InstrumentationEntity entity) {
    List<Map<String, Object>> metricsList = new ArrayList<>();
    for (EmittedMetrics.Metric metric : entity.getMetrics()) {
      Map<String, Object> metricMap = new LinkedHashMap<>();
      metricMap.put("name", metric.getName());
      metricMap.put("description", metric.getDescription());
      metricMap.put("type", metric.getType());
      metricMap.put("unit", metric.getUnit());

      List<Map<String, Object>> attributes = new ArrayList<>();
      for (EmittedMetrics.Attribute attribute : metric.getAttributes()) {
        Map<String, Object> attributeMap = new LinkedHashMap<>();
        attributeMap.put("name", attribute.getName());
        attributeMap.put("type", attribute.getType());
        attributes.add(attributeMap);
      }
      metricMap.put("attributes", attributes);
      metricsList.add(metricMap);
    }
    return metricsList;
  }

  public static InstrumentationMetaData metaDataParser(String input) {
    return new Yaml().loadAs(input, InstrumentationMetaData.class);
  }

  private YamlHelper() {}
}
