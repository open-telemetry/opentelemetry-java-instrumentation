/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.matcher;

import io.opentelemetry.javaagent.spi.IgnoreMatcherProvider;
import java.util.regex.Pattern;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * Global ignores matcher used by the agent.
 *
 * <p>This matcher services two main purposes:
 * <li>
 *
 *     <ul>
 *       Ignore classes that are unsafe or pointless to transform. 'System' level classes like jvm
 *       classes or groovy classes, other tracers, debuggers, etc.
 * </ul>
 *
 * <ul>
 *   Uses {@link AdditionalLibraryIgnoresMatcher} to also ignore additional classes to minimize
 *   number of classes we apply expensive matchers to.
 * </ul>
 */
public class GlobalIgnoresMatcher extends ElementMatcher.Junction.AbstractBase<TypeDescription> {

  private static final Pattern COM_MCHANGE_PROXY =
      Pattern.compile("com\\.mchange\\.v2\\.c3p0\\..*Proxy");

  public static ElementMatcher.Junction<TypeDescription> globalIgnoresMatcher(
      boolean additionalLibraryMatcher, IgnoreMatcherProvider ignoreMatcherProviders) {
    return new GlobalIgnoresMatcher(additionalLibraryMatcher, ignoreMatcherProviders);
  }

  private final ElementMatcher<TypeDescription> additionalLibraryIgnoreMatcher =
      AdditionalLibraryIgnoresMatcher.additionalLibraryIgnoresMatcher();
  private final boolean additionalLibraryMatcher;
  private final IgnoreMatcherProvider ignoreMatcherProvider;

  private GlobalIgnoresMatcher(
      boolean additionalLibraryMatcher, IgnoreMatcherProvider ignoreMatcherProvider) {
    this.additionalLibraryMatcher = additionalLibraryMatcher;
    this.ignoreMatcherProvider = ignoreMatcherProvider;
  }

  /**
   * Be very careful about the types of matchers used in this section as they are called on every
   * class load, so they must be fast. Generally speaking try to only use name matchers as they
   * don't have to load additional info.
   */
  @Override
  public boolean matches(TypeDescription target) {
    IgnoreMatcherProvider.Result ignoreResult = ignoreMatcherProvider.type(target);
    switch (ignoreResult) {
      case IGNORE:
        return true;
      case ALLOW:
        return false;
      case DEFAULT:
    }

    String name = target.getActualName();

    if (name.startsWith("jdk.internal.net.http.")) {
      return false;
    }

    if (name.startsWith("org.gradle.")
        || name.startsWith("net.bytebuddy.")
        || name.startsWith("jdk.")
        || name.startsWith("org.aspectj.")
        || name.startsWith("datadog.")
        || name.startsWith("com.intellij.rt.debugger.")
        || name.startsWith("com.p6spy.")
        || name.startsWith("com.dynatrace.")
        || name.startsWith("com.jloadtrace.")
        || name.startsWith("com.appdynamics.")
        || name.startsWith("com.newrelic.agent.")
        || name.startsWith("com.newrelic.api.agent.")
        || name.startsWith("com.nr.agent.")
        || name.startsWith("com.singularity.")
        || name.startsWith("com.jinspired.")
        || name.startsWith("org.jinspired.")) {
      return true;
    }

    // groovy
    if (name.startsWith("org.groovy.") || name.startsWith("org.apache.groovy.")) {
      return true;
    }
    if (name.startsWith("org.codehaus.groovy.")) {
      // We seem to instrument some classes in runtime
      if (name.startsWith("org.codehaus.groovy.runtime.")) {
        return false;
      }
      return true;
    }
    // clojure
    if (name.startsWith("clojure.") || name.contains("$fn__")) {
      return true;
    }

    if (name.startsWith("io.opentelemetry.javaagent.")) {
      // FIXME: We should remove this once
      // https://github.com/raphw/byte-buddy/issues/558 is fixed
      if (name.equals("io.opentelemetry.javaagent.instrumentation.api.concurrent.RunnableWrapper")
          || name.equals(
              "io.opentelemetry.javaagent.instrumentation.api.concurrent.CallableWrapper")) {
        return false;
      }
      return true;
    }

    if (name.startsWith("java.")) {
      if (name.equals("java.net.URL")
          || name.equals("java.net.HttpURLConnection")
          || name.equals("java.net.URLClassLoader")) {
        return false;
      }
      if (name.startsWith("java.rmi.") || name.startsWith("java.util.concurrent.")) {
        return false;
      }
      if (name.equals("java.lang.reflect.Proxy")) {
        return false;
      }
      if (name.equals("java.lang.ClassLoader")) {
        return false;
      }

      // Concurrent instrumentation modifies the structure of
      // Cleaner class incompatibly with java9+ modules.
      // Working around until a long-term fix for modules can be
      // put in place.
      if (name.startsWith("java.util.logging.")
          && !name.equals("java.util.logging.LogManager$Cleaner")) {
        return false;
      }

      return true;
    }

    if (name.startsWith("com.sun.")) {
      if (name.startsWith("com.sun.messaging.")
          || name.startsWith("com.sun.jersey.api.client")
          || name.startsWith("com.sun.appserv")
          || name.startsWith("com.sun.faces")
          || name.startsWith("com.sun.xml.ws")) {
        return false;
      }

      return true;
    }

    if (name.startsWith("sun.")) {
      if (name.startsWith("sun.net.www.protocol.")
          || name.startsWith("sun.rmi.server")
          || name.startsWith("sun.rmi.transport")
          || name.equals("sun.net.www.http.HttpClient")) {
        return false;
      }

      return true;
    }

    if (name.startsWith("org.slf4j.")) {
      if (name.equals("org.slf4j.MDC")) {
        return false;
      }

      return true;
    }

    // bytecode proxies typically have $$ in their name
    if (name.contains("$$")) {
      // scala anonymous classes
      if (name.contains("$$anon$")) {
        return false;
      }
      return true;
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
        || name.contains("__EJB31_Generated__")
        || name.startsWith("org.springframework.core.$Proxy")
        // Tapestry Proxy, check only specific class that we know would be instrumented since there
        // is no common prefix for its proxies other than "$". ByteBuddy fails to instrument this
        // proxy, and as there is no reason why it should be instrumented anyway, exclude it.
        || name.startsWith("$HttpServletRequest_")) {
      return true;
    }

    if (COM_MCHANGE_PROXY.matcher(name).matches()) {
      return true;
    }

    if (additionalLibraryMatcher && additionalLibraryIgnoreMatcher.matches(target)) {
      return true;
    }

    return false;
  }

  @Override
  public String toString() {
    return "globalIgnoresMatcher(" + additionalLibraryIgnoreMatcher.toString() + ")";
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }
    if (!(obj instanceof GlobalIgnoresMatcher)) {
      return false;
    }
    GlobalIgnoresMatcher other = (GlobalIgnoresMatcher) obj;
    return additionalLibraryIgnoreMatcher.equals(other.additionalLibraryIgnoreMatcher);
  }

  @Override
  public int hashCode() {
    return additionalLibraryIgnoreMatcher.hashCode();
  }
}
