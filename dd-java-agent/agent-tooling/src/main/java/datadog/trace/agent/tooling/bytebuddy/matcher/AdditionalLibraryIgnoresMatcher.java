package datadog.trace.agent.tooling.bytebuddy.matcher;

import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * Additional global matchers that are used to reduce number of classes we try to apply expensive
 * matchers to.
 *
 * <p>This is separated from {@link GlobalIgnoresMatcher} to allow for better testing. The idea is
 * that we should be able to remove this matcher from the agent and all tests should still pass.
 * Moreover, no classes matched by this matcher should be modified during test run.
 */
public class AdditionalLibraryIgnoresMatcher<T extends TypeDescription>
    extends ElementMatcher.Junction.AbstractBase<T> {

  public static <T extends TypeDescription> Junction<T> additionalLibraryIgnoresMatcher() {
    return new AdditionalLibraryIgnoresMatcher<>();
  }

  /**
   * Be very careful about the types of matchers used in this section as they are called on every
   * class load, so they must be fast. Generally speaking try to only use name matchers as they
   * don't have to load additional info.
   */
  @Override
  public boolean matches(final T target) {
    final String name = target.getActualName();

    if (name.startsWith("com.fasterxml.classmate.")
        || name.startsWith("com.fasterxml.jackson.")
        || name.startsWith("net.sf.cglib.")
        || name.startsWith("org.objectweb.asm.")) {
      return true;
    }

    if (name.startsWith("org.springframework.aop.")
        || name.startsWith("org.springframework.beans.factory.annotation.")
        || name.startsWith("org.springframework.beans.factory.config.")
        || name.startsWith("org.springframework.beans.factory.parsing.")
        || name.startsWith("org.springframework.beans.factory.xml.")
        || name.startsWith("org.springframework.beans.propertyeditors.")
        || name.startsWith("org.springframework.boot.autoconfigure.cache.")
        || name.startsWith("org.springframework.boot.autoconfigure.condition.")
        || name.startsWith("org.springframework.boot.autoconfigure.http.")
        || name.startsWith("org.springframework.boot.autoconfigure.jackson.")
        || name.startsWith("org.springframework.boot.autoconfigure.web.")
        || name.startsWith("org.springframework.boot.context.")
        || name.startsWith("org.springframework.boot.convert.")
        || name.startsWith("org.springframework.boot.diagnostics.")
        || name.startsWith("org.springframework.boot.web.server.")
        || name.startsWith("org.springframework.boot.web.servlet.")
        || name.startsWith("org.springframework.context.annotation.")
        || name.startsWith("org.springframework.context.event.")
        || name.startsWith("org.springframework.context.expression.")
        || name.startsWith("org.springframework.core.annotation.")
        || name.startsWith("org.springframework.core.convert.")
        || name.startsWith("org.springframework.core.env.")
        || name.startsWith("org.springframework.core.io.")
        || name.startsWith("org.springframework.core.type.")
        || name.startsWith("org.springframework.expression.")
        || name.startsWith("org.springframework.format.")
        || name.startsWith("org.springframework.ui.")
        || name.startsWith("org.springframework.validation.")
        || name.startsWith("org.springframework.web.context.")
        || name.startsWith("org.springframework.web.filter.")
        || name.startsWith("org.springframework.web.method.")
        || name.startsWith("org.springframework.web.multipart.")
        || name.startsWith("org.springframework.web.util.")) {
      return true;
    }

    // xml-apis, xerces, xalan
    if (name.startsWith("javax.xml.")
        || name.startsWith("org.apache.bcel.")
        || name.startsWith("org.apache.html.")
        || name.startsWith("org.apache.regexp.")
        || name.startsWith("org.apache.wml.")
        || name.startsWith("org.apache.xalan.")
        || name.startsWith("org.apache.xerces.")
        || name.startsWith("org.apache.xml.")
        || name.startsWith("org.apache.xpath.")
        || name.startsWith("org.xml.")) {
      return true;
    }

    // kotlin, note we do not ignore kotlinx because we instrument coroutins code
    if (name.startsWith("kotlin.")) {
      return true;
    }

    // Dynamic proxy classes we should not touch
    if (name.startsWith("org.springframework.core.$Proxy")) {
      return true;
    }

    if (name.startsWith("org.springframework.cglib.")) {
      // This class contains nested Callable instance that we'd happily not touch, but unfortunately
      // our field injection code is not flexible enough to realize that, so instead
      // we instrument this Callable to make tests happy.
      if (name.startsWith("org.springframework.cglib.core.internal.LoadingCache$")) {
        return false;
      }

      return true;
    }

    if (name.startsWith("org.springframework.http.")) {
      // There are some Mono implementation that get instrumented
      if (name.startsWith("org.springframework.http.server.reactive.")) {
        return false;
      }

      return true;
    }

    return false;
  }

  @Override
  public String toString() {
    return "additionalLibraryIgnoresMatcher()";
  }

  @Override
  public boolean equals(final Object other) {
    if (!super.equals(other)) {
      return false;
    } else if (this == other) {
      return true;
    } else if (other == null) {
      return false;
    } else {
      return getClass() == other.getClass();
    }
  }

  @Override
  public int hashCode() {
    return 17;
  }
}
