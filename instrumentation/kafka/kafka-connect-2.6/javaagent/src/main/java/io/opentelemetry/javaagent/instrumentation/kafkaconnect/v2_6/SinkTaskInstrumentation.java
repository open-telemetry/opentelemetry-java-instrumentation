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
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.util.Collection;
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

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.Argument(0) Collection<SinkRecord> records,
        @Advice.Local("otelTask") KafkaConnectTask task,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {

      Context parentContext = Java8BytecodeBridge.currentContext();

      // Extract context from first record if available
      if (!records.isEmpty()) {
        SinkRecord firstRecord = records.iterator().next();
        Context extractedContext =
            KafkaConnectSingletons.propagator()
                .extract(
                    parentContext, firstRecord, KafkaConnectSingletons.sinkRecordHeaderGetter());
        parentContext = extractedContext;
      }

      task = new KafkaConnectTask(records);
      if (!instrumenter().shouldStart(parentContext, task)) {
        return;
      }

      context = instrumenter().start(parentContext, task);
      scope = context.makeCurrent();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(
        @Advice.Thrown Throwable throwable,
        @Advice.Local("otelTask") KafkaConnectTask task,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {

      if (scope == null) {
        return;
      }
      scope.close();
      instrumenter().end(context, task, null, throwable);
    }
  }
}
