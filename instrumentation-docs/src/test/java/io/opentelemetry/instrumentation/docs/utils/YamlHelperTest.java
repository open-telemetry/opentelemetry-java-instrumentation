/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs.utils;

import static org.assertj.core.api.Assertions.assertThat;

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

    InstrumentationMetaData metadata1 =
        new InstrumentationMetaData("Spring Web 6.0 instrumentation", true, true);

    entities.add(
        new InstrumentationEntity.Builder()
            .srcPath("instrumentation/spring/spring-web/spring-web-6.0")
            .instrumentationName("spring-web-6.0")
            .namespace("spring")
            .group("spring")
            .targetVersions(targetVersions1)
            .metadata(metadata1)
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

    YamlHelper.printInstrumentationList(entities, writer);
    writer.flush();

    String expectedYaml =
        """
            spring:
              instrumentations:
              - name: spring-web-6.0
                description: Spring Web 6.0 instrumentation
                disabledByDefault: true
                srcPath: instrumentation/spring/spring-web/spring-web-6.0
                minimumJavaVersion: 11
                scope:
                  name: io.opentelemetry.spring-web-6.0
                target_versions:
                  javaagent:
                  - org.springframework:spring-web:[6.0.0,)
            struts:
              instrumentations:
              - name: struts-2.3
                srcPath: instrumentation/struts/struts-2.3
                scope:
                  name: io.opentelemetry.struts-2.3
                target_versions:
                  library:
                  - org.apache.struts:struts2-core:2.1.0
            """;

    assertThat(expectedYaml).isEqualTo(stringWriter.toString());
  }

  @Test
  void testPrintInstrumentationListIgnoresNonLibraryInstrumentation() throws Exception {
    List<InstrumentationEntity> entities = new ArrayList<>();
    Map<InstrumentationType, Set<String>> targetVersions1 = new HashMap<>();
    targetVersions1.put(
        InstrumentationType.JAVAAGENT,
        new HashSet<>(List.of("org.springframework:spring-web:[6.0.0,)")));

    InstrumentationMetaData metadata1 =
        new InstrumentationMetaData("Spring Web 6.0 instrumentation");

    entities.add(
        new InstrumentationEntity.Builder()
            .srcPath("instrumentation/spring/spring-web/spring-web-6.0")
            .instrumentationName("spring-web-6.0")
            .namespace("spring")
            .group("spring")
            .targetVersions(targetVersions1)
            .metadata(metadata1)
            .minJavaVersion(11)
            .build());

    InstrumentationMetaData metadata2 = new InstrumentationMetaData(null, false, null);

    entities.add(
        new InstrumentationEntity.Builder()
            .srcPath("instrumentation/internal/internal-application-logger")
            .instrumentationName("internal-application-logger")
            .namespace("internal")
            .group("internal")
            .metadata(metadata2)
            .targetVersions(new HashMap<>())
            .build());

    StringWriter stringWriter = new StringWriter();
    BufferedWriter writer = new BufferedWriter(stringWriter);

    YamlHelper.printInstrumentationList(entities, writer);
    writer.flush();

    String expectedYaml =
        """
            spring:
              instrumentations:
              - name: spring-web-6.0
                description: Spring Web 6.0 instrumentation
                srcPath: instrumentation/spring/spring-web/spring-web-6.0
                minimumJavaVersion: 11
                scope:
                  name: io.opentelemetry.spring-web-6.0
                target_versions:
                  javaagent:
                  - org.springframework:spring-web:[6.0.0,)
            """;

    assertThat(expectedYaml).isEqualTo(stringWriter.toString());
  }

  @Test
  void testMetadataParser() {
    String input =
        """
        description: test description
        isLibraryInstrumentation: false
        disabledByDefault: true
        """;

    InstrumentationMetaData metadata = YamlHelper.metaDataParser(input);
    assertThat(metadata.getIsLibraryInstrumentation()).isFalse();
    assertThat(metadata.getDescription()).isEqualTo("test description");
    assertThat(metadata.getDisabledByDefault()).isEqualTo(true);
  }

  @Test
  void testMetadataParserWithOnlyLibraryEntry() {
    String input = "isLibraryInstrumentation: false";
    InstrumentationMetaData metadata = YamlHelper.metaDataParser(input);
    assertThat(metadata.getIsLibraryInstrumentation()).isFalse();
    assertThat(metadata.getDescription()).isNull();
    assertThat(metadata.getDisabledByDefault()).isFalse();
  }

  @Test
  void testMetadataParserWithOnlyDescription() {
    String input = "description: false";
    InstrumentationMetaData metadata = YamlHelper.metaDataParser(input);
    assertThat(metadata.getIsLibraryInstrumentation()).isTrue();
    assertThat(metadata.getDisabledByDefault()).isFalse();
  }

  @Test
  void testMetadataParserWithOnlyDisabledByDefault() {
    String input = "disabledByDefault: true";
    InstrumentationMetaData metadata = YamlHelper.metaDataParser(input);
    assertThat(metadata.getIsLibraryInstrumentation()).isTrue();
    assertThat(metadata.getDescription()).isNull();
    assertThat(metadata.getDisabledByDefault()).isTrue();
  }
}
