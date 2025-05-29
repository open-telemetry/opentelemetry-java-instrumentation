/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.internal.classloader;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.util.stream.Stream;
import org.apache.felix.framework.BundleWiringImpl;
import org.eclipse.osgi.internal.debug.Debug;
import org.eclipse.osgi.internal.framework.EquinoxConfiguration;
import org.eclipse.osgi.internal.loader.BundleLoader;
import org.eclipse.osgi.internal.loader.ModuleClassLoader;
import org.eclipse.osgi.internal.loader.classpath.ClasspathManager;
import org.eclipse.osgi.storage.BundleInfo;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class OsgiClassloadingTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @ParameterizedTest
  @MethodSource("provideArguments")
  void testOsgiDelegatesToBootstrapClassloaderForAgentClasses(ClassLoader loader)
      throws ClassNotFoundException {
    Class<?> clazz = loader.loadClass("io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge");
    assertThat(clazz).isNotNull();
    assertThat(clazz.getClassLoader()).isNull();
  }

  private static Stream<Arguments> provideArguments() {
    return Stream.of(
        Arguments.of(new TestClassLoader()),
        Arguments.of(new BundleWiringImpl.BundleClassLoader(null, null, null)));
  }

  static class TestClassLoader extends ModuleClassLoader {
    TestClassLoader() {
      super(null);
    }

    @Override
    protected BundleInfo.Generation getGeneration() {
      return null;
    }

    @Override
    protected Debug getDebug() {
      return null;
    }

    @Override
    public ClasspathManager getClasspathManager() {
      return null;
    }

    @Override
    protected EquinoxConfiguration getConfiguration() {
      return null;
    }

    @Override
    public BundleLoader getBundleLoader() {
      return null;
    }

    @Override
    public boolean isRegisteredAsParallel() {
      return false;
    }
  }
}
