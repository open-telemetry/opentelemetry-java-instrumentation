/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pubsub;

import static io.opentelemetry.javaagent.instrumentation.pubsub.PubsubSingletons.startAndInjectSpan;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;

import com.google.pubsub.v1.PubsubMessage;
import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class PubsubPublisherInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("com.google.cloud.pubsub.v1.Publisher");
  }

  @Override
  public void transform(TypeTransformer typeTransformer) {
    typeTransformer.applyAdviceToMethod(
        isPublic().and(named("publish")),
        this.getClass().getName() + "$PubsubPublisherAddAttributesAdvice");
  }

  @SuppressWarnings("unused")
  public static class PubsubPublisherAddAttributesAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnterHandle(
        @Advice.Argument(value = 0, readOnly = false) PubsubMessage pubsubMessage) {
      Context parentContext = Java8BytecodeBridge.currentContext();
      startAndInjectSpan(parentContext, pubsubMessage);
    }
  }
}
