/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import static io.opentelemetry.instrumentation.test.utils.GcUtils.awaitGc

import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import io.opentelemetry.javaagent.testing.common.HelperInjectorAccess
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicReference

class ResourceInjectionTest extends AgentInstrumentationSpecification {

  def "resources injected to non-delegating classloader"() {
    setup:
    String resourceName = 'test-resources/test-resource.txt'
    AtomicReference<URLClassLoader> emptyLoader = new AtomicReference<>(new URLClassLoader(new URL[0], (ClassLoader) null))

    when:
    def resourceUrls = emptyLoader.get().getResources(resourceName)
    then:
    !resourceUrls.hasMoreElements()

    when:
    URLClassLoader notInjectedLoader = new URLClassLoader(new URL[0], (ClassLoader) null)

    HelperInjectorAccess.injectResources(emptyLoader.get(), resourceName)
    resourceUrls = emptyLoader.get().getResources(resourceName)

    then:
    resourceUrls.hasMoreElements()
    resourceUrls.nextElement().openStream().text.trim() == 'Hello world!'

    !notInjectedLoader.getResources(resourceName).hasMoreElements()

    when: "references to emptyLoader are gone"
    emptyLoader.get().close() // cleanup
    def ref = new WeakReference(emptyLoader.get())
    emptyLoader.set(null)

    awaitGc(ref)

    then: "HelperInjector doesn't prevent it from being collected"
    null == ref.get()
  }
}
