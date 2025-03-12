/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs.utils;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.docs.InstrumentationEntity;
import io.opentelemetry.instrumentation.docs.InstrumentationMetaData;
import io.opentelemetry.instrumentation.docs.InstrumentationType;
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
        new InstrumentationEntity(
            "instrumentation/spring/spring-web/spring-web-6.0",
            "spring-web-6.0",
            "spring",
            "spring",
            targetVersions1,
            metadata1));

    Map<InstrumentationType, Set<String>> targetVersions2 = new HashMap<>();
    targetVersions2.put(
        InstrumentationType.LIBRARY,
        new HashSet<>(List.of("org.apache.struts:struts2-core:2.1.0")));
    entities.add(
        new InstrumentationEntity(
            "instrumentation/struts/struts-2.3",
            "struts-2.3",
            "struts",
            "struts",
            targetVersions2,
            null));

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
            + "struts:\n"
            + "  instrumentations:\n"
            + "  - name: struts-2.3\n"
            + "    srcPath: instrumentation/struts/struts-2.3\n"
            + "    target_versions:\n"
            + "      library:\n"
            + "      - org.apache.struts:struts2-core:2.1.0\n";

    assertThat(expectedYaml).isEqualTo(stringWriter.toString());
  }
}
