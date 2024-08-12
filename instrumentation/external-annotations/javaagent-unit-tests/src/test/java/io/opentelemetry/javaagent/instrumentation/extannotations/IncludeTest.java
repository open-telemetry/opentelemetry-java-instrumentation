/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.extannotations;

import static io.opentelemetry.javaagent.instrumentation.extannotations.ExternalAnnotationInstrumentation.DEFAULT_ANNOTATIONS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.opentelemetry.instrumentation.api.incubator.config.internal.InstrumentationConfig;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IncludeTest {

  @Mock InstrumentationConfig config;

  @ParameterizedTest
  @MethodSource("provideArguments")
  void testConfiguration(String value, List<String> expected) {
    when(config.getString("otel.instrumentation.external-annotations.include")).thenReturn(value);

    assertThat(ExternalAnnotationInstrumentation.configureAdditionalTraceAnnotations(config))
        .isEqualTo(new HashSet<>(expected));
  }

  private static Stream<Arguments> provideArguments() {
    return Stream.of(
        Arguments.of(null, DEFAULT_ANNOTATIONS),
        Arguments.of(" ", Collections.emptyList()),
        Arguments.of("some.Invalid[]", Collections.emptyList()),
        Arguments.of(
            "some.package.ClassName ", Collections.singletonList("some.package.ClassName")),
        Arguments.of(
            " some.package.Class$Name", Collections.singletonList("some.package.Class$Name")),
        Arguments.of("  ClassName  ", Collections.singletonList("ClassName")),
        Arguments.of("ClassName", Collections.singletonList("ClassName")),
        Arguments.of("Class$1;Class$2", Arrays.asList("Class$1", "Class$2")),
        Arguments.of("Duplicate ;Duplicate ;Duplicate; ", Collections.singletonList("Duplicate")));
  }
}
