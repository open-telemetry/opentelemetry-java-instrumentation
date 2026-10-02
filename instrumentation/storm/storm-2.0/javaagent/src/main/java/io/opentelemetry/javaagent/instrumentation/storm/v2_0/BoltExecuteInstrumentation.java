/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.storm.v2_0;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.javaagent.bootstrap.CallDepth;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.util.Map;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.storm.task.IBolt;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.TupleImpl;

class BoltExecuteInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("org.apache.storm.task.IBolt");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named("org.apache.storm.task.IBolt"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("execute")
            .and(isPublic())
            .and(takesArguments(1))
            .and(takesArgument(0, named("org.apache.storm.tuple.Tuple"))),
        getClass().getName() + "$ExecuteAdvice");
  }

  public static class ExecuteScope {
    private final Instrumenter<TupleImpl, Void> instrumenter;
    private final Context context;
    private final TupleImpl tuple;
    private final Scope scope;

    ExecuteScope(
        Instrumenter<TupleImpl, Void> instrumenter, Context context, TupleImpl tuple, Scope scope) {
      this.instrumenter = instrumenter;
      this.context = context;
      this.tuple = tuple;
      this.scope = scope;
    }

    void end(@Nullable Throwable throwable) {
      scope.close();
      instrumenter.end(context, tuple, null, throwable);
    }
  }

  @SuppressWarnings("unused")
  public static class ExecuteAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    @Nullable
    public static ExecuteScope onEnter(@Advice.Argument(0) Tuple tuple) {
      CallDepth callDepth = CallDepth.forClass(IBolt.class);
      if (callDepth.getAndIncrement() > 0 || !(tuple instanceof TupleImpl)) {
        return null;
      }

      TupleImpl tupleImpl = (TupleImpl) tuple;
      Map<String, String> headers = VirtualFieldStore.getHeaders(tupleImpl);
      if (headers == null || headers.isEmpty()) {
        // Not a tuple produced by an instrumented emit, e.g. an internal ack tuple.
        return null;
      }

      Instrumenter<TupleImpl, Void> instrumenter = StormSingletons.processInstrumenter();
      Context parentContext = Context.current();
      if (!instrumenter.shouldStart(parentContext, tupleImpl)) {
        return null;
      }

      Context context = instrumenter.start(parentContext, tupleImpl);
      return new ExecuteScope(instrumenter, context, tupleImpl, context.makeCurrent());
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.Enter @Nullable ExecuteScope scope, @Advice.Thrown @Nullable Throwable throwable) {
      if (scope != null) {
        scope.end(throwable);
      }
      CallDepth.forClass(IBolt.class).decrementAndGet();
    }
  }
}
