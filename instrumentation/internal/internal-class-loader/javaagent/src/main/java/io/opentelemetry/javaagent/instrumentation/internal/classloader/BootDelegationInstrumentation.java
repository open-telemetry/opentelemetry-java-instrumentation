/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.internal.classloader;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.extendsClass;
import static java.util.logging.Level.WARNING;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isProtected;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.isStatic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.javaagent.bootstrap.BootstrapPackagePrefixesHolder;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.api.CallDepth;
import io.opentelemetry.javaagent.tooling.Constants;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;
import java.util.logging.Logger;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * Some class loaders do not delegate to their parent, so classes in those class loaders will not be
 * able to see classes in the bootstrap class loader.
 *
 * <p>In particular, instrumentation on classes in those class loaders will not be able to see the
 * shaded OpenTelemetry API classes in the bootstrap class loader.
 *
 * <p>This instrumentation forces all class loaders to delegate to the bootstrap class loader for
 * the classes that we have put in the bootstrap class loader.
 */
public class BootDelegationInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    // just an optimization to exclude common class loaders that are known to delegate to the
    // bootstrap loader (or happen to _be_ the bootstrap loader)
    return not(namedOneOf(
            "java.lang.ClassLoader",
            "com.ibm.oti.vm.BootstrapClassLoader",
            "io.opentelemetry.javaagent.instrumentation.api.AgentClassLoader"))
        .and(extendsClass(named("java.lang.ClassLoader")));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod()
            .and(named("loadClass"))
            .and(
                takesArguments(1)
                    .and(takesArgument(0, String.class))
                    .or(
                        takesArguments(2)
                            .and(takesArgument(0, String.class))
                            .and(takesArgument(1, boolean.class))))
            .and(isPublic().or(isProtected()))
            .and(not(isStatic())),
        BootDelegationInstrumentation.class.getName() + "$LoadClassAdvice");
  }

  public static class Holder {
    public static final List<String> bootstrapPackagesPrefixes = findBootstrapPackagePrefixes();

    /**
     * We have to make sure that {@link BootstrapPackagePrefixesHolder} is loaded from bootstrap
     * classloader. After that we can use in {@link LoadClassAdvice}.
     */
    private static List<String> findBootstrapPackagePrefixes() {
      try {
        Class<?> holderClass =
            Class.forName(
                "io.opentelemetry.javaagent.bootstrap.BootstrapPackagePrefixesHolder", true, null);
        MethodHandle methodHandle =
            MethodHandles.publicLookup()
                .findStatic(
                    holderClass, "getBoostrapPackagePrefixes", MethodType.methodType(List.class));
        //noinspection unchecked
        return (List<String>) methodHandle.invokeExact();
      } catch (Throwable e) {
        Logger.getLogger(Holder.class.getName())
            .log(WARNING, "Unable to load bootstrap package prefixes from the bootstrap CL", e);
        return Constants.BOOTSTRAP_PACKAGE_PREFIXES;
      }
    }
  }

  @SuppressWarnings("unused")
  public static class LoadClassAdvice {

    @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
    public static Class<?> onEnter(@Advice.Argument(0) String name) {
      // need to use call depth here to prevent re-entry from call to Class.forName() below
      // because on some JVMs (e.g. IBM's, though IBM bootstrap loader is explicitly excluded above)
      // Class.forName() ends up calling loadClass() on the bootstrap loader which would then come
      // back to this instrumentation over and over, causing a StackOverflowError
      CallDepth callDepth = CallDepth.forClass(ClassLoader.class);
      if (callDepth.getAndIncrement() > 0) {
        callDepth.decrementAndGet();
        return null;
      }

      try {
        for (String prefix : Holder.bootstrapPackagesPrefixes) {
          if (name.startsWith(prefix)) {
            try {
              return Class.forName(name, false, null);
            } catch (ClassNotFoundException ignored) {
              // Ignore
            }
          }
        }
      } finally {
        // need to reset it right away, not waiting until onExit()
        // otherwise it will prevent this instrumentation from being applied when loadClass()
        // ends up calling a ClassFileTransformer which ends up calling loadClass() further down the
        // stack on one of our bootstrap packages (since the call depth check would then suppress
        // the nested loadClass instrumentation)
        callDepth.decrementAndGet();
      }
      return null;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class)
    public static void onExit(
        @Advice.Return(readOnly = false) Class<?> result,
        @Advice.Enter Class<?> resultFromBootstrapLoader) {
      if (resultFromBootstrapLoader != null) {
        result = resultFromBootstrapLoader;
      }
    }
  }
}
