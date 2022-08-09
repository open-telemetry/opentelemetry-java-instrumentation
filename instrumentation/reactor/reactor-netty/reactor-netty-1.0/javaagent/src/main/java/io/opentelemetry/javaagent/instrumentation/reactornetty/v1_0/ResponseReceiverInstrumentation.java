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
import net.bytebuddy.asm.Advice;
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

  @SuppressWarnings("unused")
  public static class ResponseMonoAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, skipOn = Advice.OnNonDefaultValue.class)
    public static HttpClient.ResponseReceiver<?> onEnter(
        @Advice.Local("otelCallDepth") CallDepth callDepth,
        @Advice.This HttpClient.ResponseReceiver<?> receiver) {

      callDepth = CallDepth.forClass(HttpClient.ResponseReceiver.class);
      if (callDepth.getAndIncrement() > 0) {
        // execute the original method on nested calls
        return null;
      }

      // non-null value will skip the original method invocation
      return HttpResponseReceiverInstrumenter.instrument(receiver);
    }

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(
        @Advice.Local("otelCallDepth") CallDepth callDepth,
        @Advice.Enter HttpClient.ResponseReceiver<?> modifiedReceiver,
        @Advice.Return(readOnly = false) Mono<HttpClientResponse> returnValue) {

      try {
        if (modifiedReceiver != null) {
          returnValue = modifiedReceiver.response();
        }
      } finally {
        // needs to be called after original method to prevent StackOverflowError
        callDepth.decrementAndGet();
      }
    }
  }

  @SuppressWarnings("unused")
  public static class ResponseFluxAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, skipOn = Advice.OnNonDefaultValue.class)
    public static HttpClient.ResponseReceiver<?> onEnter(
        @Advice.Local("otelCallDepth") CallDepth callDepth,
        @Advice.This HttpClient.ResponseReceiver<?> receiver) {

      callDepth = CallDepth.forClass(HttpClient.ResponseReceiver.class);
      if (callDepth.getAndIncrement() > 0) {
        // execute the original method on nested calls
        return null;
      }

      // non-null value will skip the original method invocation
      return HttpResponseReceiverInstrumenter.instrument(receiver);
    }

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static <T extends HttpClient.ResponseReceiver<?>> void onExit(
        @Advice.Local("otelCallDepth") CallDepth callDepth,
        @Advice.Enter HttpClient.ResponseReceiver<T> modifiedReceiver,
        @Advice.Argument(0)
            BiFunction<? super HttpClientResponse, ? super ByteBufFlux, ? extends Publisher<T>>
                receiveFunction,
        @Advice.Return(readOnly = false) Flux<?> returnValue) {

      try {
        if (modifiedReceiver != null) {
          returnValue = modifiedReceiver.response(receiveFunction);
        }
      } finally {
        // needs to be called after original method to prevent StackOverflowError
        callDepth.decrementAndGet();
      }
    }
  }

  @SuppressWarnings("unused")
  public static class ResponseConnectionAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, skipOn = Advice.OnNonDefaultValue.class)
    public static HttpClient.ResponseReceiver<?> onEnter(
        @Advice.Local("otelCallDepth") CallDepth callDepth,
        @Advice.This HttpClient.ResponseReceiver<?> receiver) {

      callDepth = CallDepth.forClass(HttpClient.ResponseReceiver.class);
      if (callDepth.getAndIncrement() > 0) {
        // execute the original method on nested calls
        return null;
      }

      // non-null value will skip the original method invocation
      return HttpResponseReceiverInstrumenter.instrument(receiver);
    }

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static <T extends HttpClient.ResponseReceiver<?>> void onExit(
        @Advice.Local("otelCallDepth") CallDepth callDepth,
        @Advice.Enter HttpClient.ResponseReceiver<T> modifiedReceiver,
        @Advice.Argument(0)
            BiFunction<? super HttpClientResponse, ? super Connection, ? extends Publisher<T>>
                receiveFunction,
        @Advice.Return(readOnly = false) Flux<?> returnValue) {

      try {
        if (modifiedReceiver != null) {
          returnValue = modifiedReceiver.responseConnection(receiveFunction);
        }
      } finally {
        // needs to be called after original method to prevent StackOverflowError
        callDepth.decrementAndGet();
      }
    }
  }

  @SuppressWarnings("unused")
  public static class ResponseContentAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, skipOn = Advice.OnNonDefaultValue.class)
    public static HttpClient.ResponseReceiver<?> onEnter(
        @Advice.Local("otelCallDepth") CallDepth callDepth,
        @Advice.This HttpClient.ResponseReceiver<?> receiver) {

      callDepth = CallDepth.forClass(HttpClient.ResponseReceiver.class);
      if (callDepth.getAndIncrement() > 0) {
        // execute the original method on nested calls
        return null;
      }

      // non-null value will skip the original method invocation
      return HttpResponseReceiverInstrumenter.instrument(receiver);
    }

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(
        @Advice.Local("otelCallDepth") CallDepth callDepth,
        @Advice.Enter HttpClient.ResponseReceiver<?> modifiedReceiver,
        @Advice.Return(readOnly = false) ByteBufFlux returnValue) {

      try {
        if (modifiedReceiver != null) {
          returnValue = modifiedReceiver.responseContent();
        }
      } finally {
        // needs to be called after original method to prevent StackOverflowError
        callDepth.decrementAndGet();
      }
    }
  }

  @SuppressWarnings("unused")
  public static class ResponseSingleAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, skipOn = Advice.OnNonDefaultValue.class)
    public static HttpClient.ResponseReceiver<?> onEnter(
        @Advice.Local("otelCallDepth") CallDepth callDepth,
        @Advice.This HttpClient.ResponseReceiver<?> receiver) {

      callDepth = CallDepth.forClass(HttpClient.ResponseReceiver.class);
      if (callDepth.getAndIncrement() > 0) {
        // execute the original method on nested calls
        return null;
      }

      // non-null value will skip the original method invocation
      return HttpResponseReceiverInstrumenter.instrument(receiver);
    }

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static <T extends HttpClient.ResponseReceiver<?>> void onExit(
        @Advice.Local("otelCallDepth") CallDepth callDepth,
        @Advice.Enter HttpClient.ResponseReceiver<T> modifiedReceiver,
        @Advice.Argument(0)
            BiFunction<? super HttpClientResponse, ? super ByteBufMono, ? extends Mono<T>>
                receiveFunction,
        @Advice.Return(readOnly = false) Mono<?> returnValue) {

      try {
        if (modifiedReceiver != null) {
          returnValue = modifiedReceiver.responseSingle(receiveFunction);
        }
      } finally {
        // needs to be called after original method to prevent StackOverflowError
        callDepth.decrementAndGet();
      }
    }
  }
}
