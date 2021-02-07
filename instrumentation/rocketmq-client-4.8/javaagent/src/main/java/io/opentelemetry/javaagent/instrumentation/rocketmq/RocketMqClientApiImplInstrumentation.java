/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rocketmq;

import static io.opentelemetry.instrumentation.rocketmq.RocketMqProducerTracer.tracer;
import static io.opentelemetry.instrumentation.rocketmq.TextMapInjectAdapter.SETTER;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.util.Map;
import io.opentelemetry.instrumentation.rocketmq.SendCallbackWrapper;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.protocol.header.SendMessageRequestHeader;

public class RocketMqClientApiImplInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.apache.rocketmq.client.impl.MQClientAPIImpl");
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        isMethod().and(named("sendMessage")).and(takesArguments(12)),
        RocketMqClientApiImplInstrumentation.class.getName() + "$SendMessageAdvice");
  }

  public static class SendMessageAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.Argument(value = 0, readOnly = false) String addr,
        @Advice.Argument(value = 2, readOnly = false) Message msg,
        @Advice.Argument(value = 3, readOnly = false) SendMessageRequestHeader requestHeader,
        @Advice.Argument(value = 6, readOnly = false) SendCallback sendCallback,
        @Advice.Local("otelSpan") Span span,
        @Advice.Local("otelScope") Scope scope) {

      Context parent = Java8BytecodeBridge.currentContext();
      span = tracer().startProducerSpan(addr, msg);
      Context newContext = parent.with(span);
      try {
        Java8BytecodeBridge.getGlobalPropagators()
            .getTextMapPropagator()
            .inject(newContext, requestHeader, SETTER);
      } catch (IllegalStateException e) {
        requestHeader = new SendMessageRequestHeader();
        requestHeader.getBornTimestamp();
        requestHeader.getDefaultTopic();
        requestHeader.getDefaultTopicQueueNums();
        requestHeader.getFlag();
        requestHeader.getProducerGroup();
        requestHeader.getMaxReconsumeTimes();
        requestHeader.getProperties();
        requestHeader.getSysFlag();
        requestHeader.getTopic();
        requestHeader.getQueueId();
        requestHeader.getReconsumeTimes();
        Java8BytecodeBridge.getGlobalPropagators()
            .getTextMapPropagator()
            .inject(newContext, requestHeader, SETTER);
      }

      scope = newContext.makeCurrent();
      if (sendCallback != null) {
        sendCallback = new SendCallbackWrapper(sendCallback, span);
      }
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Thrown Throwable throwable,
        @Advice.Argument(value = 6, readOnly = false) SendCallback sendCallback,
        @Advice.Local("otelSpan") Span span,
        @Advice.Local("otelScope") Scope scope) {
      if (scope == null) {
        return;
      }
      scope.close();
      if (sendCallback == null) {
        if (throwable == null) {
          tracer().end(span);
        } else {
          tracer().endExceptionally(span, throwable);
        }
      }
    }
  }
}
