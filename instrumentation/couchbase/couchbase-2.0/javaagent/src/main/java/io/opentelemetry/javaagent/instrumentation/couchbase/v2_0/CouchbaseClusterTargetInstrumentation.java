/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.v2_0;

import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.couchbase.client.core.ClusterFacade;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.couchbase.common.v2_0.CouchbaseConnectionStrings;
import io.opentelemetry.javaagent.instrumentation.couchbase.common.v2_0.CouchbaseCoreTargets;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * Records the target a cluster was configured with while the cluster is being constructed, which is
 * the last point at which the connection string is still the one the client was built with.
 *
 * <p>Every way of creating a cluster funnels through this constructor, including the synchronous
 * {@code CouchbaseCluster}, which wraps a cluster built here. The connection string is taken as an
 * {@link Object} because the 2.x line moved it between packages, so naming its type would keep the
 * advice off half of the drivers it supports.
 */
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
      CouchbaseCoreTargets.register(core, CouchbaseConnectionStrings.target(connectionString));
    }
  }
}
