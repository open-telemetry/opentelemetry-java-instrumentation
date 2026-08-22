/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.ibmmq;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import javax.jms.MessageListener;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * Adds the queue manager identifier to the process span that the generic JMS instrumentation opens
 * around {@code MessageListener.onMessage}. Unlike the synchronous {@code receive()} path, that span
 * is created in entry advice and made current, so it is writable for the whole callback.
 *
 * <p>Listeners this module never saw registered simply carry no remembered QMID, so this is a no-op
 * for non-IBM JMS providers.
 */
public class IbmMqJmsListenerInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("javax.jms.MessageListener");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named("javax.jms.MessageListener"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("onMessage")
            .and(takesArgument(0, named("javax.jms.Message")))
            .and(isPublic()),
        this.getClass().getName() + "$OnMessageAdvice");
  }

  @SuppressWarnings("unused")
  public static class OnMessageAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(@Advice.This MessageListener listener) {
      IbmMqJmsListenerQmid.stamp(listener);
    }
  }
}
