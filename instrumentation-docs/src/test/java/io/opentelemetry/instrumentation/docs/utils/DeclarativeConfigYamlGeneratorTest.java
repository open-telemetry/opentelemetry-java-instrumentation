/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

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
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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

    assertThat(output)
        .containsOnlyOnce("enabled:")
        .containsOnlyOnce("# Shared switch.")
        .doesNotContain("mod-a", "mod-b");
  }

  @Test
  void preservesDistinctDescriptionsWithModuleLabelsRegardlessOfModuleOrder() throws Exception {
    ConfigurationOption grpc =
        sharedOption("Captures gRPC attributes.", "false", ConfigurationType.BOOLEAN, null);
    ConfigurationOption remoting =
        sharedOption("Captures Remoting attributes.", "false", ConfigurationType.BOOLEAN, null);
    InstrumentationModule grpcModule = module("rocketmq-5.0", grpc);
    grpcModule.getMetadata().setDisplayName("RocketMQ - gRPC Protocol");
    InstrumentationModule remotingModule = module("rocketmq-4.8", remoting);
    remotingModule.getMetadata().setDisplayName("RocketMQ - Remoting Protocol");
    // Modules without a display name fall back to their instrumentation name. Repeated
    // descriptions retain all their sources without repeating the description itself.
    InstrumentationModule otherModule = module("other-remoting", remoting);

    String output = generate(List.of(grpcModule, remotingModule, otherModule));

    assertThat(output)
        .containsOnlyOnce("experimental_span_attributes/development:")
        .containsOnlyOnce("# Captures gRPC attributes.")
        .containsOnlyOnce("# Captures Remoting attributes.")
        .contains("# RocketMQ - gRPC Protocol:\n      # Captures gRPC attributes.")
        .contains(
            "# RocketMQ - Remoting Protocol, other-remoting:\n      # Captures Remoting attributes.")
        .isEqualTo(generate(List.of(otherModule, remotingModule, grpcModule)));
    assertThat(navigate(parse(output), "instrumentation/development", "java", "rocketmq_client"))
        .isEqualTo(Map.of("experimental_span_attributes/development", false));
  }

  @Test
  void disambiguatesIdenticalDisplayNamesWithDifferentDescriptions() throws Exception {
    InstrumentationModule oldModule =
        module(
            "client-1.0",
            sharedOption("Old client attributes.", "false", ConfigurationType.BOOLEAN, null));
    InstrumentationModule newModule =
        module(
            "client-2.0",
            sharedOption("New client attributes.", "false", ConfigurationType.BOOLEAN, null));
    oldModule.getMetadata().setDisplayName("Client");
    newModule.getMetadata().setDisplayName("Client");

    String output = generate(List.of(oldModule, newModule));

    assertThat(output)
        .contains("# Client (client-1.0):\n      # Old client attributes.")
        .contains("# Client (client-2.0):\n      # New client attributes.")
        .doesNotContain("# \n")
        .isEqualTo(generate(List.of(newModule, oldModule)));
  }

  @ParameterizedTest
  @MethodSource("conflictingOptions")
  void rejectsConflictingModuleDefinitions(
      ConfigurationOption original, ConfigurationOption conflicting) {
    assertThatIllegalArgumentException()
        .isThrownBy(
            () -> generate(List.of(module("mod-a", original), module("mod-b", conflicting))))
        .withMessageContaining("java.rocketmq_client.experimental_span_attributes/development")
        .withMessageContaining("mod-a")
        .withMessageContaining("mod-b");
  }

  private static Stream<Arguments> conflictingOptions() {
    ConfigurationOption original =
        sharedOption("Original.", "false", ConfigurationType.BOOLEAN, null);
    DeclarativeSchema schema =
        new DeclarativeSchema(
            "object",
            List.of(),
            Map.of("target", new DeclarativeSchema.Property("string", "Target.", null, null)));
    DeclarativeSchema otherSchema =
        new DeclarativeSchema("object", List.of("target"), schema.properties());
    return Stream.of(
        argumentSet(
            "different defaults",
            original,
            sharedOption("Different default.", "true", ConfigurationType.BOOLEAN, null)),
        argumentSet(
            "different types",
            original,
            sharedOption("Different type.", "false", ConfigurationType.STRING, null)),
        argumentSet(
            "different schemas",
            sharedOption("Original schema.", "", ConfigurationType.STRUCTURED_LIST, schema),
            sharedOption("Different schema.", "", ConfigurationType.STRUCTURED_LIST, otherSchema)));
  }

  private static ConfigurationOption sharedOption(
      String description, String defaultValue, ConfigurationType type, DeclarativeSchema schema) {
    return new ConfigurationOption(
        "otel.instrumentation.rocketmq-client.experimental-span-attributes",
        "java.rocketmq_client.experimental_span_attributes/development",
        description,
        defaultValue,
        type,
        null,
        null,
        schema,
        null,
        null);
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
    // The flat form is a `target=service_name` map, but the declarative form is a list of objects,
    // so the emitted default must be a list and the entry shape has to be documented in the
    // comment.
    Map<String, DeclarativeSchema.Property> properties = new LinkedHashMap<>();
    properties.put(
        "target",
        new DeclarativeSchema.Property(
            "string",
            "Server address or complete configured target to match against.",
            null,
            "payments.example.com"));
    properties.put(
        "service_name",
        new DeclarativeSchema.Property(
            "string", "Logical server service name to record.", null, "payments"));
    properties.put(
        "service_namespace",
        new DeclarativeSchema.Property(
            "string", "Logical server service namespace to record.", null, "checkout"));
    ConfigurationOption option =
        new ConfigurationOption(
            "otel.instrumentation.common.server-service-mapping",
            "java.common.server_service_mapping",
            "Maps server addresses or configured targets to logical server service identities.",
            "",
            ConfigurationType.MAP,
            null,
            ConfigurationType.STRUCTURED_LIST,
            new DeclarativeSchema("object", List.of("target"), properties),
            null,
            null);

    String output = generate(List.of(module("common", option)));
    Map<String, Object> parsed = parse(output);

    assertThat(
            navigate(
                parsed, "instrumentation/development", "java", "common", "server_service_mapping"))
        .isEqualTo(List.of());
    assertThat(output)
        .contains("# Each list entry is an object with the following properties:")
        .contains(
            "#   target (string, required): Server address or complete configured target to match")
        .contains("#     against.")
        .contains("#   service_name (string): Logical server service name to record.")
        .contains("#   service_namespace (string): Logical server service namespace to record.")
        .contains("# Example:")
        .contains("#   server_service_mapping:")
        .contains("#     - target: payments.example.com")
        .contains("#       service_name: payments")
        .contains("#       service_namespace: checkout");
  }

  @Test
  void documentsNestedStructuredListExamples() throws Exception {
    Map<String, DeclarativeSchema.Property> properties = new LinkedHashMap<>();
    properties.put(
        "class", new DeclarativeSchema.Property("string", null, null, "com.example.MyClass"));
    properties.put(
        "methods",
        new DeclarativeSchema.Property("array", null, null, List.of(Map.of("name", "method1"))));
    ConfigurationOption option =
        new ConfigurationOption(
            "otel.instrumentation.methods.include",
            "java.methods.include",
            "Methods to instrument.",
            "",
            ConfigurationType.LIST,
            null,
            ConfigurationType.STRUCTURED_LIST,
            new DeclarativeSchema("object", List.of("class", "methods"), properties),
            null,
            null);

    String output = generate(List.of(module("methods", option)));

    assertThat(navigate(parse(output), "instrumentation/development", "java", "methods", "include"))
        .isEqualTo(List.of());
    assertThat(output)
        .contains("#     - class: com.example.MyClass")
        .contains("#       methods: [{name: method1}]");
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

  @Test
  void includesGlobalConfigurationsWithoutAnyModule() throws Exception {
    ConfigurationOption global =
        new ConfigurationOption(
            "otel.instrumentation.common.span-suppression-strategy",
            "java.common.span_suppression_strategy",
            "Strategy for suppressing nested instrumentation spans.",
            "semconv",
            ConfigurationType.STRING,
            null,
            null,
            null,
            null,
            null);

    String output = generate(List.of(), List.of(global));
    Map<String, Object> parsed = parse(output);

    assertThat(
            navigate(
                parsed,
                "instrumentation/development",
                "java",
                "common",
                "span_suppression_strategy"))
        .isEqualTo("semconv");
    assertThat(output).contains("# Strategy for suppressing nested instrumentation spans.");
  }

  @Test
  void globalConfigurationTakesPrecedenceOverModuleDeclaringSameName() throws Exception {
    ConfigurationOption global =
        new ConfigurationOption(
            null,
            "general.db.semconv.version",
            "Global description.",
            "0",
            ConfigurationType.INT,
            null,
            null,
            null,
            null,
            null);
    ConfigurationOption moduleOption =
        new ConfigurationOption(
            null,
            "general.db.semconv.version",
            "Module description.",
            "1",
            ConfigurationType.INT,
            null,
            null,
            null,
            null,
            null);

    String output = generate(List.of(module("test", moduleOption)), List.of(global));

    assertThat(output).contains("# Global description.").doesNotContain("Module description.");
    assertThat(navigate(parse(output), "instrumentation/development", "general", "db", "semconv"))
        .isEqualTo(Map.of("version", 0));
  }

  @Test
  void includesRegistryGlobalConfigurationsByDefault() throws Exception {
    StringWriter stringWriter = new StringWriter();
    try (BufferedWriter writer = new BufferedWriter(stringWriter)) {
      DeclarativeConfigYamlGenerator.generateConfigurationYaml(List.of(), writer);
    }

    assertThat(
            navigate(
                parse(stringWriter.toString()),
                "instrumentation/development",
                "java",
                "common",
                "span_suppression_strategy"))
        .isEqualTo("semconv");
  }

  @Test
  void omitsDeprecatedConfigurations() throws Exception {
    ConfigurationOption current =
        new ConfigurationOption(
            "otel.instrumentation.test.new-name",
            "java.test.new_name",
            "Current setting.",
            "true",
            ConfigurationType.BOOLEAN,
            null,
            null,
            null,
            null,
            null);
    ConfigurationOption deprecated =
        new ConfigurationOption(
            "otel.instrumentation.test.old-name",
            "java.test.old_name",
            "Deprecated: use `otel.instrumentation.test.new-name` instead.",
            "true",
            ConfigurationType.BOOLEAN,
            null,
            null,
            null,
            null,
            true,
            "otel.instrumentation.test.new-name",
            null);

    String output = generate(List.of(module("test", current, deprecated)), List.of(deprecated));

    assertThat(navigate(parse(output), "instrumentation/development", "java", "test"))
        .isEqualTo(Map.of("new_name", true));
  }

  @Test
  void omitsConfigurationsWithoutDefault() throws Exception {
    // leaving an option without a default unset falls back to another setting, so writing any
    // value into the example would override that fallback
    ConfigurationOption override =
        new ConfigurationOption(
            "otel.instrumentation.test.query-sanitization.enabled",
            "java.test.query_sanitization.enabled",
            "Overrides the common setting for this instrumentation.",
            null,
            ConfigurationType.BOOLEAN,
            null,
            null,
            null,
            null,
            null);
    ConfigurationOption global =
        new ConfigurationOption(
            null,
            "general.db.semconv.version",
            "When unset, the opt-in list selects the conventions.",
            null,
            ConfigurationType.INT,
            null,
            null,
            null,
            null,
            null);

    String output = generate(List.of(module("test", override)), List.of(global));

    assertThat(output).isEmpty();
  }

  private static InstrumentationModule module(String name, ConfigurationOption... options) {
    InstrumentationMetadata metadata =
        new InstrumentationMetadata.Builder().configurations(List.of(options)).build();
    return new InstrumentationModule.Builder().instrumentationName(name).metadata(metadata).build();
  }

  private static String generate(List<InstrumentationModule> modules) throws Exception {
    // no global configurations, so the output reflects only the given modules
    return generate(modules, List.of());
  }

  private static String generate(
      List<InstrumentationModule> modules, List<ConfigurationOption> globalConfigurations)
      throws Exception {
    StringWriter stringWriter = new StringWriter();
    try (BufferedWriter writer = new BufferedWriter(stringWriter)) {
      DeclarativeConfigYamlGenerator.generateConfigurationYaml(
          modules, globalConfigurations, writer);
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
