/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs.utils;

import io.opentelemetry.instrumentation.docs.internal.InstrumentationClassification;
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
    customDescriptor.substituteProperty(
        "classification", String.class, "getClassification", "setClassification");
    metaDataYaml.addTypeDescription(customDescriptor);
  }

  public static void generateInstrumentationYaml(
      List<InstrumentationEntity> list, BufferedWriter writer) {
    DumperOptions options = new DumperOptions();
    options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);

    Yaml yaml = new Yaml(options);

    Map<String, Object> libraries = getLibraryInstrumentations(list);
    if (!libraries.isEmpty()) {
      yaml.dump(getLibraryInstrumentations(list), writer);
    }

    Map<String, Object> internal = generateBaseYaml(list, InstrumentationClassification.INTERNAL);
    if (!internal.isEmpty()) {
      yaml.dump(internal, writer);
    }

    Map<String, Object> custom = generateBaseYaml(list, InstrumentationClassification.CUSTOM);
    if (!custom.isEmpty()) {
      yaml.dump(custom, writer);
    }
  }

  private static Map<String, Object> getLibraryInstrumentations(List<InstrumentationEntity> list) {
    Map<String, List<InstrumentationEntity>> libraryInstrumentations =
        list.stream()
            .filter(
                entity ->
                    entity
                        .getMetadata()
                        .getClassification()
                        .equals(InstrumentationClassification.LIBRARY))
            .collect(
                Collectors.groupingBy(
                    InstrumentationEntity::getGroup, TreeMap::new, Collectors.toList()));

    Map<String, Object> output = new TreeMap<>();
    libraryInstrumentations.forEach(
        (group, entities) -> {
          List<Map<String, Object>> instrumentations = new ArrayList<>();
          for (InstrumentationEntity entity : entities) {
            Map<String, Object> entityMap = baseProperties(entity);

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
          output.put(group, instrumentations);
        });

    Map<String, Object> newOutput = new TreeMap<>();
    if (output.isEmpty()) {
      return newOutput;
    }
    newOutput.put("libraries", output);
    return newOutput;
  }

  private static Map<String, Object> generateBaseYaml(
      List<InstrumentationEntity> list, InstrumentationClassification classification) {
    List<InstrumentationEntity> filtered =
        list.stream()
            .filter(entity -> entity.getMetadata().getClassification().equals(classification))
            .toList();

    List<Map<String, Object>> instrumentations = new ArrayList<>();
    for (InstrumentationEntity entity : filtered) {
      instrumentations.add(baseProperties(entity));
    }

    Map<String, Object> newOutput = new TreeMap<>();
    if (instrumentations.isEmpty()) {
      return newOutput;
    }
    newOutput.put(classification.toString(), instrumentations);
    return newOutput;
  }

  private static Map<String, Object> baseProperties(InstrumentationEntity entity) {
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
    return entityMap;
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
