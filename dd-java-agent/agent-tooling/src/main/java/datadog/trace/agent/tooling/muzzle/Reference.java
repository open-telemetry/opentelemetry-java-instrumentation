package datadog.trace.agent.tooling.muzzle;

import static datadog.trace.agent.tooling.ClassLoaderMatcher.BOOTSTRAP_CLASSLOADER;

import datadog.trace.agent.tooling.Utils;
import java.util.*;

/** A reference to a single class file. */
public class Reference {
  private final Source[] sources;
  private final String className;
  private final String superName;
  private final String[] interfaceNames;

  public Reference(Source[] sources, String className, String superName, String[] interfaces) {
    this.className = Utils.getClassName(className);
    this.superName = null == superName ? null : Utils.getClassName(superName);
    this.sources = null == sources ? new Source[0] : sources;
    this.interfaceNames = new String[interfaces == null ? 0 : interfaces.length];
    for (int i = 0; i < interfaceNames.length; ++i) {
      interfaceNames[i] = Utils.getClassName(interfaces[i]);
    }
  }

  public String getClassName() {
    return className;
  }

  /**
   * TODO: doc
   *
   * @param anotherReference A reference to the same class
   * @return a new Reference which merges the two references
   */
  public Reference merge(Reference anotherReference) {
    if (!anotherReference.getClassName().equals(className)) {
      throw new IllegalStateException("illegal merge " + this + " != " + anotherReference);
    }
    String superName = null == this.superName ? anotherReference.superName : this.superName;

    return new Reference(
        mergeWithoutDuplicates(sources, anotherReference.sources),
        className,
        superName,
        mergeWithoutDuplicates(interfaceNames, anotherReference.interfaceNames));
  }

  private <T> T[] mergeWithoutDuplicates(T[] array1, T[] array2) {
    final Set<T> set = new HashSet<>();
    set.addAll(Arrays.asList(array1));
    set.addAll(Arrays.asList(array2));
    return set.toArray(array1);
  }

  /**
   * Check this reference against a classloader's classpath.
   *
   * @param loader
   * @return A list of mismatched sources. A list of size 0 means the reference matches the class.
   */
  public List<Mismatch> checkMatch(ClassLoader loader) {
    if (loader == BOOTSTRAP_CLASSLOADER) {
      throw new IllegalStateException("Cannot directly check against bootstrap classloader");
    }
    if (onClasspath(className, loader)) {
      return new ArrayList<>(0);
    } else {
      final List<Mismatch> mismatches = new ArrayList<>();
      mismatches.add(new Mismatch.MissingClass(sources, className));
      return mismatches;
    }
  }

  private boolean onClasspath(final String className, final ClassLoader loader) {
    final String resourceName = Utils.getResourceName(className);
    return loader.getResource(resourceName) != null
        // helper classes are not on the resource path because they are loaded with reflection (See HelperInjector)
        || (className.startsWith("datadog.trace.")
            && Utils.findLoadedClass(className, loader) != null)
        // bootstrap class
        || Utils.getBootstrapProxy().getResource(resourceName) != null;
  }

  public static class Source {
    private final String name;
    private final int line;

    public Source(String name, int line) {
      this.name = name;
      this.line = line;
    }

    @Override
    public String toString() {
      return getName() + ":" + getLine();
    }

    public String getName() {
      return name;
    }

    public int getLine() {
      return line;
    }

    // FIXME: Override equals and hashCode
  }

  public abstract static class Mismatch {
    final Source[] mismatchSources;

    Mismatch(Source[] mismatchSources) {
      this.mismatchSources = mismatchSources;
    }

    @Override
    public String toString() {
      if (mismatchSources.length > 0) {
        return mismatchSources[0].toString() + " " + getMismatchDetails();
      } else {
        return "<no-source> " + getMismatchDetails();
      }
    }

    abstract String getMismatchDetails();

    public static class MissingClass extends Mismatch {
      final String className;

      public MissingClass(Source[] sources, String className) {
        super(sources);
        this.className = className;
      }

      @Override
      String getMismatchDetails() {
        return "Missing class " + className;
      }
    }
  }
}
