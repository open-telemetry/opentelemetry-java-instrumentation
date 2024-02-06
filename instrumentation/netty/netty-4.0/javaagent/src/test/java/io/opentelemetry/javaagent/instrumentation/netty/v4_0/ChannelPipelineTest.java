/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v4_0;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelPipeline;
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

  // regression test for
  // https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/1373
  // and https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/4040
  @ParameterizedTest
  @CsvSource({"by instance", "by class", "by name", "first"})
  void testRemoveOurHandler(String testName) throws Exception {
    EmbeddedChannel channel = new EmbeddedChannel(new NoopChannelHandler());
    ChannelPipeline channelPipeline = channel.pipeline();
    HttpClientCodec handler = new HttpClientCodec();

    // remove the default head and tail handlers
    if (channelPipeline.first() != null) {
      channelPipeline.removeFirst();
    }
    if (channelPipeline.last() != null) {
      channelPipeline.removeLast();
    }

    // no handlers initially
    assertEquals(0, channelPipeline.toMap().size());

    // add handler
    channelPipeline.addLast("http", handler);
    assertEquals(handler, channelPipeline.first());
    // our handler was also added
    assertEquals("HttpClientTracingHandler", channelPipeline.last().getClass().getSimpleName());

    if ("by instance".equals(testName)) {
      channelPipeline.remove(handler);
    } else if ("by class".equals(testName)) {
      channelPipeline.remove(handler.getClass());
    } else if ("by name".equals(testName)) {
      channelPipeline.remove("http");
    } else if ("first".equals(testName)) {
      channelPipeline.removeFirst();
    }

    // removing handler also removes our handler
    assertEquals(0, channelPipeline.toMap().size());
  }

  // regression test for
  // https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/4040
  @ParameterizedTest
  @CsvSource({"by instance", "by class", "by name"})
  void shouldReplaceHandler(String desc) {
    EmbeddedChannel channel = new EmbeddedChannel(new NoopChannelHandler());
    ChannelPipeline channelPipeline = channel.pipeline();
    HttpClientCodec httpHandler = new HttpClientCodec();

    // remove the default head and tail handlers
    if (channelPipeline.first() != null) {
      channelPipeline.removeFirst();
    }
    if (channelPipeline.last() != null) {
      channelPipeline.removeLast();
    }

    // no handlers initially
    assertEquals(0, channelPipeline.toMap().size());

    NoopChannelHandler noopHandler = new NoopChannelHandler();
    channelPipeline.addFirst("test", noopHandler);

    // only the noop handler
    assertEquals(1, channelPipeline.toMap().size());
    assertEquals(noopHandler, channelPipeline.first());

    if ("by instance".equals(desc)) {
      channelPipeline.replace(noopHandler, "http", httpHandler);
    } else if ("by class".equals(desc)) {
      channelPipeline.replace(noopHandler.getClass(), "http", httpHandler);
    } else if ("by name".equals(desc)) {
      channelPipeline.replace("test", "http", httpHandler);
    }

    // noop handler was removed; http and instrumentation handlers were added
    assertEquals(1, channelPipeline.toMap().size());
    assertEquals(httpHandler, channelPipeline.first());
    assertEquals("HttpClientTracingHandler", channelPipeline.last().getClass().getSimpleName());

    NoopChannelHandler anotherNoopHandler = new NoopChannelHandler();
    channelPipeline.replace("http", "test", anotherNoopHandler);

    // http and instrumentation handlers were removed; noop handler was added
    assertEquals(1, channelPipeline.toMap().size());
    assertEquals(anotherNoopHandler, channelPipeline.first());
  }

  // regression test for
  // https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/4056
  @Test
  void shouldAddAfterAndRemoveLastHandler() {
    EmbeddedChannel channel = new EmbeddedChannel(new NoopChannelHandler());
    ChannelPipeline channelPipeline = channel.pipeline();
    HttpClientCodec httpHandler = new HttpClientCodec();

    if (channelPipeline.first() != null) {
      channelPipeline.removeFirst();
    }
    if (channelPipeline.last() != null) {
      channelPipeline.removeLast();
    }

    assertEquals(0, channelPipeline.toMap().size());

    channelPipeline.addLast("http", httpHandler);
    assertEquals(2, channelPipeline.toMap().size());
    assertEquals(channelPipeline.first(), httpHandler);
    assertEquals(channelPipeline.last().getClass().getSimpleName(), "HttpClientTracingHandler");

    NoopChannelHandler noopHandler = new NoopChannelHandler();
    channelPipeline.addAfter("http", "noop", noopHandler);

    assertEquals(3, channelPipeline.toMap().size());
    assertEquals(channelPipeline.first(), httpHandler);
    assertEquals(channelPipeline.last(), noopHandler);

    // removeLast will remove everything after httpHandler
    ChannelHandler removed = channelPipeline.removeLast();
    assertEquals(httpHandler, removed);

    // there i sno handler in the pipeline
    assertEquals(0, channelPipeline.toMap().size());
  }

  private static class NoopChannelHandler extends ChannelHandlerAdapter {}
}
