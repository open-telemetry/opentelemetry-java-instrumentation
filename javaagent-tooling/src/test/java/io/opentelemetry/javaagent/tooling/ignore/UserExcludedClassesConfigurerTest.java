/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.ignore;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import io.opentelemetry.javaagent.extension.ignore.IgnoredTypesBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserExcludedClassesConfigurerTest {

  @Mock IgnoredTypesBuilder builder;

  UserExcludedClassesConfigurer underTest = new UserExcludedClassesConfigurer();

  @Test
  void shouldAddNothingToBuilderWhenPropertyIsEmpty() {
    // when
    underTest.configureInternal(builder, emptyList());

    // then
    verifyNoInteractions(builder);
  }

  @Test
  void shouldIgnoreClassesAndPackages() {
    // when
    underTest.configureInternal(
        builder, asList("com.example.IgnoredClass", "com.example.ignored.*", "com.another_ignore"));

    // then
    verify(builder).ignoreClass("com.example.IgnoredClass");
    verify(builder).ignoreClass("com.example.ignored.");
    verify(builder).ignoreClass("com.another_ignore");
  }
}
