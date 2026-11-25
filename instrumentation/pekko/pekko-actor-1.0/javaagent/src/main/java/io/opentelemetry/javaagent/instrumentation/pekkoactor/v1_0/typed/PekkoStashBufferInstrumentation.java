/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkoactor.v1_0.typed;

import static io.opentelemetry.javaagent.instrumentation.pekkoactor.v1_0.typed.PekkoActorTypedSingletons.currentUnstashScope;
import static io.opentelemetry.javaagent.instrumentation.pekkoactor.v1_0.typed.VirtualFields.STASH_NODE_CONTEXT;
import static net.bytebuddy.matcher.ElementMatchers.nameEndsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.pekko.actor.typed.internal.StashBufferImpl;

/**
 * Unstashed messages are interpreted while the message that unstashes them is being processed, run
 * each of them in the context that was current when it was stashed.
 *
 * <p>Pekko's own hook for interpreting an unstashed message is inlined in the scala 2.13 artifacts.
 * Instead, the scope of a message is opened when its node is taken from the stash, which happens
 * right before the message is interpreted. The scope is closed when the next node is taken or when
 * unstashing ends, also when it ends with an exception.
 */
class PekkoStashBufferInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.apache.pekko.actor.typed.internal.StashBufferImpl");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    // unstashAll and the javadsl unstash delegate to the scaladsl unstash, but that call may be
    // inlined, instrument all of them
    transformer.applyAdviceToMethod(
        named("unstash").and(takesArguments(3)).or(named("unstashAll").and(takesArguments(1))),
        getClass().getName() + "$UnstashAdvice");
    // the name is mangled because the method is private and called from an inner class
    transformer.applyAdviceToMethod(
        nameEndsWith("dropHeadForUnstash").and(takesArguments(0)),
        getClass().getName() + "$DropHeadAdvice");
  }

  @SuppressWarnings("unused")
  public static class UnstashAdvice {

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static UnstashScope enter() {
      return currentUnstashScope().set(new UnstashScope());
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void exit(@Advice.Enter @Nullable UnstashScope previous) {
      UnstashScope unstashScope = currentUnstashScope().get();
      if (unstashScope != null) {
        unstashScope.close();
      }
      currentUnstashScope().restore(previous);
    }
  }

  @SuppressWarnings("unused")
  public static class DropHeadAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void exit(@Advice.Return StashBufferImpl.Node<?> node) {
      UnstashScope unstashScope = currentUnstashScope().get();
      if (unstashScope != null) {
        unstashScope.switchTo(STASH_NODE_CONTEXT.get(node));
      }
    }
  }
}
