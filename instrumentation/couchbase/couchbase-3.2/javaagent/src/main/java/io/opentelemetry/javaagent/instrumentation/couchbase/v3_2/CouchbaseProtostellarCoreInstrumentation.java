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
import com.couchbase.client.core.env.SeedNode;
import com.couchbase.client.core.util.ConnectionString;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.util.Set;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

class CouchbaseProtostellarCoreInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("com.couchbase.client.core.CoreProtostellar");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isConstructor()
            .and(takesArguments(3))
            .and(takesArgument(0, named("com.couchbase.client.core.Core")))
            .and(takesArgument(2, named("java.util.Set"))),
        getClass().getName() + "$LegacyConstructorAdvice");
    transformer.applyAdviceToMethod(
        isConstructor()
            .and(takesArguments(3))
            .and(takesArgument(2, named("com.couchbase.client.core.util.ConnectionString"))),
        getClass().getName() + "$CurrentConstructorAdvice");
  }

  @SuppressWarnings("unused")
  public static class LegacyConstructorAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.Argument(0) Core core, @Advice.Argument(2) Set<SeedNode> seedNodes) {
      CouchbaseProtostellarTargets.registerCore(core, seedNodes);
    }
  }

  @SuppressWarnings("unused")
  public static class CurrentConstructorAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.This CoreProtostellar core, @Advice.Argument(2) ConnectionString connectionString) {
      CouchbaseProtostellarTargets.registerCore(core, connectionString);
    }
  }
}
