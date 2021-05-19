/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.netty.channel.DefaultChannelPipeline
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.http.HttpClientCodec
import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import io.opentelemetry.javaagent.instrumentation.netty.v4_1.client.HttpClientTracingHandler
import spock.lang.Unroll

class ChannelPipelineTest extends AgentInstrumentationSpecification {
  // regression test for https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/1373
  @Unroll
  def "test remove our handler #testName"() {
    setup:
    def channel = new EmbeddedChannel()
    def channelPipeline = new DefaultChannelPipeline(channel)
    def handler = new HttpClientCodec()

    when:
    // no handlers
    channelPipeline.first() == null

    then:
    // add handler
    channelPipeline.addLast("http", handler)
    channelPipeline.first() == handler
    // our handler was also added
    channelPipeline.last().getClass() == HttpClientTracingHandler

    and:
    removeMethod.call(channelPipeline, handler)
    // removing handler also removes our handler
    channelPipeline.first() == null

    where:
    testName      | removeMethod
    "by instance" | { pipeline, h -> pipeline.remove(h) }
    "by class"    | { pipeline, h -> pipeline.remove(h.getClass()) }
    "by name"     | { pipeline, h -> pipeline.remove("http") }
  }
}
