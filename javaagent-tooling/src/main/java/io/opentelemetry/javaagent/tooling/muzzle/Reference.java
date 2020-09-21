/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.javaagent.tooling.muzzle;

import io.opentelemetry.javaagent.tooling.Utils;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
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
  public Reference merge(Reference anotherReference) {
    if (!anotherReference.getClassName().equals(className)) {
      throw new IllegalStateException("illegal merge " + this + " != " + anotherReference);
    }
    String superName = null == this.superName ? anotherReference.superName : this.superName;

    return new Reference(
        merge(sources, anotherReference.sources),
        mergeFlags(flags, anotherReference.flags),
        className,
        superName,
        merge(interfaces, anotherReference.interfaces),
        mergeFields(fields, anotherReference.fields),
        mergeMethods(methods, anotherReference.methods));
  }

  private static <T> Set<T> merge(Set<T> set1, Set<T> set2) {
    Set<T> set = new HashSet<>();
    set.addAll(set1);
    set.addAll(set2);
    return set;
  }

  private static Set<Method> mergeMethods(Set<Method> methods1, Set<Method> methods2) {
    List<Method> merged = new ArrayList<>(methods1);
    for (Method method : methods2) {
      int i = merged.indexOf(method);
      if (i == -1) {
        merged.add(method);
      } else {
        merged.set(i, merged.get(i).merge(method));
      }
    }
    return new HashSet<>(merged);
  }

  private static Set<Field> mergeFields(Set<Field> fields1, Set<Field> fields2) {
    List<Field> merged = new ArrayList<>(fields1);
    for (Field field : fields2) {
      int i = merged.indexOf(field);
      if (i == -1) {
        merged.add(field);
      } else {
        merged.set(i, merged.get(i).merge(field));
      }
    }
    return new HashSet<>(merged);
  }

  private static Set<Flag> mergeFlags(Set<Flag> flags1, Set<Flag> flags2) {
    Set<Flag> merged = merge(flags1, flags2);
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

  /**
   * A mismatch between a Reference and a runtime class.
   *
   * <p>This class' toString returns a human-readable description of the mismatch along with
   * source-code locations of the instrumentation which caused the mismatch.
   */
  public abstract static class Mismatch {
    /** Instrumentation sources which caused the mismatch. */
    private final Source[] mismatchSources;

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

    /** Human-readable string describing the mismatch. */
    abstract String getMismatchDetails();

    public static class MissingClass extends Mismatch {
      private final String className;

      public MissingClass(Source[] sources, String className) {
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
          Source[] sources, String classMethodOrFieldDesc, Flag expectedFlag, int foundAccess) {
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
          Exception e, Reference referenceBeingChecked, ClassLoader classLoaderBeingChecked) {
        super(new Source[0]);
        referenceCheckException = e;
        this.referenceBeingChecked = referenceBeingChecked;
        this.classLoaderBeingChecked = classLoaderBeingChecked;
      }

      @Override
      String getMismatchDetails() {
        StringWriter sw = new StringWriter();
        sw.write("Failed to generate reference check for: ");
        sw.write(referenceBeingChecked.toString());
        sw.write(" on classloader ");
        sw.write(classLoaderBeingChecked.toString());
        sw.write("\n");
        // add exception message and stack trace
        PrintWriter pw = new PrintWriter(sw);
        referenceCheckException.printStackTrace(pw);
        return sw.toString();
      }
    }

    public static class MissingField extends Mismatch {
      private final String className;
      private final String fieldName;
      private final String fieldDesc;

      public MissingField(Source[] sources, String className, String fieldName, String fieldDesc) {
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
      private final String methodSignature;

      public MissingMethod(Source[] sources, String className, String methodSignature) {
        super(sources);
        this.className = className;
        this.methodSignature = methodSignature;
      }

      @Override
      String getMismatchDetails() {
        return "Missing method " + className + "#" + methodSignature;
      }
    }
  }

  /** Expected flag (or lack of flag) on a class, method, or field reference. */
  public enum Flag {
    // The following constants represent the exact visibility of a referenced class/method
    PUBLIC {
      @Override
      public boolean matches(int asmFlags) {
        return (Opcodes.ACC_PUBLIC & asmFlags) != 0;
      }
    },
    PROTECTED {
      @Override
      public boolean matches(int asmFlags) {
        return (Opcodes.ACC_PROTECTED & asmFlags) != 0;
      }
    },
    PACKAGE {
      @Override
      public boolean matches(int asmFlags) {
        return !(PUBLIC.matches(asmFlags)
            || PROTECTED.matches(asmFlags)
            || PRIVATE.matches(asmFlags));
      }
    },
    PRIVATE {
      @Override
      public boolean matches(int asmFlags) {
        return (Opcodes.ACC_PRIVATE & asmFlags) != 0;
      }
    },

    // The following constants represent a minimum access level required by a method call or field
    // access
    PROTECTED_OR_HIGHER {
      @Override
      public boolean matches(int asmFlags) {
        return PUBLIC.matches(asmFlags) || PROTECTED.matches(asmFlags);
      }
    },
    PACKAGE_OR_HIGHER {
      @Override
      public boolean matches(int asmFlags) {
        return PUBLIC.matches(asmFlags) || PROTECTED.matches(asmFlags) || PACKAGE.matches(asmFlags);
      }
    },
    PRIVATE_OR_HIGHER {
      @Override
      public boolean matches(int asmFlags) {
        // you can't out-private a private
        return true;
      }
    },

    // The following constants describe whether classes and methods are abstract or final
    FINAL {
      @Override
      public boolean matches(int asmFlags) {
        return (Opcodes.ACC_FINAL & asmFlags) != 0;
      }
    },
    NON_FINAL {
      @Override
      public boolean matches(int asmFlags) {
        return ((Opcodes.ACC_ABSTRACT | Opcodes.ACC_FINAL) & asmFlags) == 0;
      }
    },
    ABSTRACT {
      @Override
      public boolean matches(int asmFlags) {
        return (Opcodes.ACC_ABSTRACT & asmFlags) != 0;
      }
    },

    // The following constants describe whether a method/field is static or not
    STATIC {
      @Override
      public boolean matches(int asmFlags) {
        return (Opcodes.ACC_STATIC & asmFlags) != 0;
      }
    },
    NON_STATIC {
      @Override
      public boolean matches(int asmFlags) {
        return (Opcodes.ACC_STATIC & asmFlags) == 0;
      }
    },

    // The following constants describe whether a class is an interface
    INTERFACE {
      @Override
      public boolean matches(int asmFlags) {
        return (Opcodes.ACC_INTERFACE & asmFlags) != 0;
      }
    },
    NON_INTERFACE {
      @Override
      public boolean matches(int asmFlags) {
        return (Opcodes.ACC_INTERFACE & asmFlags) == 0;
      }
    };

    /**
     * Predicate method that determines whether this flag is present in the passed bitmask.
     *
     * @see Opcodes
     */
    public abstract boolean matches(int asmFlags);
  }

  public static class Method {
    private final Set<Source> sources;
    private final Set<Flag> flags;
    private final String name;
    private final Type returnType;
    private final List<Type> parameterTypes;

    public Method(String name, String descriptor) {
      this(
          new Source[0],
          new Flag[0],
          name,
          Type.getMethodType(descriptor).getReturnType(),
          Type.getMethodType(descriptor).getArgumentTypes());
    }

    public Method(
        Source[] sources, Flag[] flags, String name, Type returnType, Type[] parameterTypes) {
      this(
          new HashSet<>(Arrays.asList(sources)),
          new HashSet<>(Arrays.asList(flags)),
          name,
          returnType,
          Arrays.asList(parameterTypes));
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

    public Method merge(Method anotherMethod) {
      if (!equals(anotherMethod)) {
        throw new IllegalStateException("illegal merge " + this + " != " + anotherMethod);
      }

      Set<Source> mergedSources = new HashSet<>();
      mergedSources.addAll(sources);
      mergedSources.addAll(anotherMethod.sources);

      Set<Flag> mergedFlags = new HashSet<>();
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
    public boolean equals(Object o) {
      if (o instanceof Method) {
        Method m = (Method) o;
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

    public Field(Source[] sources, Flag[] flags, String name, Type fieldType) {
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

    public Field merge(Field anotherField) {
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
    private final Set<Flag> flags = EnumSet.noneOf(Flag.class);
    private final String className;
    private String superName = null;
    private final Set<String> interfaces = new HashSet<>();
    private final List<Field> fields = new ArrayList<>();
    private final List<Method> methods = new ArrayList<>();

    public Builder(String className) {
      this.className = className;
    }

    public Builder withSuperName(String superName) {
      this.superName = superName;
      return this;
    }

    public Builder withInterfaces(Collection<String> interfaceNames) {
      interfaces.addAll(interfaceNames);
      return this;
    }

    public Builder withInterface(String interfaceName) {
      interfaces.add(interfaceName);
      return this;
    }

    public Builder withSource(String sourceName) {
      return withSource(sourceName, 0);
    }

    public Builder withSource(String sourceName, int line) {
      sources.add(new Source(sourceName, line));
      return this;
    }

    public Builder withFlags(Collection<Flag> flags) {
      this.flags.addAll(flags);
      return this;
    }

    public Builder withFlag(Flag flag) {
      flags.add(flag);
      return this;
    }

    public Builder withField(
        Source[] sources, Flag[] fieldFlags, String fieldName, Type fieldType) {
      Field field = new Field(sources, fieldFlags, fieldName, fieldType);
      int existingIndex = fields.indexOf(field);
      if (existingIndex == -1) {
        fields.add(field);
      } else {
        fields.set(existingIndex, field.merge(fields.get(existingIndex)));
      }
      return this;
    }

    public Builder withMethod(
        Source[] sources,
        Flag[] methodFlags,
        String methodName,
        Type returnType,
        Type... methodArgs) {
      Method method = new Method(sources, methodFlags, methodName, returnType, methodArgs);
      int existingIndex = methods.indexOf(method);
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
