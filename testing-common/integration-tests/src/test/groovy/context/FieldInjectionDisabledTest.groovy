/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package context

import io.opentelemetry.instrumentation.test.AgentTestRunner
import java.lang.reflect.Field
import java.util.function.BiFunction
import library.DisabledKeyClass

// this test is run using:
//   -Dotel.javaagent.runtime.context.field.injection=false
//   -Dotel.instrumentation.context-test-instrumentation.enabled=true
// (see integration-tests.gradle)
class FieldInjectionDisabledTest extends AgentTestRunner {

  @Override
  protected List<BiFunction<String, Throwable, Boolean>> skipErrorConditions() {
    return [
      new BiFunction<String, Throwable, Boolean>() {
        @Override
        Boolean apply(String typeName, Throwable throwable) {
          return typeName.startsWith(ContextTestInstrumentationModule.getName() + '$Incorrect') && throwable.getMessage().startsWith("Incorrect Context Api Usage detected.")
        }
      }
    ]
  }

  def "Check that structure is not modified when structure modification is disabled"() {
    setup:
    def keyClass = DisabledKeyClass
    boolean hasField = false
    for (Field field : keyClass.getDeclaredFields()) {
      if (field.getName().startsWith("__opentelemetry")) {
        hasField = true
        break
      }
    }

    boolean hasMarkerInterface = false
    boolean hasAccessorInterface = false
    for (Class inter : keyClass.getInterfaces()) {
      if (inter.getName() == 'io.opentelemetry.javaagent.bootstrap.FieldBackedContextStoreAppliedMarker') {
        hasMarkerInterface = true
      }
      if (inter.getName().startsWith('io.opentelemetry.javaagent.bootstrap.instrumentation.context.FieldBackedProvider$ContextAccessor')) {
        hasAccessorInterface = true
      }
    }

    expect:
    hasField == false
    hasMarkerInterface == false
    hasAccessorInterface == false
    keyClass.newInstance().isInstrumented() == true
  }
}
