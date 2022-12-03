/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pulsar;

import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.pulsar.telemetry.PulsarTelemetry;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.pulsar.client.api.Consumer;
import org.apache.pulsar.client.api.Message;
import org.apache.pulsar.client.api.MessageListener;
import org.apache.pulsar.client.impl.conf.ConsumerConfigurationData;

public class MessageListenerInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    // return hasSuperType(named("org.apache.pulsar.client.api.MessageListener"));
    // can't enhance MessageListener here like above due to jvm can't enhance lambda.
    return named("org.apache.pulsar.client.impl.conf.ConsumerConfigurationData");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod().and(isPublic()).and(named("getMessageListener")),
        MessageListenerInstrumentation.class.getName() + "$ConsumerConfigurationDataMethodAdviser");
  }

  @SuppressWarnings("unused")
  public static class ConsumerConfigurationDataMethodAdviser {
    private ConsumerConfigurationDataMethodAdviser() {}

    @Advice.OnMethodExit
    public static void after(
        @Advice.This ConsumerConfigurationData<?> data,
        @Advice.Return(readOnly = false, typing = Assigner.Typing.DYNAMIC)
            MessageListener<?> listener) {
      if (null == listener) {
        return;
      }

      listener = new MessageListenerWrapper<>(listener);
    }
  }

  public static class MessageListenerWrapper<T> implements MessageListener<T> {
    private static final long serialVersionUID = 1L;

    private final MessageListener<T> delegator;

    public MessageListenerWrapper(MessageListener<T> messageListener) {
      this.delegator = messageListener;
    }

    @Override
    public void received(Consumer<T> consumer, Message<T> msg) {
      Context parent = VirtualFieldStore.extract(msg);

      Instrumenter<Message<?>, Void> instrumenter = PulsarTelemetry.consumerListenerInstrumenter();
      if (!instrumenter.shouldStart(parent, msg)) {
        this.delegator.received(consumer, msg);
        return;
      }

      Context current = instrumenter.start(parent, msg);
      try (Scope scope = current.makeCurrent()) {
        this.delegator.received(consumer, msg);
        instrumenter.end(current, msg, null, null);
      } catch (Throwable t) {
        instrumenter.end(current, msg, null, t);
        throw t;
      }
    }

    @Override
    public void reachedEndOfTopic(Consumer<T> consumer) {
      this.delegator.reachedEndOfTopic(consumer);
    }
  }
}
