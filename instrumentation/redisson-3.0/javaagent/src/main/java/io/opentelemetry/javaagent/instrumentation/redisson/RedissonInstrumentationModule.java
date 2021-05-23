/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.redisson;

import static io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.instrumentation.redisson.RedissonInstrumenters.instrumenter;
import static java.util.Collections.singletonList;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;

import com.google.auto.service.AutoService;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.util.List;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.redisson.client.RedisConnection;

@AutoService(InstrumentationModule.class)
public class RedissonInstrumentationModule extends InstrumentationModule {

  public RedissonInstrumentationModule() {
    super("redisson", "redisson-3.0");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new RedisConnectionInstrumentation());
  }

  public static class RedisConnectionInstrumentation implements TypeInstrumentation {
    @Override
    public ElementMatcher<TypeDescription> typeMatcher() {
      return named("org.redisson.client.RedisConnection");
    }

    @Override
    public void transform(TypeTransformer transformer) {
      transformer.applyAdviceToMethod(
          isMethod().and(named("send")),
          RedissonInstrumentationModule.class.getName() + "$RedissonAdvice");
    }
  }

  public static class RedissonAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.This RedisConnection connection,
        @Advice.Argument(0) Object arg,
        @Advice.Local("otelRedissonRequest") RedissonRequest request,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      Context parentContext = currentContext();
      request = RedissonRequest.create(connection, arg);
      if (!instrumenter().shouldStart(parentContext, request)) {
        return;
      }

      context = instrumenter().start(parentContext, request);
      scope = context.makeCurrent();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Thrown Throwable throwable,
        @Advice.Local("otelRedissonRequest") RedissonRequest request,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      scope.close();
      instrumenter().end(context, request, null, throwable);
    }
  }
}
