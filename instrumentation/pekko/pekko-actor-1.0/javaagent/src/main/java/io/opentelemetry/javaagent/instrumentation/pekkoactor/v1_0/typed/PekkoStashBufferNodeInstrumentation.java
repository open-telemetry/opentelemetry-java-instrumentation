/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkoactor.v1_0.typed;

import static io.opentelemetry.javaagent.instrumentation.pekkoactor.v1_0.typed.VirtualFields.STASH_NODE_CONTEXT;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;

import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.pekko.actor.typed.internal.StashBufferImpl;

/**
 * A node is created for every stashed message. Pekko's own hook for this, {@code createNode}, is
 * inlined in the scala 2.13 artifacts, the node constructor is not.
 */
class PekkoStashBufferNodeInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.apache.pekko.actor.typed.internal.StashBufferImpl$Node");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(isConstructor(), getClass().getName() + "$ConstructorAdvice");
  }

  @SuppressWarnings("unused")
  public static class ConstructorAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void exit(@Advice.This StashBufferImpl.Node<?> node) {
      Context context = Context.current();
      if (context != Context.root()) {
        STASH_NODE_CONTEXT.set(node, context);
      }
    }
  }
}
