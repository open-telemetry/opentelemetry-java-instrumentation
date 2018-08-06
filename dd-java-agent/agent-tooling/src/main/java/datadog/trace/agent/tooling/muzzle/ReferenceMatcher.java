package datadog.trace.agent.tooling.muzzle;

import static datadog.trace.bootstrap.WeakMapManager.newWeakMap;
import static net.bytebuddy.dynamic.loading.ClassLoadingStrategy.BOOTSTRAP_LOADER;

import com.blogspot.mydailyjava.weaklockfree.WeakConcurrentMap;
import datadog.trace.agent.tooling.Utils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

/** Matches a set of references against a classloader. */
@Slf4j
public class ReferenceMatcher {
  private final WeakConcurrentMap<ClassLoader, List<Reference.Mismatch>> mismatchCache =
      newWeakMap();
  private final Reference[] references;
  private final Set<String> helperClassNames;

  public ReferenceMatcher(final Reference... references) {
    this(new String[0], references);
  }

  public ReferenceMatcher(final String[] helperClassNames, final Reference[] references) {
    this.references = references;
    this.helperClassNames = new HashSet<>(Arrays.asList(helperClassNames));
  }

  /**
   * @param loader Classloader to validate against (or null for bootstrap)
   * @return true if all references match the classpath of loader
   */
  public boolean matches(final ClassLoader loader) {
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
          for (final Reference reference : references) {
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
