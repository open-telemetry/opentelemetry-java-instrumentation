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
package io.opentelemetry.auto.tooling

import io.opentelemetry.common.AttributeValue
import io.opentelemetry.exporters.inmemory.InMemorySpanExporter
import io.opentelemetry.sdk.trace.data.test.TestSpanData
import io.opentelemetry.sdk.trace.export.SpanExporter
import io.opentelemetry.trace.Span
import io.opentelemetry.trace.SpanId
import io.opentelemetry.trace.Status
import io.opentelemetry.trace.TraceId
import io.opentelemetry.trace.attributes.SemanticAttributes
import spock.lang.Specification

import static io.opentelemetry.common.AttributeValue.stringAttributeValue

class PeerMappingExporterTest extends Specification {

  def "maps peers to name"() {
    setup:
    def delegateExporter = InMemorySpanExporter.create()
    def exporter = new PeerMappingExporter(
      delegateExporter,
      ["1.2.3.4": "catservice", "dogs.com": "dogsservice", "opentelemetry.io": "specservice"])

    def spans = [
      span("1.2.3.4", null, null),
      span("1.2.3.4", null, "notcatsservice"),
      span("1.2.3.4", "dogs.com", null),
      span("2.3.4.5", null, null),
      span(null, "github.com", null),
    ]

    def result = exporter.export(spans)
    def decorated = delegateExporter.finishedSpanItems

    expect:
    result == SpanExporter.ResultCode.SUCCESS

    decorated.size() == 5
    decorated[0].attributes["peer.service"].stringValue == "catservice"
    decorated[1].attributes["peer.service"].stringValue == "notcatsservice"
    decorated[2].attributes["peer.service"].stringValue == "dogsservice"
    decorated[3].attributes["peer.service"] == null
    decorated[4].attributes["peer.service"] == null
  }

  private static span(String peerIp, String peerName, String peerService) {
    def attributes = new HashMap<String, AttributeValue>()
    if (peerIp) {
      attributes[SemanticAttributes.NET_PEER_IP.key()] = stringAttributeValue(peerIp)
    }
    if (peerName) {
      attributes[SemanticAttributes.NET_PEER_NAME.key()] = stringAttributeValue(peerName)
    }
    if (peerService) {
      attributes["peer.service"] = stringAttributeValue(peerService)
    }
    return TestSpanData.newBuilder()
      .setTraceId(TraceId.getInvalid())
      .setSpanId(SpanId.getInvalid())
      .setName("test")
      .setKind(Span.Kind.INTERNAL)
      .setStartEpochNanos(0)
      .setStatus(Status.OK)
      .setEndEpochNanos(0)
      .setHasEnded(true)
      .setAttributes(attributes)
      .build()
  }
}
