/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs.utils;

import static io.opentelemetry.instrumentation.docs.internal.SemanticConvention.DATABASE_CLIENT_METRICS;
import static io.opentelemetry.instrumentation.docs.internal.SemanticConvention.DATABASE_CLIENT_SPANS;
import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.instrumentation.docs.internal.ConfigurationOption;
import io.opentelemetry.instrumentation.docs.internal.ConfigurationType;
import io.opentelemetry.instrumentation.docs.internal.EmittedMetrics;
import io.opentelemetry.instrumentation.docs.internal.EmittedSpans;
import io.opentelemetry.instrumentation.docs.internal.InstrumentationClassification;
import io.opentelemetry.instrumentation.docs.internal.InstrumentationFeature;
import io.opentelemetry.instrumentation.docs.internal.InstrumentationMetadata;
import io.opentelemetry.instrumentation.docs.internal.InstrumentationModule;
import io.opentelemetry.instrumentation.docs.internal.TelemetryAttribute;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class YamlHelperTest {
  @Test
  void testPrintInstrumentationList() throws Exception {
    List<InstrumentationModule> modules = new ArrayList<>();

    InstrumentationMetadata springMetadata =
        new InstrumentationMetadata.Builder()
            .description("Spring Web 6.0 instrumentation")
            .displayName("Spring Web")
            .classification(InstrumentationClassification.LIBRARY.name())
            .disabledByDefault(true)
            .semanticConventions(List.of(DATABASE_CLIENT_METRICS, DATABASE_CLIENT_SPANS))
            .build();

    modules.add(
        new InstrumentationModule.Builder()
            .srcPath("instrumentation/spring/spring-web/spring-web-6.0")
            .instrumentationName("spring-web-6.0")
            .scope(
                InstrumentationScopeInfo.builder("io.opentelemetry.spring-web-6.0")
                    .setVersion("2.14.0")
                    .setSchemaUrl("http:://www.schema.org")
                    .setAttributes(
                        Attributes.builder()
                            .put("instrumentation.type", "library")
                            .put("version.major", 6L)
                            .build())
                    .build())
            .namespace("spring")
            .group("spring")
            .targetVersions(Set.of("org.springframework:spring-web:[6.0.0,)"))
            .metadata(springMetadata)
            .minJavaVersion(11)
            .build());

    modules.add(
        new InstrumentationModule.Builder()
            .srcPath("instrumentation/struts/struts-2.3")
            .instrumentationName("struts-2.3")
            .namespace("struts")
            .targetVersions(Set.of("org.apache.struts:struts2-core:2.1.0"))
            .hasStandaloneLibrary(true)
            .group("struts")
            .build());

    String result = generateInstrumentationYaml(modules);
    String expectedYaml =
        """
        libraries:
        - name: spring-web-6.0
          display_name: Spring Web
          description: Spring Web 6.0 instrumentation
          semantic_conventions:
          - DATABASE_CLIENT_METRICS
          - DATABASE_CLIENT_SPANS
          disabled_by_default: true
          source_path: instrumentation/spring/spring-web/spring-web-6.0
          minimum_java_version: 11
          scope:
            name: io.opentelemetry.spring-web-6.0
            schema_url: http:://www.schema.org
            attributes:
              instrumentation.type: library
              version.major: 6
          javaagent_target_versions:
          - org.springframework:spring-web:[6.0.0,)
        - name: struts-2.3
          source_path: instrumentation/struts/struts-2.3
          scope:
            name: io.opentelemetry.struts-2.3
          has_standalone_library: true
          javaagent_target_versions:
          - org.apache.struts:struts2-core:2.1.0
        """;

    assertThat(result).isEqualTo(expectedYaml);
  }

  @Test
  void testGenerateInstrumentationYamlSeparatesClassifications() throws Exception {
    List<InstrumentationModule> modules = new ArrayList<>();

    InstrumentationMetadata springMetadata =
        new InstrumentationMetadata.Builder()
            .description("Spring Web 6.0 instrumentation")
            .classification(InstrumentationClassification.LIBRARY.name())
            .disabledByDefault(false)
            .features(
                List.of(
                    InstrumentationFeature.HTTP_ROUTE, InstrumentationFeature.CONTEXT_PROPAGATION))
            .semanticConventions(List.of(DATABASE_CLIENT_METRICS, DATABASE_CLIENT_SPANS))
            .configurations(
                List.of(
                    new ConfigurationOption(
                        "otel.instrumentation.spring-web-6.0.enabled",
                        "Enables or disables Spring Web 6.0 instrumentation.",
                        "true",
                        ConfigurationType.BOOLEAN)))
            .build();

    modules.add(
        new InstrumentationModule.Builder()
            .srcPath("instrumentation/spring/spring-web/spring-web-6.0")
            .instrumentationName("spring-web-6.0")
            .namespace("spring")
            .group("spring")
            .targetVersions(Set.of("org.springframework:spring-web:[6.0.0,)"))
            .metadata(springMetadata)
            .minJavaVersion(11)
            .build());

    InstrumentationMetadata internalMetadata =
        new InstrumentationMetadata.Builder()
            .classification(InstrumentationClassification.INTERNAL.name())
            .build();

    // we don't include internal
    modules.add(
        new InstrumentationModule.Builder()
            .srcPath("instrumentation/internal/internal-application-logger")
            .instrumentationName("internal-application-logger")
            .namespace("internal")
            .group("internal")
            .metadata(internalMetadata)
            .build());

    InstrumentationMetadata customMetadata =
        new InstrumentationMetadata.Builder()
            .classification(InstrumentationClassification.CUSTOM.name())
            .build();

    modules.add(
        new InstrumentationModule.Builder("opentelemetry-external-annotations")
            .srcPath("instrumentation/opentelemetry-external-annotations-1.0")
            .metadata(customMetadata)
            .targetVersions(
                Set.of("io.opentelemetry:opentelemetry-extension-annotations:[0.16.0,)"))
            .build());

    String result = generateInstrumentationYaml(modules);
    String expectedYaml =
        """
            definitions:
              configurations:
                otel.instrumentation.spring-web-6.0.enabled:
                  name: otel.instrumentation.spring-web-6.0.enabled
                  description: Enables or disables Spring Web 6.0 instrumentation.
                  type: boolean
                  default: true
            libraries:
            - name: spring-web-6.0
              description: Spring Web 6.0 instrumentation
              semantic_conventions:
              - DATABASE_CLIENT_METRICS
              - DATABASE_CLIENT_SPANS
              features:
              - HTTP_ROUTE
              - CONTEXT_PROPAGATION
              source_path: instrumentation/spring/spring-web/spring-web-6.0
              minimum_java_version: 11
              scope:
                name: io.opentelemetry.spring-web-6.0
              javaagent_target_versions:
              - org.springframework:spring-web:[6.0.0,)
              configuration_refs:
              - otel.instrumentation.spring-web-6.0.enabled
            custom:
            - name: opentelemetry-external-annotations
              source_path: instrumentation/opentelemetry-external-annotations-1.0
              scope:
                name: io.opentelemetry.opentelemetry-external-annotations
              javaagent_target_versions:
              - io.opentelemetry:opentelemetry-extension-annotations:[0.16.0,)
            """;

    assertThat(result).isEqualTo(expectedYaml);
  }

  @Test
  void testTargetVersionsAreOrdered() throws Exception {
    List<InstrumentationModule> modules = new ArrayList<>();

    modules.add(
        new InstrumentationModule.Builder("test-instrumentation")
            .srcPath("instrumentation/test-instrumentation")
            .targetVersions(
                Set.of(
                    "org.springframework.data:spring-data-commons:[1.8.0.RELEASE,)",
                    "org.springframework:spring-aop:[1.2,)"))
            .build());

    modules.add(
        new InstrumentationModule.Builder("test-instrumentation2")
            .srcPath("instrumentation/test-instrumentation2")
            .targetVersions(
                Set.of(
                    "org.springframework:spring-aop:[1.2,)",
                    "org.springframework.data:spring-data-commons:[1.8.0.RELEASE,)"))
            .build());

    String result = generateInstrumentationYaml(modules);
    String expectedYaml =
        """
        libraries:
        - name: test-instrumentation
          source_path: instrumentation/test-instrumentation
          scope:
            name: io.opentelemetry.test-instrumentation
          javaagent_target_versions:
          - org.springframework.data:spring-data-commons:[1.8.0.RELEASE,)
          - org.springframework:spring-aop:[1.2,)
        - name: test-instrumentation2
          source_path: instrumentation/test-instrumentation2
          scope:
            name: io.opentelemetry.test-instrumentation2
          javaagent_target_versions:
          - org.springframework.data:spring-data-commons:[1.8.0.RELEASE,)
          - org.springframework:spring-aop:[1.2,)
        """;

    assertThat(result).isEqualTo(expectedYaml);
  }

  @Test
  void testMetadataParser() throws JsonProcessingException {
    String input =
        """
            description: test description
            classification: internal
            disabled_by_default: true
            library_link: https://example.com/test-library
            features:
              - HTTP_ROUTE
              - CONTROLLER_SPANS
            configurations:
              - name: otel.instrumentation.common.db.query-sanitization.enabled
                description: Enables query sanitization for database queries.
                type: boolean
                default: true
            """;

    InstrumentationMetadata metadata = YamlHelper.metaDataParser(input);

    ConfigurationOption config = metadata.getConfigurations().get(0);
    assertThat(config.name())
        .isEqualTo("otel.instrumentation.common.db.query-sanitization.enabled");
    assertThat(config.description()).isEqualTo("Enables query sanitization for database queries.");
    assertThat(config.defaultValue()).isEqualTo("true");

    assertThat(metadata.getFeatures())
        .containsExactly(
            InstrumentationFeature.HTTP_ROUTE, InstrumentationFeature.CONTROLLER_SPANS);

    assertThat(metadata.getClassification()).isEqualTo(InstrumentationClassification.INTERNAL);
    assertThat(metadata.getDescription()).isEqualTo("test description");
    assertThat(metadata.getDisabledByDefault()).isEqualTo(true);
    assertThat(metadata.getLibraryLink()).isEqualTo("https://example.com/test-library");
  }

  @Test
  void testMetadataParserWithOnlyLibraryEntry() throws JsonProcessingException {
    String input = "classification: internal";
    InstrumentationMetadata metadata = YamlHelper.metaDataParser(input);
    assertThat(metadata.getClassification()).isEqualTo(InstrumentationClassification.INTERNAL);
    assertThat(metadata.getDescription()).isNull();
    assertThat(metadata.getLibraryLink()).isNull();
    assertThat(metadata.getDisabledByDefault()).isFalse();
    assertThat(metadata.getConfigurations()).isEmpty();
  }

  @Test
  void testMetadataParserWithOnlyLibraryLink() throws JsonProcessingException {
    String input = "library_link: https://example.com/only-link";
    InstrumentationMetadata metadata = YamlHelper.metaDataParser(input);
    assertThat(metadata.getClassification()).isEqualTo(InstrumentationClassification.LIBRARY);
    assertThat(metadata.getDescription()).isNull();
    assertThat(metadata.getLibraryLink()).isEqualTo("https://example.com/only-link");
    assertThat(metadata.getDisabledByDefault()).isFalse();
    assertThat(metadata.getConfigurations()).isEmpty();
  }

  @Test
  void testMetadataParserWithOnlyDescription() throws JsonProcessingException {
    String input = "description: false";
    InstrumentationMetadata metadata = YamlHelper.metaDataParser(input);
    assertThat(metadata.getClassification()).isEqualTo(InstrumentationClassification.LIBRARY);
    assertThat(metadata.getDisabledByDefault()).isFalse();
    assertThat(metadata.getConfigurations()).isEmpty();
  }

  @Test
  void testMetadataParserWithOnlyDisabledByDefault() throws JsonProcessingException {
    String input = "disabled_by_default: true";
    InstrumentationMetadata metadata = YamlHelper.metaDataParser(input);
    assertThat(metadata.getClassification()).isEqualTo(InstrumentationClassification.LIBRARY);
    assertThat(metadata.getDescription()).isNull();
    assertThat(metadata.getDisabledByDefault()).isTrue();
    assertThat(metadata.getConfigurations()).isEmpty();
  }

  @Test
  void testMetadataParserWithOnlyConfigurations() throws JsonProcessingException {
    String input =
        """
            configurations:
              - name: otel.instrumentation.common.db.query-sanitization.enabled
                description: Enables query sanitization for database queries.
                type: boolean
                default: true
        """;
    InstrumentationMetadata metadata = YamlHelper.metaDataParser(input);
    ConfigurationOption config = metadata.getConfigurations().get(0);

    assertThat(metadata.getClassification()).isEqualTo(InstrumentationClassification.LIBRARY);
    assertThat(metadata.getDescription()).isNull();
    assertThat(metadata.getDisabledByDefault()).isFalse();

    assertThat(config.name())
        .isEqualTo("otel.instrumentation.common.db.query-sanitization.enabled");
    assertThat(config.description()).isEqualTo("Enables query sanitization for database queries.");
    assertThat(config.defaultValue()).isEqualTo("true");
    assertThat(config.type()).isEqualTo(ConfigurationType.BOOLEAN);
  }

  @Test
  void testMetadataParserWithOnlyFeatures() throws JsonProcessingException {
    String input =
        """
            features:
              - HTTP_ROUTE
        """;
    InstrumentationMetadata metadata = YamlHelper.metaDataParser(input);

    assertThat(metadata.getClassification()).isEqualTo(InstrumentationClassification.LIBRARY);
    assertThat(metadata.getDescription()).isNull();
    assertThat(metadata.getDisabledByDefault()).isFalse();
    assertThat(metadata.getFeatures()).containsExactly(InstrumentationFeature.HTTP_ROUTE);
  }

  @Test
  void testMetricsParsing() throws Exception {
    List<InstrumentationModule> modules = new ArrayList<>();

    EmittedMetrics.Metric metric =
        new EmittedMetrics.Metric(
            "db.client.operation.duration",
            "Duration of database client operations.",
            "HISTOGRAM",
            "s",
            List.of(
                new TelemetryAttribute("db.namespace", "STRING"),
                new TelemetryAttribute("db.operation.name", "STRING"),
                new TelemetryAttribute("db.system.name", "STRING"),
                new TelemetryAttribute("server.address", "STRING"),
                new TelemetryAttribute("server.port", "LONG")));

    modules.add(
        new InstrumentationModule.Builder()
            .srcPath("instrumentation/mylib/mylib-core-2.3")
            .instrumentationName("mylib-2.3")
            .namespace("mylib")
            .group("mylib")
            .targetVersions(Set.of("org.apache.mylib:mylib-core:2.3.0"))
            .metrics(Map.of("default", List.of(metric)))
            .build());

    String result = generateInstrumentationYaml(modules);
    String expectedYaml =
        """
        definitions:
          metrics:
            db.client.operation.duration-fce1854c:
              name: db.client.operation.duration
              description: Duration of database client operations.
              instrument: histogram
              data_type: HISTOGRAM
              unit: s
              attributes:
              - name: db.namespace
                type: STRING
              - name: db.operation.name
                type: STRING
              - name: db.system.name
                type: STRING
              - name: server.address
                type: STRING
              - name: server.port
                type: LONG
        libraries:
        - name: mylib-2.3
          source_path: instrumentation/mylib/mylib-core-2.3
          scope:
            name: io.opentelemetry.mylib-2.3
          javaagent_target_versions:
          - org.apache.mylib:mylib-core:2.3.0
          telemetry:
          - when: default
            metric_refs:
            - db.client.operation.duration-fce1854c
        """;

    assertThat(result).isEqualTo(expectedYaml);
  }

  @Test
  void testMetricsWithDifferentInstrumentTypes() throws Exception {
    List<InstrumentationModule> modules = new ArrayList<>();

    List<EmittedMetrics.Metric> metrics =
        List.of(
            new EmittedMetrics.Metric("test.histogram", "desc", "HISTOGRAM", "s", emptyList()),
            new EmittedMetrics.Metric("test.counter", "desc", "LONG_SUM", true, "1", emptyList()),
            new EmittedMetrics.Metric(
                "test.updowncounter", "desc", "LONG_SUM", false, "1", emptyList()),
            new EmittedMetrics.Metric("test.gauge", "desc", "DOUBLE_GAUGE", "{test}", emptyList()));

    modules.add(
        new InstrumentationModule.Builder()
            .srcPath("instrumentation/test/test-1.0")
            .instrumentationName("test-1.0")
            .namespace("test")
            .group("test")
            .metrics(Map.of("default", metrics))
            .build());

    String result = generateInstrumentationYaml(modules);
    String expectedYaml =
        """
        definitions:
          metrics:
            test.counter-0cc8d1c0:
              name: test.counter
              description: desc
              instrument: counter
              data_type: LONG_SUM
              unit: '1'
              attributes: []
            test.gauge-3c51c34b:
              name: test.gauge
              description: desc
              instrument: gauge
              data_type: DOUBLE_GAUGE
              unit: '{test}'
              attributes: []
            test.histogram-f3e00ac6:
              name: test.histogram
              description: desc
              instrument: histogram
              data_type: HISTOGRAM
              unit: s
              attributes: []
            test.updowncounter-368958ee:
              name: test.updowncounter
              description: desc
              instrument: updowncounter
              data_type: LONG_SUM
              unit: '1'
              attributes: []
        libraries:
        - name: test-1.0
          source_path: instrumentation/test/test-1.0
          scope:
            name: io.opentelemetry.test-1.0
          telemetry:
          - when: default
            metric_refs:
            - test.counter-0cc8d1c0
            - test.gauge-3c51c34b
            - test.histogram-f3e00ac6
            - test.updowncounter-368958ee
        """;

    assertThat(result).isEqualTo(expectedYaml);
  }

  @Test
  void testSpanParsing() throws Exception {
    List<InstrumentationModule> modules = new ArrayList<>();

    EmittedSpans.Span span =
        new EmittedSpans.Span(
            "CLIENT",
            List.of(
                new TelemetryAttribute("db.namespace", "STRING"),
                new TelemetryAttribute("db.operation.name", "STRING"),
                new TelemetryAttribute("db.system.name", "STRING"),
                new TelemetryAttribute("server.address", "STRING"),
                new TelemetryAttribute("server.port", "LONG")));

    modules.add(
        new InstrumentationModule.Builder()
            .srcPath("instrumentation/mylib/mylib-core-2.3")
            .instrumentationName("mylib-2.3")
            .namespace("mylib")
            .hasStandaloneLibrary(true)
            .group("mylib")
            .spans(Map.of("default", List.of(span)))
            .build());

    String result = generateInstrumentationYaml(modules);
    String expectedYaml =
        """
        libraries:
        - name: mylib-2.3
          source_path: instrumentation/mylib/mylib-core-2.3
          scope:
            name: io.opentelemetry.mylib-2.3
          has_standalone_library: true
          telemetry:
          - when: default
            spans:
            - span_kind: CLIENT
              attributes:
              - name: db.namespace
                type: STRING
              - name: db.operation.name
                type: STRING
              - name: db.system.name
                type: STRING
              - name: server.address
                type: STRING
              - name: server.port
                type: LONG
        """;

    assertThat(result).isEqualTo(expectedYaml);
  }

  @Test
  void testTelemetryGroupsAreSorted() throws Exception {
    EmittedMetrics.Metric metric =
        new EmittedMetrics.Metric("a.metric", "description", "COUNTER", "1", emptyList());
    EmittedSpans.Span span = new EmittedSpans.Span("SERVER", emptyList());

    // First ordering
    Map<String, List<EmittedMetrics.Metric>> metrics1 = new LinkedHashMap<>();
    metrics1.put("z-group", List.of(metric));
    metrics1.put("a-group", List.of(metric));

    Map<String, List<EmittedSpans.Span>> spans1 = new LinkedHashMap<>();
    spans1.put("c-group", List.of(span));
    spans1.put("f-group", List.of(span));
    spans1.put("b-group", List.of(span));

    List<InstrumentationModule> modules1 = new ArrayList<>();
    modules1.add(
        new InstrumentationModule.Builder()
            .srcPath("instrumentation/test/test-1.0")
            .instrumentationName("test-1.0")
            .namespace("test")
            .group("test")
            .metrics(metrics1)
            .spans(spans1)
            .build());

    String yaml1 = generateInstrumentationYaml(modules1);

    // Different ordering
    Map<String, List<EmittedMetrics.Metric>> metrics2 = new LinkedHashMap<>();
    metrics2.put("a-group", List.of(metric));
    metrics2.put("z-group", List.of(metric));

    Map<String, List<EmittedSpans.Span>> spans2 = new LinkedHashMap<>();
    spans2.put("f-group", List.of(span));
    spans2.put("b-group", List.of(span));
    spans2.put("c-group", List.of(span));

    List<InstrumentationModule> modules2 = new ArrayList<>();
    modules2.add(
        new InstrumentationModule.Builder()
            .srcPath("instrumentation/test/test-1.0")
            .instrumentationName("test-1.0")
            .namespace("test")
            .group("test")
            .metrics(metrics2)
            .spans(spans2)
            .build());

    String yaml2 = generateInstrumentationYaml(modules2);

    assertThat(yaml1).isEqualTo(yaml2);
  }

  @Test
  void testYamlGenerationWithLibraryLink() throws Exception {
    List<InstrumentationModule> modules = new ArrayList<>();

    Set<String> targetVersions = Set.of("com.example:test-library:[1.0.0,)");

    InstrumentationMetadata metadataWithLink =
        new InstrumentationMetadata.Builder()
            .description("Test library instrumentation with link")
            .classification(InstrumentationClassification.LIBRARY.name())
            .disabledByDefault(false)
            .libraryLink("https://example.com/test-library-docs")
            .build();

    modules.add(
        new InstrumentationModule.Builder()
            .srcPath("instrumentation/test-lib/test-lib-1.0")
            .instrumentationName("test-lib-1.0")
            .namespace("test-lib")
            .group("test-lib")
            .targetVersions(targetVersions)
            .metadata(metadataWithLink)
            .build());

    InstrumentationMetadata metadataWithoutLink =
        new InstrumentationMetadata.Builder()
            .description("Test library instrumentation without link")
            .classification(InstrumentationClassification.LIBRARY.name())
            .disabledByDefault(false)
            .build();

    modules.add(
        new InstrumentationModule.Builder()
            .srcPath("instrumentation/other-lib/other-lib-1.0")
            .instrumentationName("other-lib-1.0")
            .namespace("other-lib")
            .group("other-lib")
            .targetVersions(targetVersions)
            .metadata(metadataWithoutLink)
            .build());

    String result = generateInstrumentationYaml(modules);
    String expectedYaml =
        """
            libraries:
            - name: other-lib-1.0
              description: Test library instrumentation without link
              source_path: instrumentation/other-lib/other-lib-1.0
              scope:
                name: io.opentelemetry.other-lib-1.0
              javaagent_target_versions:
              - com.example:test-library:[1.0.0,)
            - name: test-lib-1.0
              description: Test library instrumentation with link
              library_link: https://example.com/test-library-docs
              source_path: instrumentation/test-lib/test-lib-1.0
              scope:
                name: io.opentelemetry.test-lib-1.0
              javaagent_target_versions:
              - com.example:test-library:[1.0.0,)
            """;

    assertThat(result).isEqualTo(expectedYaml);
  }

  @Test
  void testHasJavaAgentFlag() throws Exception {
    List<InstrumentationModule> modules = new ArrayList<>();

    modules.add(
        new InstrumentationModule.Builder()
            .srcPath("instrumentation/runtime-telemetry/runtime-telemetry-java8")
            .instrumentationName("runtime-telemetry-java8")
            .namespace("runtime-telemetry")
            .group("runtime-telemetry")
            .hasJavaAgent(true)
            .build());

    modules.add(
        new InstrumentationModule.Builder()
            .srcPath("instrumentation/library-only/library-only-1.0")
            .instrumentationName("library-only-1.0")
            .namespace("library-only")
            .group("library-only")
            .hasStandaloneLibrary(true)
            .build());

    String result = generateInstrumentationYaml(modules);
    String expectedYaml =
        """
        libraries:
        - name: library-only-1.0
          source_path: instrumentation/library-only/library-only-1.0
          scope:
            name: io.opentelemetry.library-only-1.0
          has_standalone_library: true
        - name: runtime-telemetry-java8
          source_path: instrumentation/runtime-telemetry/runtime-telemetry-java8
          scope:
            name: io.opentelemetry.runtime-telemetry-java8
          has_javaagent: true
        """;

    assertThat(result).isEqualTo(expectedYaml);
  }

  @Test
  void testInstrumentationsSortedBySemanticVersion() throws Exception {
    List<InstrumentationModule> modules = new ArrayList<>();
    InstrumentationMetadata metadata =
        new InstrumentationMetadata.Builder()
            .classification(InstrumentationClassification.LIBRARY.name())
            .build();

    modules.add(
        new InstrumentationModule.Builder("opentelemetry-api-1.57")
            .srcPath("instrumentation/opentelemetry-api/opentelemetry-api-1.57")
            .group("opentelemetry-api")
            .metadata(metadata)
            .build());

    modules.add(
        new InstrumentationModule.Builder("opentelemetry-api-1.10")
            .srcPath("instrumentation/opentelemetry-api/opentelemetry-api-1.10")
            .group("opentelemetry-api")
            .metadata(metadata)
            .build());

    modules.add(
        new InstrumentationModule.Builder("opentelemetry-api-1.56")
            .srcPath("instrumentation/opentelemetry-api/opentelemetry-api-1.56")
            .group("opentelemetry-api")
            .metadata(metadata)
            .build());

    modules.add(
        new InstrumentationModule.Builder("opentelemetry-api-1.9")
            .srcPath("instrumentation/opentelemetry-api/opentelemetry-api-1.9")
            .group("opentelemetry-api")
            .metadata(metadata)
            .build());

    modules.add(
        new InstrumentationModule.Builder("opentelemetry-api-2.0")
            .srcPath("instrumentation/opentelemetry-api/opentelemetry-api-2.0")
            .group("opentelemetry-api")
            .metadata(metadata)
            .build());

    String result = generateInstrumentationYaml(modules);
    String expectedYaml =
        """
            libraries:
            - name: opentelemetry-api-1.9
              source_path: instrumentation/opentelemetry-api/opentelemetry-api-1.9
              scope:
                name: io.opentelemetry.opentelemetry-api-1.9
            - name: opentelemetry-api-1.10
              source_path: instrumentation/opentelemetry-api/opentelemetry-api-1.10
              scope:
                name: io.opentelemetry.opentelemetry-api-1.10
            - name: opentelemetry-api-1.56
              source_path: instrumentation/opentelemetry-api/opentelemetry-api-1.56
              scope:
                name: io.opentelemetry.opentelemetry-api-1.56
            - name: opentelemetry-api-1.57
              source_path: instrumentation/opentelemetry-api/opentelemetry-api-1.57
              scope:
                name: io.opentelemetry.opentelemetry-api-1.57
            - name: opentelemetry-api-2.0
              source_path: instrumentation/opentelemetry-api/opentelemetry-api-2.0
              scope:
                name: io.opentelemetry.opentelemetry-api-2.0
            """;

    assertThat(result).isEqualTo(expectedYaml);
  }

  @Test
  void testConfigurationRefIsResolvedFromSharedRegistry() throws JsonProcessingException {
    String input =
        """
        configurations:
          - ref: http.known-methods
        """;

    InstrumentationMetadata metadata = YamlHelper.metaDataParser(input);

    assertThat(metadata.getConfigurations()).hasSize(1);
    ConfigurationOption resolved = metadata.getConfigurations().get(0);
    assertThat(resolved.id()).isEqualTo("http.known-methods");
    assertThat(resolved.name()).isEqualTo("otel.instrumentation.http.known-methods");
    assertThat(resolved.type()).isEqualTo(ConfigurationType.LIST);
  }

  @Test
  void testInlineConfigurationCannotSupplyId() throws JsonProcessingException {
    // The definition id is assigned internally; an inline option must not be able to claim one from
    // metadata.yaml, otherwise it could overwrite a registry-backed definition in the catalog.
    String input =
        """
        configurations:
          - name: otel.instrumentation.my-module.example
            description: Example option.
            type: boolean
            default: false
            id: http.known-methods
        """;

    InstrumentationMetadata metadata = YamlHelper.metaDataParser(input);

    assertThat(metadata.getConfigurations()).hasSize(1);
    assertThat(metadata.getConfigurations().get(0).id()).isNull();
  }

  @Test
  void testUnknownConfigurationRefFails() {
    String input =
        """
        configurations:
          - ref: does.not.exist
        """;

    assertThatThrownBy(() -> YamlHelper.metaDataParser(input))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("does.not.exist");
  }

  @Test
  void testSharedMetricDefinitionIsDeduplicatedAcrossModules() throws Exception {
    // Two modules emit an identical metric; it should be cataloged once and referenced by both.
    EmittedMetrics.Metric metric =
        new EmittedMetrics.Metric(
            "http.client.request.duration",
            "Duration of HTTP client requests.",
            "HISTOGRAM",
            "s",
            emptyList());

    List<InstrumentationModule> modules = new ArrayList<>();
    for (String name : List.of("alpha-1.0", "beta-1.0")) {
      modules.add(
          new InstrumentationModule.Builder(name)
              .srcPath("instrumentation/" + name)
              .metrics(Map.of("default", List.of(metric)))
              .build());
    }

    String result = generateInstrumentationYaml(modules);

    long definitionCount =
        result
            .lines()
            .filter(
                l -> l.trim().startsWith("http.client.request.duration-") && l.trim().endsWith(":"))
            .count();
    long refCount =
        result.lines().filter(l -> l.trim().startsWith("- http.client.request.duration-")).count();

    assertThat(definitionCount).isEqualTo(1);
    assertThat(refCount).isEqualTo(2);
  }

  private static String generateInstrumentationYaml(List<InstrumentationModule> modules)
      throws IOException {
    StringWriter stringWriter = new StringWriter();
    try (BufferedWriter writer = new BufferedWriter(stringWriter)) {
      YamlHelper.generateInstrumentationYaml(modules, writer);
    }
    return stringWriter.toString();
  }
}
