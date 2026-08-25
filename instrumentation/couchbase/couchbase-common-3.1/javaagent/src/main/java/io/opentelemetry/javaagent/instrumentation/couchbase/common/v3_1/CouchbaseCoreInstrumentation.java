/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.common.v3_1;

import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.couchbase.client.core.Core;
import com.couchbase.client.core.util.ConnectionString;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.util.Set;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * Records the target a core was configured with while the core is being constructed, which is the
 * earliest point at which the client is complete and the last point at which its configuration is
 * still the one it was built with.
 *
 * <p>All three shapes the constructor has taken are matched, because a driver version range rarely
 * lines up with one of them: the drivers up to 3.2 are handed only the seed nodes their connection
 * string was resolved into, 3.3 to 3.5 are handed the connection string as the text the user wrote,
 * and from 3.6 they are handed it already parsed. The three shapes differ in arity or argument
 * type, so a constructor only ever matches one of them.
 *
 * <p>A client built from seed nodes rather than a connection string carries none, and is left
 * without a target unless its seed nodes were resolved by an instrumented driver.
 */
public final class CouchbaseCoreInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("com.couchbase.client.core.Core");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isConstructor().and(takesArguments(3)).and(takesArgument(2, Set.class)),
        getClass().getName() + "$SeedNodesConstructorAdvice");
    transformer.applyAdviceToMethod(
        isConstructor().and(takesArguments(4)).and(takesArgument(3, String.class)),
        getClass().getName() + "$TextConstructorAdvice");
    transformer.applyAdviceToMethod(
        isConstructor()
            .and(takesArguments(3))
            .and(takesArgument(2, named("com.couchbase.client.core.util.ConnectionString"))),
        getClass().getName() + "$ParsedConstructorAdvice");
  }

  @SuppressWarnings("unused")
  public static class SeedNodesConstructorAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void captureConfiguredTarget(
        @Advice.This Core core, @Advice.Argument(2) Set<?> seedNodes) {
      CouchbaseServerTargets.registerFromSeedNodes(core, seedNodes);
    }
  }

  @SuppressWarnings("unused")
  public static class TextConstructorAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void captureConfiguredTarget(
        @Advice.This Core core,
        @Advice.Argument(2) Set<?> seedNodes,
        @Advice.Argument(3) String connectionString) {
      if (connectionString == null) {
        CouchbaseServerTargets.registerFromSeedNodes(core, seedNodes);
        return;
      }
      CouchbaseServerTargets.register(core, CouchbaseConnectionStrings.target(connectionString));
    }
  }

  @SuppressWarnings("unused")
  public static class ParsedConstructorAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void captureConfiguredTarget(
        @Advice.This Core core, @Advice.Argument(2) ConnectionString connectionString) {
      CouchbaseServerTargets.register(core, CouchbaseConnectionStrings.target(connectionString));
    }
  }
}
