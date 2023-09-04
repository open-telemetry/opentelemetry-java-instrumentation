/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.instrumentation.indy;

import io.opentelemetry.javaagent.bootstrap.CallDepth;
import io.opentelemetry.javaagent.bootstrap.IndyBootstrapDispatcher;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.utility.JavaConstant;

public class IndyBootstrap {

  private static final Logger logger = Logger.getLogger(IndyBootstrap.class.getName());

  private static final Method indyBootstrapMethod;

  private static final CallDepth callDepth = CallDepth.forClass(IndyBootstrap.class);

  static {
    try {
      indyBootstrapMethod =
          IndyBootstrapDispatcher.class.getMethod(
              "bootstrap",
              MethodHandles.Lookup.class,
              String.class,
              MethodType.class,
              Object[].class);

      IndyBootstrapDispatcher.bootstrap =
          IndyBootstrap.class.getMethod(
              "bootstrap",
              MethodHandles.Lookup.class,
              String.class,
              MethodType.class,
              Object[].class);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private IndyBootstrap() {}

  public static Method getIndyBootstrapMethod() {
    return indyBootstrapMethod;
  }

  @Nullable
  public static ConstantCallSite bootstrap(
      MethodHandles.Lookup lookup,
      String adviceMethodName,
      MethodType adviceMethodType,
      Object... args) {

    if (System.getSecurityManager() == null) {
      return internalBootstrap(lookup, adviceMethodName, adviceMethodType, args);
    }

    // callsite resolution needs privileged access to call Class#getClassLoader() and
    // MethodHandles$Lookup#findStatic
    return AccessController.doPrivileged(
        new PrivilegedAction<ConstantCallSite>() {
          @Override
          public ConstantCallSite run() {
            return internalBootstrap(lookup, adviceMethodName, adviceMethodType, args);
          }
        });
  }

  private static ConstantCallSite internalBootstrap(
      MethodHandles.Lookup lookup,
      String adviceMethodName,
      MethodType adviceMethodType,
      Object[] args) {
    try {
      if (callDepth.getAndIncrement() > 0) {
        // avoid re-entrancy and stack overflow errors
        // may happen when bootstrapping an instrumentation that also gets triggered during the
        // bootstrap
        // for example, adding correlation ids to the thread context when executing logger.debug.
        logger.log(
            Level.WARNING,
            "Nested instrumented invokedynamic instruction linkage detected",
            new Throwable());
        return null;
      }
      // See the getAdviceBootstrapArguments-method for where these arguments come from
      String moduleClassName = (String) args[0];
      String adviceClassName = (String) args[1];

      InstrumentationModuleClassLoader instrumentationClassloader =
          IndyModuleRegistry.getInstrumentationClassloader(
              moduleClassName, lookup.lookupClass().getClassLoader());

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
