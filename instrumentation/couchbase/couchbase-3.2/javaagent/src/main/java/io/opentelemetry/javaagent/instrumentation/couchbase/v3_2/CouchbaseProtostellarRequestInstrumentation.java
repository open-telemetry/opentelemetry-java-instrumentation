/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.v3_2;

import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.couchbase.client.core.Core;
import com.couchbase.client.core.CoreProtostellar;
import com.couchbase.client.core.cnc.RequestSpan;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

class CouchbaseProtostellarRequestInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("com.couchbase.client.core.protostellar.ProtostellarRequest");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isConstructor()
            .and(takesArguments(8))
            .and(takesArgument(0, named("com.couchbase.client.core.Core"))),
        getClass().getName() + "$LegacyConstructorAdvice");
    transformer.applyAdviceToMethod(
        isConstructor()
            .and(takesArguments(8))
            .and(takesArgument(0, named("com.couchbase.client.core.CoreProtostellar"))),
        getClass().getName() + "$OriginalConstructorAdvice");
    transformer.applyAdviceToMethod(
        isConstructor()
            .and(takesArguments(11))
            .and(takesArgument(1, named("com.couchbase.client.core.CoreProtostellar"))),
        getClass().getName() + "$CurrentConstructorAdvice");
  }

  @SuppressWarnings("unused")
  public static class LegacyConstructorAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(@Advice.Argument(0) Core core, @Advice.Argument(3) RequestSpan span) {
      CouchbaseProtostellarTargets.captureRequestSpan(core, span);
    }
  }

  @SuppressWarnings("unused")
  public static class OriginalConstructorAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.Argument(0) CoreProtostellar core, @Advice.Argument(3) RequestSpan span) {
      CouchbaseProtostellarTargets.captureRequestSpan(core, span);
    }
  }

  @SuppressWarnings("unused")
  public static class CurrentConstructorAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.Argument(1) CoreProtostellar core, @Advice.Argument(4) RequestSpan span) {
      CouchbaseProtostellarTargets.captureRequestSpan(core, span);
    }
  }
}
