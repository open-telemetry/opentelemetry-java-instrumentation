/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs.utils;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.opentelemetry.instrumentation.docs.internal.ConfigurationOption;
import io.opentelemetry.instrumentation.docs.internal.ConfigurationType;
import io.opentelemetry.instrumentation.docs.internal.DeclarativeSchema;
import io.opentelemetry.instrumentation.docs.internal.EmittedMetrics;
import io.opentelemetry.instrumentation.docs.internal.EmittedScope;
import io.opentelemetry.instrumentation.docs.internal.EmittedSpans;
import io.opentelemetry.instrumentation.docs.internal.InstrumentationClassification;
import io.opentelemetry.instrumentation.docs.internal.InstrumentationMetadata;
import io.opentelemetry.instrumentation.docs.internal.InstrumentationModule;
import io.opentelemetry.instrumentation.docs.internal.SharedConfigurationRegistry;
import io.opentelemetry.instrumentation.docs.internal.TelemetryAttribute;
import java.io.BufferedWriter;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

/**
 * Used for reading and writing Yaml files. This class is responsible for the structure and contents
 * of the instrumentation-list.yaml file.
 */
public class YamlHelper {

  private static final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

  public static void generateInstrumentationYaml(
      List<InstrumentationModule> list, BufferedWriter writer) {
    DumperOptions options = new DumperOptions();
    options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);

    Yaml yaml = new Yaml(options);

    // Common metrics and configurations are collected once into a shared catalog; each module then
    // references them by id instead of inlining full copies (see DefinitionCatalog).
    DefinitionCatalog catalog = buildDefinitionCatalog(list);

    Map<String, Object> definitions = catalog.toDefinitionsMap();
    if (!definitions.isEmpty()) {
      Map<String, Object> definitionsRoot = new LinkedHashMap<>();
      definitionsRoot.put("definitions", definitions);
      yaml.dump(definitionsRoot, writer);
    }

    Map<String, Object> libraries = getLibraryInstrumentations(list, catalog);
    if (!libraries.isEmpty()) {
      yaml.dump(libraries, writer);
    }

