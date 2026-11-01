/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.javaagent.bootstrap.DefineClassHelper.Handler.DefineClassContext;
import org.junit.jupiter.api.Test;

class DefineClassHandlerTest {

  @Test
  void nestedDefineClassCall() {
    ClassLoader classLoader = new ClassLoader() {};
    String className = "test";
    DefineClassHandler defineClassHandler = new DefineClassHandler();
    DefineClassContext definClassContext =
        defineClassHandler.beforeDefineClass(classLoader, className, new byte[0], 0, 0);
    assertThat(definClassContext).isNotNull();
    DefineClassContext nestedDefinClassContext =
        defineClassHandler.beforeDefineClass(classLoader, className, new byte[0], 0, 0);
    assertThat(nestedDefinClassContext).isNull();
    definClassContext.exit();
  }
}
