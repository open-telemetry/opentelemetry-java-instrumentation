package datadog.trace.agent.tooling.muzzle;

import datadog.trace.agent.tooling.Utils;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.bytebuddy.jar.asm.Opcodes;
import net.bytebuddy.jar.asm.Type;

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
      final Set<Source> sources,
      final Set<Flag> flags,
      final String className,
      final String superName,
      final Set<String> interfaces,
      final Set<Field> fields,
      final Set<Method> methods) {
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
    return methods;
  }

  public Set<Field> getFields() {
    return fields;
  }

  /**
   * Create a new reference which combines this reference with another reference of the same type.
   *
   * @param anotherReference A reference to the same class
   * @return a new Reference which merges the two references
   */
  public Reference merge(final Reference anotherReference) {
    if (!anotherReference.getClassName().equals(className)) {
      throw new IllegalStateException("illegal merge " + this + " != " + anotherReference);
    }
    final String superName = null == this.superName ? anotherReference.superName : this.superName;

    return new Reference(
        merge(sources, anotherReference.sources),
        mergeFlags(flags, anotherReference.flags),
        className,
        superName,
        merge(interfaces, anotherReference.interfaces),
        mergeFields(fields, anotherReference.fields),
        mergeMethods(methods, anotherReference.methods));
  }

  private static <T> Set<T> merge(final Set<T> set1, final Set<T> set2) {
    final Set<T> set = new HashSet<>();
    set.addAll(set1);
    set.addAll(set2);
    return set;
  }

  private static Set<Method> mergeMethods(final Set<Method> methods1, final Set<Method> methods2) {
    final List<Method> merged = new ArrayList<>(methods1);
    for (final Method method : methods2) {
      final int i = merged.indexOf(method);
      if (i == -1) {
        merged.add(method);
      } else {
        merged.set(i, merged.get(i).merge(method));
      }
    }
    return new HashSet<>(merged);
  }

  private static Set<Field> mergeFields(final Set<Field> fields1, final Set<Field> fields2) {
    final List<Field> merged = new ArrayList<>(fields1);
    for (final Field field : fields2) {
      final int i = merged.indexOf(field);
      if (i == -1) {
        merged.add(field);
      } else {
        merged.set(i, merged.get(i).merge(field));
      }
    }
    return new HashSet<>(merged);
  }

  private static Set<Flag> mergeFlags(final Set<Flag> flags1, final Set<Flag> flags2) {
    final Set<Flag> merged = merge(flags1, flags2);
    // TODO: Assert flags are non-contradictory and resolve
    // public > protected > package-private > private
    return merged;
  }

  @Override
  public String toString() {
    return "Reference<" + className + ">";
  }

  public static class Source {
    private final String name;
    private final int line;

    public Source(final String name, final int line) {
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
    public boolean equals(final Object o) {
      if (o instanceof Source) {
        final Source other = (Source) o;
        return name.equals(other.name) && line == other.line;
      }
      return false;
    }

    @Override
    public int hashCode() {
      return name.hashCode() + line;
    }
  }

  /**
   * A mismatch between a Reference and a runtime class.
   *
   * <p>This class' toString returns a human-readable description of the mismatch along with
   * source-code locations of the instrumentation which caused the mismatch.
   */
  public abstract static class Mismatch {
    /** Instrumentation sources which caused the mismatch. */
    private final Source[] mismatchSources;

    Mismatch(final Source[] mismatchSources) {
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

    /** Human-readable string describing the mismatch. */
    abstract String getMismatchDetails();

    public static class MissingClass extends Mismatch {
      private final String className;

      public MissingClass(final Source[] sources, final String className) {
        super(sources);
        this.className = className;
      }

      @Override
      String getMismatchDetails() {
        return "Missing class " + className;
      }
    }

    public static class MissingFlag extends Mismatch {
      private final Flag expectedFlag;
      private final String classMethodOrFieldDesc;
      private final int foundAccess;

      public MissingFlag(
          final Source[] sources,
          final String classMethodOrFieldDesc,
          final Flag expectedFlag,
          final int foundAccess) {
        super(sources);
        this.classMethodOrFieldDesc = classMethodOrFieldDesc;
        this.expectedFlag = expectedFlag;
        this.foundAccess = foundAccess;
      }

      @Override
      String getMismatchDetails() {
        return classMethodOrFieldDesc + " requires flag " + expectedFlag + " found " + foundAccess;
      }
    }

    /** Fallback mismatch in case an unexpected exception occurs during reference checking. */
    public static class ReferenceCheckError extends Mismatch {
      private final Exception referenceCheckException;
      private final Reference referenceBeingChecked;
      private final ClassLoader classLoaderBeingChecked;

      public ReferenceCheckError(
          final Exception e,
          final Reference referenceBeingChecked,
          final ClassLoader classLoaderBeingChecked) {
        super(new Source[0]);
        referenceCheckException = e;
        this.referenceBeingChecked = referenceBeingChecked;
        this.classLoaderBeingChecked = classLoaderBeingChecked;
      }

      @Override
      String getMismatchDetails() {
        final StringWriter sw = new StringWriter();
        sw.write("Failed to generate reference check for: ");
        sw.write(referenceBeingChecked.toString());
        sw.write(" on classloader ");
        sw.write(classLoaderBeingChecked.toString());
        sw.write("\n");
        // add exception message and stack trace
        final PrintWriter pw = new PrintWriter(sw);
        referenceCheckException.printStackTrace(pw);
        return sw.toString();
      }
    }

    public static class MissingField extends Mismatch {
      private final String className;
      private final String fieldName;
      private final String fieldDesc;

      public MissingField(
          final Source[] sources,
          final String className,
          final String fieldName,
          final String fieldDesc) {
        super(sources);
        this.className = className;
        this.fieldName = fieldName;
        this.fieldDesc = fieldDesc;
      }

      @Override
      String getMismatchDetails() {
        return "Missing field " + className + "#" + fieldName + fieldDesc;
      }
    }

    public static class MissingMethod extends Mismatch {
      private final String className;
      private final String method;

      public MissingMethod(final Source[] sources, final String className, final String method) {
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
  public enum Flag {
    PUBLIC {
      @Override
      public boolean supersedes(final Flag anotherFlag) {
        switch (anotherFlag) {
          case PRIVATE_OR_HIGHER:
          case PROTECTED_OR_HIGHER:
          case PACKAGE_OR_HIGHER:
            return true;
          default:
            return false;
        }
      }

      @Override
      public boolean matches(final int asmFlags) {
        return (Opcodes.ACC_PUBLIC & asmFlags) != 0;
      }
    },
    PACKAGE_OR_HIGHER {
      @Override
      public boolean supersedes(final Flag anotherFlag) {
        return anotherFlag == PRIVATE_OR_HIGHER;
      }

      @Override
      public boolean matches(final int asmFlags) {
        return (Opcodes.ACC_PUBLIC & asmFlags) != 0
            || ((Opcodes.ACC_PRIVATE & asmFlags) == 0 && (Opcodes.ACC_PROTECTED & asmFlags) == 0);
      }
    },
    PROTECTED_OR_HIGHER {
      @Override
      public boolean supersedes(final Flag anotherFlag) {
        return anotherFlag == PRIVATE_OR_HIGHER;
      }

      @Override
      public boolean matches(final int asmFlags) {
        return PUBLIC.matches(asmFlags) || (Opcodes.ACC_PROTECTED & asmFlags) != 0;
      }
    },
    PRIVATE_OR_HIGHER {
      @Override
      public boolean matches(final int asmFlags) {
        // you can't out-private a private
        return true;
      }
    },
    NON_FINAL {
      @Override
      public boolean contradicts(final Flag anotherFlag) {
        return anotherFlag == FINAL;
      }

      @Override
      public boolean matches(final int asmFlags) {
        return (Opcodes.ACC_FINAL & asmFlags) == 0;
      }
    },
    FINAL {
      @Override
      public boolean contradicts(final Flag anotherFlag) {
        return anotherFlag == NON_FINAL;
      }

      @Override
      public boolean matches(final int asmFlags) {
        return (Opcodes.ACC_FINAL & asmFlags) != 0;
      }
    },
    STATIC {
      @Override
      public boolean contradicts(final Flag anotherFlag) {
        return anotherFlag == NON_STATIC;
      }

      @Override
      public boolean matches(final int asmFlags) {
        return (Opcodes.ACC_STATIC & asmFlags) != 0;
      }
    },
    NON_STATIC {
      @Override
      public boolean contradicts(final Flag anotherFlag) {
        return anotherFlag == STATIC;
      }

      @Override
      public boolean matches(final int asmFlags) {
        return (Opcodes.ACC_STATIC & asmFlags) == 0;
      }
    },
    INTERFACE {
      @Override
      public boolean contradicts(final Flag anotherFlag) {
        return anotherFlag == NON_INTERFACE;
      }

      @Override
      public boolean matches(final int asmFlags) {
        return (Opcodes.ACC_INTERFACE & asmFlags) != 0;
      }
    },
    NON_INTERFACE {
      @Override
      public boolean contradicts(final Flag anotherFlag) {
        return anotherFlag == INTERFACE;
      }

      @Override
      public boolean matches(final int asmFlags) {
        return (Opcodes.ACC_INTERFACE & asmFlags) == 0;
      }
    };

    public boolean contradicts(final Flag anotherFlag) {
      return false;
    }

    public boolean supersedes(final Flag anotherFlag) {
      return false;
    }

    public abstract boolean matches(int asmFlags);
  }

  public static class Method {
    private final Set<Source> sources;
    private final Set<Flag> flags;
    private final String name;
    private final Type returnType;
    private final List<Type> parameterTypes;

    public Method(final String name, final String descriptor) {
      this(
          new Source[0],
          new Flag[0],
          name,
          Type.getMethodType(descriptor).getReturnType(),
          Type.getMethodType(descriptor).getArgumentTypes());
    }

    public Method(
        final Source[] sources,
        final Flag[] flags,
        final String name,
        final Type returnType,
        final Type[] parameterTypes) {
      this(
          new HashSet<>(Arrays.asList(sources)),
          new HashSet<>(Arrays.asList(flags)),
          name,
          returnType,
          Arrays.asList(parameterTypes));
    }

    public Method(
        final Set<Source> sources,
        final Set<Flag> flags,
        final String name,
        final Type returnType,
        final List<Type> parameterTypes) {
      this.sources = sources;
      this.flags = flags;
      this.name = name;
      this.returnType = returnType;
      this.parameterTypes = parameterTypes;
    }

    public Set<Source> getSources() {
      return sources;
    }

    public Set<Flag> getFlags() {
      return flags;
    }

    public String getName() {
      return name;
    }

    public Type getReturnType() {
      return returnType;
    }

    public List<Type> getParameterTypes() {
      return parameterTypes;
    }

    public Method merge(final Method anotherMethod) {
      if (!equals(anotherMethod)) {
        throw new IllegalStateException("illegal merge " + this + " != " + anotherMethod);
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
      return name + getDescriptor();
    }

    public String getDescriptor() {
      return Type.getMethodType(returnType, parameterTypes.toArray(new Type[0])).getDescriptor();
    }

    @Override
    public boolean equals(final Object o) {
      if (o instanceof Method) {
        final Method m = (Method) o;
        return name.equals(m.name) && getDescriptor().equals(m.getDescriptor());
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
    private final Type type;

    public Field(
        final Source[] sources, final Flag[] flags, final String name, final Type fieldType) {
      this.sources = new HashSet<>(Arrays.asList(sources));
      this.flags = new HashSet<>(Arrays.asList(flags));
      this.name = name;
      type = fieldType;
    }

    public String getName() {
      return name;
    }

    public Set<Source> getSources() {
      return sources;
    }

    public Set<Flag> getFlags() {
      return flags;
    }

    public Type getType() {
      return type;
    }

    public Field merge(final Field anotherField) {
      if (!equals(anotherField) || !type.equals(anotherField.type)) {
        throw new IllegalStateException("illegal merge " + this + " != " + anotherField);
      }
      return new Field(
          Reference.merge(sources, anotherField.sources).toArray(new Source[0]),
          mergeFlags(flags, anotherField.flags).toArray(new Flag[0]),
          name,
          type);
    }

    @Override
    public String toString() {
      return "FieldRef:" + name + type.getInternalName();
    }

    @Override
    public boolean equals(final Object o) {
      if (o instanceof Field) {
        final Field other = (Field) o;
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

    public Builder withSuperName(final String superName) {
      this.superName = superName;
      return this;
    }

    public Builder withInterface(final String interfaceName) {
      interfaces.add(interfaceName);
      return this;
    }

    public Builder withSource(final String sourceName, final int line) {
      sources.add(new Source(sourceName, line));
      return this;
    }

    public Builder withFlag(final Flag flag) {
      flags.add(flag);
      return this;
    }

    public Builder withField(
        final Source[] sources,
        final Flag[] fieldFlags,
        final String fieldName,
        final Type fieldType) {
      final Field field = new Field(sources, fieldFlags, fieldName, fieldType);
      final int existingIndex = fields.indexOf(field);
      if (existingIndex == -1) {
        fields.add(field);
      } else {
        fields.set(existingIndex, field.merge(fields.get(existingIndex)));
      }
      return this;
    }

    public Builder withMethod(
        final Source[] sources,
        final Flag[] methodFlags,
        final String methodName,
        final Type returnType,
        final Type... methodArgs) {
      final Method method = new Method(sources, methodFlags, methodName, returnType, methodArgs);
      final int existingIndex = methods.indexOf(method);
      if (existingIndex == -1) {
        methods.add(method);
      } else {
        methods.set(existingIndex, method.merge(methods.get(existingIndex)));
      }
      return this;
    }

    public Reference build() {
      return new Reference(
          sources,
          flags,
          className,
          superName,
          interfaces,
          new HashSet<>(fields),
          new HashSet<>(methods));
    }
  }
}
