/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v3_0;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasSuperMethod;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.methodIsDeclaredByType;
import static io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.instrumentation.jedis.v3_0.JedisSingletons.instrumenter;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.nameEndsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.jedis.v3_0.resolver.CommandResolver;
import io.opentelemetry.javaagent.instrumentation.jedis.v3_0.resolver.DefaultCommandResolver;
import java.util.Locale;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.Protocol;

public class JedisMethodInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("redis.clients.jedis.Jedis").or(named("redis.clients.jedis.JedisCluster"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod()
            .and(isPublic())
            .and(
                hasSuperMethod(methodIsDeclaredByType(nameEndsWith("Commands")))
                    .or(named("ping"))
                    .or(named("pubsubChannels"))
                    .or(named("pubsubNumPat"))
                    .or(named("pubsubNumSub"))
                    .or(named("asking"))),
        this.getClass().getName() + "$JedisMethodAdvice");
  }

  @SuppressWarnings("unused")
  public static class JedisMethodAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.This Jedis jedis,
        @Advice.AllArguments Object[] args,
        @Advice.Origin("#m") String methodName,
        @Advice.Local("otelJedisRequest") JedisRequest request,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      Context parentContext = currentContext();
      CommandResolver resolver = null;
      try {
        Protocol.Command command = Protocol.Command.valueOf(methodName.toUpperCase(Locale.ROOT));
        resolver = new DefaultCommandResolver(command);
      } catch (IllegalArgumentException e) {
        MethodNameNotMatchingCommandMapping mapping =
            MethodNameNotMatchingCommandMapping.mapping(methodName);
        if (mapping != null) {
          resolver = mapping.getResolver();
        }
      }
      if (resolver == null) {
        return;
      }
      request =
          JedisRequest.create(jedis.getClient(), resolver.getCommand(), resolver.resolveArgs(args));
      if (!instrumenter().shouldStart(parentContext, request)) {
        return;
      }

      context = instrumenter().start(parentContext, request);
      scope = context.makeCurrent();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Thrown Throwable throwable,
        @Advice.Local("otelJedisRequest") JedisRequest request,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      if (scope == null) {
        return;
      }

      scope.close();
      instrumenter().end(context, request, null, throwable);
    }
  }
}
