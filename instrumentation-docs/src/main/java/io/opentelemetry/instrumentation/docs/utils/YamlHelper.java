/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.opentelemetry.instrumentation.docs.internal.ConfigurationOption;
import io.opentelemetry.instrumentation.docs.internal.ConfigurationType;
import io.opentelemetry.instrumentation.docs.internal.InstrumentationClassification;
import io.opentelemetry.instrumentation.docs.internal.InstrumentationMetaData;
import io.opentelemetry.instrumentation.docs.internal.InstrumentationModule;
import java.io.BufferedWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

public class YamlHelper {

  private static final Logger logger = Logger.getLogger(YamlHelper.class.getName());

  private static final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

  public static void generateInstrumentationYaml(
      List<InstrumentationModule> list, BufferedWriter writer) {
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

  private static Map<String, Object> getLibraryInstrumentations(List<InstrumentationModule> list) {
    Map<String, List<InstrumentationModule>> libraryInstrumentations =
        list.stream()
            .filter(
                module ->
                    module
                        .getMetadata()
                        .getClassification()
                        .equals(InstrumentationClassification.LIBRARY))
            .collect(
                Collectors.groupingBy(
                    InstrumentationModule::getGroup, TreeMap::new, Collectors.toList()));

    Map<String, Object> output = new TreeMap<>();
    libraryInstrumentations.forEach(
        (group, modules) -> {
          List<Map<String, Object>> instrumentations = new ArrayList<>();
          for (InstrumentationModule module : modules) {
            instrumentations.add(baseProperties(module));
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
      List<InstrumentationModule> list, InstrumentationClassification classification) {
    List<InstrumentationModule> filtered =
        list.stream()
            .filter(module -> module.getMetadata().getClassification().equals(classification))
            .toList();

    List<Map<String, Object>> instrumentations = new ArrayList<>();
    for (InstrumentationModule module : filtered) {
      instrumentations.add(baseProperties(module));
    }

    Map<String, Object> newOutput = new TreeMap<>();
    if (instrumentations.isEmpty()) {
      return newOutput;
    }
    newOutput.put(classification.toString(), instrumentations);
    return newOutput;
  }

  private static Map<String, Object> baseProperties(InstrumentationModule module) {
    Map<String, Object> moduleMap = new LinkedHashMap<>();
    moduleMap.put("name", module.getInstrumentationName());

    if (module.getMetadata() != null) {
      if (module.getMetadata().getDescription() != null) {
        moduleMap.put("description", module.getMetadata().getDescription());
      }

      if (module.getMetadata().getDisabledByDefault()) {
        moduleMap.put("disabled_by_default", module.getMetadata().getDisabledByDefault());
      }
    }

    moduleMap.put("source_path", module.getSrcPath());

    if (module.getMinJavaVersion() != null) {
      moduleMap.put("minimum_java_version", module.getMinJavaVersion());
    }

    Map<String, Object> scopeMap = getScopeMap(module);
    moduleMap.put("scope", scopeMap);

    Map<String, Object> targetVersions = new TreeMap<>();
    if (module.getTargetVersions() != null && !module.getTargetVersions().isEmpty()) {
      module
          .getTargetVersions()
          .forEach(
              (type, versions) -> {
                if (!versions.isEmpty()) {
                  targetVersions.put(type.toString(), new ArrayList<>(versions));
                }
              });
    }
    if (targetVersions.isEmpty()) {
      logger.info("No Target versions found for " + module.getInstrumentationName());
    } else {
      moduleMap.put("target_versions", targetVersions);
    }

    if (module.getMetadata() != null && !module.getMetadata().getConfigurations().isEmpty()) {
      List<Map<String, Object>> configurations = new ArrayList<>();
      for (ConfigurationOption configuration : module.getMetadata().getConfigurations()) {
        Map<String, Object> conf = new LinkedHashMap<>();
        conf.put("name", configuration.name());
        conf.put("description", configuration.description());
        conf.put("type", configuration.type().toString());
        if (configuration.type().equals(ConfigurationType.BOOLEAN)) {
          conf.put("default", Boolean.parseBoolean(configuration.defaultValue()));
        } else if (configuration.type().equals(ConfigurationType.INT)) {
          conf.put("default", Integer.parseInt(configuration.defaultValue()));
        } else {
          conf.put("default", configuration.defaultValue());
        }

        configurations.add(conf);
      }
      moduleMap.put("configurations", configurations);
    }

    return moduleMap;
  }

  private static Map<String, Object> getScopeMap(InstrumentationModule module) {
    Map<String, Object> scopeMap = new LinkedHashMap<>();
    scopeMap.put("name", module.getScopeInfo().getName());
    return scopeMap;
  }

  public static InstrumentationMetaData metaDataParser(String input)
      throws JsonProcessingException {
    return mapper.readValue(input, InstrumentationMetaData.class);
  }

  private YamlHelper() {}
}
