/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs.utils;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.opentelemetry.instrumentation.docs.internal.ConfigurationOption;
import io.opentelemetry.instrumentation.docs.internal.ConfigurationType;
import io.opentelemetry.instrumentation.docs.internal.EmittedMetrics;
import io.opentelemetry.instrumentation.docs.internal.EmittedSpans;
import io.opentelemetry.instrumentation.docs.internal.InstrumentationClassification;
import io.opentelemetry.instrumentation.docs.internal.InstrumentationMetaData;
import io.opentelemetry.instrumentation.docs.internal.InstrumentationModule;
import io.opentelemetry.instrumentation.docs.internal.InstrumentationType;
import io.opentelemetry.instrumentation.docs.internal.TelemetryAttribute;
import java.io.BufferedWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class YamlHelperTest {
  @Test
  void testPrintInstrumentationList() throws Exception {
    List<InstrumentationModule> modules = new ArrayList<>();
    Map<InstrumentationType, Set<String>> targetVersions1 = new HashMap<>();
    targetVersions1.put(
        InstrumentationType.JAVAAGENT,
        new HashSet<>(List.of("org.springframework:spring-web:[6.0.0,)")));

    InstrumentationMetaData springMetadata =
        new InstrumentationMetaData(
            "Spring Web 6.0 instrumentation",
            InstrumentationClassification.LIBRARY.toString(),
            true,
            null);

    modules.add(
        new InstrumentationModule.Builder()
            .srcPath("instrumentation/spring/spring-web/spring-web-6.0")
            .instrumentationName("spring-web-6.0")
            .namespace("spring")
            .group("spring")
            .targetVersions(targetVersions1)
            .metadata(springMetadata)
            .minJavaVersion(11)
            .build());

    Map<InstrumentationType, Set<String>> targetVersions2 = new HashMap<>();

    targetVersions2.put(
        InstrumentationType.LIBRARY,
        new HashSet<>(List.of("org.apache.struts:struts2-core:2.1.0")));
    modules.add(
        new InstrumentationModule.Builder()
            .srcPath("instrumentation/struts/struts-2.3")
            .instrumentationName("struts-2.3")
            .namespace("struts")
            .group("struts")
            .targetVersions(targetVersions2)
            .build());

    StringWriter stringWriter = new StringWriter();
    BufferedWriter writer = new BufferedWriter(stringWriter);

    YamlHelper.generateInstrumentationYaml(modules, writer);
    writer.flush();

    String expectedYaml =
        """
            libraries:
              spring:
              - name: spring-web-6.0
                description: Spring Web 6.0 instrumentation
                disabled_by_default: true
                source_path: instrumentation/spring/spring-web/spring-web-6.0
                minimum_java_version: 11
                scope:
                  name: io.opentelemetry.spring-web-6.0
                target_versions:
                  javaagent:
                  - org.springframework:spring-web:[6.0.0,)
              struts:
              - name: struts-2.3
                source_path: instrumentation/struts/struts-2.3
                scope:
                  name: io.opentelemetry.struts-2.3
                target_versions:
                  library:
                  - org.apache.struts:struts2-core:2.1.0
            """;

    assertThat(expectedYaml).isEqualTo(stringWriter.toString());
  }

  @Test
  void testGenerateInstrumentationYamlSeparatesClassifications() throws Exception {
    List<InstrumentationModule> modules = new ArrayList<>();
    Map<InstrumentationType, Set<String>> springTargetVersions =
        Map.of(InstrumentationType.JAVAAGENT, Set.of("org.springframework:spring-web:[6.0.0,)"));

    InstrumentationMetaData springMetadata =
        new InstrumentationMetaData(
            "Spring Web 6.0 instrumentation",
            InstrumentationClassification.LIBRARY.toString(),
            false,
            List.of(
                new ConfigurationOption(
                    "otel.instrumentation.spring-web-6.0.enabled",
                    "Enables or disables Spring Web 6.0 instrumentation.",
                    "true",
                    ConfigurationType.BOOLEAN)));

    modules.add(
        new InstrumentationModule.Builder()
            .srcPath("instrumentation/spring/spring-web/spring-web-6.0")
            .instrumentationName("spring-web-6.0")
            .namespace("spring")
            .group("spring")
            .targetVersions(springTargetVersions)
            .metadata(springMetadata)
            .minJavaVersion(11)
            .build());

    InstrumentationMetaData internalMetadata =
        new InstrumentationMetaData(
            null, InstrumentationClassification.INTERNAL.toString(), null, null);

    modules.add(
        new InstrumentationModule.Builder()
            .srcPath("instrumentation/internal/internal-application-logger")
            .instrumentationName("internal-application-logger")
            .namespace("internal")
            .group("internal")
            .metadata(internalMetadata)
            .targetVersions(new HashMap<>())
            .build());

    InstrumentationMetaData customMetadata =
        new InstrumentationMetaData(
            null, InstrumentationClassification.CUSTOM.toString(), null, null);

    Map<InstrumentationType, Set<String>> externalAnnotationsVersions =
        Map.of(
            InstrumentationType.JAVAAGENT,
            Set.of("io.opentelemetry:opentelemetry-extension-annotations:[0.16.0,)"));

    modules.add(
        new InstrumentationModule.Builder()
            .srcPath("instrumentation/opentelemetry-external-annotations-1.0")
            .instrumentationName("opentelemetry-external-annotations")
            .namespace("opentelemetry-external-annotations")
            .group("opentelemetry-external-annotations")
            .metadata(customMetadata)
            .targetVersions(externalAnnotationsVersions)
            .build());

    StringWriter stringWriter = new StringWriter();
    BufferedWriter writer = new BufferedWriter(stringWriter);

    YamlHelper.generateInstrumentationYaml(modules, writer);
    writer.flush();

    String expectedYaml =
        """
            libraries:
              spring:
              - name: spring-web-6.0
                description: Spring Web 6.0 instrumentation
                source_path: instrumentation/spring/spring-web/spring-web-6.0
                minimum_java_version: 11
                scope:
                  name: io.opentelemetry.spring-web-6.0
                target_versions:
                  javaagent:
                  - org.springframework:spring-web:[6.0.0,)
                configurations:
                - name: otel.instrumentation.spring-web-6.0.enabled
                  description: Enables or disables Spring Web 6.0 instrumentation.
                  type: boolean
                  default: true
            internal:
            - name: internal-application-logger
              source_path: instrumentation/internal/internal-application-logger
              scope:
                name: io.opentelemetry.internal-application-logger
            custom:
            - name: opentelemetry-external-annotations
              source_path: instrumentation/opentelemetry-external-annotations-1.0
              scope:
                name: io.opentelemetry.opentelemetry-external-annotations
              target_versions:
                javaagent:
                - io.opentelemetry:opentelemetry-extension-annotations:[0.16.0,)
            """;

    assertThat(expectedYaml).isEqualTo(stringWriter.toString());
  }

  @Test
  void testMetadataParser() throws JsonProcessingException {
    String input =
        """
            description: test description
            classification: internal
            disabled_by_default: true
            configurations:
              - name: otel.instrumentation.common.db-statement-sanitizer.enabled
                description: Enables statement sanitization for database queries.
                type: boolean
                default: true
            """;

    InstrumentationMetaData metadata = YamlHelper.metaDataParser(input);

    ConfigurationOption config = metadata.getConfigurations().get(0);
    assertThat(config.name())
        .isEqualTo("otel.instrumentation.common.db-statement-sanitizer.enabled");
    assertThat(config.description())
        .isEqualTo("Enables statement sanitization for database queries.");
    assertThat(config.defaultValue()).isEqualTo("true");

    assertThat(metadata.getClassification()).isEqualTo(InstrumentationClassification.INTERNAL);
    assertThat(metadata.getDescription()).isEqualTo("test description");
    assertThat(metadata.getDisabledByDefault()).isEqualTo(true);
  }

  @Test
  void testMetadataParserWithOnlyLibraryEntry() throws JsonProcessingException {
    String input = "classification: internal";
    InstrumentationMetaData metadata = YamlHelper.metaDataParser(input);
    assertThat(metadata.getClassification()).isEqualTo(InstrumentationClassification.INTERNAL);
    assertThat(metadata.getDescription()).isNull();
    assertThat(metadata.getDisabledByDefault()).isFalse();
    assertThat(metadata.getConfigurations()).isEmpty();
  }

  @Test
  void testMetadataParserWithOnlyDescription() throws JsonProcessingException {
    String input = "description: false";
    InstrumentationMetaData metadata = YamlHelper.metaDataParser(input);
    assertThat(metadata.getClassification()).isEqualTo(InstrumentationClassification.LIBRARY);
    assertThat(metadata.getDisabledByDefault()).isFalse();
    assertThat(metadata.getConfigurations()).isEmpty();
  }

  @Test
  void testMetadataParserWithOnlyDisabledByDefault() throws JsonProcessingException {
    String input = "disabled_by_default: true";
    InstrumentationMetaData metadata = YamlHelper.metaDataParser(input);
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
              - name: otel.instrumentation.common.db-statement-sanitizer.enabled
                description: Enables statement sanitization for database queries.
                type: boolean
                default: true
        """;
    InstrumentationMetaData metadata = YamlHelper.metaDataParser(input);
    ConfigurationOption config = metadata.getConfigurations().get(0);

    assertThat(metadata.getClassification()).isEqualTo(InstrumentationClassification.LIBRARY);
    assertThat(metadata.getDescription()).isNull();
    assertThat(metadata.getDisabledByDefault()).isFalse();

    assertThat(config.name())
        .isEqualTo("otel.instrumentation.common.db-statement-sanitizer.enabled");
    assertThat(config.description())
        .isEqualTo("Enables statement sanitization for database queries.");
    assertThat(config.defaultValue()).isEqualTo("true");
    assertThat(config.type()).isEqualTo(ConfigurationType.BOOLEAN);
  }

  @Test
  void testMetricsParsing() throws Exception {
    List<InstrumentationModule> modules = new ArrayList<>();
    Map<InstrumentationType, Set<String>> targetVersions = new HashMap<>();

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

    targetVersions.put(
        InstrumentationType.LIBRARY, new HashSet<>(List.of("org.apache.mylib:mylib-core:2.3.0")));
    modules.add(
        new InstrumentationModule.Builder()
            .srcPath("instrumentation/mylib/mylib-core-2.3")
            .instrumentationName("mylib-2.3")
            .namespace("mylib")
            .group("mylib")
            .targetVersions(targetVersions)
            .metrics(Map.of("default", List.of(metric)))
            .build());

    StringWriter stringWriter = new StringWriter();
    BufferedWriter writer = new BufferedWriter(stringWriter);

    YamlHelper.generateInstrumentationYaml(modules, writer);
    writer.flush();

    String expectedYaml =
        """
        libraries:
          mylib:
          - name: mylib-2.3
            source_path: instrumentation/mylib/mylib-core-2.3
            scope:
              name: io.opentelemetry.mylib-2.3
            target_versions:
              library:
              - org.apache.mylib:mylib-core:2.3.0
            telemetry:
            - when: default
              metrics:
              - name: db.client.operation.duration
                description: Duration of database client operations.
                type: HISTOGRAM
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
        """;

    assertThat(expectedYaml).isEqualTo(stringWriter.toString());
  }

  @Test
  void testSpanParsing() throws Exception {
    List<InstrumentationModule> modules = new ArrayList<>();
    Map<InstrumentationType, Set<String>> targetVersions = new HashMap<>();

    EmittedSpans.Span span =
        new EmittedSpans.Span(
            "CLIENT",
            List.of(
                new TelemetryAttribute("db.namespace", "STRING"),
                new TelemetryAttribute("db.operation.name", "STRING"),
                new TelemetryAttribute("db.system.name", "STRING"),
                new TelemetryAttribute("server.address", "STRING"),
                new TelemetryAttribute("server.port", "LONG")));

    targetVersions.put(
        InstrumentationType.LIBRARY, new HashSet<>(List.of("org.apache.mylib:mylib-core:2.3.0")));
    modules.add(
        new InstrumentationModule.Builder()
            .srcPath("instrumentation/mylib/mylib-core-2.3")
            .instrumentationName("mylib-2.3")
            .namespace("mylib")
            .group("mylib")
            .targetVersions(targetVersions)
            .spans(Map.of("default", List.of(span)))
            .build());

    StringWriter stringWriter = new StringWriter();
    BufferedWriter writer = new BufferedWriter(stringWriter);

    YamlHelper.generateInstrumentationYaml(modules, writer);
    writer.flush();

    String expectedYaml =
        """
        libraries:
          mylib:
          - name: mylib-2.3
            source_path: instrumentation/mylib/mylib-core-2.3
            scope:
              name: io.opentelemetry.mylib-2.3
            target_versions:
              library:
              - org.apache.mylib:mylib-core:2.3.0
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

    assertThat(expectedYaml).isEqualTo(stringWriter.toString());
  }
}
