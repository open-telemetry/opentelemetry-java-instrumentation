/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.ignore;

import io.opentelemetry.javaagent.instrumentation.api.util.Trie;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class IgnoredTypesMatcher extends ElementMatcher.Junction.AbstractBase<TypeDescription> {

  private final Trie<IgnoreAllow> ignoredTypes;

  public IgnoredTypesMatcher(Trie<IgnoreAllow> ignoredTypes) {
    this.ignoredTypes = ignoredTypes;
  }

  @Override
  public boolean matches(TypeDescription target) {
    String name = target.getActualName();

    IgnoreAllow ignored = ignoredTypes.getOrNull(name);
    if (ignored == IgnoreAllow.ALLOW) {
      return false;
    } else if (ignored == IgnoreAllow.IGNORE) {
      return true;
    }

    // bytecode proxies typically have $$ in their name
    if (name.contains("$$") && !name.contains("$$Lambda$")) {
      // allow scala anonymous classes
      return !name.contains("$$anon$");
    }

    if (name.contains("$JaxbAccessor")
        || name.contains("CGLIB$$")
        || name.contains("javassist")
        || name.contains(".asm.")
        || name.contains("$__sisu")
        || name.contains("$$EnhancerByProxool$$")
        // glassfish ejb proxy
        // We skip instrumenting these because some instrumentations e.g. jax-rs instrument methods
        // that are annotated with @Path in an interface implemented by the class. We don't really
        // want to instrument these methods in generated classes as this would create spans that
        // have the generated class name in them instead of the actual class that handles the call.
        || name.contains("__EJB31_Generated__")) {
      return true;
    }

    if (name.startsWith("com.mchange.v2.c3p0.") && name.endsWith("Proxy")) {
      return true;
    }

    return false;
  }
}
