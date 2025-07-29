/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.docs.internal.InstrumentationModule;
import io.opentelemetry.instrumentation.docs.internal.InstrumentationType;
import io.opentelemetry.instrumentation.docs.parsers.ModuleParser;
import io.opentelemetry.instrumentation.docs.utils.InstrumentationPath;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

@SuppressWarnings("NullAway")
class InstrumentationAnalyzerTest {

  @Test
  void testConvertToInstrumentationModule() {
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

    List<InstrumentationModule> modules = ModuleParser.convertToModules("test", paths);

    assertThat(modules.size()).isEqualTo(2);

    InstrumentationModule log4jModule =
        modules.stream()
            .filter(e -> e.getInstrumentationName().equals("log4j-appender-2.17"))
            .findFirst()
            .orElse(null);

    assertThat(log4jModule).isNotNull();
    assertThat(log4jModule.getNamespace()).isEqualTo("log4j");
    assertThat(log4jModule.getGroup()).isEqualTo("log4j");
    assertThat(log4jModule.getSrcPath()).isEqualTo("instrumentation/log4j/log4j-appender-2.17");
    assertThat(log4jModule.getScopeInfo().getName())
        .isEqualTo("io.opentelemetry.log4j-appender-2.17");

    InstrumentationModule springModule =
        modules.stream()
            .filter(e -> e.getInstrumentationName().equals("spring-web"))
            .findFirst()
            .orElse(null);

    assertThat(springModule).isNotNull();
    assertThat(springModule.getNamespace()).isEqualTo("spring");
    assertThat(springModule.getGroup()).isEqualTo("spring");
    assertThat(springModule.getSrcPath()).isEqualTo("instrumentation/spring/spring-web");
    assertThat(springModule.getScopeInfo().getName()).isEqualTo("io.opentelemetry.spring-web");
  }

  @Test
  void testModuleConverterCreatesUniqueModules() {
    List<InstrumentationPath> paths =
        Arrays.asList(
            new InstrumentationPath(
                "same-name",
                "instrumentation/test1/same-name/library",
                "namespace1",
                "group1",
                InstrumentationType.LIBRARY),
            new InstrumentationPath(
                "same-name",
                "instrumentation/test2/same-name/library",
                "namespace2",
                "group2",
                InstrumentationType.LIBRARY));

    List<InstrumentationModule> modules = ModuleParser.convertToModules("test", paths);

    // Should create 2 separate modules because they have different group/namespace combinations
    assertThat(modules.size()).isEqualTo(2);

    assertThat(
            modules.stream()
                .anyMatch(
                    m -> m.getGroup().equals("group1") && m.getNamespace().equals("namespace1")))
        .isTrue();
    assertThat(
            modules.stream()
                .anyMatch(
                    m -> m.getGroup().equals("group2") && m.getNamespace().equals("namespace2")))
        .isTrue();
  }
}
