package datadog.trace.agent.tooling.muzzle;

import static datadog.trace.agent.tooling.ClassLoaderMatcher.BOOTSTRAP_CLASSLOADER;

import datadog.trace.agent.tooling.Utils;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** An immutable reference to a jvm class. */
public class Reference {
  private final Set<Source> sources;
  private final String className;
  private final String superName;
  private final Set<Flag> flags;
  private final Set<String> interfaces;
  private final Set<Field> fields;
  private final Set<Method> methods;

  private Reference(
      Set<Source> sources,
      Set<Flag> flags,
      String className,
      String superName,
      Set<String> interfaces,
      Set<Field> fields,
      Set<Method> methods) {
    this.sources = sources;
    this.flags = flags;
    this.className = Utils.getClassName(className);
    this.superName = null == superName ? null : Utils.getClassName(superName);
    this.interfaces = interfaces;
    this.methods = methods;
    this.fields = fields;
  }

  public String getClassName() {
    return className;
  }

  public String getSuperName() {
    return superName;
  }

  public Set<String> getInterfaces() {
    return interfaces;
  }

  public Set<Source> getSources() {
    return sources;
  }

  public Set<Flag> getFlags() {
    return flags;
  }

  public Set<Method> getMethods() {
    return this.methods;
  }

  public Set<Field> getFields() {
    return this.fields;
  }

  /**
   * Create a new reference which combines this reference with another reference.
   *
   * <p>Attempts to merge incompatible references will throw an IllegalStateException.
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
        merge(sources, anotherReference.sources),
        merge(flags, anotherReference.flags),
        className,
        superName,
        merge(interfaces, anotherReference.interfaces),
        merge(fields, anotherReference.fields),
        merge(methods, anotherReference.methods));
  }

  private static <T> Set<T> merge(Set<T> set1, Set<T> set2) {
    final Set<T> set = new HashSet<>();
    set.addAll(set1);
    set.addAll(set2);
    return set;
  }

  private static Set<Flag> mergeFlags(Set<Flag> flags1, Set<Flag> flags2) {
    Set<Flag> merged = merge(flags1, flags2);
    // TODO: Assert flags are non-contradictory and resolve
    // public > protected > package-private > private
    return merged;
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
      mismatches.add(new Mismatch.MissingClass(sources.toArray(new Source[0]), className));
      return mismatches;
    }
  }

  private boolean onClasspath(final String className, final ClassLoader loader) {
    final String resourceName = Utils.getResourceName(className);
    return loader.getResource(resourceName) != null
        // we can also reach bootstrap classes
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

    @Override
    public boolean equals(Object o) {
      if (o instanceof Source) {
        Source other = (Source) o;
        return name.equals(other.name) && line == other.line;
      }
      return false;
    }

    @Override
    public int hashCode() {
      return name.hashCode() + line;
    }
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

  /** Expected flag (or lack of flag) on a class, method, or field reference. */
  public static enum Flag {
    PUBLIC,
    PACKAGE_OR_HIGHER,
    PROTECTED_OR_HIGHER,
    PRIVATE_OR_HIGHER,
    NON_FINAL,
    STATIC,
    NON_STATIC,
    INTERFACE,
    NON_INTERFACE
  }

  public static class Method {
    private final Set<Source> sources;
    private final Set<Flag> flags;
    private final String name;
    private final String returnType;
    private final List<String> parameterTypes;

    public Method(
        Set<Source> sources,
        Set<Flag> flags,
        String name,
        String returnType,
        List<String> parameterTypes) {
      this.sources = sources;
      this.flags = flags;
      this.name = name;
      this.returnType = returnType;
      this.parameterTypes = parameterTypes;
    }

    public String getName() {
      return name;
    }

    public Set<Flag> getFlags() {
      return flags;
    }

    public String getReturnType() {
      return returnType;
    }

    public List<String> getParameterTypes() {
      return parameterTypes;
    }

    public Method merge(Method anotherMethod) {
      // TODO
      return this;
    }

    @Override
    public boolean equals(Object o) {
      if (o instanceof Method) {
        Method other = (Method) o;
        if ((!name.equals(other.name))
            || (!returnType.equals(other.returnType))
            || parameterTypes.size() != other.parameterTypes.size()) {
          return false;
        }
        for (int i = 0; i < parameterTypes.size(); ++i) {
          if (!parameterTypes.get(i).equals(other.parameterTypes.get(i))) {
            return false;
          }
        }
        return true;
      }
      return false;
    }

    @Override
    public int hashCode() {
      // will cause collisions for overloaded method refs but performance hit should be negligable
      return name.hashCode();
    }
  }

  public static class Field {
    private final Set<Source> sources;
    private final Set<Flag> flags;
    private final String name;

    public Field(Set<Source> sources, Set<Flag> flags, String name) {
      this.sources = sources;
      this.flags = flags;
      this.name = name;
    }

    public String getName() {
      return name;
    }

    public Set<Flag> getFlags() {
      return flags;
    }

    public Field merge(Field anotherField) {
      // TODO: implement
      // also assert same class
      return this;
    }

    @Override
    public boolean equals(Object o) {
      if (o instanceof Field) {
        Field other = (Field) o;
        return name.equals(other.name);
      }
      return false;
    }

    @Override
    public int hashCode() {
      return name.hashCode();
    }
  }

  public static class Builder {
    private final Set<Source> sources = new HashSet<>();
    private final Set<Flag> flags = new HashSet<>();
    private final String className;
    private String superName = null;
    private final Set<String> interfaces = new HashSet<>();
    private final Set<Field> fields = new HashSet<>();
    private final Set<Method> methods = new HashSet<>();

    public Builder(final String className) {
      this.className = className;
    }

    public Builder withSuperName(String superName) {
      this.superName = superName;
      return this;
    }

    public Builder withInterface(String interfaceName) {
      interfaces.add(interfaceName);
      return this;
    }

    public Builder withSource(String sourceName, int line) {
      sources.add(new Source(sourceName, line));
      return this;
    }

    public Builder withFlag(Flag flag) {
      // TODO
      return this;
    }

    public Builder withField(String fieldName, Flag... fieldFlags) {
      // TODO
      return this;
    }

    public Builder withMethod(
        String methodName, Flag[] methodFlags, String returnType, String[] methodArgs) {
      // TODO
      return this;
    }

    public Reference build() {
      return new Reference(sources, flags, className, superName, interfaces, fields, methods);
    }
  }
}
