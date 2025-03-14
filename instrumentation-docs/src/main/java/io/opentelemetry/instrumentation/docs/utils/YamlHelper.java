/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs.utils;

import io.opentelemetry.instrumentation.docs.internal.EmittedScope;
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
              Map<String, Object> scopeMap = getScopeMap(entity);
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

            instrumentations.add(entityMap);
          }
          groupMap.put("instrumentations", instrumentations);
          output.put(group, groupMap);
        });

    DumperOptions options = new DumperOptions();
    options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);

    Yaml yaml = new Yaml(options);
    yaml.dump(output, writer);
  }

  private static Map<String, Object> getScopeMap(InstrumentationEntity entity) {
    Map<String, Object> scopeMap = new LinkedHashMap<>();
    scopeMap.put("name", entity.getScope().getName());
    scopeMap.put("version", entity.getScope().getVersion());
    scopeMap.put("schemaUrl", entity.getScope().getSchemaUrl());

    if (entity.getScope().getAttributes() != null && !entity.getScope().getAttributes().isEmpty()) {

      Map<String, Object> attributesMap = new LinkedHashMap<>();
      entity
          .getScope()
          .getAttributes()
          .forEach((key, value) -> attributesMap.put(String.valueOf(key), value));
      scopeMap.put("attributes", attributesMap);
    }
    return scopeMap;
  }

  public static InstrumentationMetaData metaDataParser(String input) {
    return new Yaml().loadAs(input, InstrumentationMetaData.class);
  }

  public static EmittedScope emittedScopeParser(String input) {
    return new Yaml().loadAs(input, EmittedScope.class);
  }

  private YamlHelper() {}
}
