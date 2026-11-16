/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkoactor.v1_0;

import static io.opentelemetry.javaagent.instrumentation.pekkoactor.v1_0.VirtualFields.ENVELOPE_PROPAGATED_CONTEXT;
import static io.opentelemetry.javaagent.instrumentation.pekkoactor.v1_0.VirtualFields.SYSTEM_MESSAGE_PROPAGATED_CONTEXT;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.bootstrap.executors.ExecutorAdviceHelper;
import io.opentelemetry.javaagent.bootstrap.executors.PropagatedContext;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.pekko.dispatch.Envelope;
import org.apache.pekko.dispatch.sysmsg.SystemMessage;

/**
 * A top level actor is backed by an {@code UnstartedCell} until it has started. Messages that are
 * sent to it in the meantime are queued and later handed to the started cell by the thread that
 * starts the actor, attach the context to them when they are queued.
 */
class PekkoUnstartedCellInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.apache.pekko.actor.UnstartedCell");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("sendMessage")
            .and(takesArguments(1))
            .and(takesArgument(0, named("org.apache.pekko.dispatch.Envelope"))),
        getClass().getName() + "$SendMessageAdvice");
    transformer.applyAdviceToMethod(
        named("sendSystemMessage")
            .and(takesArgument(0, named("org.apache.pekko.dispatch.sysmsg.SystemMessage"))),
        getClass().getName() + "$SendSystemMessageAdvice");
    transformer.applyAdviceToMethod(
        named("replaceWith").and(takesArguments(1)), getClass().getName() + "$ReplaceWithAdvice");
  }

  @SuppressWarnings("unused")
  public static class SendMessageAdvice {

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static PropagatedContext enter(@Advice.Argument(0) Envelope envelope) {
      Context context = Context.current();
      if (ExecutorAdviceHelper.shouldPropagateContext(context, envelope.message())) {
        return ExecutorAdviceHelper.attachContextToTask(
            context, ENVELOPE_PROPAGATED_CONTEXT, envelope);
      }
      return null;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void exit(
        @Advice.Argument(0) Envelope envelope,
        @Advice.Enter @Nullable PropagatedContext propagatedContext,
        @Advice.Thrown @Nullable Throwable throwable) {
      ExecutorAdviceHelper.cleanUpAfterSubmit(
          propagatedContext, throwable, ENVELOPE_PROPAGATED_CONTEXT, envelope);
    }
  }

  @SuppressWarnings("unused")
  public static class SendSystemMessageAdvice {

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static PropagatedContext enter(@Advice.Argument(0) SystemMessage systemMessage) {
      Context context = Context.current();
      if (ExecutorAdviceHelper.shouldPropagateContext(context, systemMessage)) {
        return ExecutorAdviceHelper.attachContextToTask(
            context, SYSTEM_MESSAGE_PROPAGATED_CONTEXT, systemMessage);
      }
      return null;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void exit(
        @Advice.Argument(0) SystemMessage systemMessage,
        @Advice.Enter @Nullable PropagatedContext propagatedContext,
        @Advice.Thrown @Nullable Throwable throwable) {
      ExecutorAdviceHelper.cleanUpAfterSubmit(
          propagatedContext, throwable, SYSTEM_MESSAGE_PROPAGATED_CONTEXT, systemMessage);
    }
  }

  @SuppressWarnings("unused")
  public static class ReplaceWithAdvice {

    // the queued messages already carry the context they were sent with, without this the context
    // of the thread that starts the actor would replace it when they are handed to the started cell
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Scope enter() {
      return Context.root().makeCurrent();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void exit(@Advice.Enter @Nullable Scope scope) {
      if (scope != null) {
        scope.close();
      }
    }
  }
}
