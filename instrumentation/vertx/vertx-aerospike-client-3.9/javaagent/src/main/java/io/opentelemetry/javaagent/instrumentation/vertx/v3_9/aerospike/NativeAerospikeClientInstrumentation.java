/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.v3_9.aerospike;

import static io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.instrumentation.vertx.v3_9.aerospike.AerospikeSingletons.instrumenter;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;

import com.aerospike.client.Key;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * Instrumentation for the Aerospike client library.
 * 
 * Instruments: com.aerospike.client.AerospikeClient (from com.aerospike:aerospike-client library)
 * Methods: get(), put(), delete() and their overloads
 */
public class NativeAerospikeClientInstrumentation implements TypeInstrumentation {

  // Shared context holder for all advice classes
  public static class ContextHolder {
    public final Context context;
    public final AerospikeRequest request;
    public final Scope scope;
    
    public ContextHolder(Context context, AerospikeRequest request, Scope scope) {
      this.context = context;
      this.request = request;
      this.scope = scope;
    }
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("com.aerospike.client.AerospikeClient");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    // Instrument PUT operations
    transformer.applyAdviceToMethod(
        isMethod()
            .and(isPublic())
            .and(named("put")),
        this.getClass().getName() + "$PutAdvice");

    // Instrument GET operations (13 overloads in AerospikeClient)
    transformer.applyAdviceToMethod(
        isMethod()
            .and(isPublic())
            .and(named("get")),
        this.getClass().getName() + "$GetAdvice");

    // Instrument DELETE operations
    transformer.applyAdviceToMethod(
        isMethod()
            .and(isPublic())
            .and(named("delete")),
        this.getClass().getName() + "$DeleteAdvice");
  }

  @SuppressWarnings("unused")
  public static class PutAdvice {
  
    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static ContextHolder onEnter(@Advice.AllArguments Object[] args) {
      // Find the Key argument (usually at index 1 for synchronous methods)
      Key key = null;
      for (Object arg : args) {
        if (arg instanceof Key) {
          key = (Key) arg;
          break;
        }
      }

      AerospikeRequest request = AerospikeInstrumentationHelper.createRequest("PUT", key);
      if (request == null) {
        return null;
      }

      Context parentContext = currentContext();
      if (!instrumenter().shouldStart(parentContext, request)) {
        return null;
      }

      Context context = instrumenter().start(parentContext, request);
      return new ContextHolder(context, request, context.makeCurrent());
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(
        @Advice.Enter @Nullable ContextHolder holder,
        @Advice.Thrown Throwable throwable) {

      if (holder == null) {
        return;
      }

      holder.scope.close();
      instrumenter().end(holder.context, holder.request, null, throwable);
    }
  }

  @SuppressWarnings("unused")
  public static class GetAdvice {
  
    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static ContextHolder onEnter(@Advice.AllArguments Object[] args) {
      System.out.println("[AEROSPIKE-INST] GET onEnter called with " + args.length + " arguments");
      
      Key key = null;
      for (Object arg : args) {
        if (arg instanceof Key) {
          key = (Key) arg;
          System.out.println("[AEROSPIKE-INST] Found Key in GET: " + key.namespace + "/" + key.setName + "/" + key.userKey);
          break;
        }
      }

      AerospikeRequest request = AerospikeInstrumentationHelper.createRequest("GET", key);
      if (request == null) {
        System.out.println("[AEROSPIKE-INST] GET request is null");
        return null;
      }

      Context parentContext = currentContext();
      if (!instrumenter().shouldStart(parentContext, request)) {
        System.out.println("[AEROSPIKE-INST] GET instrumenter said NO");
        return null;
      }

      System.out.println("[AEROSPIKE-INST] GET starting span...");
      Context context = instrumenter().start(parentContext, request);
      return new ContextHolder(context, request, context.makeCurrent());
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(
        @Advice.Enter @Nullable ContextHolder holder,
        @Advice.Thrown Throwable throwable) {

      System.out.println("[AEROSPIKE-INST] GET onExit called, holder=" + holder + ", throwable=" + throwable);

      if (holder == null) {
        System.out.println("[AEROSPIKE-INST] GET holder is null - returning");
        return;
      }

      System.out.println("[AEROSPIKE-INST] GET closing scope...");
      holder.scope.close();
      System.out.println("[AEROSPIKE-INST] GET ending span...");
      instrumenter().end(holder.context, holder.request, null, throwable);
      System.out.println("[AEROSPIKE-INST] GET span ended!");
    }
  }

  @SuppressWarnings("unused")
  public static class DeleteAdvice {
  
    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static ContextHolder onEnter(@Advice.AllArguments Object[] args) {
      Key key = null;
      for (Object arg : args) {
        if (arg instanceof Key) {
          key = (Key) arg;
          break;
        }
      }

      AerospikeRequest request = AerospikeInstrumentationHelper.createRequest("DELETE", key);
      if (request == null) {
        return null;
      }

      Context parentContext = currentContext();
      if (!instrumenter().shouldStart(parentContext, request)) {
        return null;
      }

      Context context = instrumenter().start(parentContext, request);
      return new ContextHolder(context, request, context.makeCurrent());
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(
        @Advice.Enter @Nullable ContextHolder holder,
        @Advice.Thrown Throwable throwable) {

      if (holder == null) {
        return;
      }

      holder.scope.close();
      instrumenter().end(holder.context, holder.request, null, throwable);
    }
  }

}

