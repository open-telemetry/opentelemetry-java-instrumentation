/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.instrumentation.config.bridge.DeclarativeConfigBridge;
import io.opentelemetry.instrumentation.docs.internal.ConfigurationOption;
import io.opentelemetry.instrumentation.docs.internal.ConfigurationType;
import io.opentelemetry.instrumentation.docs.internal.DeclarativeSchema;
import io.opentelemetry.instrumentation.docs.internal.InstrumentationMetadata;
import io.opentelemetry.instrumentation.docs.internal.SharedConfigurationRegistry;
import io.opentelemetry.instrumentation.docs.utils.YamlHelper;
import io.opentelemetry.sdk.autoconfigure.spi.internal.DefaultConfigProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Validates that declarative_name values in metadata.yaml files correctly map to their
 * corresponding flat property names using the actual
 * ConfigPropertiesBackedDeclarativeConfigProperties bridge.
 */
class DeclarativeConfigValidationTest {

  private static final Logger logger =
      Logger.getLogger(DeclarativeConfigValidationTest.class.getName());

  private static final Path INSTRUMENTATION_DIR = Paths.get("../instrumentation");

  // Declarative names that were published under an earlier spelling. The bridge keeps them in
  // SPECIAL_MAPPINGS so existing configuration files keep working, but metadata.yaml must declare
  // the name from the declarative configuration schema.
  private static final Map<String, String> DEPRECATED_DECLARATIVE_NAMES =
      Map.of("general.semconv_stability.opt_in", "general.stability_opt_in_list");

  private static final String SHARED_DEFINITIONS = "shared-config-definitions.yaml";

  @Test
  void validateDeclarativeNames() throws IOException {
    List<ValidationResult> results = new ArrayList<>();
    List<String> errors = new ArrayList<>();

    allConfigurations()
        .forEach(
            (source, configs) -> {
              for (ConfigurationOption config : configs) {
                // Structured-list schemas are validated structurally, even for declarative-only
                // configs (those without a flat property name, such as url_template_rules).
                validateStructuredListSchema(source, config, errors);

                // Deprecated spellings stay resolvable at runtime, but must not be declared here.
                validateNotDeprecated(source, config, errors);

                // The flat -> declarative round-trip needs a flat system property to drive the
                // bridge. Declarative-only configs (no name) are skipped here.
                if (config.name() != null
                    && !config.name().isBlank()
                    && config.declarativeName() != null
                    && !config.declarativeName().isBlank()) {
                  ValidationResult result = validateConfig(source, config);
                  results.add(result);
                  if (!result.valid) {
                    errors.add(result.toString());
                  }
                }
              }
            });

    long validCount = results.stream().filter(r -> r.valid).count();
    logger.info(
        String.format(
            Locale.ROOT,
            "Validated %d declarative names: %d valid, %d invalid",
            results.size(),
            validCount,
            results.size() - validCount));

    if (!errors.isEmpty()) {
      fail(
          String.format(
              Locale.ROOT,
              "Found %d invalid declarative_name mappings:%n%s",
              errors.size(),
              String.join("\n", errors)));
    }
  }

  /**
   * The `deprecated` flag is what keeps a deprecated setting out of
   * docs/declarative-configuration-example.yaml and marks it in docs/instrumentation-list.yaml, so
   * it must agree with the description, and `replaced_by` must point at a documented setting.
   */
  @Test
  void deprecationIsDeclared() throws IOException {
    Map<String, List<ConfigurationOption>> configsBySource = allConfigurations();
    Set<String> documentedNames = new HashSet<>();
    configsBySource.values().stream()
        .flatMap(List::stream)
        .forEach(
            config -> {
              if (config.name() != null) {
                documentedNames.add(config.name());
              }
              if (config.declarativeName() != null) {
                documentedNames.add(config.declarativeName());
              }
            });

    List<String> errors = new ArrayList<>();
    configsBySource.forEach(
        (source, configs) -> {
          for (ConfigurationOption config : configs) {
            String id = config.name() != null ? config.name() : config.declarativeName();
            boolean describedAsDeprecated = config.description().startsWith("Deprecated");
            if (describedAsDeprecated != config.isDeprecated()) {
              errors.add(
                  source
                      + ": '"
                      + id
                      + "' must set `deprecated: true` exactly when its description starts with"
                      + " \"Deprecated\"");
            }
            if (config.replacedBy() != null && !documentedNames.contains(config.replacedBy())) {
              errors.add(
                  source
                      + ": '"
                      + id
                      + "' is replaced_by '"
                      + config.replacedBy()
                      + "', which is not a documented name or declarative_name");
            }
          }
        });

    assertThat(errors).isEmpty();
  }

