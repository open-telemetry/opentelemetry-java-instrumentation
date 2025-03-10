/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs.utils;

import io.opentelemetry.instrumentation.docs.InstrumentationEntity;
import java.io.BufferedWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.representer.Representer;

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
          Map<String, Object> groupMap = new TreeMap<>();
          List<Map<String, Object>> instrumentations = new ArrayList<>();
          for (InstrumentationEntity entity : entities) {
            Map<String, Object> entityMap = new TreeMap<>();
            entityMap.put("name", entity.getInstrumentationName());
            entityMap.put("srcPath", entity.getSrcPath());

            Map<String, Object> targetVersions = new TreeMap<>();
            if (entity.getTargetVersions() != null && !entity.getTargetVersions().isEmpty()) {
              entity
                  .getTargetVersions()
                  .forEach(
                      (type, versions) -> {
                        targetVersions.put(type.toString(), new ArrayList<>(versions));
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
    Representer representer = new Representer(options);
    representer.getPropertyUtils().setSkipMissingProperties(true);
    Yaml yaml = new Yaml(representer, options);
    yaml.dump(output, writer);
  }

  private YamlHelper() {}
}
