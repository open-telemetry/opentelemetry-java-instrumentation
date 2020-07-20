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


import io.opentelemetry.auto.tooling.AgentInstaller
import io.opentelemetry.auto.tooling.HelperInjector
import io.opentelemetry.auto.tooling.Utils
import io.opentelemetry.auto.util.test.AgentSpecification
import net.bytebuddy.agent.ByteBuddyAgent
import net.bytebuddy.description.type.TypeDescription
import net.bytebuddy.dynamic.ClassFileLocator
import net.bytebuddy.dynamic.loading.ClassInjector
import spock.lang.Timeout

import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicReference

import static io.opentelemetry.auto.test.utils.ClasspathUtils.isClassLoaded
import static io.opentelemetry.auto.tooling.ClassLoaderMatcher.BOOTSTRAP_CLASSLOADER
import static io.opentelemetry.auto.util.gc.GCUtils.awaitGC

class HelperInjectionTest extends AgentSpecification {

  @Timeout(10)
  def "helpers injected to non-delegating classloader"() {
    setup:
    String helperClassName = HelperInjectionTest.getPackage().getName() + '.HelperClass'
    HelperInjector injector = new HelperInjector("test", helperClassName)
    AtomicReference<URLClassLoader> emptyLoader = new AtomicReference<>(new URLClassLoader(new URL[0], (ClassLoader) null))

    when:
    emptyLoader.get().loadClass(helperClassName)
    then:
    thrown ClassNotFoundException

    when:
    injector.transform(null, null, emptyLoader.get(), null)
    emptyLoader.get().loadClass(helperClassName)
    then:
    isClassLoaded(helperClassName, emptyLoader.get())
    // injecting into emptyLoader should not load on agent's classloader
    !isClassLoaded(helperClassName, Utils.getAgentClassLoader())

    when: "references to emptyLoader are gone"
    emptyLoader.get().close() // cleanup
    def ref = new WeakReference(emptyLoader.get())
    emptyLoader.set(null)

    awaitGC(ref)

    then: "HelperInjector doesn't prevent it from being collected"
    null == ref.get()
  }

  def "helpers injected on bootstrap classloader"() {
    setup:
    ByteBuddyAgent.install()
    AgentInstaller.installBytebuddyAgent(ByteBuddyAgent.getInstrumentation())
    String helperClassName = HelperInjectionTest.getPackage().getName() + '.HelperClass'
    HelperInjector injector = new HelperInjector("test", helperClassName)
    URLClassLoader bootstrapChild = new URLClassLoader(new URL[0], (ClassLoader) null)

    when:
    bootstrapChild.loadClass(helperClassName)
    then:
    thrown ClassNotFoundException

    when:
    injector.transform(null, null, BOOTSTRAP_CLASSLOADER, null)
    Class<?> helperClass = bootstrapChild.loadClass(helperClassName)
    then:
    helperClass.getClassLoader() == BOOTSTRAP_CLASSLOADER
  }

  def "check hard references on class injection"() {
    setup:
    String helperClassName = HelperInjectionTest.getPackage().getName() + '.HelperClass'

    // Copied from HelperInjector:
    ClassFileLocator locator =
      ClassFileLocator.ForClassLoader.of(Utils.getAgentClassLoader())
    byte[] classBytes = locator.locate(helperClassName).resolve()
    TypeDescription typeDesc =
      new TypeDescription.Latent(
        helperClassName, 0, null, Collections.<TypeDescription.Generic> emptyList())

    AtomicReference<URLClassLoader> emptyLoader = new AtomicReference<>(new URLClassLoader(new URL[0], (ClassLoader) null))
    AtomicReference<ClassInjector> injector = new AtomicReference<>(new ClassInjector.UsingReflection(emptyLoader.get()))
    injector.get().inject([(typeDesc): classBytes])

    when:
    def injectorRef = new WeakReference(injector.get())
    injector.set(null)

    awaitGC(injectorRef)

    then:
    null == injectorRef.get()

    when:
    def loaderRef = new WeakReference(emptyLoader.get())
    emptyLoader.set(null)

    awaitGC(loaderRef)

    then:
    null == loaderRef.get()
  }
}
