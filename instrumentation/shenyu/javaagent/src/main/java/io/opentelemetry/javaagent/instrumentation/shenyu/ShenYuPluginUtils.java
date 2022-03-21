/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.shenyu;

import static io.opentelemetry.javaagent.instrumentation.shenyu.ShenYuSingletons.instrumenter;

import io.opentelemetry.context.Context;
import java.util.Deque;
import java.util.LinkedList;
import javax.annotation.Nullable;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

public class ShenYuPluginUtils {

  public static final String ON_SPAN_END = ShenYuPluginUtils.class.getName() + ".Context";

  public static void registerSpan(ServerWebExchange exchange, Context context, Object plugin) {
    ShenYuPlugin shenYuPlugin = (ShenYuPlugin) exchange.getAttributes()
        .getOrDefault(ON_SPAN_END, new ShenYuPlugin());

    shenYuPlugin.getOnSpanEndDeque().addLast(t -> instrumenter().end(context, plugin, null, t));

    exchange.getAttributes()
        .put(ON_SPAN_END, shenYuPlugin);
  }

  public static <T> Mono<T> end(Mono<T> mono, ServerWebExchange exchange) {
    return mono.doOnError(throwable -> end(exchange, throwable))
        .doOnSuccess(t -> end(exchange, null))
        .doOnCancel(() -> end(exchange, null));
  }

  private static void end(ServerWebExchange exchange, @Nullable Throwable throwable) {
    ShenYuPlugin shenYuPlugin = (ShenYuPlugin) exchange.getAttributes().get(ON_SPAN_END);
    Deque<OnSpanEnd> onSpanEndDeque = shenYuPlugin.getOnSpanEndDeque();

    OnSpanEnd onSpanEnd = onSpanEndDeque.pollLast();
    if (onSpanEnd != null) {
      onSpanEnd.end(throwable);
    }

  }

  @FunctionalInterface
  interface OnSpanEnd {
    void end(Throwable throwable);
  }

  /**
   * Many Plugins in ShenYu, so record these in Deque
   */
  protected static class ShenYuPlugin {
    private final Deque<OnSpanEnd> onSpanEndDeque;

    public ShenYuPlugin() {
      onSpanEndDeque = new LinkedList<>();
    }

    public Deque<OnSpanEnd> getOnSpanEndDeque() {
      return onSpanEndDeque;
    }
  }

}
