package datadog.trace.agent.tooling.muzzle;

import static net.bytebuddy.dynamic.loading.ClassLoadingStrategy.BOOTSTRAP_LOADER;

import datadog.trace.agent.tooling.Utils;
import java.security.ProtectionDomain;
import java.util.*;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.agent.builder.AgentBuilder.Transformer;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.utility.JavaModule;

/**
 * A bytebuddy matcher that matches if expected references (classes, fields, methods, visibility)
 * are present on the classpath.
 */
public class ReferenceMatcher implements AgentBuilder.RawMatcher {
  // TODO: Cache safe and unsafe classloaders

  // list of unique references (by class name)
  Map<String, Reference> references = new HashMap<>();
  Set<String> referenceSources = new HashSet<>();

  // take a list of references
  public ReferenceMatcher() {
    // TODO: pass in references
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

  public boolean matches(ClassLoader loader) {
    return getMismatchedReferenceSources(loader).size() == 0;
  }

  public List<Reference.Mismatch> getMismatchedReferenceSources(ClassLoader loader) {
    if (loader == BOOTSTRAP_LOADER) {
      loader = Utils.getBootstrapProxy();
    }
    final List<Reference.Mismatch> mismatchedReferences = new ArrayList<>(0);
    for (Reference reference : references.values()) {
      mismatchedReferences.addAll(reference.checkMatch(loader));
    }
    // TODO: log mismatches
    /*
    if (mismatchedReferences.size() > 0) {
      System.out.println(mismatchedReferences.size() + " mismatches on classloader " + loader);
      for (Reference.Mismatch mismatch : mismatchedReferences) {
        // TODO: log more info about why mismatch occurred. Missing method, missing field, signature mismatch.
        System.out.println("--" + mismatch.toString());
      }
    }
    */
    return mismatchedReferences;
  }

  public Transformer assertSafeTransformation(String... adviceClassNames) {
    // load or check cache for advice class names
    for (String adviceClass : adviceClassNames) {
      if (!referenceSources.contains(adviceClass)) {
        referenceSources.add(adviceClass);
        for (Map.Entry<String, Reference> entry :
            AdviceReferenceVisitor.createReferencesFrom(
                    adviceClass, ReferenceMatcher.class.getClassLoader())
                .entrySet()) {
          if (references.containsKey(entry.getKey())) {
            references.put(entry.getKey(), references.get(entry.getKey()).merge(entry.getValue()));
          } else {
            references.put(entry.getKey(), entry.getValue());
          }
        }
      }
    }

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
          // TODO: make mismatch exception type and add more descriptive logging at listener level.
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