  /**
   * A setting without a default falls back to another setting when unset. When that other setting
   * is a shared definition (for example a per-module override of
   * `otel.instrumentation.common.db.query-sanitization.enabled`), the module must also `ref` it, so
   * that its documentation lists the setting that actually applies by default.
   */
  @Test
  void fallbackSettingsAreReferenced() throws IOException {
    Map<String, String> sharedIdsByName = new HashMap<>();
    SharedConfigurationRegistry.getInstance()
        .definitions()
        .forEach(
            (id, config) -> {
              if (config.name() != null) {
                sharedIdsByName.put(config.name(), id);
              }
            });

    List<String> errors = new ArrayList<>();
    metadataConfigurations()
        .forEach(
            (source, configs) -> {
              Set<String> refs = new HashSet<>();
              for (ConfigurationOption config : configs) {
                if (config.id() != null) {
                  refs.add(config.id());
                }
              }
              for (ConfigurationOption config : configs) {
                if (config.id() != null || config.defaultValue() != null) {
                  continue;
                }
                sharedIdsByName.forEach(
                    (name, id) -> {
                      if (config.description().contains("`" + name + "`") && !refs.contains(id)) {
                        errors.add(
                            source
                                + ": '"
                                + config.name()
                                + "' falls back to '"
                                + name
                                + "', so the module must also declare `- ref: "
                                + id
                                + "`");
                      }
                    });
              }
            });

    assertThat(errors).isEmpty();
  }

  /**
   * Returns the configurations of every metadata.yaml, plus the shared definitions (a module only
   * carries a ref to them), keyed by where they are declared.
   */
  private static Map<String, List<ConfigurationOption>> allConfigurations() throws IOException {
    Map<String, List<ConfigurationOption>> configsBySource =
        new LinkedHashMap<>(metadataConfigurations());
    SharedConfigurationRegistry registry = SharedConfigurationRegistry.getInstance();
    configsBySource.put(
        SHARED_DEFINITIONS + " (configurations)", List.copyOf(registry.definitions().values()));
    return configsBySource;
  }

  private static Map<String, List<ConfigurationOption>> metadataConfigurations()
      throws IOException {
    Map<String, List<ConfigurationOption>> configsBySource = new LinkedHashMap<>();
    try (Stream<Path> paths = Files.walk(INSTRUMENTATION_DIR)) {
      List<Path> metadataFiles =
          paths.filter(p -> p.getFileName().toString().equals("metadata.yaml")).toList();

      for (Path metadataFile : metadataFiles) {
        String content = Files.readString(metadataFile);
        try {
          InstrumentationMetadata metadata = YamlHelper.metaDataParser(content);
          configsBySource.put(metadataFile.toString(), metadata.getConfigurations());
        } catch (Exception e) {
          throw new IllegalStateException(
              String.format("Failed to parse %s: %s", metadataFile, e.getMessage()), e);
        }
      }
    }
    return configsBySource;
  }

  private static void validateNotDeprecated(
      String source, ConfigurationOption config, List<String> errors) {
    if (config.declarativeName() == null) {
      return;
    }
    String replacement = DEPRECATED_DECLARATIVE_NAMES.get(config.declarativeName());
    if (replacement != null) {
      errors.add(
          String.format(
              Locale.ROOT,
              "Deprecated declarative_name in %s: '%s' is kept in the bridge for backwards"
                  + " compatibility only; use '%s' instead.",
              source,
              config.declarativeName(),
              replacement));
    }
  }

  private static ValidationResult validateConfig(String source, ConfigurationOption config) {
    String flatProperty = config.name();
    String declarativePath = config.declarativeName();
    ConfigurationType type = config.type();

    // The declarative form is a structured list when either the flat type is structured_list
    // (legacy) or the dedicated declarative_type marks it as such. service_peer_mapping uses
    // type: map (the flat host=service form) + declarative_type: structured_list.
    boolean structuredList =
        type == ConfigurationType.STRUCTURED_LIST
            || config.declarativeType() == ConfigurationType.STRUCTURED_LIST;

    // Create test value appropriate for the type
    TestValue testValue = structuredList ? structuredListTestValue() : createTestValue(type);

    Map<String, String> properties = new HashMap<>();
    properties.put(flatProperty, testValue.propertyValue);
    DefaultConfigProperties configProperties = DefaultConfigProperties.createFromMap(properties);

    DeclarativeConfigProperties declarativeConfig =
        DeclarativeConfigBridge.createInstrumentationConfig(configProperties)
            .getInstrumentationConfig();

    Object retrievedValue =
        navigateAndGetValue(declarativeConfig, declarativePath, type, structuredList);

    boolean valid = Objects.equals(testValue.expectedValue, retrievedValue);

    return new ValidationResult(
        source,
        flatProperty,
        declarativePath,
        type,
        valid,
        testValue.expectedValue,
        retrievedValue);
  }

