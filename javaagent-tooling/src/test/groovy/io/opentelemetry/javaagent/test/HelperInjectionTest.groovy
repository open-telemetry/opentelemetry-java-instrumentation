/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.test

import static io.opentelemetry.instrumentation.test.utils.ClasspathUtils.isClassLoaded
import static io.opentelemetry.instrumentation.test.utils.GcUtils.awaitGc
import static io.opentelemetry.javaagent.tooling.bytebuddy.matcher.ClassLoaderMatcher.BOOTSTRAP_CLASSLOADER

import io.opentelemetry.javaagent.tooling.AgentInstaller
import io.opentelemetry.javaagent.tooling.HelperInjector
import io.opentelemetry.javaagent.tooling.Utils
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicReference
import net.bytebuddy.agent.ByteBuddyAgent
import net.bytebuddy.description.type.TypeDescription
import net.bytebuddy.dynamic.ClassFileLocator
import net.bytebuddy.dynamic.loading.ClassInjector
import spock.lang.Specification
import spock.lang.Timeout

class HelperInjectionTest extends Specification {

  @Timeout(10)
  def "helpers injected to non-delegating classloader"() {
    setup:
    String helperClassName = HelperInjectionTest.getPackage().getName() + '.HelperClass'
    HelperInjector injector = new HelperInjector("test", [helperClassName], [])
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

    awaitGc(ref)

    then: "HelperInjector doesn't prevent it from being collected"
    null == ref.get()
  }

  def "helpers injected on bootstrap classloader"() {
    setup:
    ByteBuddyAgent.install()
    AgentInstaller.installBytebuddyAgent(ByteBuddyAgent.getInstrumentation())
    String helperClassName = HelperInjectionTest.getPackage().getName() + '.HelperClass'
    HelperInjector injector = new HelperInjector("test", [helperClassName], [])
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

    awaitGc(injectorRef)

    then:
    null == injectorRef.get()

    when:
    def loaderRef = new WeakReference(emptyLoader.get())
    emptyLoader.set(null)

    awaitGc(loaderRef)

    then:
    null == loaderRef.get()
  }
}
