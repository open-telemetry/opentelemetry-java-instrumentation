/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kafkaclients.v0_11;

import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned;
import net.bytebuddy.asm.Advice.AssignReturned.ToArguments.ToArgument;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.kafka.clients.consumer.OffsetCommitCallback;

class KafkaConsumerDelegateInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.apache.kafka.clients.consumer.internals.ClassicKafkaConsumer")
        .or(named("org.apache.kafka.clients.consumer.internals.LegacyKafkaConsumer"))
        .or(named("org.apache.kafka.clients.consumer.internals.AsyncKafkaConsumer"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("commitAsync").and(isPublic()).and(takesArguments(0)).and(returns(void.class)),
        getClass().getName() + "$CommitAsyncNoArgumentsAdvice");
    transformer.applyAdviceToMethod(
        named("commitAsync")
            .and(isPublic())
            .and(takesArguments(1))
            .and(takesArgument(0, named("org.apache.kafka.clients.consumer.OffsetCommitCallback")))
            .and(returns(void.class)),
        getClass().getName() + "$CommitAsyncCallbackAdvice");
    transformer.applyAdviceToMethod(
        named("commitAsync")
            .and(isPublic())
            .and(takesArguments(2))
            .and(takesArgument(0, Map.class))
            .and(takesArgument(1, named("org.apache.kafka.clients.consumer.OffsetCommitCallback")))
            .and(returns(void.class)),
        getClass().getName() + "$CommitAsyncOffsetsCallbackAdvice");
    transformer.applyAdviceToMethod(
        named("commit").and(returns(named("java.util.concurrent.CompletableFuture"))),
        getClass().getName() + "$CommitFutureAdvice");
  }

  @SuppressWarnings("unused")
  public static class CommitAsyncNoArgumentsAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static KafkaCommitAsyncTracing.AdviceScope onEnter() {
      return KafkaCommitAsyncTracing.join(null);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.Thrown @Nullable Throwable error,
        @Advice.Enter KafkaCommitAsyncTracing.AdviceScope adviceScope) {
      adviceScope.end(error);
    }
  }

  @SuppressWarnings("unused")
  public static class CommitAsyncCallbackAdvice {

    @AssignReturned.ToArguments(@ToArgument(value = 0, index = 1))
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Object[] onEnter(@Advice.Argument(0) @Nullable OffsetCommitCallback callback) {
      KafkaCommitAsyncTracing.AdviceScope adviceScope = KafkaCommitAsyncTracing.join(callback);
      return new Object[] {adviceScope, adviceScope.callback()};
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.Thrown @Nullable Throwable error, @Advice.Enter Object[] enterResult) {
      ((KafkaCommitAsyncTracing.AdviceScope) enterResult[0]).end(error);
    }
  }

  @SuppressWarnings("unused")
  public static class CommitAsyncOffsetsCallbackAdvice {

    @AssignReturned.ToArguments(@ToArgument(value = 1, index = 1))
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Object[] onEnter(@Advice.Argument(1) @Nullable OffsetCommitCallback callback) {
      KafkaCommitAsyncTracing.AdviceScope adviceScope = KafkaCommitAsyncTracing.join(callback);
      return new Object[] {adviceScope, adviceScope.callback()};
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.Thrown @Nullable Throwable error, @Advice.Enter Object[] enterResult) {
      ((KafkaCommitAsyncTracing.AdviceScope) enterResult[0]).end(error);
    }
  }

  @SuppressWarnings("unused")
  public static class CommitFutureAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(@Advice.Return @Nullable CompletableFuture<?> commitFuture) {
      KafkaCommitAsyncTracing.endOnCompletion(commitFuture);
    }
  }
}
