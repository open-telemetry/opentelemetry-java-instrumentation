/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.instrumentation.indy;

import static java.util.Arrays.asList;
import static java.util.logging.Level.FINE;
import static java.util.logging.Level.SEVERE;

import io.opentelemetry.javaagent.bootstrap.IndyBootstrapDispatcher;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.MutableCallSite;
import java.lang.reflect.Method;
import java.security.PrivilegedAction;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.utility.JavaConstant;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

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
 *     System CL                     ╷          │        {@link IndyModuleRegistry#getInstrumentationClassLoader(String, ClassLoader)}
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
              CallSite.class,
              MethodHandles.Lookup.class,
              String.class,
              MethodType.class,
              Object[].class);

      IndyBootstrapDispatcher.init(
          MethodHandles.lookup().findStatic(IndyBootstrap.class, "bootstrap", bootstrapMethodType));

      AdviceBootstrapState.initialize();
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
  private static CallSite bootstrap(
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
        (PrivilegedAction<CallSite>)
            () -> internalBootstrap(lookup, adviceMethodName, adviceMethodType, args));
  }

  private static CallSite internalBootstrap(
      MethodHandles.Lookup lookup,
      String adviceMethodName,
      MethodType adviceMethodType,
      Object[] args) {
    try {
      // See the getAdviceBootstrapArguments method for the argument definitions
      return bootstrapAdvice(
          lookup,
          adviceMethodName,
          adviceMethodType,
          (String) args[0],
          (String) args[1],
          (String) args[2]);
    } catch (Exception e) {
      logger.log(SEVERE, e.getMessage(), e);
      return null;
    }
  }

  private static CallSite bootstrapAdvice(
      MethodHandles.Lookup lookup,
      String adviceMethodName,
      MethodType invokedynamicMethodType,
      String moduleClassName,
      String adviceMethodDescriptor,
      String adviceClassName)
      throws NoSuchMethodException, IllegalAccessException, ClassNotFoundException {
    try (AdviceBootstrapState nestedState =
        AdviceBootstrapState.enter(
            lookup.lookupClass(),
            moduleClassName,
            adviceClassName,
            adviceMethodName,
            adviceMethodDescriptor)) {
      if (nestedState.isNestedInvocation()) {
        // avoid re-entrancy and stack overflow errors, which may happen when bootstrapping an
        // instrumentation that also gets triggered during the bootstrap
        // for example, adding correlation ids to the thread context when executing logger.debug.
        MutableCallSite mutableCallSite = nestedState.getMutableCallSite();
        if (mutableCallSite == null) {
          mutableCallSite =
              new MutableCallSite(
                  IndyBootstrapDispatcher.generateNoopMethodHandle(invokedynamicMethodType));
          nestedState.initMutableCallSite(mutableCallSite);
        }
        return mutableCallSite;
      }

      InstrumentationModuleClassLoader instrumentationClassloader =
          IndyModuleRegistry.getInstrumentationClassLoader(
              moduleClassName, lookup.lookupClass().getClassLoader());

      // Advices are not inlined. They are loaded as normal classes by the
      // InstrumentationModuleClassloader and invoked via a method call from the instrumented method
      Class<?> adviceClass = instrumentationClassloader.loadClass(adviceClassName);
      MethodType actualAdviceMethodType =
          MethodType.fromMethodDescriptorString(adviceMethodDescriptor, instrumentationClassloader);

      MethodHandle methodHandle =
          instrumentationClassloader
              .getLookup()
              .findStatic(adviceClass, adviceMethodName, actualAdviceMethodType)
              .asType(invokedynamicMethodType);

      MutableCallSite nestedBootstrapCallSite = nestedState.getMutableCallSite();
      if (nestedBootstrapCallSite != null) {
        // There have been nested bootstrapping attempts
        // Update the callsite of those to run the actual instrumentation
        logger.log(
            FINE,
            "Fixing nested instrumentation invokedynamic instruction bootstrapping for instrumented class {0} and advice {1}.{2}, the instrumentation should be active now",
            new Object[] {lookup.lookupClass().getName(), adviceClassName, adviceMethodName});
        nestedBootstrapCallSite.setTarget(methodHandle);
        MutableCallSite.syncAll(new MutableCallSite[] {nestedBootstrapCallSite});
        return nestedBootstrapCallSite;
      } else {
        return new ConstantCallSite(methodHandle);
      }
    }
  }

  static Advice.BootstrapArgumentResolver.Factory getAdviceBootstrapArguments(
      InstrumentationModule instrumentationModule) {
    String moduleName = instrumentationModule.getClass().getName();
    return (adviceMethod, exit) ->
        (instrumentedType, instrumentedMethod) ->
            asList(
                JavaConstant.Simple.ofLoaded(moduleName),
                JavaConstant.Simple.ofLoaded(adviceMethod.getDescriptor()),
                JavaConstant.Simple.ofLoaded(adviceMethod.getDeclaringType().getName()));
  }

  /** Emit invokedynamic instruction that will call the given helper class static method. */
  public static void emitIndyStaticCall(
      MethodVisitor mv,
      String name,
      String descriptor,
      Class<?> instrumentationModule,
      String helperClassDotName) {
    mv.visitInvokeDynamicInsn(
        name,
        descriptor,
        new Handle(
            Opcodes.H_INVOKESTATIC,
            Type.getInternalName(IndyBootstrapDispatcher.class),
            "bootstrap",
            "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;",
            false),
        instrumentationModule.getName(),
        descriptor,
        helperClassDotName);
  }
}
