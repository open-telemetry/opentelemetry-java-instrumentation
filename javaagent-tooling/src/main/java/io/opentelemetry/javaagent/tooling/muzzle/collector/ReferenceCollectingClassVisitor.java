/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.muzzle.collector;

import io.opentelemetry.javaagent.tooling.Utils;
import io.opentelemetry.javaagent.tooling.muzzle.InstrumentationClassPredicate;
import io.opentelemetry.javaagent.tooling.muzzle.Reference;
import io.opentelemetry.javaagent.tooling.muzzle.Reference.Flag;
import io.opentelemetry.javaagent.tooling.muzzle.Reference.Flag.ManifestationFlag;
import io.opentelemetry.javaagent.tooling.muzzle.Reference.Flag.MinimumVisibilityFlag;
import io.opentelemetry.javaagent.tooling.muzzle.Reference.Flag.OwnershipFlag;
import io.opentelemetry.javaagent.tooling.muzzle.Reference.Flag.VisibilityFlag;
import io.opentelemetry.javaagent.tooling.muzzle.Reference.Source;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.jar.asm.ClassVisitor;
import net.bytebuddy.jar.asm.FieldVisitor;
import net.bytebuddy.jar.asm.Handle;
import net.bytebuddy.jar.asm.Label;
import net.bytebuddy.jar.asm.MethodVisitor;
import net.bytebuddy.jar.asm.Opcodes;
import net.bytebuddy.jar.asm.Type;

/** Visit a class and collect all references made by the visited class. */
// Additional things we could check
// - annotations on class
// - outer class
// - inner class
// - cast opcodes in method bodies
class ReferenceCollectingClassVisitor extends ClassVisitor {

  /**
   * Get the package of an internal class name.
   *
   * <p>foo/bar/Baz -> foo/bar/
   */
  private static String internalPackageName(String internalName) {
    return internalName.replaceAll("/[^/]+$", "");
  }

  /**
   * Compute the minimum required access for FROM class to access the TO class.
   *
   * @return A reference flag with the required level of access.
   */
  private static MinimumVisibilityFlag computeMinimumClassAccess(Type from, Type to) {
    if (from.getInternalName().equalsIgnoreCase(to.getInternalName())) {
      return MinimumVisibilityFlag.PRIVATE_OR_HIGHER;
    } else if (internalPackageName(from.getInternalName())
        .equals(internalPackageName(to.getInternalName()))) {
      return MinimumVisibilityFlag.PACKAGE_OR_HIGHER;
    } else {
      return MinimumVisibilityFlag.PUBLIC;
    }
  }

  /**
   * Compute the minimum required access for FROM class to access a field on the TO class.
   *
   * @return A reference flag with the required level of access.
   */
  private static MinimumVisibilityFlag computeMinimumFieldAccess(Type from, Type to) {
    if (from.getInternalName().equalsIgnoreCase(to.getInternalName())) {
      return MinimumVisibilityFlag.PRIVATE_OR_HIGHER;
    } else if (internalPackageName(from.getInternalName())
        .equals(internalPackageName(to.getInternalName()))) {
      return MinimumVisibilityFlag.PACKAGE_OR_HIGHER;
    } else {
      // Additional references: check the type hierarchy of FROM to distinguish public from
      // protected
      return MinimumVisibilityFlag.PROTECTED_OR_HIGHER;
    }
  }

  /**
   * Compute the minimum required access for FROM class to access METHODTYPE on the TO class.
   *
   * @return A reference flag with the required level of access.
   */
  private static MinimumVisibilityFlag computeMinimumMethodAccess(
      Type from, Type to, Type methodType) {
    if (from.getInternalName().equalsIgnoreCase(to.getInternalName())) {
      return MinimumVisibilityFlag.PRIVATE_OR_HIGHER;
    } else {
      // Additional references: check the type hierarchy of FROM to distinguish public from
      // protected
      return MinimumVisibilityFlag.PROTECTED_OR_HIGHER;
    }
  }

  /**
   * If TYPE is an array, returns the underlying type. If TYPE is not an array simply return the
   * type.
   */
  private static Type underlyingType(Type type) {
    while (type.getSort() == Type.ARRAY) {
      type = type.getElementType();
    }
    return type;
  }

