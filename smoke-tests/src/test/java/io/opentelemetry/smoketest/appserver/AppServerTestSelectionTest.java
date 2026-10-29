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
  void partitionsPreserveEachServersEffectiveMatrix(boolean windows, boolean skipOpenJ9) {
    assertThat(APP_SERVERS).isNotEmpty();
    Map<Class<?>, List<Class<?>>> suites =
        APP_SERVERS.stream().collect(groupingBy(Class::getEnclosingClass));
    for (List<Class<?>> suite : suites.values()) {
      List<Class<?>> effective =
          suite.stream()
              .filter(type -> !(skipOpenJ9 && type.getName().contains("Openj9")))
              .collect(toList());
      List<Class<?>> full = select(effective, "all", windows);
      List<Class<?>> limitedMatrix = select(effective, "limitedMatrix", windows);
      List<Class<?>> additionalVariants = select(effective, "additionalVariants", windows);

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
    assertThat(select(tomee, "limitedMatrix", false))
        .isNotEmpty()
        .allMatch(type -> type.getAnnotation(AppServer.class).jdk().endsWith("-openj9"));
    assertThat(select(tomee, "limitedMatrix", true)).isEmpty();
    assertThat(select(tomee, "additionalVariants", true))
        .isNotEmpty()
        .containsExactlyInAnyOrderElementsOf(select(tomee, "all", true));
  }

  @Test
  void emptyAdditionalVariantsGroupDoesNotSelectFullSuite() {
    List<Class<?>> servletOnly =
        APP_SERVERS.stream()
            .filter(LibertyServletOnlySmokeTest.class::isAssignableFrom)
            .collect(toList());
    assertThat(servletOnly).isNotEmpty();
    assertThat(select(servletOnly, "additionalVariants", false)).isEmpty();
    assertThat(select(servletOnly, "limitedMatrix", false))
        .containsExactlyInAnyOrderElementsOf(servletOnly);
  }

  @Test
  void unknownGroupFails() {
    assertThatThrownBy(
            () ->
                AppServerTestSelection.includes(
                    TomeeSmokeTest.Tomee8Jdk11.class.getAnnotation(AppServer.class),
                    "unknown",
                    false))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Unknown app-server test group: unknown");
  }

  private static List<Class<?>> select(List<Class<?>> classes, String group, boolean windows) {
    return classes.stream()
        .filter(
            type ->
                AppServerTestSelection.includes(
                    type.getAnnotation(AppServer.class), group, windows))
        .collect(toList());
  }
}
