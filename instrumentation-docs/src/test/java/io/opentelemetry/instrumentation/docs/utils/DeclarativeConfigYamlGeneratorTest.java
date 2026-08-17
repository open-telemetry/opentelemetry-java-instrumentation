/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs.utils;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.docs.internal.ConfigurationOption;
import io.opentelemetry.instrumentation.docs.internal.ConfigurationType;
import io.opentelemetry.instrumentation.docs.internal.DeclarativeSchema;
import io.opentelemetry.instrumentation.docs.internal.InstrumentationMetadata;
import io.opentelemetry.instrumentation.docs.internal.InstrumentationModule;
import java.io.BufferedWriter;
import java.io.StringWriter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

class DeclarativeConfigYamlGeneratorTest {

  @Test
  void nestsConfigurationsUnderDeclarativeName() throws Exception {
    ConfigurationOption option =
        new ConfigurationOption(
            "otel.instrumentation.test.enabled",
            "java.test.enabled",
            "Enables the test instrumentation.",
            "true",
            ConfigurationType.BOOLEAN,
            null,
            null,
            null,
            null,
            null);

    String output = generate(List.of(module("test", option)));
    Map<String, Object> parsed = parse(output);

    assertThat(navigate(parsed, "instrumentation/development", "java", "test", "enabled"))
        .isEqualTo(true);
  }

  @Test
  void skipsConfigurationsWithoutDeclarativeName() throws Exception {
    ConfigurationOption option =
        new ConfigurationOption(
            "otel.instrumentation.test.flat-only",
            "No declarative form.",
            "true",
            ConfigurationType.BOOLEAN);

    String output = generate(List.of(module("test", option)));

    assertThat(output).isEmpty();
  }

  @Test
  void deduplicatesSharedDeclarativeNamesAcrossModules() throws Exception {
    ConfigurationOption option =
        new ConfigurationOption(
            "otel.instrumentation.common.enabled",
            "general.enabled",
            "Shared switch.",
            "true",
            ConfigurationType.BOOLEAN,
            null,
            null,
            null,
            null,
            null);

    // Two modules declaring the exact same declarative_name (as common configs typically do).
    String output = generate(List.of(module("mod-a", option), module("mod-b", option)));

    assertThat(output).containsOnlyOnce("enabled:");
  }

  @Test
  void quotesYamlSpecialScalarsInListDefaults() throws Exception {
    // "*" alone is a YAML alias indicator; "true"/"null" are boolean/null literals; a leading
    // "- " looks like a nested sequence item. Unquoted, any of these breaks parsing or changes
    // the value's type when the generated file is read back.
    ConfigurationOption option =
        new ConfigurationOption(
            "otel.instrumentation.test.patterns",
            "java.test.patterns",
            "Patterns to match.",
            "*,true,null,- dash,plain",
            ConfigurationType.LIST,
            null,
            null,
            null,
            null,
            null);

    String output = generate(List.of(module("test", option)));
    Map<String, Object> parsed = parse(output);

    @SuppressWarnings("unchecked")
    List<String> patterns =
        (List<String>) navigate(parsed, "instrumentation/development", "java", "test", "patterns");
    assertThat(patterns).containsExactly("*", "true", "null", "- dash", "plain");
  }

  @Test
  void documentsStructuredListEntryShape() throws Exception {
    // The flat form is a `host=service` map, but the declarative form is a list of objects, so the
    // emitted default must be a list and the entry shape has to be documented in the comment.
    Map<String, DeclarativeSchema.Property> properties = new LinkedHashMap<>();
    properties.put(
        "peer",
        new DeclarativeSchema.Property(
            "string", "Host name or IP address to match against.", null, "host"));
    properties.put(
        "service_name",
        new DeclarativeSchema.Property(
            "string", "Peer service name to record.", null, "serviceName"));
    ConfigurationOption option =
        new ConfigurationOption(
            "otel.instrumentation.common.peer-service-mapping",
            "java.common.service_peer_mapping",
            "Used to specify a mapping from host names or IP addresses to peer services.",
            "",
            ConfigurationType.MAP,
            null,
            ConfigurationType.STRUCTURED_LIST,
            new DeclarativeSchema("object", List.of("peer", "service_name"), properties),
            null,
            null);

    String output = generate(List.of(module("common", option)));
    Map<String, Object> parsed = parse(output);

    assertThat(
            navigate(
                parsed, "instrumentation/development", "java", "common", "service_peer_mapping"))
        .isEqualTo(List.of());
    assertThat(output)
        .contains("# Each list entry is an object with the following properties:")
        .contains("#   peer (string, required): Host name or IP address to match against.")
        .contains("#   service_name (string, required): Peer service name to record.")
        .contains("# Example:")
        .contains("#   service_peer_mapping:")
        .contains("#     - peer: host")
        .contains("#       service_name: serviceName");
  }

  @Test
  void fallsBackToDefaultAndPlaceholderForSchemaPropertiesWithoutExamples() throws Exception {
    Map<String, DeclarativeSchema.Property> properties = new LinkedHashMap<>();
    properties.put("pattern", new DeclarativeSchema.Property("string", null, null, null));
    properties.put("override", new DeclarativeSchema.Property("boolean", null, false, null));
    ConfigurationOption option =
        new ConfigurationOption(
            null,
            "java.common.http.client.url_template_rules",
            "Rules for deriving low-cardinality URL templates.",
            "",
            ConfigurationType.LIST,
            null,
            ConfigurationType.STRUCTURED_LIST,
            new DeclarativeSchema("object", List.of("pattern"), properties),
            null,
            null);

    String output = generate(List.of(module("common", option)));

    assertThat(output)
        .contains("#   pattern (string, required)")
        .contains("#   override (boolean)")
        .contains("#     - pattern: <pattern>")
        .contains("#       override: false");
  }

  private static InstrumentationModule module(String name, ConfigurationOption... options) {
    InstrumentationMetadata metadata =
        new InstrumentationMetadata.Builder().configurations(List.of(options)).build();
    return new InstrumentationModule.Builder().instrumentationName(name).metadata(metadata).build();
  }

  private static String generate(List<InstrumentationModule> modules) throws Exception {
    StringWriter stringWriter = new StringWriter();
    try (BufferedWriter writer = new BufferedWriter(stringWriter)) {
      DeclarativeConfigYamlGenerator.generateConfigurationYaml(modules, writer);
    }
    return stringWriter.toString();
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> parse(String yaml) {
    return new Yaml().load(yaml);
  }

  @SuppressWarnings("unchecked")
  private static Object navigate(Map<String, Object> root, String... path) {
    Object current = root;
    for (String segment : path) {
      current = ((Map<String, Object>) current).get(segment);
    }
    return current;
  }
}
