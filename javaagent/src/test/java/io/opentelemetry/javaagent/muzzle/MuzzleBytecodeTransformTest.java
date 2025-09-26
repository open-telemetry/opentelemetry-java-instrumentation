/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.muzzle;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.javaagent.IntegrationTestUtils;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import org.junit.jupiter.api.Test;

class MuzzleBytecodeTransformTest {

  @Test
  void muzzleFieldsAddedToAllInstrumentation() throws Exception {
    List<Class<?>> unMuzzledClasses = new ArrayList<>();
    List<Class<?>> nonLazyFields = new ArrayList<>();
    List<Class<?>> unInitFields = new ArrayList<>();

    Class<?> instrumentationModuleClass =
        IntegrationTestUtils.getAgentClassLoader()
            .loadClass("io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule");

    ServiceLoader<?> serviceLoader = ServiceLoader.load(instrumentationModuleClass);
    for (Object instrumenter : serviceLoader) {
      if (!instrumentationModuleClass.isAssignableFrom(instrumenter.getClass())) {
        // muzzle only applies to default instrumenters
        continue;
      }
      Field field = null;
      Method method = null;
      try {
        field = instrumenter.getClass().getDeclaredField("muzzleReferences");
        field.setAccessible(true);
        if (field.get(instrumenter) != null) {
          nonLazyFields.add(instrumenter.getClass());
        }
        method = instrumenter.getClass().getDeclaredMethod("getMuzzleReferences");
        method.setAccessible(true);
        method.invoke(instrumenter);
        if (field.get(instrumenter) == null) {
          unInitFields.add(instrumenter.getClass());
        }
      } catch (NoSuchFieldException | NoSuchMethodException e) {
        unMuzzledClasses.add(instrumenter.getClass());
      } finally {
        if (field != null) {
          field.setAccessible(false);
        }
        if (method != null) {
          method.setAccessible(false);
        }
      }
    }

    assertThat(unMuzzledClasses).isEmpty();
    assertThat(nonLazyFields).isEmpty();
    assertThat(unInitFields).isEmpty();
  }
}