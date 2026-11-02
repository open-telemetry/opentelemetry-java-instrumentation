/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkoactor.v1_0;

import static io.opentelemetry.javaagent.instrumentation.pekkoactor.v1_0.VirtualFields.ENVELOPE_PROPAGATED_CONTEXT;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.bootstrap.executors.ExecutorAdviceHelper;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.pekko.actor.ActorCell;
import org.apache.pekko.actor.StashSupport;
import org.apache.pekko.dispatch.Envelope;

/**
 * The context attached to an envelope is removed when the envelope is first processed. A stashed
 * envelope is processed again once it is unstashed, but unstashing puts it back into the mailbox
 * without going through the dispatcher, so attach the current context to it again when it is
 * stashed.
 */
class PekkoStashSupportInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.apache.pekko.actor.StashSupport");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("stash").and(takesArguments(0)), getClass().getName() + "$StashAdvice");
  }

  @SuppressWarnings("unused")
  public static class StashAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void exit(@Advice.This StashSupport stashSupport) {
      Envelope envelope = ((ActorCell) stashSupport.context()).currentMessage();
      Context context = Context.current();
      if (envelope != null
          && ExecutorAdviceHelper.shouldPropagateContext(context, envelope.message())) {
        ExecutorAdviceHelper.attachContextToTask(context, ENVELOPE_PROPAGATED_CONTEXT, envelope);
      }
    }
  }
}
