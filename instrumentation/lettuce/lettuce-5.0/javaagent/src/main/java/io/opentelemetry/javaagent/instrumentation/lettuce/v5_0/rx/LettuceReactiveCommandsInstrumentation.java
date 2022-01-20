/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v5_0.rx;

import static io.opentelemetry.javaagent.instrumentation.lettuce.v5_0.LettuceInstrumentationUtil.expectsResponse;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.nameEndsWith;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.lettuce.core.protocol.RedisCommand;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.util.function.Supplier;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.reactivestreams.Subscription;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class LettuceReactiveCommandsInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("io.lettuce.core.AbstractRedisReactiveCommands");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod()
            .and(named("createMono"))
            .and(takesArgument(0, Supplier.class))
            .and(returns(named("reactor.core.publisher.Mono"))),
        LettuceReactiveCommandsInstrumentation.class.getName() + "$CreateMonoAdvice");
    transformer.applyAdviceToMethod(
        isMethod()
            .and(nameStartsWith("create"))
            .and(nameEndsWith("Flux"))
            .and(isPublic())
            .and(takesArgument(0, Supplier.class))
            .and(returns(named("reactor.core.publisher.Flux"))),
        LettuceReactiveCommandsInstrumentation.class.getName() + "$CreateFluxAdvice");
  }

  @SuppressWarnings("unused")
  public static class CreateMonoAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static <K, V, T> RedisCommand<K, V, T> extractCommandName(
        @Advice.Argument(0) Supplier<RedisCommand<K, V, T>> supplier) {
      return supplier.get();
    }

    // throwables wouldn't matter here, because no spans have been started due to redis command not
    // being run until the user subscribes to the Mono publisher
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static <K, V, T> void monitorSpan(
        @Advice.Enter RedisCommand<K, V, T> command,
        @Advice.Return(readOnly = false) Mono<T> publisher) {
      boolean finishSpanOnClose = !expectsResponse(command);
      LettuceMonoDualConsumer<? super Subscription, T> mdc =
          new LettuceMonoDualConsumer<>(command, finishSpanOnClose);
      publisher = publisher.doOnSubscribe(mdc);
      // register the call back to close the span only if necessary
      if (!finishSpanOnClose) {
        publisher = publisher.doOnSuccessOrError(mdc);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class CreateFluxAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static <K, V, T> RedisCommand<K, V, T> extractCommandName(
        @Advice.Argument(0) Supplier<RedisCommand<K, V, T>> supplier) {
      return supplier.get();
    }

    // if there is an exception thrown, then don't make spans
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static <K, V, T> void monitorSpan(
        @Advice.Enter RedisCommand<K, V, T> command,
        @Advice.Return(readOnly = false) Flux<T> publisher) {

      boolean expectsResponse = expectsResponse(command);
      LettuceFluxTerminationRunnable handler =
          new LettuceFluxTerminationRunnable(command, expectsResponse);
      publisher = publisher.doOnSubscribe(handler.getOnSubscribeConsumer());
      // don't register extra callbacks to finish the spans if the command being instrumented is one
      // of those that return
      // Mono<Void> (In here a flux is created first and then converted to Mono<Void>)
      if (expectsResponse) {
        publisher = publisher.doOnEach(handler);
        publisher = publisher.doOnCancel(handler);
      }
    }
  }
}
