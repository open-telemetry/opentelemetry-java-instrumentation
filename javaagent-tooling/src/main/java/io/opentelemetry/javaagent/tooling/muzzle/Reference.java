/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.muzzle;

import io.opentelemetry.javaagent.tooling.Utils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import net.bytebuddy.jar.asm.Opcodes;
import net.bytebuddy.jar.asm.Type;
import org.checkerframework.checker.nullness.qual.Nullable;

/** This class represents a reference to a Java class used in an instrumentation advice code. */
public final class Reference {
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

  @Nullable
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
    Set<T> set = new LinkedHashSet<>();
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
    return new LinkedHashSet<>(merged);
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
    return new LinkedHashSet<>(merged);
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

  /** Represents the source (file name, line number) of a reference. */
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
    public boolean equals(Object obj) {
      if (obj == this) {
        return true;
      }
      if (!(obj instanceof Source)) {
        return false;
      }
      Source other = (Source) obj;
      return name.equals(other.name) && line == other.line;
    }

    @Override
    public int hashCode() {
      return Objects.hash(name, line);
    }
  }

  /** Expected flag (or lack of flag) on a class, method or field reference. */
  public interface Flag {
    /**
     * Predicate method that determines whether this flag is present in the passed bitmask.
     *
     * @see Opcodes
     */
    boolean matches(int asmFlags);

    // This method is internally used to generate the getMuzzleReferenceMatcher() implementation
    /** Same as {@link Enum#name()}. */
    String name();

    /**
     * The constants of this enum represent the exact visibility of a referenced class, method or
     * field.
     *
     * @see net.bytebuddy.description.modifier.Visibility
     */
    enum VisibilityFlag implements Flag {
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
      }
    }

    /**
     * The constants of this enum represent the minimum visibility flag required by a type access,
     * method call or field access.
     *
     * @see net.bytebuddy.description.modifier.Visibility
     */
    enum MinimumVisibilityFlag implements Flag {
      PUBLIC {
        @Override
        public boolean matches(int asmFlags) {
          return VisibilityFlag.PUBLIC.matches(asmFlags);
        }
      },
      PROTECTED_OR_HIGHER {
        @Override
        public boolean matches(int asmFlags) {
          return VisibilityFlag.PUBLIC.matches(asmFlags)
              || VisibilityFlag.PROTECTED.matches(asmFlags);
        }
      },
      PACKAGE_OR_HIGHER {
        @Override
        public boolean matches(int asmFlags) {
          return !VisibilityFlag.PRIVATE.matches(asmFlags);
        }
      },
      PRIVATE_OR_HIGHER {
        @Override
        public boolean matches(int asmFlags) {
          // you can't out-private a private
          return true;
        }
      }
    }

    /**
     * The constants of this enum describe whether a method or class is abstract, final or
     * non-final.
     *
     * @see net.bytebuddy.description.modifier.TypeManifestation
     * @see net.bytebuddy.description.modifier.MethodManifestation
     */
    enum ManifestationFlag implements Flag {
      FINAL {
        @Override
        public boolean matches(int asmFlags) {
          return (Opcodes.ACC_FINAL & asmFlags) != 0;
        }
      },
      NON_FINAL {
        @Override
        public boolean matches(int asmFlags) {
          return !(ABSTRACT.matches(asmFlags) || FINAL.matches(asmFlags));
        }
      },
      ABSTRACT {
        @Override
        public boolean matches(int asmFlags) {
          return (Opcodes.ACC_ABSTRACT & asmFlags) != 0;
        }
      },
      INTERFACE {
        @Override
        public boolean matches(int asmFlags) {
          return (Opcodes.ACC_INTERFACE & asmFlags) != 0;
        }
      },
      NON_INTERFACE {
        @Override
        public boolean matches(int asmFlags) {
          return !INTERFACE.matches(asmFlags);
        }
      }
    }

    /**
     * The constants of this enum describe whether a method/field is static or not.
     *
     * @see net.bytebuddy.description.modifier.Ownership
     */
    enum OwnershipFlag implements Flag {
      STATIC {
        @Override
        public boolean matches(int asmFlags) {
          return (Opcodes.ACC_STATIC & asmFlags) != 0;
        }
      },
      NON_STATIC {
        @Override
        public boolean matches(int asmFlags) {
          return !STATIC.matches(asmFlags);
        }
      }
    }
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
          new LinkedHashSet<>(Arrays.asList(sources)),
          new LinkedHashSet<>(Arrays.asList(flags)),
          name,
          returnType,
          Arrays.asList(parameterTypes));
    }

    private Method(
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

      Set<Source> mergedSources = new LinkedHashSet<>();
      mergedSources.addAll(sources);
      mergedSources.addAll(anotherMethod.sources);

      Set<Flag> mergedFlags = new LinkedHashSet<>();
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
    public boolean equals(Object obj) {
      if (obj == this) {
        return true;
      }
      if (!(obj instanceof Method)) {
        return false;
      }
      Method other = (Method) obj;
      return name.equals(other.name) && getDescriptor().equals(other.getDescriptor());
    }

    @Override
    public int hashCode() {
      return Objects.hash(name, getDescriptor());
    }
  }

  public static class Field {
    private final Set<Source> sources;
    private final Set<Flag> flags;
    private final String name;
    private final Type type;

    public Field(Source[] sources, Flag[] flags, String name, Type fieldType) {
      this.sources = new LinkedHashSet<>(Arrays.asList(sources));
      this.flags = new LinkedHashSet<>(Arrays.asList(flags));
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
    public boolean equals(Object obj) {
      if (obj == this) {
        return true;
      }
      if (!(obj instanceof Field)) {
        return false;
      }
      Field other = (Field) obj;
      return name.equals(other.name);
    }

    @Override
    public int hashCode() {
      return name.hashCode();
    }
  }

  public static class Builder {
    private final Set<Source> sources = new LinkedHashSet<>();
    private final Set<Flag> flags = new LinkedHashSet<>();
    private final String className;
    private String superName = null;
    private final Set<String> interfaces = new LinkedHashSet<>();
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
          new LinkedHashSet<>(fields),
          new LinkedHashSet<>(methods));
    }
  }
}
