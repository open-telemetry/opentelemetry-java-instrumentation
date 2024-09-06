/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v4_1;

import static org.assertj.core.api.Assertions.assertThat;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.DefaultChannelPipeline;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.HttpClientCodec;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ChannelPipelineTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private EmbeddedChannel channel;
  private ChannelPipeline channelPipeline;
  private ChannelHandler handler;

  static Stream<Arguments> removeMethodProvider() {
    return Stream.of(
        Arguments.of(
            "by instance",
            (BiConsumer<ChannelPipeline, ChannelHandler>)
                (pipeline, handler) -> pipeline.remove(handler)),
        Arguments.of(
            "by class",
            (BiConsumer<ChannelPipeline, ChannelHandler>)
                (pipeline, handler) -> pipeline.remove(handler.getClass())),
        Arguments.of(
            "by name",
            (BiConsumer<ChannelPipeline, ChannelHandler>)
                (pipeline, handler) -> pipeline.remove("http")),
        Arguments.of(
            "first",
            (BiConsumer<ChannelPipeline, ChannelHandler>)
                (pipeline, handler) -> pipeline.removeFirst()));
  }

  @BeforeEach
  void setUp() {
    channel = new EmbeddedChannel();
    channelPipeline = new DefaultChannelPipeline(channel) {};
    handler = new HttpClientCodec();
  }

  // regression test for
  // https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/1373
  // and https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/4040
  @ParameterizedTest(name = "{0}")
  @MethodSource("removeMethodProvider")
  @DisplayName("Test remove our handler")
  void testRemoveOurHandler(
      String testName, BiConsumer<ChannelPipeline, ChannelHandler> removeMethod) {
    // when no handlers
    assertThat(channelPipeline.first()).isNull();
    assertThat(channelPipeline.last()).isNull();
    assertThat(channelPipeline.toMap().size()).isEqualTo(0);

    // then add handler
    channelPipeline.addLast("http", handler);
    assertThat(channelPipeline.first()).isEqualTo(handler);
    assertThat(channelPipeline.toMap().size()).isEqualTo(1);

    // our handler was also added
    assertThat(channelPipeline.last().getClass().getSimpleName())
        .isEqualTo("HttpClientTracingHandler");

    // and
    removeMethod.accept(channelPipeline, handler);
    // removing handler also removes our handler
    assertThat(channelPipeline.first()).isNull();
    assertThat(channelPipeline.last()).isNull();
    assertThat(channelPipeline.toMap().size()).isEqualTo(0);
  }

  static Stream<Arguments> replaceMethodProvider() {
    return Stream.of(
        Arguments.of(
            "by instance",
            (ReplaceMethod)
                (pipeline, oldName, oldHandler, newName, newHandler) ->
                    pipeline.replace(oldHandler, newName, newHandler)),
        Arguments.of(
            "by class",
            (ReplaceMethod)
                (pipeline, oldName, oldHandler, newName, newHandler) ->
                    pipeline.replace(oldHandler.getClass(), newName, newHandler)),
        Arguments.of(
            "by name",
            (ReplaceMethod)
                (pipeline, oldName, oldHandler, newName, newHandler) ->
                    pipeline.replace(oldName, newName, newHandler)));
  }

  @FunctionalInterface
  interface ReplaceMethod {
    void accept(
        ChannelPipeline pipeline,
        String oldName,
        ChannelHandler oldHandler,
        String newName,
        ChannelHandler newHandler);
  }

  // regression test for
  //   https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/4040
  @ParameterizedTest(name = "{0}")
  @MethodSource("replaceMethodProvider")
  @DisplayName("Test remove our handler")
  void testReplaceHandlerDesc(String desc, ReplaceMethod replaceMethod) {
    // no handlers initially
    assertThat(channelPipeline.first()).isNull();
    assertThat(channelPipeline.last()).isNull();
    assertThat(channelPipeline.toMap().size()).isEqualTo(0);

    NoopChannelHandler noopHandler = new NoopChannelHandler();
    channelPipeline.addFirst("test", noopHandler);

    // only the noop handler
    assertThat(channelPipeline.first()).isEqualTo(noopHandler);
    assertThat(channelPipeline.toMap().size()).isEqualTo(1);

    // when
    replaceMethod.accept(channelPipeline, "test", noopHandler, "http", handler);

    // noop handler was removed; http and instrumentation handlers were added
    assertThat(channelPipeline.toMap().size()).isEqualTo(1);
    assertThat(channelPipeline.first()).isEqualTo(handler);
    assertThat(channelPipeline.last().getClass().getSimpleName())
        .isEqualTo("HttpClientTracingHandler");

    // replace again
    ChannelHandler anotherNoopHandler = new NoopChannelHandler();
    replaceMethod.accept(channelPipeline, "http", handler, "test", anotherNoopHandler);
    // http and instrumentation handlers were removed; noop handler was added
    assertThat(channelPipeline.first()).isEqualTo(anotherNoopHandler);
    assertThat(channelPipeline.toMap().size()).isEqualTo(1);
  }

  // regression test for
  // https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/4056
  @DisplayName("Should addAfter and removeLast handler")
  @Test
  void testAddAfterAndRemoveLast() {
    // no handlers initially
    assertThat(channelPipeline.first()).isNull();
    assertThat(channelPipeline.last()).isNull();
    assertThat(channelPipeline.toMap().size()).isEqualTo(0);

    channelPipeline.addLast("http", handler);
    assertThat(channelPipeline.toMap().size()).isEqualTo(1);
    assertThat(channelPipeline.first()).isEqualTo(handler);
    assertThat(channelPipeline.last().getClass().getSimpleName())
        .isEqualTo("HttpClientTracingHandler");

    ChannelHandler noopHandler = new NoopChannelHandler();
    channelPipeline.addAfter("http", "noop", noopHandler);
    // instrumentation handler is between with http and noop;
    assertThat(channelPipeline.toMap().size()).isEqualTo(2);
    assertThat(channelPipeline.first()).isEqualTo(handler);
    assertThat(channelPipeline.last()).isEqualTo(noopHandler);

    channelPipeline.removeLast();
    // http and instrumentation handlers will be remained;
    assertThat(channelPipeline.toMap().size()).isEqualTo(1);
    assertThat(channelPipeline.first()).isEqualTo(handler);
    assertThat(channelPipeline.last().getClass().getSimpleName())
        .isEqualTo("HttpClientTracingHandler");

    ChannelHandler removed = channelPipeline.removeLast();
    // removing tracing handler also removes the http handler and returns it
    assertThat(channelPipeline.toMap().size()).isEqualTo(0);
    assertThat(channelPipeline.first()).isNull();
    assertThat(channelPipeline.last()).isNull();
    assertThat(removed).isEqualTo(handler);
  }

  // regression test for
  // https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/10377
  @DisplayName("our handler not in handlers map")
  @Test
  void testHandlerNotInHandlersMap() {
    // no handlers initially
    assertThat(channelPipeline.first()).isNull();
    assertThat(channelPipeline.last()).isNull();
    assertThat(channelPipeline.toMap().size()).isEqualTo(0);

    // add handler
    channelPipeline.addLast("http", handler);
    assertThat(channelPipeline.first()).isEqualTo(handler);
    assertThat(channelPipeline.last().getClass().getSimpleName())
        .isEqualTo("HttpClientTracingHandler");
    // our handler is not in handlers map
    assertThat(channelPipeline.toMap().size()).isEqualTo(1);
    // our handler is not in handlers iterator
    List<Map.Entry<String, ChannelHandler>> list = new ArrayList<>();
    channelPipeline.iterator().forEachRemaining(list::add);
    assertThat(list.size()).isEqualTo(1);
  }

  private static class NoopChannelHandler extends ChannelHandlerAdapter {}
}
