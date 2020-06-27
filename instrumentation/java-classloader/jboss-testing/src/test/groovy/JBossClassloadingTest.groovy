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
import org.jboss.modules.ModuleFinder
import org.jboss.modules.ModuleIdentifier
import org.jboss.modules.ModuleLoadException
import org.jboss.modules.ModuleLoader
import org.jboss.modules.ModuleSpec

class JBossClassloadingTest extends AgentTestRunner {
  def "delegates to bootstrap class loader for agent classes"() {
    setup:
    def moduleFinders = new ModuleFinder[1]
    moduleFinders[0] = new ModuleFinder() {
      @Override
      ModuleSpec findModule(ModuleIdentifier identifier, ModuleLoader delegateLoader) throws ModuleLoadException {
        return ModuleSpec.build(identifier).create()
      }
    }
    def moduleLoader = new ModuleLoader(moduleFinders)
    def moduleId = ModuleIdentifier.fromString("test")
    def testModule = moduleLoader.loadModule(moduleId)
    def classLoader = testModule.getClassLoader()

    when:
    Class<?> clazz
    try {
      clazz = Class.forName("io.opentelemetry.auto.instrumentation.api.Tags", false, classLoader)
    } catch (ClassNotFoundException e) {
    }

    then:
    assert clazz != null
    assert clazz.getClassLoader() == null
  }
}
