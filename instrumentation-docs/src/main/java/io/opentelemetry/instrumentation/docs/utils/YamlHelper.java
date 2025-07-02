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
import io.opentelemetry.instrumentation.docs.internal.EmittedMetrics;
import io.opentelemetry.instrumentation.docs.internal.EmittedSpans;
import io.opentelemetry.instrumentation.docs.internal.InstrumentationClassification;
import io.opentelemetry.instrumentation.docs.internal.InstrumentationMetaData;
import io.opentelemetry.instrumentation.docs.internal.InstrumentationModule;
import io.opentelemetry.instrumentation.docs.internal.TelemetryAttribute;
import java.io.BufferedWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

/**
 * Used for reading and writing Yaml files. This class is responsible for the structure and contents
 * of the instrumentation-list.yaml file.
 */
public class YamlHelper {

  private static final Logger logger = Logger.getLogger(YamlHelper.class.getName());

  private static final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

  public static void generateInstrumentationYaml(
      List<InstrumentationModule> list, BufferedWriter writer) {
    DumperOptions options = new DumperOptions();
    options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);

    Yaml yaml = new Yaml(options);

    // Add library modules
    Map<String, Object> libraries = getLibraryInstrumentations(list);
    if (!libraries.isEmpty()) {
      yaml.dump(getLibraryInstrumentations(list), writer);
    }

    // Add internal modules
    Map<String, Object> internal = generateBaseYaml(list, InstrumentationClassification.INTERNAL);
    if (!internal.isEmpty()) {
      yaml.dump(internal, writer);
    }

