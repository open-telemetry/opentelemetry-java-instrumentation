package datadog.trace.agent.tooling.muzzle;

import static net.bytebuddy.dynamic.loading.ClassLoadingStrategy.BOOTSTRAP_LOADER;

import datadog.trace.agent.tooling.Utils;
import java.util.*;
import lombok.extern.slf4j.Slf4j;

/** Matches a set of references against a classloader. */
@Slf4j
public class ReferenceMatcher {
  private final Map<ClassLoader, List<Reference.Mismatch>> mismatchCache =
      Collections.synchronizedMap(new WeakHashMap<ClassLoader, List<Reference.Mismatch>>());
  private final Reference[] references;
  private final Set<String> helperClassNames;

  public ReferenceMatcher(Reference... references) {
    this(new String[0], references);
  }

  public ReferenceMatcher(String[] helperClassNames, Reference[] references) {
    this.references = references;
    this.helperClassNames = new HashSet<>(Arrays.asList(helperClassNames));
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
    List<Reference.Mismatch> mismatches = mismatchCache.get(loader);
    if (null == mismatches) {
      synchronized (loader) {
        mismatches = mismatchCache.get(loader);
        if (null == mismatches) {
          mismatches = new ArrayList<>(0);
          for (Reference reference : references) {
            // Don't reference-check helper classes.
            // They will be injected by the instrumentation's HelperInjector.
            if (!helperClassNames.contains(reference.getClassName())) {
              mismatches.addAll(reference.checkMatch(loader));
            }
          }
          mismatchCache.put(loader, mismatches);
        }
      }
    }
    return mismatches;
  }
}
