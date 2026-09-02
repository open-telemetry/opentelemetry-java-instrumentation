/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v2_0;

import static io.opentelemetry.javaagent.instrumentation.jedis.v2_0.JedisSingletons.instrumenter;
import static java.util.Arrays.asList;
import static net.bytebuddy.matcher.ElementMatchers.is;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisServerTarget;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.jedis.common.v1_4.JedisRequestContext;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import net.bytebuddy.matcher.ElementMatcher;
import redis.clients.jedis.Connection;
import redis.clients.jedis.Protocol;

class JedisConnectionInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("redis.clients.jedis.Connection");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(isConstructor(), getClass().getName() + "$ConstructorAdvice");
    transformer.applyAdviceToMethod(
        namedOneOf("setHost", "setPort").and(takesArguments(1)),
        getClass().getName() + "$UpdateTargetAdvice");

    transformer.applyAdviceToMethod(
        named("sendCommand")
            .and(takesArguments(1))
            .and(
                takesArgument(
                    0,
                    namedOneOf(
                        "redis.clients.jedis.Protocol$Command",
                        "redis.clients.jedis.ProtocolCommand"))),
        getClass().getName() + "$SendCommandNoArgsAdvice");
    transformer.applyAdviceToMethod(
        named("sendCommand")
            .and(takesArguments(2))
            .and(
                takesArgument(
                    0,
                    namedOneOf(
                        "redis.clients.jedis.Protocol$Command",
                        "redis.clients.jedis.ProtocolCommand")))
            .and(takesArgument(1, is(byte[][].class))),
        getClass().getName() + "$SendCommandWithArgsAdvice");
  }

  @SuppressWarnings("unused")
  public static class ConstructorAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(@Advice.This Connection connection) {
      JedisSingletons.setConnectionTarget(
          connection, RedisServerTarget.ofHostAndPort(connection.getHost(), connection.getPort()));
    }
  }

  @SuppressWarnings("unused")
  public static class UpdateTargetAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(@Advice.This Connection connection) {
      JedisSingletons.setConnectionTarget(
          connection, RedisServerTarget.ofHostAndPort(connection.getHost(), connection.getPort()));
    }
  }

  public static class AdviceScope {
    @Nullable private final Context context;
    @Nullable private final Scope scope;
    private final JedisRequest request;
    @Nullable private final JedisClusterCommandContext clusterCommandContext;

    private AdviceScope(
        @Nullable Context context,
        @Nullable Scope scope,
        JedisRequest request,
        @Nullable JedisClusterCommandContext clusterCommandContext) {
      this.context = context;
      this.scope = scope;
      this.request = request;
      this.clusterCommandContext = clusterCommandContext;
    }

    @Nullable
    public static AdviceScope start(JedisRequest request) {
      if (JedisPipelineContext.inTransactionFraming()) {
        // MULTI/EXEC/DISCARD frame a batched transaction; they are represented by the MULTI batch
        // span rather than getting their own spans. Keep the request until method exit so a
        // connected EXEC socket observed at method exit can become the transaction's last peer.
        return new AdviceScope(null, null, request, null);
      }
      Context parentContext = Context.current();
      if (JedisPipelineContext.capture(request)) {
        // Keep the request until method exit so its post-send peer snapshot is available to the
        // batch span created at sync()/exec().
        return new AdviceScope(null, null, request, null);
      }
      JedisClusterCommandContext clusterCommandContext = JedisClusterCommandContext.current();
      if (clusterCommandContext != null && clusterCommandContext.hasRequest()) {
        return new AdviceScope(null, null, request, clusterCommandContext);
      }
      if (!instrumenter().shouldStart(parentContext, request)) {
        return null;
      }
      Context context = instrumenter().start(parentContext, request);
      return new AdviceScope(context, context.makeCurrent(), request, clusterCommandContext);
    }

    public void end(@Nullable Throwable throwable) {
      try {
        request.capturePeerAddress();
        JedisPipelineContext.captureTransactionFramingPeer(request);
      } finally {
        Context context = this.context;
        if (scope != null) {
          scope.close();
        }
        if (clusterCommandContext != null) {
          clusterCommandContext.capture(context, request);
        } else if (context != null) {
          JedisRequestContext.endIfNotAttached(instrumenter(), context, request, throwable);
        }
      }
    }
  }

  @SuppressWarnings("unused")
  public static class SendCommandNoArgsAdvice {

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static AdviceScope onEnter(
        @Advice.This Connection connection,
        @Advice.Argument(value = 0, typing = Assigner.Typing.DYNAMIC) Protocol.Command command) {
      JedisRequest request = JedisRequest.create(connection, command);
      return AdviceScope.start(request);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void stopSpan(
        @Advice.Thrown @Nullable Throwable throwable,
        @Advice.Enter @Nullable AdviceScope adviceScope) {
      if (adviceScope != null) {
        adviceScope.end(throwable);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class SendCommandWithArgsAdvice {

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static AdviceScope onEnter(
        @Advice.This Connection connection,
        @Advice.Argument(value = 0, typing = Assigner.Typing.DYNAMIC) Protocol.Command command,
        @Advice.Argument(1) byte[][] args) {
      JedisRequest request = JedisRequest.create(connection, command, asList(args));
      return AdviceScope.start(request);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void stopSpan(
        @Advice.Thrown @Nullable Throwable throwable,
        @Advice.Enter @Nullable AdviceScope adviceScope) {
      if (adviceScope != null) {
        adviceScope.end(throwable);
      }
    }
  }
}
