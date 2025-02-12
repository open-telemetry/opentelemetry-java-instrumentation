/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package context;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.javaagent.testing.common.TestAgentListenerAccess;
import java.lang.reflect.Field;
import library.DisabledKeyClass;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

// this test is run using:
//   -Dotel.javaagent.experimental.field-injection.enabled=false
//   -Dotel.instrumentation.context-test-instrumentation.enabled=true
// (see integration-tests.gradle)
class FieldInjectionDisabledTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @BeforeAll
  static void setUp() {
    TestAgentListenerAccess.addSkipErrorCondition(
        (typeName, throwable) ->
            typeName.startsWith(ContextTestInstrumentationModule.class.getName() + "$Incorrect")
                && throwable.getMessage().startsWith("Incorrect Context Api Usage detected."));
  }

  @Test
  void structuralModificationDisabled() {
    Class<?> keyClass = DisabledKeyClass.class;
    boolean hasField = false;
    for (Field field : keyClass.getDeclaredFields()) {
      if (field.getName().startsWith("__opentelemetry")) {
        hasField = true;
        break;
      }
    }

    boolean hasMarkerInterface = false;
    boolean hasAccessorInterface = false;
    for (Class<?> inter : keyClass.getInterfaces()) {
      if ("io.opentelemetry.javaagent.bootstrap.VirtualFieldInstalledMarker"
          .equals(inter.getName())) {
        hasMarkerInterface = true;
      }
      if (inter
          .getName()
          .startsWith(
              "io.opentelemetry.javaagent.bootstrap.instrumentation.context.FieldBackedProvider$ContextAccessor")) {
        hasAccessorInterface = true;
      }
    }

    assertThat(hasField).isFalse();
    assertThat(hasMarkerInterface).isFalse();
    assertThat(hasAccessorInterface).isFalse();
    assertThat(new DisabledKeyClass().isInstrumented()).isTrue();
  }
}
