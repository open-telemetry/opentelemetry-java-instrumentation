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

package io.opentelemetry.instrumentation.api.tracer

import static io.opentelemetry.auto.test.utils.ConfigUtils.updateConfig

import io.opentelemetry.instrumentation.api.tracer.utils.NetPeerUtils
import io.opentelemetry.trace.attributes.SemanticAttributes

class NetPeerUtilsTest extends BaseTracerTest {

  static {
    updateConfig {
      System.setProperty(
        "otel.endpoint.peer.service.mapping",
        "1.2.3.4=catservice,dogs.com=dogsservice,opentelemetry.io=specservice")
    }
  }

  def "test setAttributes"() {
    when:
    NetPeerUtils.setNetPeer(span, connection)

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
    new InetSocketAddress("1.2.1.2", 888)           | null                | "1.2.1.2"
    resolvedAddress                                 | "github.com"        | resolvedAddress.address.hostAddress
    new InetSocketAddress("bad.address.local", 999) | "bad.address.local" | null
  }

  def "test setAttributes with mapped peer"() {
    when:
    NetPeerUtils.setNetPeer(span, connection)

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
}
	