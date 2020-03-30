/*
 * Copyright 2020, OpenTelemetry Authors
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
package io.opentelemetry.auto.bootstrap.instrumentation.decorator

import io.opentelemetry.auto.instrumentation.api.MoreTags
import io.opentelemetry.auto.util.test.AgentSpecification
import io.opentelemetry.trace.Span
import io.opentelemetry.trace.Status
import spock.lang.Shared

class BaseDecoratorTest extends AgentSpecification {

  @Shared
  def decorator = newDecorator()

  def span = Mock(Span)

  def "test afterStart"() {
    when:
    decorator.afterStart(span)

    then:
    _ * span.setAttribute(_, _) // Want to allow other calls from child implementations.
    0 * _
  }

  def "test onPeerConnection"() {
    when:
    decorator.onPeerConnection(span, connection)

    then:
    if (connection.getAddress()) {
      2 * span.setAttribute(MoreTags.NET_PEER_NAME, connection.hostName)
      1 * span.setAttribute(MoreTags.NET_PEER_IP, connection.address.hostAddress)
    } else {
      1 * span.setAttribute(MoreTags.NET_PEER_NAME, connection.hostName)
    }
    1 * span.setAttribute(MoreTags.NET_PEER_PORT, connection.port)
    0 * _

    where:
    connection                                      | _
    new InetSocketAddress("localhost", 888)         | _
    new InetSocketAddress("ipv6.google.com", 999)   | _
    new InetSocketAddress("bad.address.local", 999) | _
  }

  def "test onError"() {
    when:
    decorator.onError(span, error)

    then:
    if (error) {
      1 * span.setStatus(Status.UNKNOWN)
      1 * span.setAttribute(MoreTags.ERROR_TYPE, error.getClass().getName())
      1 * span.setAttribute(MoreTags.ERROR_STACK, _)
      1 * span.setAttribute(MoreTags.ERROR_MSG, null)
    }
    0 * _

    where:
    error << [new Exception(), null]
  }

  def "test beforeFinish"() {
    when:
    decorator.beforeFinish(span)

    then:
    0 * _
  }

  def "test assert null span"() {
    when:
    decorator.afterStart((Span) null)

    then:
    thrown(AssertionError)

    when:
    decorator.onError((Span) null, null)

    then:
    thrown(AssertionError)

    when:
    decorator.onError((Span) null, null)

    then:
    thrown(AssertionError)

    when:
    decorator.onPeerConnection((Span) null, null)

    then:
    thrown(AssertionError)
  }

  def "test spanNameForMethod"() {
    when:
    def result = decorator.spanNameForMethod(method)

    then:
    result == "${name}.run"

    where:
    target                         | name
    SomeInnerClass                 | "SomeInnerClass"
    SomeNestedClass                | "SomeNestedClass"
    SampleJavaClass.anonymousClass | "SampleJavaClass\$1"

    method = target.getDeclaredMethod("run")
  }

  def newDecorator() {
    return new BaseDecorator() {

      @Override
      protected String getComponentName() {
        return "test-component"
      }
    }
  }

  class SomeInnerClass implements Runnable {
    void run() {
    }
  }

  static class SomeNestedClass implements Runnable {
    void run() {
    }
  }
}
