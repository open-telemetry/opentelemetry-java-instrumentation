/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.internal.classloader;

import static java.util.logging.Level.WARNING;

import io.opentelemetry.javaagent.bootstrap.BootstrapPackagePrefixesHolder;
import io.opentelemetry.javaagent.bootstrap.CallDepth;
import io.opentelemetry.javaagent.tooling.Constants;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;
import java.util.logging.Logger;

public class BootDelegationHelper {

  private BootDelegationHelper() {}

  public static class Holder {

    public static final List<String> bootstrapPackagesPrefixes = findBootstrapPackagePrefixes();

    /**
     * We have to make sure that {@link BootstrapPackagePrefixesHolder} is loaded from bootstrap
     * class loader. After that we can use in {@link BootDelegationHelper}.
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

    private Holder() {}
  }

  public static Class<?> onEnter(String name) {
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
            System.out.println("trying bootstrap for class " + name);
            return Class.forName(name, false, null);
          } catch (ClassNotFoundException ignored) {
            // Ignore
          }
        }
      }
    } finally {
      // need to reset it right away, not waiting until method exit
      // otherwise it will prevent this instrumentation from being applied when loadClass()
      // ends up calling a ClassFileTransformer which ends up calling loadClass() further down the
      // stack on one of our bootstrap packages (since the call depth check would then suppress
      // the nested loadClass instrumentation)
      callDepth.decrementAndGet();
    }
    return null;
  }
}
