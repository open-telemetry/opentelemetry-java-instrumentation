/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.v2_0;

import static io.opentelemetry.javaagent.instrumentation.couchbase.v2_0.VirtualFieldHelper.COUCHBASE_SERVER_TARGET;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.couchbase.client.core.ClusterFacade;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbServerTarget;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

// The synchronous CouchbaseCluster also funnels through this constructor. Object is used because
// the 2.x line moved the connection string type between packages.
class CouchbaseClusterTargetInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("com.couchbase.client.java.CouchbaseAsyncCluster");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isConstructor().and(takesArguments(3)).and(takesArgument(2, boolean.class)),
        getClass().getName() + "$ConstructorAdvice");
  }

  @SuppressWarnings("unused")
  public static class ConstructorAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void captureConfiguredTarget(
        @Advice.FieldValue("core") ClusterFacade core,
        @Advice.Argument(1) Object connectionString) {
      DbServerTarget target = CouchbaseConnectionStrings.target(connectionString);
      if (target != null) {
        COUCHBASE_SERVER_TARGET.set(core, target);
      }
    }
  }
}