  private final InstrumentationClassPredicate instrumentationClassPredicate;
  private final boolean isAdviceClass;

  private final Map<String, Reference> references = new LinkedHashMap<>();
  private final Set<String> helperClasses = new HashSet<>();
  // helper super classes which are themselves also helpers
  // this is needed for injecting the helper classes into the class loader in the correct order
  private final Set<String> helperSuperClasses = new HashSet<>();
  private String refSourceClassName;
  private Type refSourceType;

  ReferenceCollectingClassVisitor(
      InstrumentationClassPredicate instrumentationClassPredicate, boolean isAdviceClass) {
    super(Opcodes.ASM7);
    this.instrumentationClassPredicate = instrumentationClassPredicate;
    this.isAdviceClass = isAdviceClass;
  }

  Map<String, Reference> getReferences() {
    return references;
  }

  Set<String> getHelperClasses() {
    return helperClasses;
  }

  Set<String> getHelperSuperClasses() {
    return helperSuperClasses;
  }

  private void addExtendsReference(Reference ref) {
    addReference(ref);
    if (instrumentationClassPredicate.isInstrumentationClass(ref.getClassName())) {
      helperSuperClasses.add(ref.getClassName());
    }
  }

  private void addReference(Reference ref) {
    if (!ref.getClassName().startsWith("java.")) {
      Reference reference = references.get(ref.getClassName());
      if (null == reference) {
        references.put(ref.getClassName(), ref);
      } else {
        references.put(ref.getClassName(), reference.merge(ref));
      }
    }
    if (instrumentationClassPredicate.isInstrumentationClass(ref.getClassName())) {
      helperClasses.add(ref.getClassName());
    }
  }

  @Override
  public void visit(
      int version,
      int access,
      String name,
      String signature,
      String superName,
      String[] interfaces) {
    refSourceClassName = Utils.getClassName(name);
    refSourceType = Type.getType("L" + name + ";");

    // class references are not generated for advice classes, only for helper classes
    if (!isAdviceClass) {
      String fixedSuperClassName = Utils.getClassName(superName);

      addExtendsReference(
          new Reference.Builder(fixedSuperClassName).withSource(refSourceClassName).build());

      List<String> fixedInterfaceNames = new ArrayList<>(interfaces.length);
      for (String interfaceName : interfaces) {
        String fixedInterfaceName = Utils.getClassName(interfaceName);
        fixedInterfaceNames.add(fixedInterfaceName);

        addExtendsReference(
            new Reference.Builder(fixedInterfaceName).withSource(refSourceClassName).build());
      }

      addReference(
          new Reference.Builder(refSourceClassName)
              .withSource(refSourceClassName)
              .withSuperName(fixedSuperClassName)
              .withInterfaces(fixedInterfaceNames)
              .withFlag(computeTypeManifestationFlag(access))
              .build());
    }

    super.visit(version, access, name, signature, superName, interfaces);
  }

  @Override
  public FieldVisitor visitField(
      int access, String name, String descriptor, String signature, Object value) {
    // Additional references we could check
    // - annotations on field

    // intentionally not creating refs to fields here.
    // Will create refs in method instructions to include line numbers.
    return super.visitField(access, name, descriptor, signature, value);
  }

  @Override
  public MethodVisitor visitMethod(
      int access, String name, String descriptor, String signature, String[] exceptions) {

    // declared method references are not generated for advice classes, only for helper classes
    if (!isAdviceClass) {
      Type methodType = Type.getMethodType(descriptor);

      Flag visibilityFlag = computeVisibilityFlag(access);
      Flag ownershipFlag = computeOwnershipFlag(access);
      Flag manifestationFlag = computeTypeManifestationFlag(access);

      // as an optimization skip constructors, private and static methods
      if (!(visibilityFlag == VisibilityFlag.PRIVATE
          || ownershipFlag == OwnershipFlag.STATIC
          || MethodDescription.CONSTRUCTOR_INTERNAL_NAME.equals(name))) {
        addReference(
            new Reference.Builder(refSourceClassName)
                .withSource(refSourceClassName)
                .withMethod(
                    new Source[0],
                    new Flag[] {visibilityFlag, ownershipFlag, manifestationFlag},
                    name,
                    methodType.getReturnType(),
                    methodType.getArgumentTypes())
                .build());
      }
    }

    // Additional references we could check
    // - Classes in signature (return type, params) and visible from this package
    return new AdviceReferenceMethodVisitor(
        super.visitMethod(access, name, descriptor, signature, exceptions));
  }