    // add custom instrumentation modules
    Map<String, Object> custom = getCustomInstrumentations(list, catalog);
    if (!custom.isEmpty()) {
      yaml.dump(custom, writer);
    }
  }

  private static Map<String, Object> getLibraryInstrumentations(
      List<InstrumentationModule> list, DefinitionCatalog catalog) {
    List<InstrumentationModule> libraryInstrumentations =
        list.stream()
            .filter(
                module ->
                    module
                        .getMetadata()
                        .getClassification()
                        .equals(InstrumentationClassification.LIBRARY))
            .sorted(InstrumentationNameComparator.BY_NAME_AND_VERSION)
            .toList();

    if (libraryInstrumentations.isEmpty()) {
      return new TreeMap<>();
    }

    List<Map<String, Object>> instrumentations = new ArrayList<>();
    for (InstrumentationModule module : libraryInstrumentations) {
      instrumentations.add(baseProperties(module, catalog));
    }

    Map<String, Object> output = new TreeMap<>();
    output.put("libraries", instrumentations);
    return output;
  }

  private static Map<String, Object> getCustomInstrumentations(
      List<InstrumentationModule> list, DefinitionCatalog catalog) {
    List<InstrumentationModule> filtered =
        list.stream()
            .filter(
                module ->
                    module
                        .getMetadata()
                        .getClassification()
                        .equals(InstrumentationClassification.CUSTOM))
            .sorted(InstrumentationNameComparator.BY_NAME_AND_VERSION)
            .toList();

    List<Map<String, Object>> instrumentations = new ArrayList<>();
    for (InstrumentationModule module : filtered) {
      instrumentations.add(baseProperties(module, catalog));
    }

    Map<String, Object> newOutput = new TreeMap<>();
    if (instrumentations.isEmpty()) {
      return newOutput;
    }
    newOutput.put(InstrumentationClassification.CUSTOM.toString(), instrumentations);
    return newOutput;
  }

  /** Assembles map of properties that all modules could have */
  private static Map<String, Object> baseProperties(
      InstrumentationModule module, DefinitionCatalog catalog) {
    Map<String, Object> moduleMap = new LinkedHashMap<>();
    moduleMap.put("name", module.getInstrumentationName());

    addMetadataProperties(module, moduleMap);
    moduleMap.put("source_path", module.getSrcPath());

    if (module.getMinJavaVersion() != null) {
      moduleMap.put("minimum_java_version", module.getMinJavaVersion());
    }

    moduleMap.put("scope", getScopeMap(module));

    if (module.hasStandaloneLibrary()) {
      moduleMap.put("has_standalone_library", true);
    }

    if (module.hasJavaAgent()) {
      moduleMap.put("has_javaagent", true);
    }

    if (module.getAgentTargetVersions() != null && !module.getAgentTargetVersions().isEmpty()) {
      List<String> agentTargetVersions = new ArrayList<>(module.getAgentTargetVersions());
      Collections.sort(agentTargetVersions);
      moduleMap.put("javaagent_target_versions", agentTargetVersions);
    }

    addConfigurations(module, moduleMap, catalog);

    // Get telemetry grouping lists
    Set<String> telemetryGroups = new TreeSet<>(module.getMetrics().keySet());
    telemetryGroups.addAll(module.getSpans().keySet());

    if (!telemetryGroups.isEmpty()) {
      List<Map<String, Object>> telemetryList = new ArrayList<>();
      for (String group : telemetryGroups) {
        Map<String, Object> telemetryEntry = new LinkedHashMap<>();
        telemetryEntry.put("when", group);

        Set<String> metricRefs = new TreeSet<>();
        for (EmittedMetrics.Metric metric : module.getMetrics().getOrDefault(group, emptyList())) {
          metricRefs.add(catalog.metricId(metric));
        }
        if (!metricRefs.isEmpty()) {
          telemetryEntry.put("metric_refs", new ArrayList<>(metricRefs));
        }

        List<EmittedSpans.Span> spans =
            new ArrayList<>(module.getSpans().getOrDefault(group, emptyList()));
        List<Map<String, Object>> spanList = new ArrayList<>();

        // sort by name for determinism in the order
        spans.sort(Comparator.comparing(EmittedSpans.Span::getSpanKind));

        for (EmittedSpans.Span span : spans) {
          spanList.add(getSpanMap(span));
        }
        if (!spanList.isEmpty()) {
          telemetryEntry.put("spans", spanList);
        }

        if (!spanList.isEmpty() || !metricRefs.isEmpty()) {
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
      if (module.getMetadata().getDisplayName() != null) {
        moduleMap.put("display_name", module.getMetadata().getDisplayName());
      }
      if (module.getMetadata().getDescription() != null) {
        moduleMap.put("description", module.getMetadata().getDescription());
      }
      if (module.getMetadata().getSemanticConventions() != null
          && !module.getMetadata().getSemanticConventions().isEmpty()) {
        List<String> conventionNames =
            module.getMetadata().getSemanticConventions().stream()
                .map(Enum::name)
                .collect(toList());
        moduleMap.put("semantic_conventions", conventionNames);
      }
      if (module.getMetadata().getLibraryLink() != null) {
        moduleMap.put("library_link", module.getMetadata().getLibraryLink());
      }
      if (module.getMetadata().getDisabledByDefault()) {
        moduleMap.put("disabled_by_default", module.getMetadata().getDisabledByDefault());
      }
      if (!module.getMetadata().getFeatures().isEmpty()) {
        List<String> functionNames =
            module.getMetadata().getFeatures().stream().map(Enum::name).collect(toList());
        moduleMap.put("features", functionNames);
      }
    }
  }

  private static Map<String, Object> getScopeMap(InstrumentationModule module) {
    Map<String, Object> scopeMap = new LinkedHashMap<>();
    scopeMap.put("name", module.getScopeInfo().getName());
    if (module.getScopeInfo().getSchemaUrl() != null) {
      scopeMap.put("schema_url", module.getScopeInfo().getSchemaUrl());
    }
    if (module.getScopeInfo().getAttributes() != null
        && !module.getScopeInfo().getAttributes().isEmpty()) {
      Map<String, Object> attributesMap = new LinkedHashMap<>();
      module
          .getScopeInfo()
          .getAttributes()
          .forEach((key, value) -> attributesMap.put(key.getKey(), value));
      scopeMap.put("attributes", attributesMap);
    }
    return scopeMap;
  }

  private static void addConfigurations(
      InstrumentationModule module, Map<String, Object> moduleMap, DefinitionCatalog catalog) {
    if (module.getMetadata() != null && !module.getMetadata().getConfigurations().isEmpty()) {
      Set<String> configRefs = new TreeSet<>();
      for (ConfigurationOption configuration : module.getMetadata().getConfigurations()) {
        configRefs.add(catalog.configId(configuration));
      }
      moduleMap.put("configuration_refs", new ArrayList<>(configRefs));
    }
  }

  private static Map<String, Object> configurationToMap(ConfigurationOption configuration) {
    Map<String, Object> conf = new LinkedHashMap<>();
    // Declarative-only configs (e.g. url_template_rules) have no flat system property name.
    if (configuration.name() != null) {
      conf.put("name", configuration.name());
    }
    if (configuration.declarativeName() != null) {
      conf.put("declarative_name", configuration.declarativeName());
    }
    conf.put("description", configuration.description());
    conf.put("type", configuration.type().toString());
    if (configuration.type().equals(ConfigurationType.BOOLEAN)) {
      conf.put("default", Boolean.parseBoolean(configuration.defaultValue()));
    } else if (configuration.type().equals(ConfigurationType.INT)) {
      conf.put("default", Integer.parseInt(configuration.defaultValue()));
    } else {
      conf.put("default", configuration.defaultValue());
    }
    if (configuration.examples() != null && !configuration.examples().isEmpty()) {
      conf.put("examples", configuration.examples());
    }
    if (configuration.declarativeType() != null) {
      conf.put("declarative_type", configuration.declarativeType().toString());
    }
    if (configuration.declarativeSchema() != null) {
      conf.put("declarative_schema", declarativeSchemaToMap(configuration.declarativeSchema()));
    }
    return conf;
  }

  private static Map<String, Object> declarativeSchemaToMap(DeclarativeSchema schema) {
    Map<String, Object> schemaMap = new LinkedHashMap<>();
    schemaMap.put("type", schema.type());
    if (schema.required() != null && !schema.required().isEmpty()) {
      schemaMap.put("required", schema.required());
    }
    Map<String, Object> properties = new LinkedHashMap<>();
    schema
        .properties()
        .forEach(
            (key, property) -> {
              Map<String, Object> propertyMap = new LinkedHashMap<>();
              propertyMap.put("type", property.type());
              if (property.description() != null) {
                propertyMap.put("description", property.description());
              }
              if (property.defaultValue() != null) {
                propertyMap.put("default", property.defaultValue());
              }
              properties.put(key, propertyMap);
            });
    schemaMap.put("properties", properties);
    return schemaMap;
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
    innerMetricMap.put("instrument", metric.getInstrumentType());
    innerMetricMap.put("data_type", metric.getType());
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

  public static InstrumentationMetadata metaDataParser(String input)
      throws JsonProcessingException {
    InstrumentationMetadata metadata = mapper.readValue(input, InstrumentationMetadata.class);
    // Expand any `- ref: <id>` configuration entries into their shared definitions so all consumers
    // (the doc generator and the declarative-config validation test) see fully-resolved options.
    metadata.setConfigurations(
        SharedConfigurationRegistry.getInstance().resolve(metadata.getConfigurations()));
    return metadata;
  }

  public static EmittedScope emittedScopeParser(String input) {
    return new Yaml().loadAs(input, EmittedScope.class);
  }

  public static EmittedMetrics emittedMetricsParser(String input) throws JsonProcessingException {
    return mapper.readValue(input, EmittedMetrics.class);
  }

  public static EmittedSpans emittedSpansParser(String input) throws JsonProcessingException {
    return mapper.readValue(input, EmittedSpans.class);
  }

  /**
   * Builds the shared definitions catalog from every module that will be emitted. Each unique
   * metric/configuration block is assigned a deterministic id and stored once; modules then
   * reference these ids.
   */
  private static DefinitionCatalog buildDefinitionCatalog(List<InstrumentationModule> list) {
    DefinitionCatalog catalog = new DefinitionCatalog();

    List<InstrumentationModule> emitted =
        list.stream()
            .filter(
                module -> {
                  InstrumentationClassification classification =
                      module.getMetadata().getClassification();
                  return classification.equals(InstrumentationClassification.LIBRARY)
                      || classification.equals(InstrumentationClassification.CUSTOM);
                })
            .toList();

    for (InstrumentationModule module : emitted) {
      for (List<EmittedMetrics.Metric> metrics : module.getMetrics().values()) {
        for (EmittedMetrics.Metric metric : metrics) {
          Map<String, Object> def = getMetricsMap(metric);
          String canonical = canonicalize(def);
          if (!catalog.metricCanonicalToId.containsKey(canonical)) {
            String id = metric.getName() + "-" + shortHash(canonical);
            catalog.metricCanonicalToId.put(canonical, id);
            catalog.metricDefs.put(id, def);
          }
        }
      }
    }

    // Configurations: registry-backed configs (resolved from a `ref`) use their curated registry
    // id. Module-specific configs are keyed by their config name, unless two distinct contents
    // share a name, in which case a short content hash disambiguates them.
    Map<String, Set<String>> nonRegistryNameToCanonicals = new HashMap<>();
    Map<String, Map<String, Object>> canonicalToDef = new LinkedHashMap<>();
    for (InstrumentationModule module : emitted) {
      if (module.getMetadata() == null) {
        continue;
      }
      for (ConfigurationOption configuration : module.getMetadata().getConfigurations()) {
        Map<String, Object> def = configurationToMap(configuration);
        String canonical = canonicalize(def);
        canonicalToDef.put(canonical, def);
        if (configuration.id() != null) {
          catalog.configDefs.put(configuration.id(), def);
          catalog.configCanonicalToId.put(canonical, configuration.id());
        } else {
          nonRegistryNameToCanonicals
              .computeIfAbsent(configNameKey(configuration), k -> new TreeSet<>())
              .add(canonical);
        }
      }
    }
    nonRegistryNameToCanonicals.forEach(
        (name, canonicals) -> {
          boolean unique = canonicals.size() == 1;
          for (String canonical : canonicals) {
            // Skip content already cataloged via a registry ref
            if (catalog.configCanonicalToId.containsKey(canonical)) {
              continue;
            }
            String id = unique ? name : name + "-" + shortHash(canonical);
            catalog.configDefs.put(id, canonicalToDef.get(canonical));
            catalog.configCanonicalToId.put(canonical, id);
          }
        });

    return catalog;
  }

  private static String configNameKey(ConfigurationOption configuration) {
    String key =
        configuration.name() != null ? configuration.name() : configuration.declarativeName();
    return requireNonNull(
        key, "configuration must have a name or declarative_name to derive a definition id");
  }

  /**
   * Produces a stable, order-independent string representation of a definition map (map keys sorted
   * recursively) used both as the de-duplication key and as the input to the id hash.
   */
  private static String canonicalize(Object value) {
    StringBuilder sb = new StringBuilder();
    canonicalize(value, sb);
    return sb.toString();
  }

  private static void canonicalize(Object value, StringBuilder sb) {
    if (value instanceof Map<?, ?> map) {
      TreeMap<String, Object> sorted = new TreeMap<>();
      map.forEach((k, v) -> sorted.put(String.valueOf(k), v));
      sb.append('{');
      sorted.forEach(
          (k, v) -> {
            sb.append(k).append('=');
            canonicalize(v, sb);
            sb.append(';');
          });
      sb.append('}');
    } else if (value instanceof List<?> list) {
      sb.append('[');
      for (Object item : list) {
        canonicalize(item, sb);
        sb.append(',');
      }
      sb.append(']');
    } else {
      sb.append(value);
    }
  }

  /** First 8 hex chars of the SHA-256 of the input. */
  private static String shortHash(String input) {
    try {
      byte[] hash = MessageDigest.getInstance("SHA-256").digest(input.getBytes(UTF_8));
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < 4; i++) {
        sb.append(String.format(Locale.ROOT, "%02x", hash[i]));
      }
      return sb.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new AssertionError("SHA-256 not available", e);
    }
  }

  /**
   * Holds the shared metric and configuration definitions and the lookups needed to resolve a
   * module's metric/config back to its catalog id. This class is internal and is hence not for
   * public use.
   */
  private static final class DefinitionCatalog {
    final Map<String, Map<String, Object>> metricDefs = new TreeMap<>();
    final Map<String, Map<String, Object>> configDefs = new TreeMap<>();
    final Map<String, String> metricCanonicalToId = new HashMap<>();
    final Map<String, String> configCanonicalToId = new HashMap<>();

    String metricId(EmittedMetrics.Metric metric) {
      return requireNonNull(
          metricCanonicalToId.get(canonicalize(getMetricsMap(metric))),
          "metric not present in definitions catalog");
    }

    String configId(ConfigurationOption configuration) {
      String id = configuration.id();
      if (id != null) {
        return id;
      }
      return requireNonNull(
          configCanonicalToId.get(canonicalize(configurationToMap(configuration))),
          "configuration not present in definitions catalog");
    }

    Map<String, Object> toDefinitionsMap() {
      Map<String, Object> definitions = new LinkedHashMap<>();
      if (!configDefs.isEmpty()) {
        definitions.put("configurations", configDefs);
      }
      if (!metricDefs.isEmpty()) {
        definitions.put("metrics", metricDefs);
      }
      return definitions;
    }
  }

  private YamlHelper() {}
}
