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

import io.opentelemetry.javaagent.bootstrap.CallDepth;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.util.List;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.storm.spout.ISpoutOutputCollector;

class SpoutOutputCollectorInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("org.apache.storm.spout.ISpoutOutputCollector");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named("org.apache.storm.spout.ISpoutOutputCollector"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("emit")
            .and(isPublic())
            .and(takesArguments(3))
            .and(takesArgument(0, String.class))
            .and(takesArgument(1, List.class))
            .and(takesArgument(2, Object.class)),
        getClass().getName() + "$EmitAdvice");
    transformer.applyAdviceToMethod(
        named("emitDirect")
            .and(isPublic())
            .and(takesArguments(4))
            .and(takesArgument(0, int.class))
            .and(takesArgument(1, String.class))
            .and(takesArgument(2, List.class))
            .and(takesArgument(3, Object.class)),
        getClass().getName() + "$EmitDirectAdvice");
  }

  @SuppressWarnings("unused")
  public static class EmitAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static StormEmitScope onEnter(
        @Advice.Argument(0) @Nullable String streamId, @Advice.Argument(1) List<Object> values) {
      return StormEmitScope.start(
          CallDepth.forClass(ISpoutOutputCollector.class), streamId, values);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.Enter StormEmitScope scope, @Advice.Thrown @Nullable Throwable throwable) {
      scope.end(throwable);
    }
  }

  @SuppressWarnings("unused")
  public static class EmitDirectAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static StormEmitScope onEnter(
        @Advice.Argument(1) @Nullable String streamId, @Advice.Argument(2) List<Object> values) {
      return StormEmitScope.start(
          CallDepth.forClass(ISpoutOutputCollector.class), streamId, values);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.Enter StormEmitScope scope, @Advice.Thrown @Nullable Throwable throwable) {
      scope.end(throwable);
    }
  }
}