  private static VisibilityFlag computeVisibilityFlag(int access) {
    if (VisibilityFlag.PUBLIC.matches(access)) {
      return VisibilityFlag.PUBLIC;
    } else if (VisibilityFlag.PROTECTED.matches(access)) {
      return VisibilityFlag.PROTECTED;
    } else if (VisibilityFlag.PACKAGE.matches(access)) {
      return VisibilityFlag.PACKAGE;
    } else {
      return VisibilityFlag.PRIVATE;
    }
  }

  private static OwnershipFlag computeOwnershipFlag(int access) {
    if (OwnershipFlag.STATIC.matches(access)) {
      return OwnershipFlag.STATIC;
    } else {
      return OwnershipFlag.NON_STATIC;
    }
  }

  private static ManifestationFlag computeTypeManifestationFlag(int access) {
    if (ManifestationFlag.ABSTRACT.matches(access)) {
      return ManifestationFlag.ABSTRACT;
    } else if (ManifestationFlag.FINAL.matches(access)) {
      return ManifestationFlag.FINAL;
    } else {
      return ManifestationFlag.NON_FINAL;
    }
  }

  private class AdviceReferenceMethodVisitor extends MethodVisitor {
    private int currentLineNumber = -1;

    public AdviceReferenceMethodVisitor(MethodVisitor methodVisitor) {
      super(Opcodes.ASM7, methodVisitor);
    }

    @Override
    public void visitLineNumber(int line, Label start) {
      currentLineNumber = line;
      super.visitLineNumber(line, start);
    }

    @Override
    public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
      // Additional references we could check
      // * DONE owner class
      //   * DONE owner class has a field (name)
      //   * DONE field is static or non-static
      //   * DONE field's visibility from this point (NON_PRIVATE?)
      // * DONE owner class's visibility from this point (NON_PRIVATE?)
      //
      // * DONE field-source class (descriptor)
      //   * DONE field-source visibility from this point (PRIVATE?)

      Type ownerType =
          owner.startsWith("[")
              ? underlyingType(Type.getType(owner))
              : Type.getType("L" + owner + ";");
      Type fieldType = Type.getType(descriptor);

      List<Flag> fieldFlags = new ArrayList<>();
      fieldFlags.add(computeMinimumFieldAccess(refSourceType, ownerType));
      fieldFlags.add(
          opcode == Opcodes.GETSTATIC || opcode == Opcodes.PUTSTATIC
              ? OwnershipFlag.STATIC
              : OwnershipFlag.NON_STATIC);

      addReference(
          new Reference.Builder(ownerType.getInternalName())
              .withSource(refSourceClassName, currentLineNumber)
              .withFlag(computeMinimumClassAccess(refSourceType, ownerType))
              .withField(
                  new Reference.Source[] {
                    new Reference.Source(refSourceClassName, currentLineNumber)
                  },
                  fieldFlags.toArray(new Reference.Flag[0]),
                  name,
                  fieldType)
              .build());

      Type underlyingFieldType = underlyingType(fieldType);
      if (underlyingFieldType.getSort() == Type.OBJECT) {
        addReference(
            new Reference.Builder(underlyingFieldType.getInternalName())
                .withSource(refSourceClassName, currentLineNumber)
                .withFlag(computeMinimumClassAccess(refSourceType, underlyingFieldType))
                .build());
      }
      super.visitFieldInsn(opcode, owner, name, descriptor);
    }

