/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kafkaconnect.v2_6;

import static net.bytebuddy.matcher.ElementMatchers.nameContains;

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
import java.lang.reflect.Method;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

public class SinkTaskInstrumentation implements TypeInstrumentation {

  // Change from private to public:
  public static final Logger logger = Logger.getLogger(SinkTaskInstrumentation.class.getName());

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    logger.info("KafkaConnect: typeMatcher called");
    
    // Temporarily match ANY class with "Sink" in the name to see what's available
    return nameContains("Sink");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    logger.info("KafkaConnect: transform() called - about to apply put() advice");
    
    // Switch back to instrumenting the put method
    transformer.applyAdviceToMethod(
        named("put").and(takesArgument(0, Collection.class)).and(isPublic()),
        SinkTaskInstrumentation.class.getName() + "$SinkTaskPutAdvice");
  }

  @SuppressWarnings("unused")
  public static class ConstructorAdvice {
    // Change from private to public:
    public static final Logger logger = Logger.getLogger(ConstructorAdvice.class.getName());

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(@Advice.This Object thiz) {
      String className = thiz.getClass().getName();
      logger.info("KafkaConnect: Created instance of: " + className);
      
      // Special logging for the classes we care about
      if (className.contains("JdbcSinkTask")) {
        logger.info("🎯 KafkaConnect: Found JdbcSinkTask! " + className);
      }
      if (className.contains("SinkTask")) {
        logger.info("📋 KafkaConnect: Found SinkTask subclass: " + className);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class AllMethodsAdvice {
    private static final Logger logger = Logger.getLogger(AllMethodsAdvice.class.getName());

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(@Advice.Origin Method method, @Advice.This Object thiz) {
      // Only log methods that look interesting
      String methodName = method.getName();
      if (methodName.contains("put") || methodName.contains("process") || 
          methodName.contains("write") || methodName.contains("execute")) {
        logger.info("KafkaConnect: Method called: " + thiz.getClass().getName() + "." + methodName + "()");
      }
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
      
      logger.info("🚀 KafkaConnect: SinkTask.put() ENTER called with " + records.size() + " records");
      
      Context parentContext = Java8BytecodeBridge.currentContext();
      
      // TEMPORARILY COMMENT OUT this problematic line:
      /*
      if (!records.isEmpty()) {
        SinkRecord firstRecord = records.iterator().next();
        Context extractedContext = KafkaConnectSingletons.propagator()
            .extract(parentContext, firstRecord, SinkRecordHeadersGetter.INSTANCE);
        // ... rest of extraction logic
      }
      */

      task = new KafkaConnectTask(records);
      if (!KafkaConnectSingletons.instrumenter().shouldStart(parentContext, task)) {
        logger.info("KafkaConnect: Instrumenter shouldStart returned false");
        return;
      }
      
      context = KafkaConnectSingletons.instrumenter().start(parentContext, task);
      scope = context.makeCurrent();
      logger.info("🎯 KafkaConnect: Started span with context: " + context);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(
        @Advice.Thrown Throwable throwable,
        @Advice.Local("otelTask") KafkaConnectTask task,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      
      logger.info("KafkaConnect: SinkTask.put() EXIT called");
      
      if (scope == null) {
        logger.warning("KafkaConnect: Scope is null, exiting");
        return;
      }
      scope.close();
      KafkaConnectSingletons.instrumenter().end(context, task, null, throwable);
      logger.info("KafkaConnect: Span ended");
    }
  }
}
