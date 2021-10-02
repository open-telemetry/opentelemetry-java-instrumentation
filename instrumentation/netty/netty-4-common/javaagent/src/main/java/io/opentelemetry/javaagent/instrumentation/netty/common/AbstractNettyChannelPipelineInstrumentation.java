/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.common;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelPipeline;
import io.opentelemetry.instrumentation.api.field.VirtualField;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
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
            .and(named("remove").or(named("replace")))
            .and(takesArgument(0, named("io.netty.channel.ChannelHandler"))),
        AbstractNettyChannelPipelineInstrumentation.class.getName() + "$RemoveAdvice");
    transformer.applyAdviceToMethod(
        isMethod().and(named("remove").or(named("replace"))).and(takesArgument(0, String.class)),
        AbstractNettyChannelPipelineInstrumentation.class.getName() + "$RemoveByNameAdvice");
    transformer.applyAdviceToMethod(
        isMethod().and(named("remove").or(named("replace"))).and(takesArgument(0, Class.class)),
        AbstractNettyChannelPipelineInstrumentation.class.getName() + "$RemoveByClassAdvice");
    transformer.applyAdviceToMethod(
        isMethod().and(named("removeFirst")).and(returns(named("io.netty.channel.ChannelHandler"))),
        AbstractNettyChannelPipelineInstrumentation.class.getName() + "$RemoveFirstAdvice");
    transformer.applyAdviceToMethod(
        isMethod().and(named("removeLast")).and(returns(named("io.netty.channel.ChannelHandler"))),
        AbstractNettyChannelPipelineInstrumentation.class.getName() + "$RemoveLastAdvice");
    transformer.applyAdviceToMethod(
        isMethod()
            .and(named("addAfter"))
            .and(takesArgument(1, String.class))
            .and(takesArguments(4)),
        AbstractNettyChannelPipelineInstrumentation.class.getName() + "$AddAfterAdvice");
  }

  @SuppressWarnings("unused")
  public static class RemoveAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void removeHandler(
        @Advice.This ChannelPipeline pipeline, @Advice.Argument(0) ChannelHandler handler) {
      VirtualField<ChannelHandler, ChannelHandler> virtualField =
          VirtualField.find(ChannelHandler.class, ChannelHandler.class);
      ChannelHandler ourHandler = virtualField.get(handler);
      if (ourHandler != null) {
        pipeline.remove(ourHandler);
        virtualField.set(handler, null);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class RemoveByNameAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void removeHandler(
        @Advice.This ChannelPipeline pipeline, @Advice.Argument(0) String name) {
      ChannelHandler handler = pipeline.get(name);
      if (handler == null) {
        return;
      }

      VirtualField<ChannelHandler, ChannelHandler> virtualField =
          VirtualField.find(ChannelHandler.class, ChannelHandler.class);
      ChannelHandler ourHandler = virtualField.get(handler);
      if (ourHandler != null) {
        pipeline.remove(ourHandler);
        virtualField.set(handler, null);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class RemoveByClassAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void removeHandler(
        @Advice.This ChannelPipeline pipeline,
        @Advice.Argument(0) Class<ChannelHandler> handlerClass) {
      ChannelHandler handler = pipeline.get(handlerClass);
      if (handler == null) {
        return;
      }

      VirtualField<ChannelHandler, ChannelHandler> virtualField =
          VirtualField.find(ChannelHandler.class, ChannelHandler.class);
      ChannelHandler ourHandler = virtualField.get(handler);
      if (ourHandler != null) {
        pipeline.remove(ourHandler);
        virtualField.set(handler, null);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class RemoveFirstAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void removeHandler(
        @Advice.This ChannelPipeline pipeline, @Advice.Return ChannelHandler handler) {
      VirtualField<ChannelHandler, ChannelHandler> virtualField =
          VirtualField.find(ChannelHandler.class, ChannelHandler.class);
      ChannelHandler ourHandler = virtualField.get(handler);
      if (ourHandler != null) {
        pipeline.remove(ourHandler);
        virtualField.set(handler, null);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class RemoveLastAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void removeHandler(
        @Advice.This ChannelPipeline pipeline, @Advice.Return ChannelHandler handler) {
      VirtualField<ChannelHandler, ChannelHandler> virtualField =
          VirtualField.find(ChannelHandler.class, ChannelHandler.class);
      ChannelHandler ourHandler = virtualField.get(handler);
      if (ourHandler != null) {
        pipeline.remove(ourHandler);
        virtualField.set(handler, null);
      } else if (handler
          .getClass()
          .getName()
          .startsWith("io.opentelemetry.javaagent.instrumentation.netty.")) {
        pipeline.removeLast();
      }
    }
  }

  @SuppressWarnings("unused")
  public static class AddAfterAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void addAfterHandler(
        @Advice.This ChannelPipeline pipeline,
        @Advice.Argument(value = 1, readOnly = false) String name) {
      ChannelHandler handler = pipeline.get(name);
      if (handler != null) {
        VirtualField<ChannelHandler, ChannelHandler> virtualField =
            VirtualField.find(ChannelHandler.class, ChannelHandler.class);
        ChannelHandler ourHandler = virtualField.get(handler);
        if (ourHandler != null) {
          name = ourHandler.getClass().getName();
        }
      }
    }
  }
}
