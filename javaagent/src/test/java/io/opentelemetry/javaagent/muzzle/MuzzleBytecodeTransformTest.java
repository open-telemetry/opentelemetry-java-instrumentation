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

    Class<?> instrumentationModuleClass = IntegrationTestUtils.getAgentClassLoader()
        .loadClass("io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule");

    for (Object instrumenter : ServiceLoader.load(instrumentationModuleClass)) {
      if (!instrumentationModuleClass.isAssignableFrom(instrumenter.getClass())) {
        // muzzle only applies to default instrumenters
        continue;
      }

      Field f = null;
      Method m = null;
      try {
        f = instrumenter.getClass().getDeclaredField("muzzleReferences");
        f.setAccessible(true);
        if (f.get(instrumenter) != null) {
          nonLazyFields.add(instrumenter.getClass());
        }

        m = instrumenter.getClass().getDeclaredMethod("getMuzzleReferences");
        m.setAccessible(true);
        m.invoke(instrumenter);

        if (f.get(instrumenter) == null) {
          unInitFields.add(instrumenter.getClass());
        }
      } catch (NoSuchFieldException | NoSuchMethodException e) {
        unMuzzledClasses.add(instrumenter.getClass());
      } finally {
        if (f != null) {
          f.setAccessible(false);
        }
        if (m != null) {
          m.setAccessible(false);
        }
      }
    }

    assertThat(unMuzzledClasses).isEmpty();
    assertThat(nonLazyFields).isEmpty();
    assertThat(unInitFields).isEmpty();
  }
}
