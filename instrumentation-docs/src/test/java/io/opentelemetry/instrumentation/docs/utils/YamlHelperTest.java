/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs.utils;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.docs.internal.EmittedMetrics;
import io.opentelemetry.instrumentation.docs.internal.EmittedSpans;
import io.opentelemetry.instrumentation.docs.internal.InstrumentationEntity;
import io.opentelemetry.instrumentation.docs.internal.InstrumentationMetaData;
import io.opentelemetry.instrumentation.docs.internal.InstrumentationType;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
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

    List<EmittedSpans.EmittedSpanAttribute> spanAttributes =
        List.of(
            new EmittedSpans.EmittedSpanAttribute("db.namespace", "STRING"),
            new EmittedSpans.EmittedSpanAttribute("server.port", "LONG"));

    entities.add(
        new InstrumentationEntity.Builder()
            .srcPath("instrumentation/spring/spring-web/spring-web-6.0")
            .instrumentationName("spring-web-6.0")
            .namespace("spring")
            .group("spring")
            .targetVersions(targetVersions1)
            .metadata(metadata1)
            .spanAttributes(spanAttributes)
            .spanKinds(List.of("SERVER"))
            .build());

    Map<InstrumentationType, Set<String>> targetVersions2 = new HashMap<>();
    InstrumentationScopeInfo scope =
        InstrumentationScopeInfo.builder("struts-2.3").setVersion("2.14-ALPHA").build();

    EmittedMetrics.Metric metric =
        new EmittedMetrics.Metric(
            "db.client.operation.duration",
            "Duration of database client operations.",
            "HISTOGRAM",
            "s",
            List.of(
                new EmittedMetrics.Attribute("db.namespace", "STRING"),
                new EmittedMetrics.Attribute("db.operation.name", "STRING"),
                new EmittedMetrics.Attribute("db.system.name", "STRING"),
                new EmittedMetrics.Attribute("server.address", "STRING"),
                new EmittedMetrics.Attribute("server.port", "LONG")));

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
            .metrics(List.of(metric))
            .spanKinds(List.of("SERVER", "CLIENT"))
            .build());

    StringWriter stringWriter = new StringWriter();
    BufferedWriter writer = new BufferedWriter(stringWriter);

    YamlHelper.printInstrumentationList(entities, writer);
    writer.flush();

    String expectedYaml =
        "spring:\n"
            + "  instrumentations:\n"
            + "  - name: spring-web-6.0\n"
            + "    description: Spring Web 6.0 instrumentation\n"
            + "    srcPath: instrumentation/spring/spring-web/spring-web-6.0\n"
            + "    target_versions:\n"
            + "      javaagent:\n"
            + "      - org.springframework:spring-web:[6.0.0,)\n"
            + "    span_data:\n"
            + "      span_kinds:\n"
            + "      - SERVER\n"
            + "      attributes:\n"
            + "      - name: db.namespace\n"
            + "        type: STRING\n"
            + "      - name: server.port\n"
            + "        type: LONG\n"
            + "struts:\n"
            + "  instrumentations:\n"
            + "  - name: struts-2.3\n"
            + "    srcPath: instrumentation/struts/struts-2.3\n"
            + "    scope:\n"
            + "      name: struts-2.3\n"
            + "      version: 2.14-ALPHA\n"
            + "      schemaUrl: null\n"
            + "      attributes: {}\n"
            + "    target_versions:\n"
            + "      library:\n"
            + "      - org.apache.struts:struts2-core:2.1.0\n"
            + "    metrics:\n"
            + "    - name: db.client.operation.duration\n"
            + "      description: Duration of database client operations.\n"
            + "      type: HISTOGRAM\n"
            + "      unit: s\n"
            + "      attributes:\n"
            + "      - name: db.namespace\n"
            + "        type: STRING\n"
            + "      - name: db.operation.name\n"
            + "        type: STRING\n"
            + "      - name: db.system.name\n"
            + "        type: STRING\n"
            + "      - name: server.address\n"
            + "        type: STRING\n"
            + "      - name: server.port\n"
            + "        type: LONG\n"
            + "    span_data:\n"
            + "      span_kinds:\n"
            + "      - SERVER\n"
            + "      - CLIENT\n";

    assertThat(expectedYaml).isEqualTo(stringWriter.toString());
  }
}
