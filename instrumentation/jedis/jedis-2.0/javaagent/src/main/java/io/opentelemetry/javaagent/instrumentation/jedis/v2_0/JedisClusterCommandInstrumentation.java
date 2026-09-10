/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v2_0;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.extendsClass;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

class JedisClusterCommandInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("redis.clients.jedis.JedisClusterCommand")
        .or(extendsClass(named("redis.clients.jedis.JedisClusterCommand")));
  }

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("redis.clients.jedis.JedisClusterCommand");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isPublic().and(namedOneOf("run", "runBinary", "runWithAnyNode")),
        getClass().getName() + "$CommandAdvice");
    transformer.applyAdviceToMethod(
        named("execute").and(takesArguments(1)), getClass().getName() + "$ExecuteAdvice");
  }

  @SuppressWarnings("unused")
  public static class CommandAdvice {

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static JedisClusterCommandContext onEnter() {
      return JedisClusterCommandContext.start();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.Thrown @Nullable Throwable throwable,
        @Advice.Enter @Nullable JedisClusterCommandContext commandContext) {
      if (commandContext != null) {
        commandContext.end(throwable);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class ExecuteAdvice {

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static JedisClusterCommandContext onEnter() {
      JedisClusterCommandContext commandContext = JedisClusterCommandContext.current();
      if (commandContext != null) {
        commandContext.enterExecute();
      }
      return commandContext;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(@Advice.Enter @Nullable JedisClusterCommandContext commandContext) {
      if (commandContext != null) {
        commandContext.exitExecute();
      }
    }
  }
}
