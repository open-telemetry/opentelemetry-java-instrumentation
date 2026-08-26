/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.common.v3_1;

import static net.bytebuddy.matcher.ElementMatchers.isStatic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.util.Set;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

// Through 3.2 the core receives only resolved seed nodes, which lose the DNS SRV hostname and seed
// order. Associate the original target with the seed set until the core is constructed.
public class CouchbaseSeedNodesInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("com.couchbase.client.core.util.ConnectionStringUtil");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isStatic().and(named("seedNodesFromConnectionString")).and(takesArgument(0, String.class)),
        getClass().getName() + "$SeedNodesAdvice");
  }

  @SuppressWarnings("unused")
  public static class SeedNodesAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void captureConfiguredTarget(
        @Advice.Argument(0) String connectionString, @Advice.Return Set<?> seedNodes) {
      if (seedNodes != null) {
        CouchbaseServerTargets.registerSeedNodes(
            seedNodes, CouchbaseConnectionStrings.target(connectionString));
      }
    }
  }
}
