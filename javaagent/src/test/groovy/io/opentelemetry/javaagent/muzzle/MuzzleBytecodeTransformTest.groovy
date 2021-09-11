/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.muzzle

import io.opentelemetry.javaagent.IntegrationTestUtils
import spock.lang.Specification

import java.lang.reflect.Field
import java.lang.reflect.Method

class MuzzleBytecodeTransformTest extends Specification {

  def "muzzle fields added to all instrumentation"() {
    setup:
    List<Class> unMuzzledClasses = []
    List<Class> nonLazyFields = []
    List<Class> unInitFields = []
    def instrumentationModuleClass = IntegrationTestUtils.getAgentClassLoader().loadClass("io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule")
    for (Object instrumenter : ServiceLoader.load(instrumentationModuleClass)) {
      if (!instrumentationModuleClass.isAssignableFrom(instrumenter.getClass())) {
        // muzzle only applies to default instrumenters
        continue
      }
      Field f
      Method m
      try {
        f = instrumenter.getClass().getDeclaredField("muzzleReferences")
        f.setAccessible(true)
        if (f.get(instrumenter) != null) {
          nonLazyFields.add(instrumenter.getClass())
        }
        m = instrumenter.getClass().getDeclaredMethod("getMuzzleReferences")
        m.setAccessible(true)
        m.invoke(instrumenter)
        if (f.get(instrumenter) == null) {
          unInitFields.add(instrumenter.getClass())
        }
      } catch (NoSuchFieldException | NoSuchMethodException e) {
        unMuzzledClasses.add(instrumenter.getClass())
      } finally {
        if (null != f) {
          f.setAccessible(false)
        }
        if (null != m) {
          m.setAccessible(false)
        }
      }
    }
    expect:
    unMuzzledClasses == []
    nonLazyFields == []
    unInitFields == []
  }

}
