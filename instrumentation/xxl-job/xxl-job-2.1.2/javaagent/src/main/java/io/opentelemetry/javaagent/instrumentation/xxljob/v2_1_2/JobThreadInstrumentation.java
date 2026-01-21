/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.xxljob.v2_1_2;

import static io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge.rootContext;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.xxl.job.core.biz.model.TriggerParam;
import com.xxl.job.core.thread.JobThread;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.bootstrap.xxljob.ContextQueue;
import io.opentelemetry.javaagent.bootstrap.xxljob.ContextQueueHelper;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class JobThreadInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("com.xxl.job.core.thread.JobThread");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("pushTriggerQueue")
            .and(isPublic())
            .and(takesArgument(0, named("com.xxl.job.core.biz.model.TriggerParam"))),
        JobThreadInstrumentation.class.getName() + "$PushTriggerQueueAdvice");

    transformer.applyAdviceToMethod(
        named("run").and(isPublic()).and(takesArguments(0)),
        JobThreadInstrumentation.class.getName() + "$RunAdvice");
  }

  @SuppressWarnings("unused")
  public static class PushTriggerQueueAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.This JobThread jobThread, @Advice.Argument(0) TriggerParam triggerParam) {
      if (triggerParam == null) {
        return;
      }
      Context context = currentContext();
      if (context == rootContext()) {
        return;
      }

      VirtualField<JobThread, ContextQueue> virtualField =
          VirtualField.find(JobThread.class, ContextQueue.class);

      ContextQueueHelper.offerContext(jobThread, virtualField, context);
    }
  }

  @SuppressWarnings("unused")
  public static class RunAdvice {
    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(@Advice.This JobThread jobThread) {
      VirtualField<JobThread, ContextQueue> virtualField =
          VirtualField.find(JobThread.class, ContextQueue.class);

      ContextQueueHelper.clearContextQueue(virtualField, jobThread);
    }
  }
}
