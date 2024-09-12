/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.internal.classloader;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.extendsClass;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.not;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
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
            "io.opentelemetry.javaagent.bootstrap.AgentClassLoader"))
        .and(extendsClass(named("java.lang.ClassLoader")));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    // we must use inlined instrumentation with ASM to prevent stack overflow errors
    transformer.applyTransformer(ClassLoaderAsmUtil.getBootDelegationTransformer());
  }
}
