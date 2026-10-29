/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest.appserver;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Stream.concat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.platform.commons.support.ReflectionSupport;

class AppServerTestSelectionTest {
  private static final List<Class<?>> APP_SERVERS =
      ReflectionSupport.findAllClassesInPackage(
          AppServer.class.getPackageName(),
          type ->
              AppServerTest.class.isAssignableFrom(type)
                  && !Modifier.isAbstract(type.getModifiers())
                  && type.isAnnotationPresent(AppServer.class),
          name -> true);

  @ParameterizedTest
  @CsvSource({"false,false", "false,true", "true,false", "true,true"})
  void limitedAndAdditionalVariantsPartitionEachServersEffectiveMatrix(
      boolean windows, boolean skipOpenJ9) {
    assertThat(APP_SERVERS).isNotEmpty();
    Map<Class<?>, List<Class<?>>> suites =
        APP_SERVERS.stream().collect(groupingBy(Class::getEnclosingClass));
    for (List<Class<?>> suite : suites.values()) {
      List<Class<?>> effective =
          suite.stream()
              .filter(type -> !(skipOpenJ9 && type.getName().contains("Openj9")))
              .collect(toList());
      List<Class<?>> full = select(effective, null, windows);
      List<Class<?>> limitedMatrix = select(effective, true, windows);
      List<Class<?>> additionalVariants = select(effective, false, windows);

      assertThat(limitedMatrix).noneMatch(additionalVariants::contains);
      assertThat(full)
          .containsExactlyInAnyOrderElementsOf(
              effective.stream()
                  .filter(
                      type ->
                          !windows
                              || !type.getAnnotation(AppServer.class).jdk().endsWith("-openj9"))
                  .collect(toList()));
      assertThat(full)
          .containsExactlyInAnyOrderElementsOf(
              concat(limitedMatrix.stream(), additionalVariants.stream()).collect(toList()));
      assertThat(limitedMatrix)
          .allMatch(type -> type.getAnnotation(AppServer.class).inLimitedMatrix());
      assertThat(additionalVariants)
          .noneMatch(type -> type.getAnnotation(AppServer.class).inLimitedMatrix());
    }
  }

  @Test
  void tomeeLimitedMatrixRequiresOpenJ9AndIsEmptyOnWindows() {
    List<Class<?>> tomee =
        APP_SERVERS.stream().filter(TomeeSmokeTest.class::isAssignableFrom).collect(toList());
    assertThat(select(tomee, true, false))
        .isNotEmpty()
        .allMatch(type -> type.getAnnotation(AppServer.class).jdk().endsWith("-openj9"));
    assertThat(select(tomee, true, true)).isEmpty();
    assertThat(select(tomee, false, true))
        .isNotEmpty()
        .containsExactlyInAnyOrderElementsOf(select(tomee, null, true));
  }

  @Test
  void emptyAdditionalVariantsPartitionDoesNotSelectFullSuite() {
    List<Class<?>> servletOnly =
        APP_SERVERS.stream()
            .filter(LibertyServletOnlySmokeTest.class::isAssignableFrom)
            .collect(toList());
    assertThat(servletOnly).isNotEmpty();
    assertThat(select(servletOnly, false, false)).isEmpty();
    assertThat(select(servletOnly, true, false)).containsExactlyInAnyOrderElementsOf(servletOnly);
  }

  @ParameterizedTest
  @CsvSource({"true,true", "false,false", "TRUE,true", "FALSE,false"})
  void parsesLimitedMatrixProperty(String value, boolean expected) {
    assertThat(AppServerTest.parseInLimitedMatrix(value)).isEqualTo(expected);
  }

  @Test
  void missingLimitedMatrixPropertyDoesNotFilter() {
    assertThat(AppServerTest.parseInLimitedMatrix(null)).isNull();
    assertThat(
            AppServerTest.includes(
                TomeeSmokeTest.Tomee8Jdk11.class.getAnnotation(AppServer.class), null, false))
        .isTrue();
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "yes", "1"})
  void malformedLimitedMatrixPropertyFails(String value) {
    assertThatThrownBy(() -> AppServerTest.parseInLimitedMatrix(value))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid value for inLimitedMatrix: " + value);
  }

  private static List<Class<?>> select(
      List<Class<?>> classes, Boolean inLimitedMatrix, boolean windows) {
    return classes.stream()
        .filter(
            type ->
                AppServerTest.includes(
                    type.getAnnotation(AppServer.class), inLimitedMatrix, windows))
        .collect(toList());
  }
}
