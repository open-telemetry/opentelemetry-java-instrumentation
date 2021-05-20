/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.common;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static io.opentelemetry.javaagent.extension.matcher.ClassLoaderMatcher.hasClassesNamed;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelPipeline;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.api.ContextStore;
import io.opentelemetry.javaagent.instrumentation.api.InstrumentationContext;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public abstract class AbstractNettyChannelPipelineInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("io.netty.channel.ChannelPipeline");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named("io.netty.channel.ChannelPipeline"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod()
            .and(named("remove"))
            .and(takesArgument(0, named("io.netty.channel.ChannelHandler"))),
        AbstractNettyChannelPipelineInstrumentation.class.getName()
            + "$ChannelPipelineRemoveAdvice");
    transformer.applyAdviceToMethod(
        isMethod().and(named("remove")).and(takesArgument(0, String.class)),
        AbstractNettyChannelPipelineInstrumentation.class.getName()
            + "$ChannelPipelineRemoveByNameAdvice");
    transformer.applyAdviceToMethod(
        isMethod().and(named("remove")).and(takesArgument(0, Class.class)),
        AbstractNettyChannelPipelineInstrumentation.class.getName()
            + "$ChannelPipelineRemoveByClassAdvice");
  }

  public static class ChannelPipelineRemoveAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void removeHandler(
        @Advice.This ChannelPipeline pipeline, @Advice.Argument(0) ChannelHandler handler) {
      ContextStore<ChannelHandler, ChannelHandler> contextStore =
          InstrumentationContext.get(ChannelHandler.class, ChannelHandler.class);
      ChannelHandler ourHandler = contextStore.get(handler);
      if (ourHandler != null) {
        pipeline.remove(ourHandler);
        contextStore.put(handler, null);
      }
    }
  }

  public static class ChannelPipelineRemoveByNameAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void removeHandler(
        @Advice.This ChannelPipeline pipeline, @Advice.Argument(0) String name) {
      ChannelHandler handler = pipeline.get(name);
      if (handler == null) {
        return;
      }

      ContextStore<ChannelHandler, ChannelHandler> contextStore =
          InstrumentationContext.get(ChannelHandler.class, ChannelHandler.class);
      ChannelHandler ourHandler = contextStore.get(handler);
      if (ourHandler != null) {
        pipeline.remove(ourHandler);
        contextStore.put(handler, null);
      }
    }
  }

  public static class ChannelPipelineRemoveByClassAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void removeHandler(
        @Advice.This ChannelPipeline pipeline,
        @Advice.Argument(0) Class<ChannelHandler> handlerClass) {
      ChannelHandler handler = pipeline.get(handlerClass);
      if (handler == null) {
        return;
      }

      ContextStore<ChannelHandler, ChannelHandler> contextStore =
          InstrumentationContext.get(ChannelHandler.class, ChannelHandler.class);
      ChannelHandler ourHandler = contextStore.get(handler);
      if (ourHandler != null) {
        pipeline.remove(ourHandler);
        contextStore.put(handler, null);
      }
    }
  }
}
