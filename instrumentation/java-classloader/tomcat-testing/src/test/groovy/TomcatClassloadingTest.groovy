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
import io.opentelemetry.auto.instrumentation.api.Tags
import io.opentelemetry.auto.test.AgentTestRunner
import org.apache.catalina.WebResource
import org.apache.catalina.WebResourceRoot
import org.apache.catalina.loader.ParallelWebappClassLoader

class TomcatClassloadingTest extends AgentTestRunner {

  WebResourceRoot resources = Mock(WebResourceRoot) {
    getResource(_) >> Mock(WebResource)
    listResources(_) >> []
    // Looks like 9.x.x needs this one:
    getResources(_) >> []
  }
  ParallelWebappClassLoader classloader = new ParallelWebappClassLoader(null)

  def "tomcat class loading delegates to parent for agent classes"() {
    setup:
    classloader.setResources(resources)
    classloader.init()
    classloader.start()

    when:
    // If instrumentation didn't work this would blow up with NPE due to incomplete resources mocking
    def clazz = classloader.loadClass("io.opentelemetry.auto.instrumentation.api.Tags")

    then:
    clazz == Tags
  }
}
