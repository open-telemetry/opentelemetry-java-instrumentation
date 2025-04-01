/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs.utils;

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
import org.yaml.snakeyaml.TypeDescription;
import org.yaml.snakeyaml.Yaml;

public class YamlHelper {

  private static final Yaml metaDataYaml = new Yaml();

  static {
    TypeDescription customDescriptor = new TypeDescription(InstrumentationMetaData.class);
    customDescriptor.substituteProperty(
        "disabled_by_default", Boolean.class, "getDisabledByDefault", "setDisabledByDefault");
    metaDataYaml.addTypeDescription(customDescriptor);
  }

  public static void printInstrumentationList(
      List<InstrumentationEntity> list, BufferedWriter writer) {
    Map<String, List<InstrumentationEntity>> groupedByGroup =
        list.stream()
            .filter(entity -> isLibraryInstrumentation(entity.getMetadata()))
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

            if (entity.getMetadata() != null) {
              if (entity.getMetadata().getDescription() != null) {
                entityMap.put("description", entity.getMetadata().getDescription());
              }

              if (entity.getMetadata().getDisabledByDefault()) {
                entityMap.put("disabled_by_default", entity.getMetadata().getDisabledByDefault());
              }
            }

            entityMap.put("source_path", entity.getSrcPath());

            if (entity.getMinJavaVersion() != null) {
              entityMap.put("minimum_java_version", entity.getMinJavaVersion());
            }

            Map<String, Object> scopeMap = getScopeMap(entity);
            entityMap.put("scope", scopeMap);

            Map<String, Object> targetVersions = new TreeMap<>();
            if (entity.getTargetVersions() != null && !entity.getTargetVersions().isEmpty()) {
              entity
                  .getTargetVersions()
                  .forEach(
                      (type, versions) -> {
                        if (!versions.isEmpty()) {
                          targetVersions.put(type.toString(), new ArrayList<>(versions));
                        }
                      });
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

  // We assume true unless explicitly overridden
  private static Boolean isLibraryInstrumentation(InstrumentationMetaData metadata) {
    if (metadata == null) {
      return true;
    }
    return metadata.getIsLibraryInstrumentation();
  }

  private static Map<String, Object> getScopeMap(InstrumentationEntity entity) {
    Map<String, Object> scopeMap = new LinkedHashMap<>();
    scopeMap.put("name", entity.getScopeInfo().getName());
    return scopeMap;
  }

  public static InstrumentationMetaData metaDataParser(String input) {
    return metaDataYaml.loadAs(input, InstrumentationMetaData.class);
  }

  private YamlHelper() {}
}
