/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.batch.v3_0;

import static io.opentelemetry.javaagent.instrumentation.spring.batch.v3_0.SpringBatchInstrumentationConfig.shouldTraceItems;
import static io.opentelemetry.javaagent.instrumentation.spring.batch.v3_0.item.ItemSingletons.getChunkContext;
import static io.opentelemetry.javaagent.instrumentation.spring.batch.v3_0.item.ItemSingletons.itemInstrumenter;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.instrumentation.spring.batch.v3_0.item.ItemSingletons;
import javax.annotation.Nullable;
import org.springframework.batch.core.scope.context.ChunkContext;

public final class AdviceScope {
  private final Context context;
  private final String item;
  private final Scope scope;

  private AdviceScope(Context context, Scope scope, String item) {
    this.context = context;
    this.scope = scope;
    this.item = item;
  }

  @Nullable
  public static AdviceScope enter(String itemOperationName) {
    Context parentContext = Context.current();
    ChunkContext chunkContext = getChunkContext(parentContext);
    if (chunkContext == null || !shouldTraceItems()) {
      return null;
    }

    String item = ItemSingletons.itemName(chunkContext, itemOperationName);
    if (!itemInstrumenter().shouldStart(parentContext, item)) {
      return null;
    }
    Context context = itemInstrumenter().start(parentContext, item);
    return new AdviceScope(context, context.makeCurrent(), item);
  }

  public void exit(@Nullable Throwable thrown) {
    scope.close();
    itemInstrumenter().end(context, item, null, thrown);
  }
}
