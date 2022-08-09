/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v4_1;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;

import io.netty.channel.Channel;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * This instrumentation preserves the context that was active during call to any "write" operation
 * on Netty Channel in that channel's attribute. This context is later used by our various tracing
 * handlers to scope the work.
 */
public class ChannelInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("io.netty.channel.Channel");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named("io.netty.channel.Channel"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod().and(namedOneOf("write", "writeAndFlush")),
        ChannelInstrumentation.class.getName() + "$AttachContextAdvice");
  }

  @SuppressWarnings("unused")
  public static class AttachContextAdvice {

    @Advice.OnMethodEnter
    public static void attachContext(@Advice.This Channel channel) {
      channel
          .attr(AttributeKeys.WRITE_CONTEXT)
          .compareAndSet(null, Java8BytecodeBridge.currentContext());
    }
  }
}
