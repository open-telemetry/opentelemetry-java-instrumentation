/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v3_8;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.javaagent.bootstrap.CallDepth;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.channel.ChannelPipeline;

public class NettyChannelPipelineInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("org.jboss.netty.channel.ChannelPipeline");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named("org.jboss.netty.channel.ChannelPipeline"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod()
            .and(nameStartsWith("add"))
            .and(takesArgument(1, named("org.jboss.netty.channel.ChannelHandler"))),
        NettyChannelPipelineInstrumentation.class.getName() + "$ChannelPipelineAdd2ArgsAdvice");
    transformer.applyAdviceToMethod(
        isMethod()
            .and(nameStartsWith("add"))
            .and(takesArgument(2, named("org.jboss.netty.channel.ChannelHandler"))),
        NettyChannelPipelineInstrumentation.class.getName() + "$ChannelPipelineAdd3ArgsAdvice");
  }

  @SuppressWarnings("unused")
  public static class ChannelPipelineAdd2ArgsAdvice {

    @Advice.OnMethodEnter
    public static CallDepth checkDepth(
        @Advice.This ChannelPipeline pipeline, @Advice.Argument(1) ChannelHandler handler) {
      // Pipelines are created once as a factory and then copied multiple times using the same add
      // methods as we are hooking. If our handler has already been added we need to remove it so we
      // don't end up with duplicates (this throws an exception)
      if (pipeline.get(handler.getClass().getName()) != null) {
        pipeline.remove(handler.getClass().getName());
      }
      CallDepth callDepth = CallDepth.forClass(ChannelPipeline.class);
      callDepth.getAndIncrement();
      return callDepth;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void addHandler(
        @Advice.This ChannelPipeline pipeline,
        @Advice.Argument(1) ChannelHandler handler,
        @Advice.Enter CallDepth callDepth) {

      if (callDepth.decrementAndGet() > 0) {
        return;
      }

      ChannelPipelineUtil.wrapHandler(pipeline, handler);
    }
  }

  @SuppressWarnings("unused")
  public static class ChannelPipelineAdd3ArgsAdvice {

    @Advice.OnMethodEnter
    public static CallDepth checkDepth(
        @Advice.This ChannelPipeline pipeline, @Advice.Argument(2) ChannelHandler handler) {
      // Pipelines are created once as a factory and then copied multiple times using the same add
      // methods as we are hooking. If our handler has already been added we need to remove it so we
      // don't end up with duplicates (this throws an exception)
      if (pipeline.get(handler.getClass().getName()) != null) {
        pipeline.remove(handler.getClass().getName());
      }
      CallDepth callDepth = CallDepth.forClass(ChannelPipeline.class);
      callDepth.getAndIncrement();
      return callDepth;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void addHandler(
        @Advice.This ChannelPipeline pipeline,
        @Advice.Argument(2) ChannelHandler handler,
        @Advice.Enter CallDepth callDepth) {

      if (callDepth.decrementAndGet() > 0) {
        return;
      }
      ChannelPipelineUtil.wrapHandler(pipeline, handler);
    }
  }
}
