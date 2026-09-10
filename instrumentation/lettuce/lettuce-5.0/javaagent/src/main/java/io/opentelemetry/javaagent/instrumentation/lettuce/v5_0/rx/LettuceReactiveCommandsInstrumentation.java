/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v5_0.rx;

import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.nameEndsWith;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.lettuce.core.AbstractRedisReactiveCommands;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.util.function.Supplier;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
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

    @AssignReturned.ToReturned
    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static <K, V, T> Mono<T> monitorSpan(
        @Advice.This AbstractRedisReactiveCommands<K, V> commands,
        @Advice.Return Mono<T> originalPublisher) {
      return LettuceMonoDualConsumer.monitor(originalPublisher, commands.getConnection());
    }
  }

  @SuppressWarnings("unused")
  public static class CreateFluxAdvice {

    @AssignReturned.ToReturned
    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static <K, V, T> Flux<T> monitorSpan(
        @Advice.This AbstractRedisReactiveCommands<K, V> commands,
        @Advice.Return Flux<T> originalPublisher) {
      return LettuceFluxTerminationRunnable.monitor(originalPublisher, commands.getConnection());
    }
  }
}
