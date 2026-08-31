/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.common.v3_1;

import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.isStatic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.couchbase.client.core.Core;
import com.couchbase.client.core.env.SeedNode;
import com.couchbase.client.core.util.ConnectionString;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.util.Set;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

// Core constructors receive seed nodes through 3.2, connection string text in 3.3-3.5, and a parsed
// connection string from 3.6. Current clients convert direct seed sets to connection strings before
// construction, so their factory restores the deterministic direct-seed target afterward.
public class CouchbaseCoreInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("com.couchbase.client.core.Core");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isStatic().and(named("create")).and(takesArguments(3)).and(takesArgument(2, Set.class)),
        getClass().getName() + "$SeedNodesFactoryAdvice");
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
  public static class SeedNodesFactoryAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void captureConfiguredTarget(
        @Advice.Return Core core, @Advice.Argument(2) Set<SeedNode> seedNodes) {
      CouchbaseServerTargets.registerFromSeedNodes(core, seedNodes);
    }
  }

  @SuppressWarnings("unused")
  public static class SeedNodesConstructorAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void captureConfiguredTarget(
        @Advice.This Core core, @Advice.Argument(2) Set<SeedNode> seedNodes) {
      CouchbaseServerTargets.registerFromSeedNodes(core, seedNodes);
    }
  }

  @SuppressWarnings("unused")
  public static class TextConstructorAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void captureConfiguredTarget(
        @Advice.This Core core,
        @Advice.Argument(2) Set<SeedNode> seedNodes,
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
