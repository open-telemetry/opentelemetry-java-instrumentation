/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.reactornetty.v0_9;

import static io.opentelemetry.javaagent.extension.matcher.ClassLoaderMatcher.hasClassesNamed;
import static java.util.Collections.singletonList;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.isStatic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.auto.service.AutoService;
import io.netty.bootstrap.Bootstrap;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.netty.v4_1.AttributeKeys;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.instrumentation.api.CallDepthThreadLocalMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import reactor.core.publisher.Mono;
import reactor.netty.Connection;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.client.HttpClientRequest;
import reactor.netty.http.client.HttpClientResponse;

/**
 * This instrumentation solves the problem of the correct context propagation through the roller
 * coaster of Project Reactor and Netty thread hopping. It uses two public hooks of {@link
 * HttpClient}: {@link HttpClient#mapConnect(BiFunction)} and {@link
 * HttpClient#doOnRequest(BiConsumer)} to pass context from the caller to Reactor to Netty.
 */
@AutoService(InstrumentationModule.class)
public class ReactorNettyInstrumentationModule extends InstrumentationModule {

  public ReactorNettyInstrumentationModule() {
    super("reactor-netty", "reactor-netty-0.9");
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    // Removed in 1.0.0
    return hasClassesNamed("reactor.netty.tcp.InetSocketAddressUtil");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new HttpClientInstrumentation());
  }

  public static class HttpClientInstrumentation implements TypeInstrumentation {
    @Override
    public ElementMatcher<TypeDescription> typeMatcher() {
      return named("reactor.netty.http.client.HttpClient");
    }

    @Override
    public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
      Map<ElementMatcher.Junction<MethodDescription>, String> transformers = new HashMap<>();
      transformers.put(
          isStatic().and(namedOneOf("create", "newConnection", "from")),
          ReactorNettyInstrumentationModule.class.getName() + "$CreateAdvice");

      // advice classes below expose current context in doOn*/doAfter* callbacks
      transformers.put(
          isPublic()
              .and(namedOneOf("doOnRequest", "doAfterRequest"))
              .and(takesArguments(1))
              .and(takesArgument(0, BiConsumer.class)),
          ReactorNettyInstrumentationModule.class.getName() + "$OnRequestAdvice");
      transformers.put(
          isPublic()
              .and(named("doOnRequestError"))
              .and(takesArguments(1))
              .and(takesArgument(0, BiConsumer.class)),
          ReactorNettyInstrumentationModule.class.getName() + "$OnRequestErrorAdvice");
      transformers.put(
          isPublic()
              .and(namedOneOf("doOnResponse", "doAfterResponse"))
              .and(takesArguments(1))
              .and(takesArgument(0, BiConsumer.class)),
          ReactorNettyInstrumentationModule.class.getName() + "$OnResponseAdvice");
      transformers.put(
          isPublic()
              .and(named("doOnResponseError"))
              .and(takesArguments(1))
              .and(takesArgument(0, BiConsumer.class)),
          ReactorNettyInstrumentationModule.class.getName() + "$OnResponseErrorAdvice");
      transformers.put(
          isPublic()
              .and(named("doOnError"))
              .and(takesArguments(2))
              .and(takesArgument(0, BiConsumer.class))
              .and(takesArgument(1, BiConsumer.class)),
          ReactorNettyInstrumentationModule.class.getName() + "$OnErrorAdvice");
      return transformers;
    }
  }

  public static class CreateAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter() {
      CallDepthThreadLocalMap.incrementCallDepth(HttpClient.class);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Thrown Throwable throwable, @Advice.Return(readOnly = false) HttpClient client) {

      if (CallDepthThreadLocalMap.decrementCallDepth(HttpClient.class) == 0 && throwable == null) {
        client = client.doOnRequest(new OnRequest()).mapConnect(new MapConnect());
      }
    }
  }

  public static class MapConnect
      implements BiFunction<Mono<? extends Connection>, Bootstrap, Mono<? extends Connection>> {

    static final String CONTEXT_ATTRIBUTE = MapConnect.class.getName() + ".Context";

    @Override
    public Mono<? extends Connection> apply(Mono<? extends Connection> m, Bootstrap b) {
      return m.subscriberContext(s -> s.put(CONTEXT_ATTRIBUTE, Context.current()));
    }
  }

  public static class OnRequest implements BiConsumer<HttpClientRequest, Connection> {
    @Override
    public void accept(HttpClientRequest r, Connection c) {
      Context context = r.currentContext().get(MapConnect.CONTEXT_ATTRIBUTE);
      c.channel().attr(AttributeKeys.WRITE_CONTEXT).set(context);
    }
  }

  public static class OnRequestAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.Argument(value = 0, readOnly = false)
            BiConsumer<? super HttpClientRequest, ? super Connection> callback) {
      if (DecoratorFunctions.shouldDecorate(callback.getClass())) {
        callback = new DecoratorFunctions.OnRequestDecorator(callback);
      }
    }
  }

  public static class OnRequestErrorAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.Argument(value = 0, readOnly = false)
            BiConsumer<? super HttpClientRequest, ? super Throwable> callback) {
      if (DecoratorFunctions.shouldDecorate(callback.getClass())) {
        callback = new DecoratorFunctions.OnRequestErrorDecorator(callback);
      }
    }
  }

  public static class OnResponseAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.Argument(value = 0, readOnly = false)
            BiConsumer<? super HttpClientResponse, ? super Connection> callback,
        @Advice.Origin("#m") String methodName) {
      if (DecoratorFunctions.shouldDecorate(callback.getClass())) {
        boolean forceParentContext = methodName.equals("doAfterResponse");
        callback = new DecoratorFunctions.OnResponseDecorator(callback, forceParentContext);
      }
    }
  }

  public static class OnResponseErrorAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.Argument(value = 0, readOnly = false)
            BiConsumer<? super HttpClientResponse, ? super Throwable> callback) {
      if (DecoratorFunctions.shouldDecorate(callback.getClass())) {
        callback = new DecoratorFunctions.OnResponseErrorDecorator(callback);
      }
    }
  }

  public static class OnErrorAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.Argument(value = 0, readOnly = false)
            BiConsumer<? super HttpClientRequest, ? super Throwable> requestCallback,
        @Advice.Argument(value = 1, readOnly = false)
            BiConsumer<? super HttpClientResponse, ? super Throwable> responseCallback) {
      if (DecoratorFunctions.shouldDecorate(requestCallback.getClass())) {
        requestCallback = new DecoratorFunctions.OnRequestErrorDecorator(requestCallback);
      }
      if (DecoratorFunctions.shouldDecorate(responseCallback.getClass())) {
        responseCallback = new DecoratorFunctions.OnResponseErrorDecorator(responseCallback);
      }
    }
  }
}
