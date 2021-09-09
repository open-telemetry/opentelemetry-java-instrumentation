/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.netty.channel.ChannelHandlerAdapter
import io.netty.channel.DefaultChannelPipeline
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.http.HttpClientCodec
import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import io.opentelemetry.javaagent.instrumentation.netty.v4_0.client.HttpClientTracingHandler
import spock.lang.Unroll

@Unroll
class ChannelPipelineTest extends AgentInstrumentationSpecification {

  // regression test for https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/1373
  // and https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/4040
  def "test remove our handler #testName"() {
    setup:
    def channel = new EmbeddedChannel(new NoopChannelHandler())
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
    "first"       | { pipeline, h -> pipeline.removeFirst() }
  }

  // regression test for https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/4040
  def "should replace handler #desc"() {
    setup:
    def channel = new EmbeddedChannel(new NoopChannelHandler())
    def channelPipeline = new DefaultChannelPipeline(channel)
    def httpHandler = new HttpClientCodec()

    expect: "no handlers initially"
    channelPipeline.size() == 0

    when:
    def noopHandler = new NoopChannelHandler()
    channelPipeline.addFirst("test", noopHandler)

    then: "only the noop handler"
    channelPipeline.size() == 1
    channelPipeline.first() == noopHandler

    when:
    replaceMethod(channelPipeline, "test", noopHandler, "http", httpHandler)

    then: "noop handler was removed; http and instrumentation handlers were added"
    channelPipeline.size() == 2
    channelPipeline.first() == httpHandler
    channelPipeline.last().getClass() == HttpClientTracingHandler

    when:
    def anotherNoopHandler = new NoopChannelHandler()
    replaceMethod(channelPipeline, "http", httpHandler, "test", anotherNoopHandler)

    then: "http and instrumentation handlers were removed; noop handler was added"
    channelPipeline.size() == 1
    channelPipeline.first() == anotherNoopHandler

    where:
    desc          | replaceMethod
    "by instance" | { pipeline, oldName, oldHandler, newName, newHandler -> pipeline.replace(oldHandler, newName, newHandler) }
    "by class"    | { pipeline, oldName, oldHandler, newName, newHandler -> pipeline.replace(oldHandler.getClass(), newName, newHandler) }
    "by name"     | { pipeline, oldName, oldHandler, newName, newHandler -> pipeline.replace(oldName, newName, newHandler) }
  }

  private static class NoopChannelHandler extends ChannelHandlerAdapter {
  }
}
