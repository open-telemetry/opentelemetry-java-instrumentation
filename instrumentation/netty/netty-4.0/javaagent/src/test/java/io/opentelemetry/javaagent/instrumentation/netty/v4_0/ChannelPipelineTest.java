/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v4_0;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.HttpClientCodec;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ChannelPipelineTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private static final Class<?> defaultChannelPipelineClass = getDefaultChannelPipelineClass();

  @Nullable
  private static Class<?> getDefaultChannelPipelineClass() {
    try {
      return Class.forName("io.netty.channel.DefaultChannelPipeline");
    } catch (Exception e) {
      return null;
    }
  }

  @NotNull
  private static Constructor<?> getConstructor() throws NoSuchMethodException {
    assertNotNull(defaultChannelPipelineClass);
    Constructor<?> constructor = defaultChannelPipelineClass.getDeclaredConstructor(Channel.class);
    constructor.setAccessible(true);
    return constructor;
  }

  // regression test for
  // https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/1373
  // and https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/4040
  @ParameterizedTest
  @CsvSource({"by instance", "by class", "by name", "first"})
  void testRemoveOurHandler(String testName) throws Exception {
    EmbeddedChannel channel = new EmbeddedChannel(new NoopChannelHandler());
    ChannelPipeline channelPipeline = (ChannelPipeline) getConstructor().newInstance(channel);
    HttpClientCodec handler = new HttpClientCodec();

    // no handlers initially except the default one
    assertTrue(
        channelPipeline.first() == null
            || "io.netty.channel.DefaultChannelPipeline$TailHandler"
                .equals(channelPipeline.first().getClass().getName()));
    assertNull(channelPipeline.last());
    assertEquals(0, channelPipeline.toMap().size());

    // add handler
    channelPipeline.addLast("http", handler);
    assertEquals(handler, channelPipeline.first());
    // our handler was also added
    assertEquals("HttpClientTracingHandler", channelPipeline.last().getClass().getSimpleName());
    assertEquals(1, channelPipeline.toMap().size());

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
    assertTrue(
        channelPipeline.first() == null
            || "io.netty.channel.DefaultChannelPipeline$TailHandler"
                .equals(channelPipeline.first().getClass().getName()));
    assertNull(channelPipeline.last());
    assertEquals(0, channelPipeline.toMap().size());
  }

  // regression test for
  // https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/4040
  @ParameterizedTest
  @CsvSource({"by instance", "by class", "by name"})
  void shouldReplaceHandler(String desc) throws Exception {
    EmbeddedChannel channel = new EmbeddedChannel(new NoopChannelHandler());
    ChannelPipeline channelPipeline = (ChannelPipeline) getConstructor().newInstance(channel);
    HttpClientCodec httpHandler = new HttpClientCodec();

    // no handlers initially except the default one
    assertTrue(
        channelPipeline.first() == null
            || "io.netty.channel.DefaultChannelPipeline$TailHandler"
                .equals(channelPipeline.first().getClass().getName()));
    assertNull(channelPipeline.last());
    assertEquals(0, channelPipeline.toMap().size());

    NoopChannelHandler noopHandler = new NoopChannelHandler();
    channelPipeline.addFirst("test", noopHandler);

    // only the noop handler
    assertEquals(noopHandler, channelPipeline.first());
    assertEquals(1, channelPipeline.toMap().size());

    if ("by instance".equals(desc)) {
      channelPipeline.replace(noopHandler, "http", httpHandler);
    } else if ("by class".equals(desc)) {
      channelPipeline.replace(noopHandler.getClass(), "http", httpHandler);
    } else if ("by name".equals(desc)) {
      channelPipeline.replace("test", "http", httpHandler);
    }

    // noop handler was removed; http and instrumentation handlers were added
    assertEquals(httpHandler, channelPipeline.first());
    assertEquals("HttpClientTracingHandler", channelPipeline.last().getClass().getSimpleName());
    assertEquals(1, channelPipeline.toMap().size());

    NoopChannelHandler anotherNoopHandler = new NoopChannelHandler();
    channelPipeline.replace("http", "test", anotherNoopHandler);

    // http and instrumentation handlers were removed; noop handler was added
    assertEquals(anotherNoopHandler, channelPipeline.first());
  }

  // regression test for
  // https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/4056
  @Test
  void shouldAddAfterAndRemoveLastHandler() throws Exception {
    EmbeddedChannel channel = new EmbeddedChannel(new NoopChannelHandler());
    ChannelPipeline channelPipeline = (ChannelPipeline) getConstructor().newInstance(channel);
    HttpClientCodec httpHandler = new HttpClientCodec();

    // no handlers initially
    assertTrue(
        channelPipeline.first() == null
            || "io.netty.channel.DefaultChannelPipeline$TailHandler"
                .equals(channelPipeline.first().getClass().getName()));
    assertNull(channelPipeline.last());
    assertEquals(0, channelPipeline.toMap().size());

    // Add http and instrumentation handlers
    channelPipeline.addLast("http", httpHandler);
    assertEquals(channelPipeline.first(), httpHandler);
    assertEquals("HttpClientTracingHandler", channelPipeline.last().getClass().getSimpleName());
    assertEquals(1, channelPipeline.toMap().size());

    NoopChannelHandler noopHandler = new NoopChannelHandler();
    channelPipeline.addAfter("http", "noop", noopHandler);

    // instrumentation handler is between http and noop handlers
    assertEquals(channelPipeline.first(), httpHandler);
    assertEquals(channelPipeline.last(), noopHandler);
    assertEquals(2, channelPipeline.toMap().size());

    // http and instrumentation handlers will remain when last handler is removed
    {
      ChannelHandler removed = channelPipeline.removeLast();
      assertEquals(noopHandler, removed);
      assertEquals(channelPipeline.first(), httpHandler);
      assertEquals("HttpClientTracingHandler", channelPipeline.last().getClass().getSimpleName());
      assertEquals(1, channelPipeline.toMap().size());
    }

    // there is no handler in pipeline when last handler is removed
    {
      ChannelHandler removed = channelPipeline.removeLast();
      assertEquals(httpHandler, removed);
      assertEquals(0, channelPipeline.toMap().size());
    }
  }

  // regression test for
  // https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/10377
  @Test
  void ourHandlerNotInHandlerMap() throws Exception {
    EmbeddedChannel channel = new EmbeddedChannel(new NoopChannelHandler());
    ChannelPipeline channelPipeline = (ChannelPipeline) getConstructor().newInstance(channel);
    HttpClientCodec httpHandler = new HttpClientCodec();

    // no handlers initially
    assertTrue(
        channelPipeline.first() == null
            || "io.netty.channel.DefaultChannelPipeline$TailHandler"
                .equals(channelPipeline.first().getClass().getName()));
    assertNull(channelPipeline.last());
    assertEquals(0, channelPipeline.toMap().size());

    // add handler
    channelPipeline.addLast("http", httpHandler);
    assertEquals(httpHandler, channelPipeline.first());

    // our handler was also added
    assertEquals("HttpClientTracingHandler", channelPipeline.last().getClass().getSimpleName());

    // our handler is not in handlers map
    assertEquals(1, channelPipeline.toMap().size());

    // our handler is not in handlers iterator
    List<ChannelHandler> list = new ArrayList<>();
    channelPipeline
        .iterator()
        .forEachRemaining(
            entry -> {
              list.add(entry.getValue());
            });
    assertEquals(1, list.size());
  }

  private static class NoopChannelHandler extends ChannelHandlerAdapter {}
}
