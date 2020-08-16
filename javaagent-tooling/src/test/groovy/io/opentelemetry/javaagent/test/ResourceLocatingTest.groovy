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

package io.opentelemetry.auto.test

import io.opentelemetry.auto.util.test.AgentSpecification
import io.opentelemetry.javaagent.tooling.bytebuddy.AgentLocationStrategy
import java.util.concurrent.atomic.AtomicReference
import net.bytebuddy.agent.builder.AgentBuilder
import spock.lang.Shared

class ResourceLocatingTest extends AgentSpecification {
  @Shared
  def lastLookup = new AtomicReference<String>()
  @Shared
  def childLoader = new ClassLoader(this.getClass().getClassLoader()) {
    @Override
    URL getResource(String name) {
      lastLookup.set(name)
      // do not delegate resource lookup
      return findResource(name)
    }
  }

  def cleanup() {
    lastLookup.set(null)
  }

  def "finds resources from parent classloader"() {
    expect:
    locator.locate("java/lang/Object").isResolved() == usesProvidedClassloader
    // lastLookup verifies that the given classloader is only used when expected
    lastLookup.get() == usesProvidedClassloader ? null : "java/lang/Object.class"

    and:
    !locator.locate("java/lang/InvalidClass").isResolved()
    lastLookup.get() == "java/lang/InvalidClass.class"

    where:
    locator                                                                                 | usesProvidedClassloader
    new AgentLocationStrategy().classFileLocator(childLoader, null)                         | true
    AgentBuilder.LocationStrategy.ForClassLoader.STRONG.classFileLocator(childLoader, null) | false
  }
}
