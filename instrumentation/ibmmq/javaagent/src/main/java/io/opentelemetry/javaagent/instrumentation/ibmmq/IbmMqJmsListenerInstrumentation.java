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
import javax.jms.Message;
import javax.jms.MessageListener;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * Adds the queue manager identifier to the process span that the generic JMS instrumentation opens
 * around {@code MessageListener.onMessage}. That span is created in entry advice and made current,
 * so it is writable for the whole callback -- unlike the synchronous {@code receive()} span itself,
 * which this module still never touches (see {@link IbmMqInstrumentationModule}).
 *
 * <p>Resolves the QMID from whichever of two sources applies: the consumer this listener was
 * registered on via {@code setMessageListener} ({@link IbmMqJmsListenerQmid#associate}), or, when
 * that never happened, the QMID captured when this exact message was returned from {@code
 * receive()} ({@link IbmMqJmsListenerQmid#captureFromReceive}). The latter covers containers --
 * such as Spring's default {@code JmsListenerContainerFactory} -- that drive {@code onMessage} by
 * calling {@code receive()} and invoking the listener directly, without ever calling {@code
 * setMessageListener}.
 *
 * <p>Listeners and messages this module never saw carry no remembered QMID, so this is a no-op for
 * non-IBM JMS providers.
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
        named("onMessage").and(takesArgument(0, named("javax.jms.Message"))).and(isPublic()),
        this.getClass().getName() + "$OnMessageAdvice");
  }

  @SuppressWarnings("unused")
  public static class OnMessageAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.This MessageListener listener, @Advice.Argument(0) Message message) {
      IbmMqJmsListenerQmid.stamp(listener, message);
    }
  }
}
