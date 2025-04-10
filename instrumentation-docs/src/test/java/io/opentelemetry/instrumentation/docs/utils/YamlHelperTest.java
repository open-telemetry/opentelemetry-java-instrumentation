/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs.utils;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.docs.internal.InstrumentationClassification;
import io.opentelemetry.instrumentation.docs.internal.InstrumentationEntity;
import io.opentelemetry.instrumentation.docs.internal.InstrumentationMetaData;
import io.opentelemetry.instrumentation.docs.internal.InstrumentationType;
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
    List<InstrumentationEntity> entities = new ArrayList<>();
    Map<InstrumentationType, Set<String>> targetVersions1 = new HashMap<>();
    targetVersions1.put(
        InstrumentationType.JAVAAGENT,
        new HashSet<>(List.of("org.springframework:spring-web:[6.0.0,)")));

    InstrumentationMetaData springMetadata =
        new InstrumentationMetaData(
            "Spring Web 6.0 instrumentation",
            InstrumentationClassification.LIBRARY.toString(),
            true);

    entities.add(
        new InstrumentationEntity.Builder()
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
    entities.add(
        new InstrumentationEntity.Builder()
            .srcPath("instrumentation/struts/struts-2.3")
            .instrumentationName("struts-2.3")
            .namespace("struts")
            .group("struts")
            .targetVersions(targetVersions2)
            .build());

    StringWriter stringWriter = new StringWriter();
    BufferedWriter writer = new BufferedWriter(stringWriter);

    YamlHelper.generateInstrumentationYaml(entities, writer);
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
    List<InstrumentationEntity> entities = new ArrayList<>();
    Map<InstrumentationType, Set<String>> springTargetVersions = new HashMap<>();
    springTargetVersions.put(
        InstrumentationType.JAVAAGENT,
        new HashSet<>(List.of("org.springframework:spring-web:[6.0.0,)")));

    InstrumentationMetaData springMetadata =
        new InstrumentationMetaData(
            "Spring Web 6.0 instrumentation",
            InstrumentationClassification.LIBRARY.toString(),
            false);

    entities.add(
        new InstrumentationEntity.Builder()
            .srcPath("instrumentation/spring/spring-web/spring-web-6.0")
            .instrumentationName("spring-web-6.0")
            .namespace("spring")
            .group("spring")
            .targetVersions(springTargetVersions)
            .metadata(springMetadata)
            .minJavaVersion(11)
            .build());

    InstrumentationMetaData internalMetadata =
        new InstrumentationMetaData(null, InstrumentationClassification.INTERNAL.toString(), null);

    entities.add(
        new InstrumentationEntity.Builder()
            .srcPath("instrumentation/internal/internal-application-logger")
            .instrumentationName("internal-application-logger")
            .namespace("internal")
            .group("internal")
            .metadata(internalMetadata)
            .targetVersions(new HashMap<>())
            .build());

    InstrumentationMetaData customMetadata =
        new InstrumentationMetaData(null, InstrumentationClassification.CUSTOM.toString(), null);

    entities.add(
        new InstrumentationEntity.Builder()
            .srcPath("instrumentation/opentelemetry-external-annotations-1.0")
            .instrumentationName("opentelemetry-external-annotations")
            .namespace("opentelemetry-external-annotations")
            .group("opentelemetry-external-annotations")
            .metadata(customMetadata)
            .targetVersions(new HashMap<>())
            .build());

    StringWriter stringWriter = new StringWriter();
    BufferedWriter writer = new BufferedWriter(stringWriter);

    YamlHelper.generateInstrumentationYaml(entities, writer);
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
            """;

    assertThat(expectedYaml).isEqualTo(stringWriter.toString());
  }

  @Test
  void testMetadataParser() {
    String input =
        """
            description: test description
            classification: internal
            disabled_by_default: true
            """;

    InstrumentationMetaData metadata = YamlHelper.metaDataParser(input);
    assertThat(metadata.getClassification()).isEqualTo(InstrumentationClassification.INTERNAL);
    assertThat(metadata.getDescription()).isEqualTo("test description");
    assertThat(metadata.getDisabledByDefault()).isEqualTo(true);
  }

  @Test
  void testMetadataParserWithOnlyLibraryEntry() {
    String input = "classification: internal";
    InstrumentationMetaData metadata = YamlHelper.metaDataParser(input);
    assertThat(metadata.getClassification()).isEqualTo(InstrumentationClassification.INTERNAL);
    assertThat(metadata.getDescription()).isNull();
    assertThat(metadata.getDisabledByDefault()).isFalse();
  }

  @Test
  void testMetadataParserWithOnlyDescription() {
    String input = "description: false";
    InstrumentationMetaData metadata = YamlHelper.metaDataParser(input);
    assertThat(metadata.getClassification()).isEqualTo(InstrumentationClassification.LIBRARY);
    assertThat(metadata.getDisabledByDefault()).isFalse();
  }

  @Test
  void testMetadataParserWithOnlyDisabledByDefault() {
    String input = "disabled_by_default: true";
    InstrumentationMetaData metadata = YamlHelper.metaDataParser(input);
    assertThat(metadata.getClassification()).isEqualTo(InstrumentationClassification.LIBRARY);
    assertThat(metadata.getDescription()).isNull();
    assertThat(metadata.getDisabledByDefault()).isTrue();
  }
}
