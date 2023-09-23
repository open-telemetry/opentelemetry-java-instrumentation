/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.instrumentation.indy;

import io.opentelemetry.javaagent.bootstrap.CallDepth;
import io.opentelemetry.javaagent.bootstrap.IndyBootstrapDispatcher;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.security.PrivilegedAction;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.utility.JavaConstant;

/**
 * We instruct Byte Buddy (via {@link Advice.WithCustomMapping#bootstrap(java.lang.reflect.Method)})
 * to dispatch {@linkplain Advice.OnMethodEnter#inline() non-inlined advices} via an invokedynamic
 * (indy) instruction. The target method is linked to a dynamically created instrumentation module
 * class loader that is specific to an instrumentation module and the class loader of the
 * instrumented method.
 *
 * <p>The first invocation of an {@code INVOKEDYNAMIC} causes the JVM to dynamically link a {@link
 * CallSite}. In this case, it will use the {@link #bootstrap} method to do that. This will also
 * create the {@link InstrumentationModuleClassLoader}.
 *
 * <pre>
 *
 *   Bootstrap CL ←──────────────────────────── Agent CL
 *       ↑ └───────── IndyBootstrapDispatcher ─ ↑ ──→ └────────────── {@link IndyBootstrap#bootstrap}
 *     Ext/Platform CL               ↑          │                        ╷
 *       ↑                           ╷          │                        ↓
 *     System CL                     ╷          │        {@link IndyModuleRegistry#getInstrumentationClassloader(String, ClassLoader)}
 *       ↑                           ╷          │                        ╷
 *     Common               linking of CallSite │                        ╷
 *     ↑    ↑             (on first invocation) │                        ╷
 * WebApp1  WebApp2                  ╷          │                     creates
 *          ↑ - InstrumentedClass    ╷          │                        ╷
 *          │                ╷       ╷          │                        ╷
 *          │                INVOKEDYNAMIC      │                        ↓
 *          └────────────────┼──────────────────{@link InstrumentationModuleClassLoader}
 *                           └╶╶╶╶╶╶╶╶╶╶╶╶╶╶╶╶╶╶╶╶╶→├ AdviceClass
 *                                                  ├ AdviceHelper
 *                                                  └ {@link LookupExposer}
 *
 * Legend:
 *  ╶╶→ method calls
 *  ──→ class loader parent/child relationships
 * </pre>
 */
public class IndyBootstrap {

  private static final Logger logger = Logger.getLogger(IndyBootstrap.class.getName());

  private static final Method indyBootstrapMethod;

  static {
    try {
      indyBootstrapMethod =
          IndyBootstrapDispatcher.class.getMethod(
              "bootstrap",
              MethodHandles.Lookup.class,
              String.class,
              MethodType.class,
              Object[].class);

      MethodType bootstrapMethodType =
          MethodType.methodType(
              ConstantCallSite.class,
              MethodHandles.Lookup.class,
              String.class,
              MethodType.class,
              Object[].class);

      IndyBootstrapDispatcher.init(
          MethodHandles.lookup().findStatic(IndyBootstrap.class, "bootstrap", bootstrapMethodType));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private IndyBootstrap() {}

  public static Method getIndyBootstrapMethod() {
    return indyBootstrapMethod;
  }

  @Nullable
  @SuppressWarnings({"unused", "removal"}) // SecurityManager and AccessController are deprecated
  private static ConstantCallSite bootstrap(
      MethodHandles.Lookup lookup,
      String adviceMethodName,
      MethodType adviceMethodType,
      Object[] args) {

    if (System.getSecurityManager() == null) {
      return internalBootstrap(lookup, adviceMethodName, adviceMethodType, args);
    }

    // callsite resolution needs privileged access to call Class#getClassLoader() and
    // MethodHandles$Lookup#findStatic
    return java.security.AccessController.doPrivileged(
        (PrivilegedAction<ConstantCallSite>)
            () -> internalBootstrap(lookup, adviceMethodName, adviceMethodType, args));
  }

  private static ConstantCallSite internalBootstrap(
      MethodHandles.Lookup lookup,
      String adviceMethodName,
      MethodType adviceMethodType,
      Object[] args) {
    CallDepth callDepth = CallDepth.forClass(IndyBootstrap.class);
    try {
      if (callDepth.getAndIncrement() > 0) {
        // avoid re-entrancy and stack overflow errors, which may happen when bootstrapping an
        // instrumentation that also gets triggered during the bootstrap
        // for example, adding correlation ids to the thread context when executing logger.debug.
        logger.log(
            Level.WARNING,
            "Nested instrumented invokedynamic instruction linkage detected",
            new Throwable());
        return null;
      }
      // See the getAdviceBootstrapArguments method for where these arguments come from
      String moduleClassName = (String) args[0];
      String adviceClassName = (String) args[1];

      InstrumentationModuleClassLoader instrumentationClassloader =
          IndyModuleRegistry.getInstrumentationClassloader(
              moduleClassName, lookup.lookupClass().getClassLoader());

      // Advices are not inlined. They are loaded as normal classes by the
      // InstrumentationModuleClassloader and invoked via a method call from the instrumented method
      Class<?> adviceClass = instrumentationClassloader.loadClass(adviceClassName);
      MethodHandle methodHandle =
          instrumentationClassloader
              .getLookup()
              .findStatic(adviceClass, adviceMethodName, adviceMethodType);
      return new ConstantCallSite(methodHandle);
    } catch (Exception e) {
      logger.log(Level.SEVERE, e.getMessage(), e);
      return null;
    } finally {
      callDepth.decrementAndGet();
    }
  }

  static Advice.BootstrapArgumentResolver.Factory getAdviceBootstrapArguments(
      InstrumentationModule instrumentationModule) {
    String moduleName = instrumentationModule.getClass().getName();
    return (adviceMethod, exit) ->
        (instrumentedType, instrumentedMethod) ->
            Arrays.asList(
                JavaConstant.Simple.ofLoaded(moduleName),
                JavaConstant.Simple.ofLoaded(adviceMethod.getDeclaringType().getName()));
  }
}
