/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.ignore;

import static io.opentelemetry.javaagent.tooling.ignore.UserExcludedClassesConfigurer.EXCLUDED_CLASSES_CONFIG;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.opentelemetry.javaagent.extension.ignore.IgnoredTypesBuilder;
import io.opentelemetry.javaagent.extension.ignore.IgnoredTypesConfigurer;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserExcludedClassesConfigurerTest {

  @Mock ConfigProperties config;
  @Mock IgnoredTypesBuilder builder;

  IgnoredTypesConfigurer underTest = new UserExcludedClassesConfigurer();

  @Test
  void shouldAddNothingToBuilderWhenPropertyIsEmpty() {
    // when
    underTest.configure(config, builder);

    // then
    verifyNoInteractions(builder);
  }

  @Test
  void shouldIgnoreClassesAndPackages() {
    // given
    // TODO: remove normalization after
    // https://github.com/open-telemetry/opentelemetry-java/issues/4562 is fixed
    when(config.getList(EXCLUDED_CLASSES_CONFIG.replace('-', '.'), emptyList()))
        .thenReturn(
            asList("com.example.IgnoredClass", "com.example.ignored.*", "com.another_ignore"));

    // when
    underTest.configure(config, builder);

    // then
    verify(builder).ignoreClass("com.example.IgnoredClass");
    verify(builder).ignoreClass("com.example.ignored.");
    verify(builder).ignoreClass("com.another_ignore");
  }
}
