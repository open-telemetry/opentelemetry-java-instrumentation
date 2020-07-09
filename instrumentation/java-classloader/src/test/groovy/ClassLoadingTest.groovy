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

class ClassLoadingTest extends AgentTestRunner {
  def "delegates to bootstrap class loader for agent classes"() {
    setup:
    def classLoader = new NonDelegatingURLClassLoader()

    when:
    Class<?> clazz
    try {
      clazz = Class.forName("io.opentelemetry.auto.instrumentation.api.MoreAttributes", false, classLoader)
    } catch (ClassNotFoundException e) {
    }

    then:
    assert clazz != null
    assert clazz.getClassLoader() == null
  }

  static class NonDelegatingURLClassLoader extends URLClassLoader {

    NonDelegatingURLClassLoader() {
      super(new URL[0])
    }

    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
      synchronized (getClassLoadingLock(name)) {
        Class<?> clazz = findLoadedClass(name)
        if (clazz == null) {
          clazz = findClass(name)
        }
        if (resolve) {
          resolveClass(clazz)
        }
        return clazz
      }
    }
  }
}
