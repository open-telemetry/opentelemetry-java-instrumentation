/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import org.apache.commons.lang3.SystemUtils

import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicReference

import static io.opentelemetry.instrumentation.test.utils.GcUtils.awaitGc

class ResourceInjectionTest extends AgentInstrumentationSpecification {

  def "resources injected to non-delegating classloader"() {
    setup:
    String resourceName = 'test-resources/test-resource.txt'
    URL[] urls = [SystemUtils.getProtectionDomain().getCodeSource().getLocation()]
    AtomicReference<URLClassLoader> emptyLoader = new AtomicReference<>(new URLClassLoader(urls, (ClassLoader) null))

    when:
    def resourceUrls = emptyLoader.get().getResources(resourceName)
    then:
    !resourceUrls.hasMoreElements()

    when:
    URLClassLoader notInjectedLoader = new URLClassLoader(urls, (ClassLoader) null)

    // this triggers resource injection
    emptyLoader.get().loadClass(SystemUtils.getName())

    resourceUrls = Collections.list(emptyLoader.get().getResources(resourceName))

    then:
    resourceUrls.size() == 2
    resourceUrls.get(0).openStream().text.trim() == 'Hello world!'
    resourceUrls.get(1).openStream().text.trim() == 'Hello there'

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
