package datadog.trace.agent.tooling.bytebuddy;

import java.util.regex.Pattern;
import net.bytebuddy.build.HashCodeAndEqualsPlugin;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@HashCodeAndEqualsPlugin.Enhance
public class GlobalIgnoresMatcher<T extends TypeDescription>
    extends ElementMatcher.Junction.AbstractBase<T> {

  private static final Pattern COM_MCHANGE_PROXY =
      Pattern.compile("com\\.mchange\\.v2\\.c3p0\\..*Proxy");

  public static <T extends TypeDescription> ElementMatcher.Junction<T> globalIgnoresMatcher() {
    return new GlobalIgnoresMatcher<>();
  }

  /**
   * Be very careful about the types of matchers used in this section as they are called on every
   * class load, so they must be fast. Generally speaking try to only use name matchers as they
   * don't have to load additional info.
   */
  @Override
  public boolean matches(final T target) {
    final String name = target.getActualName();

    if (name.startsWith("datadog.opentracing.")
        || name.startsWith("datadog.slf4j.")
        || name.startsWith("net.bytebuddy.")
        || name.startsWith("jdk.")
        || name.startsWith("org.aspectj.")
        || name.startsWith("org.groovy.")
        || name.startsWith("org.codehaus.groovy.macro.")
        || name.startsWith("com.intellij.rt.debugger.")
        || name.startsWith("com.p6spy.")
        || name.startsWith("com.newrelic.")
        || name.startsWith("com.dynatrace.")
        || name.startsWith("com.jloadtrace.")
        || name.startsWith("com.appdynamics.")
        || name.startsWith("com.singularity.")
        || name.startsWith("com.jinspired.")
        || name.startsWith("org.jinspired.")
        || name.startsWith("org.springframework.cglib.")) {
      return true;
    }

    if (name.startsWith("datadog.trace.")) {
      // FIXME: We should remove this once
      // https://github.com/raphw/byte-buddy/issues/558 is fixed
      if (name.equals("datadog.trace.bootstrap.instrumentation.java.concurrent.RunnableWrapper")
          || name.equals(
              "datadog.trace.bootstrap.instrumentation.java.concurrent.CallableWrapper")) {
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

    if (name.startsWith("org.apache.log4j.")) {
      if (name.equals("org.apache.log4j.MDC")) {
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
        || name.contains("$__sisu")) {
      return true;
    }

    if (COM_MCHANGE_PROXY.matcher(name).matches()) {
      return true;
    }

    return false;
  }
}
