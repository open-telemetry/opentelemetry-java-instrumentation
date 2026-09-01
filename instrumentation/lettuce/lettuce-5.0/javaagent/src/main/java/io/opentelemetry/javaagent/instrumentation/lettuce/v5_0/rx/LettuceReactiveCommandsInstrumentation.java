/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v5_0.rx;

import static io.opentelemetry.javaagent.instrumentation.lettuce.v5_0.LettuceInstrumentationUtil.expectsResponse;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.nameEndsWith;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.lettuce.core.AbstractRedisReactiveCommands;
import io.lettuce.core.protocol.RedisCommand;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.util.function.Supplier;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned;
import net.bytebuddy.asm.Advice.AssignReturned.ToArguments.ToArgument;
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
        named("createMono")
            .and(takesArgument(0, Supplier.class))
            .and(returns(named("reactor.core.publisher.Mono"))),
        getClass().getName() + "$CreateMonoAdvice");
    transformer.applyAdviceToMethod(
        nameStartsWith("create")
            .and(nameEndsWith("Flux"))
            .and(isPublic())
            .and(takesArgument(0, Supplier.class))
            .and(returns(named("reactor.core.publisher.Flux"))),
        getClass().getName() + "$CreateFluxAdvice");
  }

  @SuppressWarnings("unused")
  public static class CreateMonoAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    @AssignReturned.ToArguments(@ToArgument(0))
    public static <K, V, T> LettuceReactiveCommandSupplier<K, V, T> wrapCommandSupplier(
        @Advice.This AbstractRedisReactiveCommands<K, V> commands,
        @Advice.Argument(0) Supplier<RedisCommand<K, V, T>> supplier) {
      return new LettuceReactiveCommandSupplier<>(supplier, commands.getConnection());
    }

    // throwables wouldn't matter here, because no spans have been started due to redis command not
    // being run until the user subscribes to the Mono publisher
    @AssignReturned.ToReturned
    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static <K, V, T> Mono<T> monitorSpan(
        @Advice.This AbstractRedisReactiveCommands<K, V> commands,
        @Advice.Return Mono<T> originalPublisher,
        @Advice.Enter LettuceReactiveCommandSupplier<K, V, T> commandSupplier) {
      RedisCommand<K, V, T> command = commandSupplier.getTracingCommand();
      Mono<T> publisher = originalPublisher;
      boolean finishSpanOnClose = !expectsResponse(command);
      LettuceMonoDualConsumer<? super Subscription, T> mdc =
          new LettuceMonoDualConsumer<>(command, commands.getConnection(), finishSpanOnClose);
      publisher = publisher.doOnSubscribe(mdc);
      if (!finishSpanOnClose) {
        publisher = mdc.finishSpanOnTerminal(publisher);
      }
      return publisher;
    }
  }

  @SuppressWarnings("unused")
  public static class CreateFluxAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    @AssignReturned.ToArguments(@ToArgument(0))
    public static <K, V, T> LettuceReactiveCommandSupplier<K, V, T> wrapCommandSupplier(
        @Advice.This AbstractRedisReactiveCommands<K, V> commands,
        @Advice.Argument(0) Supplier<RedisCommand<K, V, T>> supplier) {
      return new LettuceReactiveCommandSupplier<>(supplier, commands.getConnection());
    }

    // if there is an exception thrown, then don't make spans
    @AssignReturned.ToReturned
    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static <K, V, T> Flux<T> monitorSpan(
        @Advice.This AbstractRedisReactiveCommands<K, V> commands,
        @Advice.Return Flux<T> originalPublisher,
        @Advice.Enter LettuceReactiveCommandSupplier<K, V, T> commandSupplier) {
      RedisCommand<K, V, T> command = commandSupplier.getTracingCommand();
      Flux<T> publisher = originalPublisher;

      boolean expectsResponse = expectsResponse(command);
      LettuceFluxTerminationRunnable handler =
          new LettuceFluxTerminationRunnable(command, commands.getConnection(), expectsResponse);
      publisher = publisher.doOnSubscribe(handler.getOnSubscribeConsumer());
      if (expectsResponse) {
        publisher = publisher.doOnEach(handler);
        publisher = publisher.doOnCancel(handler);
      }
      return publisher;
    }
  }
}
