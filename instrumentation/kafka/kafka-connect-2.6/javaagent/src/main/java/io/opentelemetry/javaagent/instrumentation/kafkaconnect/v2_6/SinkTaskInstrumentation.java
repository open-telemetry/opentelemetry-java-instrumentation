/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kafkaconnect.v2_6;

import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
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
import java.util.logging.Logger;

public class SinkTaskInstrumentation implements TypeInstrumentation {

  private static final Logger logger = Logger.getLogger(SinkTaskInstrumentation.class.getName());

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    logger.info("KafkaConnect: typeMatcher called");
    return hasSuperType(named("org.apache.kafka.connect.sink.SinkTask"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    logger.info("KafkaConnect: transform() called - applying put() advice");
    
    // Add advice to constructor to see what classes we're instrumenting
    transformer.applyAdviceToMethod(
        named("<init>"),
        SinkTaskInstrumentation.class.getName() + "$ConstructorAdvice");
        
    transformer.applyAdviceToMethod(
        named("put").and(takesArgument(0, Collection.class)).and(isPublic()),
        SinkTaskInstrumentation.class.getName() + "$SinkTaskPutAdvice");
  }

  @SuppressWarnings("unused")
  public static class ConstructorAdvice {
    
    public static final Logger logger = Logger.getLogger(ConstructorAdvice.class.getName());

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(@Advice.This Object thiz) {
      String className = thiz.getClass().getName();
      logger.info("KafkaConnect: Instrumented class instantiated: " + className);
    }
  }

  @SuppressWarnings("unused")
  public static class SinkTaskPutAdvice {

    public static final Logger logger = Logger.getLogger(SinkTaskPutAdvice.class.getName());

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.Argument(0) Collection<SinkRecord> records,
        @Advice.Local("otelTask") KafkaConnectTask task,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      
      logger.info("KafkaConnect: SinkTask.put() called with " + records.size() + " records");
      
            Context parentContext = Java8BytecodeBridge.currentContext();

      // Extract context from first record if available
      if (!records.isEmpty()) {
        SinkRecord firstRecord = records.iterator().next();
        Context extractedContext = KafkaConnectSingletons.propagator()
            .extract(parentContext, firstRecord, KafkaConnectSingletons.sinkRecordHeaderGetter());
        parentContext = extractedContext;
      }

      task = new KafkaConnectTask(records);
      if (!KafkaConnectSingletons.instrumenter().shouldStart(parentContext, task)) {
        logger.info("KafkaConnect: Instrumenter shouldStart returned false");
        return;
      }
      
      context = KafkaConnectSingletons.instrumenter().start(parentContext, task);
      scope = context.makeCurrent();
      logger.info("KafkaConnect: Started span");
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(
        @Advice.Thrown Throwable throwable,
        @Advice.Local("otelTask") KafkaConnectTask task,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      
      if (scope == null) {
        logger.info("KafkaConnect: Scope is null, exiting");
        return;
      }
      scope.close();
      KafkaConnectSingletons.instrumenter().end(context, task, null, throwable);
      logger.info("KafkaConnect: Span ended");
    }
    

  }
}
