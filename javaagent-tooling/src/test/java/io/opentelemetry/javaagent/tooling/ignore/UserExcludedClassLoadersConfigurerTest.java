/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.ignore;

import static io.opentelemetry.javaagent.tooling.ignore.UserExcludedClassLoadersConfigurer.EXCLUDED_CLASS_LOADERS_CONFIG;
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
class UserExcludedClassLoadersConfigurerTest {

  @Mock ConfigProperties config;
  @Mock IgnoredTypesBuilder builder;

  IgnoredTypesConfigurer underTest = new UserExcludedClassLoadersConfigurer();

  @Test
  void shouldAddNothingToBuilderWhenPropertyIsEmpty() {
    // when
    underTest.configure(builder, config);

    // then
    verifyNoInteractions(builder);
  }

  @Test
  void shouldIgnoreClassesAndPackages() {
    // given
    when(config.getList(EXCLUDED_CLASS_LOADERS_CONFIG, emptyList()))
        .thenReturn(
            asList("com.example.IgnoredClass", "com.example.ignored.*", "com.another_ignore"));

    // when
    underTest.configure(builder, config);

    // then
    verify(builder).ignoreClassLoader("com.example.IgnoredClass");
    verify(builder).ignoreClassLoader("com.example.ignored.");
    verify(builder).ignoreClassLoader("com.another_ignore");
  }
}
