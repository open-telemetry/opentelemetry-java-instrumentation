package datadog.trace.agent.tooling.muzzle;

import static datadog.trace.agent.tooling.ClassLoaderMatcher.BOOTSTRAP_CLASSLOADER;
import static java.util.Collections.singletonList;

import datadog.trace.agent.tooling.Utils;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.bytebuddy.description.type.TypeDefinition;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.jar.asm.Type;
import net.bytebuddy.pool.TypePool;

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
    if (!onClasspath(className, loader)) {
      return Collections.<Mismatch>singletonList(new Mismatch.MissingClass(sources.toArray(new Source[0]), className));
    }
    final List<Mismatch> mismatches = new ArrayList<>(0);
    try {
      UnloadedType typeOnClasspath = UnloadedType.of(className, loader);
      for (Method requiredMethod : this.getMethods()) {
        mismatches.addAll(typeOnClasspath.checkMatch(requiredMethod));
      }
    } catch (Exception e) {
      // Shouldn't happen. Fail the reference check and add a mismatch for debug logging.
      mismatches.add(new Mismatch.ReferenceCheckError(e));
    }
    return mismatches;
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

    /**
     * Fallback mismatch in case an unexpected exception occurs during reference checking.
     */
    public static class ReferenceCheckError extends Mismatch {
      private final Exception referenceCheckExcetpion;

      public ReferenceCheckError(Exception e) {
        super(new Source[0]);
        this.referenceCheckExcetpion = e;
      }

      @Override
      String getMismatchDetails() {
        final StringWriter sw = new StringWriter();
        sw.write("Failed to generate reference check: ");
        // add exception message and stack trace
        final PrintWriter pw = new PrintWriter(sw);
        referenceCheckExcetpion.printStackTrace(pw);
        return sw.toString();
      }
    }

    public static class MissingMethod extends Mismatch {
      final String className;
      final String method;

      public MissingMethod(Source[] sources, String className, String method) {
        super(sources);
        this.className = className;
        this.method = method;
      }

      @Override
      String getMismatchDetails() {
        return "Missing method " + className + "#" + method;
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
    private final Type returnType;
    private final List<Type> parameterTypes;

    public Method(String name, String descriptor) {
      this(new Source[0], new Flag[0], name, Type.getMethodType(descriptor).getReturnType(), Type.getMethodType(descriptor).getArgumentTypes());
    }

    public Method(
      Source[] sources,
      Flag[] flags,
      String name,
      Type returnType,
      Type[] parameterTypes) {
      this(new HashSet<>(Arrays.asList(sources)), new HashSet<>(Arrays.asList(flags)), name, returnType, Arrays.asList(parameterTypes));
    }

    public Method(
        Set<Source> sources,
        Set<Flag> flags,
        String name,
        Type returnType,
        List<Type> parameterTypes) {
      this.sources = sources;
      this.flags = flags;
      this.name = name;
      this.returnType = returnType;
      this.parameterTypes = parameterTypes;
    }

    public Set<Source> getSources() { return sources; }

    public Set<Flag> getFlags() { return flags; }

    public String getName() { return name; }

    public Type getReturnType() { return returnType; }

    public List<Type> getParameterTypes() { return parameterTypes; }

    public Method merge(Method anotherMethod) {
      if (!this.equals(anotherMethod)) {
        throw new IllegalStateException("Cannot merge incompatible methods " + this + " <> " + anotherMethod);
      }

      final Set<Source> mergedSources = new HashSet<>();
      mergedSources.addAll(sources);
      mergedSources.addAll(anotherMethod.sources);

      final Set<Flag> mergedFlags = new HashSet<>();
      mergedFlags.addAll(flags);
      mergedFlags.addAll(anotherMethod.flags);

      return new Method(mergedSources, mergedFlags, name, returnType, parameterTypes);
    }

    @Override
    public String toString() {
      // <init>()V
      // toString()Ljava/lang/String;
      return name + Type.getMethodType(returnType, parameterTypes.toArray(new Type[0])).getDescriptor();
    }

    @Override
    public boolean equals(Object o) {
      if (o instanceof Method) {
        return toString().equals(o.toString());
      }
      return false;
    }

    @Override
    public int hashCode() {
      return toString().hashCode();
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
    private final List<Field> fields = new ArrayList<>();
    private final List<Method> methods = new ArrayList<>();

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

    public Builder withMethod(Source[] sources, Flag[] methodFlags, String methodName, Type returnType, Type... methodArgs) {
      final Method method = new Method(sources, methodFlags, methodName, returnType, methodArgs);
      int existingIndex = methods.indexOf(method);
      if (existingIndex == -1) {
        methods.add(method);
      } else {
        methods.set(existingIndex, method.merge(methods.get(existingIndex)));
      }
      return this;
    }

    public Reference build() {
      return new Reference(sources, flags, className, superName, interfaces, new HashSet<>(fields), new HashSet<>(methods));
    }
  }
}
