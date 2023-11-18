/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v4_0;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.DefaultChannelPipeline;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.HttpClientCodec;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

public class ChannelPipelineTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @ParameterizedTest
  @CsvSource({"by instance", "by class", "by name", "first"})
  void testRemoveOurHandler(String testName) throws Exception {
    EmbeddedChannel channel = new EmbeddedChannel(new NoopChannelHandler());
    DefaultChannelPipeline channelPipeline = new DefaultChannelPipeline(channel);
    HttpClientCodec handler = new HttpClientCodec();

    // no handlers
    assertNull(channelPipeline.first());
    assertNull(channelPipeline.last());

    // add handler
    channelPipeline.addLast("http", handler);
    assertEquals(handler, channelPipeline.first());
    // our handler was also added
    assertEquals("HttpClientTracingHandler", channelPipeline.last().getClass().getSimpleName());

    switch (testName) {
      case "by instance":
        channelPipeline.remove(handler);
        break;
      case "by class":
        channelPipeline.remove(handler.getClass());
        break;
      case "by name":
        channelPipeline.remove("http");
        break;
      case "first":
        channelPipeline.removeFirst();
        break;
    }

    // removing handler also removes our handler
    assertNull(channelPipeline.first());
    assertNull(channelPipeline.last());
  }

  @ParameterizedTest
  @CsvSource({"by instance", "by class", "by name"})
  void shouldReplaceHandler(String desc) {
    EmbeddedChannel channel = new EmbeddedChannel(new NoopChannelHandler());
    DefaultChannelPipeline channelPipeline = new DefaultChannelPipeline(channel);
    HttpClientCodec httpHandler = new HttpClientCodec();

    // no handlers initially
    assertEquals(0, channelPipeline.size());

    NoopChannelHandler noopHandler = new NoopChannelHandler();
    channelPipeline.addFirst("test", noopHandler);

    // only the noop handler
    assertEquals(1, channelPipeline.size());
    assertEquals(noopHandler, channelPipeline.first());

    switch (desc) {
      case "by instance":
        channelPipeline.replace(noopHandler, "http", httpHandler);
        break;
      case "by class":
        channelPipeline.replace(noopHandler.getClass(), "http", httpHandler);
        break;
      case "by name":
        channelPipeline.replace("test", "http", httpHandler);
        break;
    }

    // noop handler was removed; http and instrumentation handlers were added
    assertEquals(2, channelPipeline.size());
    assertEquals(httpHandler, channelPipeline.first());
    assertEquals("HttpClientTracingHandler", channelPipeline.last().getClass().getSimpleName());

    NoopChannelHandler anotherNoopHandler = new NoopChannelHandler();
    channelPipeline.replace("http", "test", anotherNoopHandler);

    // http and instrumentation handlers were removed; noop handler was added
    assertEquals(1, channelPipeline.size());
    assertEquals(anotherNoopHandler, channelPipeline.first());
  }

  @Test
  void shouldAddAfterAndRemoveLastHandler() {
    EmbeddedChannel channel = new EmbeddedChannel(new NoopChannelHandler());
    DefaultChannelPipeline channelPipeline = new DefaultChannelPipeline(channel);
    HttpClientCodec httpHandler = new HttpClientCodec();

    // no handlers initially
    assertEquals(0, channelPipeline.size());

    channelPipeline.addLast("http", httpHandler);

    // add http and instrumentation handlers
    assertEquals(2, channelPipeline.size());
    assertEquals(httpHandler, channelPipeline.first());
    assertEquals("HttpClientTracingHandler", channelPipeline.last().getClass().getSimpleName());

    NoopChannelHandler noopHandler = new NoopChannelHandler();
    channelPipeline.addAfter("http", "noop", noopHandler);

    // instrumentation handler is between with http and noop
    assertEquals(3, channelPipeline.size());
    assertEquals(httpHandler, channelPipeline.first());
    assertEquals(noopHandler, channelPipeline.last());

    channelPipeline.removeLast();

    // http and instrumentation handlers will be remained
    assertEquals(2, channelPipeline.size());
    assertEquals(httpHandler, channelPipeline.first());
    assertEquals("HttpClientTracingHandler", channelPipeline.last().getClass().getSimpleName());

    Object removed = channelPipeline.removeLast();

    // there is no handler in pipeline
    assertEquals(0, channelPipeline.size());
    // removing tracing handler also removes the http handler and returns it
    assertEquals(httpHandler, removed);
  }

  private static class NoopChannelHandler extends ChannelHandlerAdapter {}
}
