/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.opentelemetry.instrumentation.docs.internal.EmittedScope;
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
        new InstrumentationMetaData("Spring Web 6.0 instrumentation");

    entities.add(
        new InstrumentationEntity.Builder()
            .srcPath("instrumentation/spring/spring-web/spring-web-6.0")
            .instrumentationName("spring-web-6.0")
            .namespace("spring")
            .group("spring")
            .targetVersions(targetVersions1)
            .metadata(metadata1)
            .build());

    Map<InstrumentationType, Set<String>> targetVersions2 = new HashMap<>();

    EmittedScope.Scope scope = new EmittedScope.Scope("struts-2.3", "2.14-ALPHA", null, null);

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
            .scope(scope)
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
            target_versions:
              javaagent:
              - org.springframework:spring-web:[6.0.0,)
        struts:
          instrumentations:
          - name: struts-2.3
            srcPath: instrumentation/struts/struts-2.3
            scope:
              name: struts-2.3
              version: 2.14-ALPHA
              schemaUrl: null
            target_versions:
              library:
              - org.apache.struts:struts2-core:2.1.0
        """;

    assertThat(expectedYaml).isEqualTo(stringWriter.toString());
  }

  @Test
  public void testEmittedScopeParser() {
    String yamlContent =
        """
        scope:
          name: io.opentelemetry.alibaba-druid-1.0
          version: 2.14.0-alpha-SNAPSHOT
          schemaUrl: null
          attributes:
        """;

    EmittedScope emittedScope = YamlHelper.emittedScopeParser(yamlContent);

    assertNotNull(emittedScope.getScope());
    assertThat(emittedScope.getScope().getName()).isEqualTo("io.opentelemetry.alibaba-druid-1.0");
    assertThat(emittedScope.getScope().getVersion()).isEqualTo("2.14.0-alpha-SNAPSHOT");
    assertThat(emittedScope.getScope().getSchemaUrl()).isNull();
  }
}
