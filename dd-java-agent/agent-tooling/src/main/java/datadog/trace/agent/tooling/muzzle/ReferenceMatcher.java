package datadog.trace.agent.tooling.muzzle;

import static net.bytebuddy.dynamic.loading.ClassLoadingStrategy.BOOTSTRAP_LOADER;

import datadog.trace.agent.tooling.Utils;
import java.security.ProtectionDomain;
import java.util.*;

import lombok.extern.slf4j.Slf4j;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.agent.builder.AgentBuilder.Transformer;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.utility.JavaModule;

/**
 * A bytebuddy matcher that matches if expected references (classes, fields, methods, visibility)
 * are present on the classpath.
 */
@Slf4j
public class ReferenceMatcher implements AgentBuilder.RawMatcher {
  // TODO: Cache safe and unsafe classloaders

  private final Reference[] references;

  public ReferenceMatcher(Reference... references) {
    this.references = references;
  }

  @Override
  public boolean matches(
      TypeDescription typeDescription,
      ClassLoader classLoader,
      JavaModule module,
      Class<?> classBeingRedefined,
      ProtectionDomain protectionDomain) {
    return matches(classLoader);
  }

  /**
   * @param loader Classloader to validate against (or null for bootstrap)
   * @return true if all references match the classpath of loader
   */
  public boolean matches(ClassLoader loader) {
    return getMismatchedReferenceSources(loader).size() == 0;
  }

  /**
   * @param loader Classloader to validate against (or null for bootstrap)
   * @return A list of all mismatches between this ReferenceMatcher and loader's classpath.
   */
  public List<Reference.Mismatch> getMismatchedReferenceSources(ClassLoader loader) {
    if (loader == BOOTSTRAP_LOADER) {
      loader = Utils.getBootstrapProxy();
    }
    final List<Reference.Mismatch> mismatchedReferences = new ArrayList<>(0);
    for (Reference reference : references) {
      mismatchedReferences.addAll(reference.checkMatch(loader));
    }
    return mismatchedReferences;
  }

  /**
   * Create a bytebuddy matcher which throws a MismatchException when there are mismatches with the classloader under transformation.
   */
  public Transformer assertSafeTransformation(String... adviceClassNames) {
    return new Transformer() {
      @Override
      public DynamicType.Builder<?> transform(
          DynamicType.Builder<?> builder,
          TypeDescription typeDescription,
          ClassLoader classLoader,
          JavaModule module) {
        final List<Reference.Mismatch> mismatches = getMismatchedReferenceSources(classLoader);
        if (mismatches.size() == 0) {
          return builder;
        } else {
          throw new MismatchException(classLoader, mismatches);
        }
      }
    };
  }

  public static class MismatchException extends RuntimeException {
    private final List<Reference.Mismatch> mismatches;

    public MismatchException(ClassLoader classLoader, List<Reference.Mismatch> mismatches) {
      super(mismatches.size() + " mismatches on classloader: " + classLoader);
      this.mismatches = mismatches;
    }

    public List<Reference.Mismatch> getMismatches() {
      return this.mismatches;
    }
  }
}
