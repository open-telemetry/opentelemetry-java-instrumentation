/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package context

import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import io.opentelemetry.javaagent.testing.common.TestAgentListenerAccess
import library.DisabledKeyClass

import java.lang.reflect.Field

// this test is run using:
//   -Dotel.javaagent.experimental.field-injection.enabled=false
//   -Dotel.instrumentation.context-test-instrumentation.enabled=true
// (see integration-tests.gradle)
class FieldInjectionDisabledTest extends AgentInstrumentationSpecification {

  def setupSpec() {
    TestAgentListenerAccess.addSkipErrorCondition({ typeName, throwable ->
      return typeName.startsWith(ContextTestInstrumentationModule.getName() + '$Incorrect') && throwable.getMessage().startsWith("Incorrect Context Api Usage detected.")
    })
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
      if (inter.getName() == 'io.opentelemetry.javaagent.bootstrap.VirtualFieldInstalledMarker') {
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
