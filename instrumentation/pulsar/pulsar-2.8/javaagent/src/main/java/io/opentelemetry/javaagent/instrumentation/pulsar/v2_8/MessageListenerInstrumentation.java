/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pulsar.v2_8;

import static io.opentelemetry.javaagent.instrumentation.pulsar.v2_8.telemetry.PulsarSingletons.consumerProcessInstrumenter;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.pulsar.v2_8.telemetry.PulsarRequest;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned;
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
        MessageListenerInstrumentation.class.getName() + "$ConsumerConfigurationDataMethodAdvice");
  }

  @SuppressWarnings("unused")
  public static class ConsumerConfigurationDataMethodAdvice {

    @AssignReturned.ToReturned
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static MessageListener<?> after(
        @Advice.This ConsumerConfigurationData<?> data,
        @Advice.Return(typing = Assigner.Typing.DYNAMIC) MessageListener<?> listener) {
      return listener == null ? null : new MessageListenerWrapper<>(listener);
    }
  }

  public static class MessageListenerWrapper<T> implements MessageListener<T> {
    private static final long serialVersionUID = 1L;

    private final MessageListener<T> delegate;

    public MessageListenerWrapper(MessageListener<T> messageListener) {
      this.delegate = messageListener;
    }

    @Override
    public void received(Consumer<T> consumer, Message<T> message) {
      Context parent = VirtualFieldStore.extract(message);

      Instrumenter<PulsarRequest, Void> instrumenter = consumerProcessInstrumenter();
      PulsarRequest request = PulsarRequest.create(message);
      if (!instrumenter.shouldStart(parent, request)) {
        this.delegate.received(consumer, message);
        return;
      }

      Context current = instrumenter.start(parent, request);
      try (Scope scope = current.makeCurrent()) {
        this.delegate.received(consumer, message);
        instrumenter.end(current, request, null, null);
      } catch (Throwable t) {
        instrumenter.end(current, request, null, t);
        throw t;
      }
    }

    @Override
    public void reachedEndOfTopic(Consumer<T> consumer) {
      this.delegate.reachedEndOfTopic(consumer);
    }
  }
}
