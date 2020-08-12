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

import io.opentelemetry.auto.test.AgentTestRunner
import org.apache.felix.framework.BundleWiringImpl
import org.eclipse.osgi.internal.debug.Debug
import org.eclipse.osgi.internal.framework.EquinoxConfiguration
import org.eclipse.osgi.internal.loader.BundleLoader
import org.eclipse.osgi.internal.loader.ModuleClassLoader
import org.eclipse.osgi.internal.loader.classpath.ClasspathManager
import org.eclipse.osgi.storage.BundleInfo

class OSGIClassloadingTest extends AgentTestRunner {
  def "OSGI delegates to bootstrap class loader for agent classes"() {
    when:
    def clazz
    if (args == 1) {
      clazz = loader.loadClass("io.opentelemetry.instrumentation.auto.api.concurrent.State")
    } else {
      clazz = loader.loadClass("io.opentelemetry.instrumentation.auto.api.concurrent.State", false)
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
