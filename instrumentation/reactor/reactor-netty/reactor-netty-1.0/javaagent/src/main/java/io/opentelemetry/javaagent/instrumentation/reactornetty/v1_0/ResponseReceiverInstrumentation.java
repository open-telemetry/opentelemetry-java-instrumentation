/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.reactornetty.v1_0;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.javaagent.bootstrap.CallDepth;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.util.function.BiFunction;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.ByteBufFlux;
import reactor.netty.ByteBufMono;
import reactor.netty.Connection;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.client.HttpClientResponse;

public class ResponseReceiverInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("reactor.netty.http.client.HttpClient$ResponseReceiver");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named("reactor.netty.http.client.HttpClient$ResponseReceiver"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("response").and(takesArguments(0)).and(returns(named("reactor.core.publisher.Mono"))),
        this.getClass().getName() + "$ResponseMonoAdvice");
    transformer.applyAdviceToMethod(
        named("response")
            .and(takesArguments(1))
            .and(takesArgument(0, BiFunction.class))
            .and(returns(named("reactor.core.publisher.Flux"))),
        this.getClass().getName() + "$ResponseFluxAdvice");
    transformer.applyAdviceToMethod(
        named("responseConnection")
            .and(takesArguments(1))
            .and(takesArgument(0, BiFunction.class))
            .and(returns(named("reactor.core.publisher.Flux"))),
        this.getClass().getName() + "$ResponseConnectionAdvice");
    transformer.applyAdviceToMethod(
        named("responseContent")
            .and(takesArguments(0))
            .and(returns(named("reactor.netty.ByteBufFlux"))),
        this.getClass().getName() + "$ResponseContentAdvice");
    transformer.applyAdviceToMethod(
        named("responseSingle")
            .and(takesArguments(1))
            .and(takesArgument(0, BiFunction.class))
            .and(returns(named("reactor.core.publisher.Mono"))),
        this.getClass().getName() + "$ResponseSingleAdvice");
  }

  public static class AdviceScope {
    @Nullable private final HttpClient.ResponseReceiver<?> modifiedReceiver;
    private final CallDepth callDepth;

    /**
     * Dedicated advice scope subclass that make instrumentation skip original method body using
     * {@code skipOn = Runnable.class } which does not require to expose an extra type
     */
    public static class SkipMethodBodyAdviceScope extends AdviceScope implements Runnable {
      private SkipMethodBodyAdviceScope(
          CallDepth callDepth, HttpClient.ResponseReceiver<?> receiver) {
        super(callDepth, receiver);
      }

      @Override
      public void run() {
        // do nothing, only using Runnable as a marker interface to enable skipping without
        // exposing the actual type
      }
    }

    private AdviceScope(CallDepth callDepth, @Nullable HttpClient.ResponseReceiver<?> receiver) {
      this.modifiedReceiver = receiver;
      this.callDepth = callDepth;
    }

    public static AdviceScope start(CallDepth callDepth, HttpClient.ResponseReceiver<?> receiver) {
      if (callDepth.getAndIncrement() > 0) {
        // original method body executed for nested calls
        return new AdviceScope(callDepth, null);
      }
      // original method body will be skipped due to return type and 'skipOn' value
      return new SkipMethodBodyAdviceScope(
          callDepth, HttpResponseReceiverInstrumenter.instrument(receiver));
    }

    public <T> T end(T returnValue, Function<HttpClient.ResponseReceiver<?>, T> receiverFunction) {
      try {
        if (modifiedReceiver != null) {
          return receiverFunction.apply(modifiedReceiver);
        }
      } finally {
        // needs to be called after original method to prevent StackOverflowError
        callDepth.decrementAndGet();
      }
      return returnValue;
    }

    public Mono<HttpClientResponse> end(Mono<HttpClientResponse> returnValue) {
      return end(returnValue, HttpClient.ResponseReceiver::response);
    }

    public <T> Flux<?> endResponse(
        Flux<?> returnValue,
        BiFunction<? super HttpClientResponse, ? super ByteBufFlux, ? extends Publisher<T>>
            receiveFunction) {
      return end(returnValue, receiver -> receiver.response(receiveFunction));
    }

    public <T> Flux<?> endResponseConnection(
        Flux<?> returnValue,
        BiFunction<? super HttpClientResponse, ? super Connection, ? extends Publisher<T>>
            receiveFunction) {
      return end(returnValue, receiver -> receiver.responseConnection(receiveFunction));
    }

    public ByteBufFlux endResponseContent(ByteBufFlux returnValue) {
      return end(returnValue, HttpClient.ResponseReceiver::responseContent);
    }

    public <T extends HttpClient.ResponseReceiver<?>> Mono<?> endResponseSingle(
        Mono<?> returnValue,
        BiFunction<? super HttpClientResponse, ? super ByteBufMono, ? extends Mono<T>>
            receiveFunction) {
      return end(returnValue, receiver -> receiver.responseSingle(receiveFunction));
    }
  }

  @SuppressWarnings("unused")
  public static class ResponseMonoAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, skipOn = Runnable.class)
    public static AdviceScope onEnter(@Advice.This HttpClient.ResponseReceiver<?> receiver) {
      return AdviceScope.start(CallDepth.forClass(HttpClient.ResponseReceiver.class), receiver);
    }

    @AssignReturned.ToReturned
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static Mono<HttpClientResponse> onExit(
        @Advice.Return Mono<HttpClientResponse> returnValue,
        @Advice.Enter AdviceScope adviceScope) {
      return adviceScope.end(returnValue);
    }
  }

  @SuppressWarnings("unused")
  public static class ResponseFluxAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, skipOn = Runnable.class)
    public static AdviceScope onEnter(@Advice.This HttpClient.ResponseReceiver<?> receiver) {
      return AdviceScope.start(CallDepth.forClass(HttpClient.ResponseReceiver.class), receiver);
    }

    @AssignReturned.ToReturned
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static <T extends HttpClient.ResponseReceiver<?>> Flux<?> onExit(
        @Advice.Argument(0)
            BiFunction<? super HttpClientResponse, ? super ByteBufFlux, ? extends Publisher<T>>
                receiveFunction,
        @Advice.Return Flux<?> returnValue,
        @Advice.Enter AdviceScope adviceScope) {
      return adviceScope.endResponse(returnValue, receiveFunction);
    }
  }

  @SuppressWarnings("unused")
  public static class ResponseConnectionAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, skipOn = Runnable.class)
    public static AdviceScope onEnter(@Advice.This HttpClient.ResponseReceiver<?> receiver) {
      return AdviceScope.start(CallDepth.forClass(HttpClient.ResponseReceiver.class), receiver);
    }

    @AssignReturned.ToReturned
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static <T extends HttpClient.ResponseReceiver<?>> Flux<?> onExit(
        @Advice.Argument(0)
            BiFunction<? super HttpClientResponse, ? super Connection, ? extends Publisher<T>>
                receiveFunction,
        @Advice.Return Flux<?> returnValue,
        @Advice.Enter AdviceScope adviceScope) {
      return adviceScope.endResponseConnection(returnValue, receiveFunction);
    }
  }

  @SuppressWarnings("unused")
  public static class ResponseContentAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, skipOn = Runnable.class)
    public static AdviceScope onEnter(@Advice.This HttpClient.ResponseReceiver<?> receiver) {
      return AdviceScope.start(CallDepth.forClass(HttpClient.ResponseReceiver.class), receiver);
    }

    @AssignReturned.ToReturned
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static ByteBufFlux onExit(
        @Advice.Return ByteBufFlux returnValue, @Advice.Enter AdviceScope adviceScope) {
      return adviceScope.endResponseContent(returnValue);
    }
  }

  @SuppressWarnings("unused")
  public static class ResponseSingleAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, skipOn = Runnable.class)
    public static AdviceScope onEnter(@Advice.This HttpClient.ResponseReceiver<?> receiver) {
      return AdviceScope.start(CallDepth.forClass(HttpClient.ResponseReceiver.class), receiver);
    }

    @AssignReturned.ToReturned
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static <T extends HttpClient.ResponseReceiver<?>> Mono<?> onExit(
        @Advice.Argument(0)
            BiFunction<? super HttpClientResponse, ? super ByteBufMono, ? extends Mono<T>>
                receiveFunction,
        @Advice.Return Mono<?> returnValue,
        @Advice.Enter AdviceScope adviceScope) {

      return adviceScope.endResponseSingle(returnValue, receiveFunction);
    }
  }
}
