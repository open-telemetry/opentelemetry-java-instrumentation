/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kafkaconnect.v2_6;

import static io.opentelemetry.javaagent.instrumentation.kafkaconnect.v2_6.KafkaConnectSingletons.instrumenter;
import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.util.Collection;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.kafka.connect.sink.SinkRecord;

public class SinkTaskInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return hasSuperType(named("org.apache.kafka.connect.sink.SinkTask"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("put").and(takesArgument(0, Collection.class)).and(isPublic()),
        SinkTaskInstrumentation.class.getName() + "$SinkTaskPutAdvice");
  }

  @SuppressWarnings("unused")
  public static class SinkTaskPutAdvice {

    public static class AdviceScope {
      private final KafkaConnectTask task;
      private final Context context;
      private final Scope scope;

      private AdviceScope(KafkaConnectTask task, Context context, Scope scope) {
        this.task = task;
        this.context = context;
        this.scope = scope;
      }

      @Nullable
      public static AdviceScope start(Collection<SinkRecord> records) {
        Context parentContext = Context.current();

        KafkaConnectTask task = new KafkaConnectTask(records);
        if (!instrumenter().shouldStart(parentContext, task)) {
          return null;
        }

        Context context = instrumenter().start(parentContext, task);
        return new AdviceScope(task, context, context.makeCurrent());
      }

      public void end(@Nullable Throwable throwable) {
        scope.close();
        instrumenter().end(context, task, null, throwable);
      }
    }

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static AdviceScope onEnter(@Advice.Argument(0) Collection<SinkRecord> records) {
      return AdviceScope.start(records);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(
        @Advice.Thrown @Nullable Throwable throwable,
        @Advice.Enter @Nullable AdviceScope adviceScope) {

      if (adviceScope != null) {
        adviceScope.end(throwable);
      }
    }
  }
}