  private static TestValue createTestValue(ConfigurationType type) {
    return switch (type) {
      case BOOLEAN -> new TestValue("true", true);
      case STRING -> new TestValue("test-validation-value", "test-validation-value");
      case INT -> new TestValue("42", 42);
      case LIST -> new TestValue("item1,item2,item3", List.of("item1", "item2", "item3"));
      case MAP, STRUCTURED_LIST ->
          new TestValue("key1=value1,key2=value2", "key1=value1,key2=value2");
    };
  }

  // The only structured_list config with a flat property is the common peer-service-mapping. Its
  // flat form is a host=service map, which the bridge turns into a list of {peer, service_name}
  // entries. readPeerServiceMappingAsMap below rebuilds the host->service map from those entries,
  // so
  // the comparison exercises getStructuredList (what the consumer uses) rather than a string
  // lookup.
  private static TestValue structuredListTestValue() {
    return new TestValue("host1=svc1,host2=svc2", Map.of("host1", "svc1", "host2", "svc2"));
  }

  private record TestValue(String propertyValue, Object expectedValue) {}

  /**
   * Navigates through the declarative config using the path segments and retrieves the value.
   *
   * <p>The path format is like "java.grpc.emit_message_events" or
   * "java.logback_appender.capture_code_attributes/development".
   */
  private static Object navigateAndGetValue(
      DeclarativeConfigProperties config,
      String path,
      ConfigurationType type,
      boolean structuredList) {
    String[] segments = path.split("\\.");

    DeclarativeConfigProperties current = config;

    // Navigate through all segments except the last one
    for (int i = 0; i < segments.length - 1; i++) {
      current = current.getStructured(segments[i]);
      if (current == null) {
        return null;
      }
    }

    String lastSegment = segments[segments.length - 1];
    if (structuredList) {
      return readPeerServiceMappingAsMap(current, lastSegment);
    }
    return switch (type) {
      case BOOLEAN -> current.getBoolean(lastSegment);
      case STRING -> current.getString(lastSegment);
      case INT -> current.getInt(lastSegment);
      case LIST -> current.getScalarList(lastSegment, String.class);
      case MAP, STRUCTURED_LIST -> current.getString(lastSegment);
    };
  }

  /**
   * Reads the peer-service-mapping structured list through the bridge (the same {@code
   * getStructuredList} call {@code ServicePeerResolver} makes) and collapses its {@code {peer,
   * service_name}} entries back into a {@code host -> service} map, so it can be compared against
   * the flat-property value the test fed in. This is specific to peer-service-mapping, which is
   * currently the only {@code structured_list} config.
   */
  private static Object readPeerServiceMappingAsMap(
      DeclarativeConfigProperties current, String lastSegment) {
    List<DeclarativeConfigProperties> entries = current.getStructuredList(lastSegment);
    if (entries == null) {
      return null;
    }
    Map<String, String> mapping = new HashMap<>();
    for (DeclarativeConfigProperties entry : entries) {
      mapping.put(entry.getString("peer"), entry.getString("service_name"));
    }
    return mapping;
  }

  /**
   * Validates the structure of a {@code declarative_schema} on a {@code structured_list} config.
   * Applies to every structured-list config, including declarative-only ones (no flat property)
   * such as {@code url_template_rules}, which the round-trip check above cannot exercise.
   */
  private static void validateStructuredListSchema(
      String source, ConfigurationOption config, List<String> errors) {
    if (config.declarativeType() != ConfigurationType.STRUCTURED_LIST) {
      return;
    }
    String label = source + " (" + config.declarativeName() + ")";
    DeclarativeSchema schema = config.declarativeSchema();
    if (schema == null) {
      errors.add(label + ": structured_list config is missing a declarative_schema");
      return;
    }
    if (!"object".equals(schema.type())) {
      errors.add(label + ": declarative_schema type must be 'object'");
    }
    if (schema.properties() == null || schema.properties().isEmpty()) {
      errors.add(label + ": declarative_schema must define at least one property");
      return;
    }
    if (schema.required() != null && !schema.properties().keySet().containsAll(schema.required())) {
      errors.add(label + ": declarative_schema required keys must be a subset of its properties");
    }
  }

  private record ValidationResult(
      String metadataFile,
      String flatProperty,
      String declarativePath,
      ConfigurationType type,
      boolean valid,
      Object expectedValue,
      Object retrievedValue) {

    @Override
    public String toString() {
      if (valid) {
        return String.format("  OK: %s -> %s (%s)", flatProperty, declarativePath, type);
      } else {
        return String.format(
            "  FAIL in %s:%n"
                + "    flat property: %s%n"
                + "    declarative_name: %s%n"
                + "    type: %s%n"
                + "    expected: %s%n"
                + "    got: %s",
            metadataFile, flatProperty, declarativePath, type, expectedValue, retrievedValue);
      }
    }
  }
}
