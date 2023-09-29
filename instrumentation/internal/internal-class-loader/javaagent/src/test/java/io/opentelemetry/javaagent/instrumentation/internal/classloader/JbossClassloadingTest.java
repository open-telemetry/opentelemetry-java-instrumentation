/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.internal.classloader;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleClassLoader;
import org.jboss.modules.ModuleFinder;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleLoadException;
import org.jboss.modules.ModuleLoader;
import org.jboss.modules.ModuleSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class JbossClassloadingTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Test
  void testDelegatesToBootstrapClassLoaderForAgentClasses()
      throws ModuleLoadException, ClassNotFoundException {
    ModuleFinder[] moduleFinders = new ModuleFinder[1];
    moduleFinders[0] = (identifier, delegateLoader) -> ModuleSpec.build(identifier).create();

    ModuleLoader moduleLoader = new ModuleLoader(moduleFinders);
    ModuleIdentifier moduleId = ModuleIdentifier.fromString("test");
    Module testModule = moduleLoader.loadModule(moduleId);
    ModuleClassLoader classLoader = testModule.getClassLoader();

    Class<?> clazz =
        Class.forName(
            "io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge", false, classLoader);

    assertThat(clazz).isNotNull();
    assertThat(clazz.getClassLoader()).isNull();
  }
}
