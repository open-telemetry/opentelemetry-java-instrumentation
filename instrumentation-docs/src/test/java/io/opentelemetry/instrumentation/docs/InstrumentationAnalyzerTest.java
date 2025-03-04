/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.docs.utils.InstrumentationPath;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class InstrumentationAnalyzerTest {

  @Test
  void testConvertToEntities() {
    List<InstrumentationPath> paths =
        Arrays.asList(
            new InstrumentationPath(
                "log4j-appender-2.17",
                "instrumentation/log4j/log4j-appender-2.17/library",
                "log4j",
                "log4j",
                InstrumentationType.LIBRARY),
            new InstrumentationPath(
                "log4j-appender-2.17",
                "instrumentation/log4j/log4j-appender-2.17/javaagent",
                "log4j",
                "log4j",
                InstrumentationType.JAVAAGENT),
            new InstrumentationPath(
                "spring-web",
                "instrumentation/spring/spring-web/library",
                "spring",
                "spring",
                InstrumentationType.LIBRARY));

    List<InstrumentationEntity> entities = InstrumentationAnalyzer.convertToEntities(paths);

    assertThat(entities.size()).isEqualTo(2);

    InstrumentationEntity log4jEntity =
        entities.stream()
            .filter(e -> e.getInstrumentationName().equals("log4j-appender-2.17"))
            .findFirst()
            .orElse(null);

    assertThat(log4jEntity.getNamespace()).isEqualTo("log4j");
    assertThat(log4jEntity.getGroup()).isEqualTo("log4j");
    assertThat(log4jEntity.getSrcPath()).isEqualTo("instrumentation/log4j/log4j-appender-2.17");
    assertThat(log4jEntity.getTypes()).hasSize(2);
    assertThat(log4jEntity.getTypes())
        .containsExactly(InstrumentationType.LIBRARY, InstrumentationType.JAVAAGENT);

    InstrumentationEntity springEntity =
        entities.stream()
            .filter(e -> e.getInstrumentationName().equals("spring-web"))
            .findFirst()
            .orElse(null);

    assertThat(springEntity).isNotNull();
    assertThat(springEntity.getNamespace()).isEqualTo("spring");
    assertThat(springEntity.getGroup()).isEqualTo("spring");
    assertThat(springEntity.getSrcPath()).isEqualTo("instrumentation/spring/spring-web");
    assertThat(springEntity.getTypes()).hasSize(1);
    assertThat(springEntity.getTypes()).containsExactly(InstrumentationType.LIBRARY);
  }
}
