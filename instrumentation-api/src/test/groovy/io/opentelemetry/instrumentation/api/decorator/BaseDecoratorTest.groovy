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

package io.opentelemetry.instrumentation.api.decorator

import io.opentelemetry.auto.test.utils.ConfigUtils
import io.opentelemetry.trace.Span
import io.opentelemetry.trace.Status
import io.opentelemetry.trace.attributes.SemanticAttributes
import spock.lang.Shared
import spock.lang.Specification

class BaseDecoratorTest extends Specification {

  @Shared
  def decorator = newDecorator()

  @Shared
  def resolvedAddress = new InetSocketAddress("github.com", 999)

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
    if (expectedPeerName) {
      1 * span.setAttribute(SemanticAttributes.NET_PEER_NAME, expectedPeerName)
    }
    if (expectedPeerIp) {
      1 * span.setAttribute(SemanticAttributes.NET_PEER_IP, expectedPeerIp)
    }
    1 * span.setAttribute(SemanticAttributes.NET_PEER_PORT, connection.port)
    0 * _

    where:
    connection                                      | expectedPeerName    | expectedPeerIp
    new InetSocketAddress("localhost", 888)         | "localhost"         | "127.0.0.1"
    new InetSocketAddress("1.2.3.4", 888)           | null                | "1.2.3.4"
    resolvedAddress                                 | "github.com"        | resolvedAddress.address.hostAddress
    new InetSocketAddress("bad.address.local", 999) | "bad.address.local" | null
  }

  def "test onPeerConnection with mapped peer"() {
    when:
    ConfigUtils.withConfigOverride(
      "otel.endpoint.peer.service.mapping",
      "1.2.3.4=catservice,dogs.com=dogsservice,opentelemetry.io=specservice") {
      decorator.onPeerConnection(span, connection)
    }

    then:
    if (expectedPeerService) {
      1 * span.setAttribute(SemanticAttributes.PEER_SERVICE, expectedPeerService)
    } else {
      0 * span.setAttribute(SemanticAttributes.PEER_SERVICE, _)
    }

    where:
    connection                               | expectedPeerService
    new InetSocketAddress("1.2.3.4", 888)    | "catservice"
    new InetSocketAddress("2.3.4.5", 888)    | null
    new InetSocketAddress("dogs.com", 999)   | "dogsservice"
    new InetSocketAddress("github.com", 999) | null
  }

  def "test onError"() {
    when:
    decorator.onError(span, error)

    then:
    if (error) {
      1 * span.setStatus(Status.UNKNOWN)
      1 * span.recordException(error)
    }
    0 * _

    where:
    error << [new Exception(), null]
  }

  def "test onComplete"() {
    when:
    decorator.onComplete(span, status, error)

    then:
    1 * span.setStatus(status)
    if (error) {
      1 * span.recordException(error)
    }
    0 * _

    where:
    error           | status
    new Exception() | Status.INTERNAL
    null            | Status.OK
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
