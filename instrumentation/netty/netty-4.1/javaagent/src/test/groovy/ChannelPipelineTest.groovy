/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.netty.channel.ChannelHandlerAdapter
import io.netty.channel.DefaultChannelPipeline
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.http.HttpClientCodec
import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import io.opentelemetry.javaagent.instrumentation.netty.v4_1.client.HttpClientTracingHandler
import spock.lang.Unroll

@Unroll
class ChannelPipelineTest extends AgentInstrumentationSpecification {

  // regression test for https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/1373
  // and https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/4040
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
    "first"       | { pipeline, h -> pipeline.removeFirst() }
  }

  // regression test for https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/4040
  def "should replace handler #desc"() {
    setup:
    def channel = new EmbeddedChannel()
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

  // regression test for https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/4056
  def "should addAfter and removeLast handler #desc"() {
    setup:
    def channel = new EmbeddedChannel()
    def channelPipeline = new DefaultChannelPipeline(channel)
    def httpHandler = new HttpClientCodec()

    expect: "no handlers initially"
    channelPipeline.size() == 0

    when:
    channelPipeline.addLast("http", httpHandler)

    then: "add http and instrumentation handlers"
    channelPipeline.size() == 2
    channelPipeline.first() == httpHandler
    channelPipeline.last().getClass() == HttpClientTracingHandler

    when:
    def noopHandler = new NoopChannelHandler()
    channelPipeline.addAfter("http", "noop", noopHandler)

    then: "instrumentation handler is between with http and noop"
    channelPipeline.size() == 3
    channelPipeline.first() == httpHandler
    channelPipeline.last() == noopHandler

    when:
    channelPipeline.removeLast()

    then: "http and instrumentation handlers will be remained"
    channelPipeline.size() == 2
    channelPipeline.first() == httpHandler
    channelPipeline.last().getClass() == HttpClientTracingHandler

    when:
    channelPipeline.removeLast()

    then: "there is no handler in pipeline"
    channelPipeline.size() == 0
  }

  private static class NoopChannelHandler extends ChannelHandlerAdapter {
  }
}
