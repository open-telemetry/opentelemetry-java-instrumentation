/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hbase.client.v2_0;

import static io.opentelemetry.javaagent.instrumentation.hbase.client.v2_0.HbaseSingletons.startBatch;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.bootstrap.CallDepth;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.util.List;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.hadoop.hbase.client.HTable;
import org.apache.hadoop.hbase.client.Row;

class HTableInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.apache.hadoop.hbase.client.HTable");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    // matches both batch(List, Object[]) and batch(List, Object[], int)
    transformer.applyAdviceToMethod(
        named("batch").and(takesArgument(0, named("java.util.List"))),
        getClass().getName() + "$BatchAdvice");
  }

  @SuppressWarnings("unused")
  public static class BatchAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    @Nullable
    public static Scope onEnter(
        @Advice.This HTable table, @Advice.Argument(0) List<? extends Row> actions) {
      // batch(List, Object[]) delegates to batch(List, Object[], int); only record for the
      // outermost call so the batch metadata (and any empty-batch span) is produced once.
      CallDepth callDepth = CallDepth.forClass(HTable.class);
      if (callDepth.getAndIncrement() > 0) {
        return null;
      }
      return startBatch(table.getName(), actions);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(@Advice.Enter @Nullable Scope scope) {
      CallDepth callDepth = CallDepth.forClass(HTable.class);
      if (callDepth.decrementAndGet() > 0) {
        return;
      }
      if (scope != null) {
        scope.close();
      }
    }
  }
}