    @Override
    public void visitMethodInsn(
        int opcode, String owner, String name, String descriptor, boolean isInterface) {
      // Additional references we could check
      // * DONE name of method owner's class
      //   * DONE is the owner an interface?
      //   * DONE owner's access from here (PRIVATE?)
      //   * DONE method on the owner class
      //   * DONE is the method static? Is it visible from here?
      // * Class names from the method descriptor
      //   * params classes
      //   * return type
      Type methodType = Type.getMethodType(descriptor);

      { // ref for method return type
        Type returnType = underlyingType(methodType.getReturnType());
        if (returnType.getSort() == Type.OBJECT) {
          addReference(
              new Reference.Builder(returnType.getInternalName())
                  .withSource(refSourceClassName, currentLineNumber)
                  .withFlag(computeMinimumClassAccess(refSourceType, returnType))
                  .build());
        }
      }
      // refs for method param types
      for (Type paramType : methodType.getArgumentTypes()) {
        paramType = underlyingType(paramType);
        if (paramType.getSort() == Type.OBJECT) {
          addReference(
              new Reference.Builder(paramType.getInternalName())
                  .withSource(refSourceClassName, currentLineNumber)
                  .withFlag(computeMinimumClassAccess(refSourceType, paramType))
                  .build());
        }
      }

      Type ownerType =
          owner.startsWith("[")
              ? underlyingType(Type.getType(owner))
              : Type.getType("L" + owner + ";");

      List<Reference.Flag> methodFlags = new ArrayList<>();
      methodFlags.add(
          opcode == Opcodes.INVOKESTATIC ? OwnershipFlag.STATIC : OwnershipFlag.NON_STATIC);
      methodFlags.add(computeMinimumMethodAccess(refSourceType, ownerType, methodType));

      addReference(
          new Reference.Builder(ownerType.getInternalName())
              .withSource(refSourceClassName, currentLineNumber)
              .withFlag(isInterface ? ManifestationFlag.INTERFACE : ManifestationFlag.NON_INTERFACE)
              .withFlag(computeMinimumClassAccess(refSourceType, ownerType))
              .withMethod(
                  new Reference.Source[] {
                    new Reference.Source(refSourceClassName, currentLineNumber)
                  },
                  methodFlags.toArray(new Reference.Flag[0]),
                  name,
                  methodType.getReturnType(),
                  methodType.getArgumentTypes())
              .build());
      super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
    }

    @Override
    public void visitTypeInsn(int opcode, String type) {
      Type typeObj = underlyingType(Type.getObjectType(type));
      if (typeObj.getSort() == Type.OBJECT) {
        addReference(
            new Reference.Builder(typeObj.getInternalName())
                .withSource(refSourceClassName, currentLineNumber)
                .withFlag(computeMinimumClassAccess(refSourceType, typeObj))
                .build());
      }
      super.visitTypeInsn(opcode, type);
    }

    @Override
    public void visitInvokeDynamicInsn(
        String name,
        String descriptor,
        Handle bootstrapMethodHandle,
        Object... bootstrapMethodArguments) {
      // This part might be unnecessary...
      addReference(
          new Reference.Builder(bootstrapMethodHandle.getOwner())
              .withSource(refSourceClassName, currentLineNumber)
              .withFlag(
                  computeMinimumClassAccess(
                      refSourceType, Type.getObjectType(bootstrapMethodHandle.getOwner())))
              .build());
      for (Object arg : bootstrapMethodArguments) {
        if (arg instanceof Handle) {
          Handle handle = (Handle) arg;
          addReference(
              new Reference.Builder(handle.getOwner())
                  .withSource(refSourceClassName, currentLineNumber)
                  .withFlag(
                      computeMinimumClassAccess(
                          refSourceType, Type.getObjectType(handle.getOwner())))
                  .build());
        }
      }
      super.visitInvokeDynamicInsn(
          name, descriptor, bootstrapMethodHandle, bootstrapMethodArguments);
    }

    @Override
    public void visitLdcInsn(Object value) {
      if (value instanceof Type) {
        Type type = underlyingType((Type) value);
        if (type.getSort() == Type.OBJECT) {
          addReference(
              new Reference.Builder(type.getInternalName())
                  .withSource(refSourceClassName, currentLineNumber)
                  .withFlag(computeMinimumClassAccess(refSourceType, type))
                  .build());
        }
      }
      super.visitLdcInsn(value);
    }
  }
}
