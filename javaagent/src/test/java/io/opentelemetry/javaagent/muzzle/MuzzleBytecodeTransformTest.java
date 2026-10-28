/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.muzzle;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.javaagent.IntegrationTestUtils;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

class MuzzleBytecodeTransformTest {

  @Test
  void builtInAdviceModes() throws Exception {
    ClassLoader agentClassLoader = IntegrationTestUtils.getAgentClassLoader();
    Class<?> instrumentationModuleClass =
        agentClassLoader.loadClass(
            "io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule");
    Class<?> inspectorClass =
        agentClassLoader.loadClass("io.opentelemetry.javaagent.tooling.muzzle.AdviceInspector");
    Constructor<?> constructor = inspectorClass.getConstructors()[0];
    // Resolve Byte Buddy through the constructor to account for agent package relocation.
    Class<?> locatorClass = constructor.getParameterTypes()[0];
    Object locator =
        agentClassLoader
            .loadClass(locatorClass.getName() + "$ForClassLoader")
            .getMethod("of", ClassLoader.class)
            .invoke(null, agentClassLoader);
    Object inspector = constructor.newInstance(locator);
    Method inspect = inspectorClass.getMethod("inspect", instrumentationModuleClass);
    Class<?> safeServiceLoaderClass =
        agentClassLoader.loadClass("io.opentelemetry.javaagent.tooling.SafeServiceLoader");
    Method safeServiceLoaderLoad =
        safeServiceLoaderClass.getMethod("load", Class.class, ClassLoader.class);
    Set<String> mixedModules = new TreeSet<>();
    Set<String> allInlineModules = new TreeSet<>();
    int moduleCount = 0;
    // Enumerate providers directly, including disabled modules and absent target libraries.
    List<?> instrumentationModules =
        (List<?>) safeServiceLoaderLoad.invoke(null, instrumentationModuleClass, agentClassLoader);
    for (Object module : instrumentationModules) {
      moduleCount++;
      String mode = inspect.invoke(inspector, module).toString();
      if (mode.equals("MIXED")) {
        mixedModules.add(module.getClass().getName());
      } else if (mode.equals("ALL_INLINE")) {
        allInlineModules.add(module.getClass().getName());
      }
      // NO_ADVICE (custom ASM or resources only) needs no classic-mode exception.
    }

    assertThat(moduleCount).isPositive();
    assertThat(mixedModules).isEmpty();
    assertThat(allInlineModules)
        .containsExactlyInAnyOrder(
            // Avoid invokedynamic recursion during class loading and inline protected access.
            "io.opentelemetry.javaagent.instrumentation.internal.classloader.ClassLoaderInstrumentationModule",
            // Runs during JVM lambda generation; intentionally inline pending bootstrap analysis.
            "io.opentelemetry.javaagent.instrumentation.internal.lambda.LambdaInstrumentationModule");
  }

  @Test
  void muzzleFieldsAddedToAllInstrumentation() throws Exception {
    List<Class<?>> unMuzzledClasses = new ArrayList<>();
    List<Class<?>> nonLazyFields = new ArrayList<>();
    List<Class<?>> unInitFields = new ArrayList<>();

    Class<?> instrumentationModuleClass =
        IntegrationTestUtils.getAgentClassLoader()
            .loadClass(
                "io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule");

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
