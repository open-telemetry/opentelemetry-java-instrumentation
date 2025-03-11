/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs.utils;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.opentelemetry.instrumentation.docs.EmittedTelemetry;
import io.opentelemetry.instrumentation.docs.InstrumentationEntity;
import io.opentelemetry.instrumentation.docs.InstrumentationMetaData;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import java.io.BufferedWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.representer.Representer;

public class YamlHelper {

  private static final ObjectMapper mapper =
      new ObjectMapper(new YAMLFactory())
          .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  static {
    SimpleModule module = new SimpleModule();
    module.addDeserializer(
        InstrumentationScopeInfo.class, new InstrumentationScopeInfoDeserializer());
    mapper.registerModule(module);
  }

  public static EmittedTelemetry emittedTelemetryParser(String input) throws IOException {
    return mapper.readValue(input, EmittedTelemetry.class);
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

              Map<String, Object> attributesMap = new LinkedHashMap<>();
              entity
                  .getScope()
                  .getAttributes()
                  .forEach((key, value) -> attributesMap.put(String.valueOf(key), value));
              scopeMap.put("attributes", attributesMap);
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
    Representer representer = new Representer(options);
    representer.getPropertyUtils().setSkipMissingProperties(true);
    Yaml yaml = new Yaml(representer, options);
    yaml.dump(output, writer);
  }

  public static InstrumentationMetaData metaDataParser(String input) {
    return new Yaml().loadAs(input, InstrumentationMetaData.class);
  }

  //  public static EmittedTelemetry emittedTelemetryParser(String input) {
  //    return new Yaml().loadAs(input, EmittedTelemetry.class);
  //  }

  private YamlHelper() {}
}
