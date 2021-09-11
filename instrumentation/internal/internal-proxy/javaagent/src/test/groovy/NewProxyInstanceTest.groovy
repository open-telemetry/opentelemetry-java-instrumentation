/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import io.opentelemetry.javaagent.bootstrap.FieldBackedContextStoreAppliedMarker

import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

class NewProxyInstanceTest extends AgentInstrumentationSpecification {
  def "should filter out duplicate FieldBackedContextStoreAppliedMarker interfaces from newProxyInstance"() {
    setup:
    Class[] interfaces = new Class[3]
    interfaces[0] = Runnable
    interfaces[1] = FieldBackedContextStoreAppliedMarker
    interfaces[2] = FieldBackedContextStoreAppliedMarker

    expect:
    Runnable proxy = Proxy.newProxyInstance(NewProxyInstanceTest.getClassLoader(), interfaces, new MyHandler()) as Runnable
    proxy.run()

    // should not throw IllegalArgumentException:
    // repeated interface: io.opentelemetry.javaagent.bootstrap.FieldBackedContextStoreAppliedMarker
  }

  def "should filter out duplicate FieldBackedContextStoreAppliedMarker interfaces from getProxyClass"() {
    setup:
    Class[] interfaces = new Class[3]
    interfaces[0] = Runnable
    interfaces[1] = FieldBackedContextStoreAppliedMarker
    interfaces[2] = FieldBackedContextStoreAppliedMarker

    expect:
    Class<?> proxyClass = Proxy.getProxyClass(NewProxyInstanceTest.getClassLoader(), interfaces)
    def proxy = proxyClass.newInstance(new MyHandler()) as Runnable
    proxy.run()

    // should not throw IllegalArgumentException:
    // repeated interface: io.opentelemetry.javaagent.bootstrap.FieldBackedContextStoreAppliedMarker
  }

  static class MyHandler implements InvocationHandler {

    @Override
    Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
      return null
    }
  }
}
