/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.DefaultChannelPipeline
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.http.HttpClientCodec
import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import io.opentelemetry.javaagent.instrumentation.netty.v4_0.client.HttpClientTracingHandler
import spock.lang.Unroll

class ChannelPipelineTest extends AgentInstrumentationSpecification {
  // regression test for https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/1373
  @Unroll
  def "test remove our handler #testName"() {
    setup:
    def channel = new EmbeddedChannel(new EmptyChannelHandler())
    def channelPipeline = new DefaultChannelPipeline(channel)
    def handler = new HttpClientCodec()

    when:
    // no handlers
    channelPipeline.first() == null
    channelPipeline.last() == null

    then:
    // add handler
    channelPipeline.addLast("http", handler)
    channelPipeline.first() == handler
    // our handler was also added
    channelPipeline.last().getClass() == HttpClientTracingHandler

    and:
    removeMethod.call(channelPipeline, handler)
    // removing handler also removes our handler
    channelPipeline.first() == null || "io.netty.channel.DefaultChannelPipeline\$TailHandler" == channelPipeline.first().getClass().getName()
    channelPipeline.last() == null

    where:
    testName      | removeMethod
    "by instance" | { pipeline, h -> pipeline.remove(h) }
    "by class"    | { pipeline, h -> pipeline.remove(h.getClass()) }
    "by name"     | { pipeline, h -> pipeline.remove("http") }
  }

  private static class EmptyChannelHandler implements ChannelHandler {
    @Override
    void handlerAdded(ChannelHandlerContext ctx) throws Exception {
    }

    @Override
    void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
    }

    @Override
    void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
    }
  }
}
