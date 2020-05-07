/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.opentelemetry.auto.integration.muzzle

import io.opentelemetry.auto.bootstrap.instrumentation.SafeServiceLoader
import io.opentelemetry.auto.test.IntegrationTestUtils
import spock.lang.Specification

import java.lang.reflect.Field
import java.lang.reflect.Method

class MuzzleBytecodeTransformTest extends Specification {

  def "muzzle fields added to all instrumentation"() {
    setup:
    List<Class> unMuzzledClasses = []
    List<Class> nonLazyFields = []
    List<Class> unInitFields = []
    for (Object instrumenter : SafeServiceLoader.load(IntegrationTestUtils.getAgentClassLoader().loadClass("io.opentelemetry.auto.tooling.Instrumenter"), IntegrationTestUtils.getAgentClassLoader())) {
      if (instrumenter.getClass().getName().endsWith("TraceConfigInstrumentation")) {
        // TraceConfigInstrumentation doesn't do muzzle checks
        // check on TracerClassInstrumentation instead
        instrumenter = IntegrationTestUtils.getAgentClassLoader().loadClass(instrumenter.getClass().getName() + '$TracerClassInstrumentation').newInstance()
      }
      if (!IntegrationTestUtils.getAgentClassLoader().loadClass('io.opentelemetry.auto.tooling.Instrumenter$Default').isAssignableFrom(instrumenter.getClass())) {
        // muzzle only applies to default instrumenters
        continue
      }
      Field f
      Method m
      try {
        f = instrumenter.getClass().getDeclaredField("instrumentationMuzzle")
        f.setAccessible(true)
        if (f.get(instrumenter) != null) {
          nonLazyFields.add(instrumenter.getClass())
        }
        m = instrumenter.getClass().getDeclaredMethod("getInstrumentationMuzzle")
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
