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

import static io.opentelemetry.auto.util.gc.GCUtils.awaitGC

import io.opentelemetry.auto.test.AgentTestRunner
import io.opentelemetry.javaagent.tooling.HelperInjector
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicReference

class ResourceInjectionTest extends AgentTestRunner {

  def "resources injected to non-delegating classloader"() {
    setup:
    String resourceName = 'test-resources/test-resource.txt'
    HelperInjector injector = new HelperInjector("test", [], [resourceName])
    AtomicReference<URLClassLoader> emptyLoader = new AtomicReference<>(new URLClassLoader(new URL[0], (ClassLoader) null))

    when:
    def resourceUrl = emptyLoader.get().getResource(resourceName)
    def resourceUrls = emptyLoader.get().getResources(resourceName)
    then:
    resourceUrl == null
    !resourceUrls.hasMoreElements()

    when:
    URLClassLoader notInjectedLoader = new URLClassLoader(new URL[0], (ClassLoader) null)

    injector.transform(null, null, emptyLoader.get(), null)
    resourceUrl = emptyLoader.get().getResource(resourceName)
    resourceUrls = emptyLoader.get().getResources(resourceName)

    then:
    resourceUrl != null
    resourceUrl.openStream().text.trim() == 'Hello world!'
    emptyLoader.get().getResourceAsStream(resourceName).text.trim() == 'Hello world!'

    resourceUrls.hasMoreElements()
    resourceUrls.nextElement().openStream().text.trim() == 'Hello world!'

    notInjectedLoader.getResource(resourceName) == null

    when: "references to emptyLoader are gone"
    emptyLoader.get().close() // cleanup
    def ref = new WeakReference(emptyLoader.get())
    emptyLoader.set(null)

    awaitGC(ref)

    then: "HelperInjector doesn't prevent it from being collected"
    null == ref.get()
  }
}
