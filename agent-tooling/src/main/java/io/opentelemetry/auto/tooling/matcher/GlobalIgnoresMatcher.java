/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.opentelemetry.auto.tooling.matcher;

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
public class GlobalIgnoresMatcher<T extends TypeDescription>
    extends ElementMatcher.Junction.AbstractBase<T> {

  private static final Pattern COM_MCHANGE_PROXY =
      Pattern.compile("com\\.mchange\\.v2\\.c3p0\\..*Proxy");

  public static <T extends TypeDescription> ElementMatcher.Junction<T> globalIgnoresMatcher(
      final boolean skipAdditionalLibraryMatcher) {
    return new GlobalIgnoresMatcher<>(skipAdditionalLibraryMatcher);
  }

  private final ElementMatcher<T> additionalLibraryIgnoreMatcher =
      AdditionalLibraryIgnoresMatcher.additionalLibraryIgnoresMatcher();
  private final boolean skipAdditionalLibraryMatcher;

  private GlobalIgnoresMatcher(final boolean skipAdditionalLibraryMatcher) {
    this.skipAdditionalLibraryMatcher = skipAdditionalLibraryMatcher;
  }

  /**
   * Be very careful about the types of matchers used in this section as they are called on every
   * class load, so they must be fast. Generally speaking try to only use name matchers as they
   * don't have to load additional info.
   */
  @Override
  public boolean matches(final T target) {
    final String name = target.getActualName();

    if (name.startsWith("net.bytebuddy.")
        || name.startsWith("jdk.")
        || name.startsWith("org.aspectj.")
        || name.startsWith("com.intellij.rt.debugger.")
        || name.startsWith("com.p6spy.")
        || name.startsWith("com.newrelic.")
        || name.startsWith("com.dynatrace.")
        || name.startsWith("com.jloadtrace.")
        || name.startsWith("com.appdynamics.")
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

    if (name.startsWith("io.opentelemetry.auto.")) {
      // FIXME: We should remove this once
      // https://github.com/raphw/byte-buddy/issues/558 is fixed
      if (name.equals(
              "io.opentelemetry.auto.bootstrap.instrumentation.java.concurrent.RunnableWrapper")
          || name.equals(
              "io.opentelemetry.auto.bootstrap.instrumentation.java.concurrent.CallableWrapper")) {
        return false;
      }
      return true;
    }

    if (name.startsWith("java.")) {
      if (name.equals("java.net.URL") || name.equals("java.net.HttpURLConnection")) {
        return false;
      }
      if (name.startsWith("java.rmi.") || name.startsWith("java.util.concurrent.")) {
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
      if (name.startsWith("com.sun.messaging.") || name.startsWith("com.sun.jersey.api.client")) {
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

    if (name.contains("$JaxbAccessor")
        || name.contains("CGLIB$$")
        || name.contains("javassist")
        || name.contains(".asm.")
        || name.contains("$__sisu")
        || name.contains("$$EnhancerByProxool$$")
        || name.startsWith("org.springframework.core.$Proxy")) {
      return true;
    }

    if (COM_MCHANGE_PROXY.matcher(name).matches()) {
      return true;
    }

    if (!skipAdditionalLibraryMatcher && additionalLibraryIgnoreMatcher.matches(target)) {
      return true;
    }

    return false;
  }

  @Override
  public String toString() {
    return "globalIgnoresMatcher(" + additionalLibraryIgnoreMatcher.toString() + ")";
  }

  @Override
  public boolean equals(final Object other) {
    if (this == other) {
      return true;
    } else if (other == null) {
      return false;
    } else if (getClass() != other.getClass()) {
      return false;
    } else {
      return additionalLibraryIgnoreMatcher.equals(
          ((GlobalIgnoresMatcher) other).additionalLibraryIgnoreMatcher);
    }
  }

  @Override
  public int hashCode() {
    return 17 * 31 + additionalLibraryIgnoreMatcher.hashCode();
  }
}
