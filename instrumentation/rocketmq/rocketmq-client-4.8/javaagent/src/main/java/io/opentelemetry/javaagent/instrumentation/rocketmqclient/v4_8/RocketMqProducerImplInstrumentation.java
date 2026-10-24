/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rocketmqclient.v4_8;

import static io.opentelemetry.javaagent.instrumentation.rocketmqclient.v4_8.RocketMqSingletons.batchSendHelper;
import static io.opentelemetry.javaagent.instrumentation.rocketmqclient.v4_8.RocketMqSingletons.sendMessageHook;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.rocketmqclient.v4_8.RocketMqBatchSendHelper.BatchSendState;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.util.List;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned;
import net.bytebuddy.asm.Advice.AssignReturned.ToArguments.ToArgument;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.rocketmq.client.hook.SendMessageHook;
import org.apache.rocketmq.client.impl.CommunicationMode;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.common.message.Message;

class RocketMqProducerImplInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.apache.rocketmq.client.impl.producer.DefaultMQProducerImpl");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("sendKernelImpl")
            .and(takesArgument(0, named("org.apache.rocketmq.common.message.Message")))
            .and(takesArgument(2, named("org.apache.rocketmq.client.impl.CommunicationMode")))
            .and(takesArgument(3, named("org.apache.rocketmq.client.producer.SendCallback"))),
        getClass().getName() + "$SendAdvice");
  }

  @SuppressWarnings("unused")
  public static class SendAdvice {
    @AssignReturned.ToArguments(@ToArgument(value = 3, index = 2))
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Object[] onEnter(
        @Advice.FieldValue("sendMessageHookList") List<SendMessageHook> hooks,
        @Advice.FieldValue("defaultMQProducer") Object producer,
        @Advice.Argument(0) Message message,
        @Advice.Argument(2) CommunicationMode mode,
        @Advice.Argument(3) SendCallback callback) {
      if (!hooks.contains(sendMessageHook())) {
        return null;
      }
      BatchSendState state = batchSendHelper().startSend(producer, message, mode, callback != null);
      Context context = state.getSendContext();
      SendCallback wrapped = batchSendHelper().wrap(callback, state);
      return new Object[] {state, context == null ? null : context.makeCurrent(), wrapped};
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.Enter Object[] enterResult, @Advice.Thrown Throwable throwable) {
      if (enterResult == null) {
        return;
      }
      Scope scope = (Scope) enterResult[1];
      if (scope != null) {
        scope.close();
      }
      batchSendHelper().completeSend((BatchSendState) enterResult[0], throwable);
    }
  }
}
