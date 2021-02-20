/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.reactornetty.v0_9;

import static io.opentelemetry.javaagent.tooling.bytebuddy.matcher.ClassLoaderMatcher.hasClassesNamed;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isStatic;
import static net.bytebuddy.matcher.ElementMatchers.named;

import com.google.auto.service.AutoService;
import io.netty.bootstrap.Bootstrap;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.netty.v4_1.AttributeKeys;
import io.opentelemetry.javaagent.instrumentation.api.CallDepthThreadLocalMap;
import io.opentelemetry.javaagent.tooling.InstrumentationModule;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
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
      return singletonMap(
          isStatic().and(named("create")),
          ReactorNettyInstrumentationModule.class.getName() + "$CreateAdvice");
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
}