    // add custom instrumentation modules
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
                    InstrumentationModule::getGroup,
                    TreeMap::new,
                    Collectors.collectingAndThen(
                        Collectors.toList(),
                        modules ->
                            modules.stream()
                                .sorted(
                                    Comparator.comparing(
                                        InstrumentationModule::getInstrumentationName))
                                .collect(Collectors.toList()))));

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

  /** Assembles map of properties that all modules could have */
  private static Map<String, Object> baseProperties(InstrumentationModule module) {
    Map<String, Object> moduleMap = new LinkedHashMap<>();
    moduleMap.put("name", module.getInstrumentationName());

    addMetadataProperties(module, moduleMap);
    moduleMap.put("source_path", module.getSrcPath());

    if (module.getMinJavaVersion() != null) {
      moduleMap.put("minimum_java_version", module.getMinJavaVersion());
    }

    moduleMap.put("scope", getScopeMap(module));
    addTargetVersions(module, moduleMap);
    addConfigurations(module, moduleMap);

    // Get telemetry grouping lists
    Set<String> telemetryGroups = new java.util.HashSet<>(module.getMetrics().keySet());
    telemetryGroups.addAll(module.getSpans().keySet());

    if (!telemetryGroups.isEmpty()) {
      List<Map<String, Object>> telemetryList = new ArrayList<>();
      for (String group : telemetryGroups) {
        Map<String, Object> telemetryEntry = new LinkedHashMap<>();
        telemetryEntry.put("when", group);
        List<EmittedMetrics.Metric> metrics =
            new ArrayList<>(module.getMetrics().getOrDefault(group, Collections.emptyList()));
        List<Map<String, Object>> metricsList = new ArrayList<>();

        // sort by name for determinism in the order
        metrics.sort(Comparator.comparing(EmittedMetrics.Metric::getName));

        for (EmittedMetrics.Metric metric : metrics) {
          metricsList.add(getMetricsMap(metric));
        }
        if (!metricsList.isEmpty()) {
          telemetryEntry.put("metrics", metricsList);
        }

        List<EmittedSpans.Span> spans =
            new ArrayList<>(module.getSpans().getOrDefault(group, Collections.emptyList()));
        List<Map<String, Object>> spanList = new ArrayList<>();

        // sort by name for determinism in the order
        spans.sort(Comparator.comparing(EmittedSpans.Span::getSpanKind));

        for (EmittedSpans.Span span : spans) {
          spanList.add(getSpanMap(span));
        }
        if (!spanList.isEmpty()) {
          telemetryEntry.put("spans", spanList);
        }

        if (!spanList.isEmpty() || !metricsList.isEmpty()) {
          telemetryList.add(telemetryEntry);
        }
      }

      if (!telemetryList.isEmpty()) {
        moduleMap.put("telemetry", telemetryList);
      }
    }
    return moduleMap;
  }

  private static void addMetadataProperties(
      InstrumentationModule module, Map<String, Object> moduleMap) {
    if (module.getMetadata() != null) {
      if (module.getMetadata().getDescription() != null) {
        moduleMap.put("description", module.getMetadata().getDescription());
      }
      if (module.getMetadata().getDisabledByDefault()) {
        moduleMap.put("disabled_by_default", module.getMetadata().getDisabledByDefault());
      }
    }
  }

  private static Map<String, Object> getScopeMap(InstrumentationModule module) {
    Map<String, Object> scopeMap = new LinkedHashMap<>();
    scopeMap.put("name", module.getScopeInfo().getName());
    return scopeMap;
  }

  private static void addTargetVersions(
      InstrumentationModule module, Map<String, Object> moduleMap) {
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
  }

  private static void addConfigurations(
      InstrumentationModule module, Map<String, Object> moduleMap) {
    if (module.getMetadata() != null && !module.getMetadata().getConfigurations().isEmpty()) {
      List<Map<String, Object>> configurations = new ArrayList<>();
      for (ConfigurationOption configuration : module.getMetadata().getConfigurations()) {
        configurations.add(configurationToMap(configuration));
      }
      moduleMap.put("configurations", configurations);
    }
  }

  private static Map<String, Object> configurationToMap(ConfigurationOption configuration) {
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
    return conf;
  }

  private static List<Map<String, Object>> getSortedAttributeMaps(
      List<TelemetryAttribute> attributes) {
    List<TelemetryAttribute> sortedAttributes = new ArrayList<>(attributes);
    sortedAttributes.sort(Comparator.comparing(TelemetryAttribute::getName));
    List<Map<String, Object>> attributeMaps = new ArrayList<>();
    for (TelemetryAttribute attribute : sortedAttributes) {
      Map<String, Object> attributeMap = new LinkedHashMap<>();
      attributeMap.put("name", attribute.getName());
      attributeMap.put("type", attribute.getType());
      attributeMaps.add(attributeMap);
    }
    return attributeMaps;
  }

  private static Map<String, Object> getMetricsMap(EmittedMetrics.Metric metric) {
    Map<String, Object> innerMetricMap = new LinkedHashMap<>();
    innerMetricMap.put("name", metric.getName());
    innerMetricMap.put("description", metric.getDescription());
    innerMetricMap.put("type", metric.getType());
    innerMetricMap.put("unit", metric.getUnit());
    innerMetricMap.put("attributes", getSortedAttributeMaps(metric.getAttributes()));
    return innerMetricMap;
  }

  private static Map<String, Object> getSpanMap(EmittedSpans.Span span) {
    Map<String, Object> innerMetricMap = new LinkedHashMap<>();
    innerMetricMap.put("span_kind", span.getSpanKind());
    innerMetricMap.put("attributes", getSortedAttributeMaps(span.getAttributes()));
    return innerMetricMap;
  }

  public static InstrumentationMetaData metaDataParser(String input)
      throws JsonProcessingException {
    return mapper.readValue(input, InstrumentationMetaData.class);
  }

  public static EmittedMetrics emittedMetricsParser(String input) throws JsonProcessingException {
    return mapper.readValue(input, EmittedMetrics.class);
  }

  public static EmittedSpans emittedSpansParser(String input) throws JsonProcessingException {
    return mapper.readValue(input, EmittedSpans.class);
  }

  private YamlHelper() {}
}
