/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import org.apache.felix.framework.BundleWiringImpl
import org.eclipse.osgi.internal.debug.Debug
import org.eclipse.osgi.internal.framework.EquinoxConfiguration
import org.eclipse.osgi.internal.loader.BundleLoader
import org.eclipse.osgi.internal.loader.ModuleClassLoader
import org.eclipse.osgi.internal.loader.classpath.ClasspathManager
import org.eclipse.osgi.storage.BundleInfo

class OSGIClassloadingTest extends AgentInstrumentationSpecification {
  def "OSGI delegates to bootstrap class loader for agent classes"() {
    when:
    def clazz
    if (args == 1) {
      clazz = loader.loadClass("io.opentelemetry.javaagent.instrumentation.api.InstrumentationContext")
    } else {
      clazz = loader.loadClass("io.opentelemetry.javaagent.instrumentation.api.InstrumentationContext", false)
    }

    then:
    assert clazz != null
    assert clazz.getClassLoader() == null

    where:
    loader                                                   | args
    new TestClassLoader()                                    | 1
    new TestClassLoader()                                    | 2
    new BundleWiringImpl.BundleClassLoader(null, null, null) | 1
    new BundleWiringImpl.BundleClassLoader(null, null, null) | 2
  }

  static class TestClassLoader extends ModuleClassLoader {

    TestClassLoader() {
      super(null)
    }

    @Override
    protected BundleInfo.Generation getGeneration() {
      return null
    }

    @Override
    protected Debug getDebug() {
      return null
    }

    @Override
    ClasspathManager getClasspathManager() {
      return null
    }

    @Override
    protected EquinoxConfiguration getConfiguration() {
      return null
    }

    @Override
    BundleLoader getBundleLoader() {
      return null
    }

    @Override
    boolean isRegisteredAsParallel() {
      return false
    }
  }
}
