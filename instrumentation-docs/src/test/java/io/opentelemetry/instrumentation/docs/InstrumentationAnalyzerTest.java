/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.docs.internal.EmittedSpans;
import io.opentelemetry.instrumentation.docs.internal.InstrumentationModule;
import io.opentelemetry.instrumentation.docs.internal.InstrumentationType;
import io.opentelemetry.instrumentation.docs.internal.TelemetryAttribute;
import io.opentelemetry.instrumentation.docs.utils.InstrumentationPath;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
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

    List<InstrumentationModule> modules =
        InstrumentationAnalyzer.convertToInstrumentationModules("test", paths);

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
  void testFilterSpansByScopeFiltersCorrectly() {
    InstrumentationAnalyzer analyzer = new InstrumentationAnalyzer(null);
    String scopeName = "my-instrumentation-scope";
    EmittedSpans.Span span1 =
        new EmittedSpans.Span("CLIENT", List.of(new TelemetryAttribute("my.operation", "STRING")));
    EmittedSpans.Span span2 =
        new EmittedSpans.Span("SERVER", List.of(new TelemetryAttribute("my.operation", "STRING")));

    EmittedSpans.Span testSpan =
        new EmittedSpans.Span(
            "INTERNAL", List.of(new TelemetryAttribute("my.operation", "STRING")));

    EmittedSpans.SpansByScope spansByScope =
        new EmittedSpans.SpansByScope(scopeName, List.of(span1, span2));
    EmittedSpans.SpansByScope spansByScope2 =
        new EmittedSpans.SpansByScope("test", List.of(testSpan));
    Map<String, EmittedSpans> spans =
        Map.of(
            scopeName,
            new EmittedSpans("default", List.of(spansByScope)),
            "test",
            new EmittedSpans("default", List.of(spansByScope2)));

    InstrumentationModule module =
        new InstrumentationModule.Builder()
            .instrumentationName(scopeName)
            .group("test-group")
            .namespace("test")
            .srcPath("test/")
            .build();

    analyzer.filterSpansByScope(spans, module);

    Map<String, List<EmittedSpans.Span>> filtered = module.getSpans();
    assertThat(filtered.size()).isEqualTo(1);

    // filters out the "test" scope
    assertThat(filtered.get("default").size()).isEqualTo(2);
  }
}
