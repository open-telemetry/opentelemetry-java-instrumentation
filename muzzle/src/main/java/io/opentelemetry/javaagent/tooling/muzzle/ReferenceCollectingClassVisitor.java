/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.muzzle;

import com.google.common.collect.EvictingQueue;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.extension.instrumentation.internal.AsmApi;
import io.opentelemetry.javaagent.tooling.muzzle.references.ClassRef;
import io.opentelemetry.javaagent.tooling.muzzle.references.ClassRefBuilder;
import io.opentelemetry.javaagent.tooling.muzzle.references.Flag;
import io.opentelemetry.javaagent.tooling.muzzle.references.Flag.ManifestationFlag;
import io.opentelemetry.javaagent.tooling.muzzle.references.Flag.MinimumVisibilityFlag;
import io.opentelemetry.javaagent.tooling.muzzle.references.Flag.OwnershipFlag;
import io.opentelemetry.javaagent.tooling.muzzle.references.Flag.VisibilityFlag;
import io.opentelemetry.javaagent.tooling.muzzle.references.Source;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.MethodNode;

/** Visit a class and collect all references made by the visited class. */
// Additional things we could check
// - annotations on class
// - outer class
// - inner class
// - cast opcodes in method bodies
final class ReferenceCollectingClassVisitor extends ClassVisitor {

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
  private static MinimumVisibilityFlag computeMinimumMethodAccess(Type from, Type to) {
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
   * If TYPE is an array, returns the underlying type. If TYPE is not an array simply return the
   * type.
   */
  private static Type underlyingType(Type type) {
    while (type.getSort() == Type.ARRAY) {
      type = type.getElementType();
    }
    return type;
  }

  private final HelperClassPredicate helperClassPredicate;
  private final boolean isAdviceClass;

  private final Map<String, ClassRef> references = new LinkedHashMap<>();
  private final Set<String> helperClasses = new HashSet<>();
  // helper super classes which are themselves also helpers
  // this is needed for injecting the helper classes into the class loader in the correct order
  private final Set<String> helperSuperClasses = new HashSet<>();
  private final VirtualFieldMappingsBuilderImpl virtualFieldMappingsBuilder =
      new VirtualFieldMappingsBuilderImpl();
  private String refSourceClassName;
  private Type refSourceType;

  ReferenceCollectingClassVisitor(
      HelperClassPredicate helperClassPredicate, boolean isAdviceClass) {
    super(AsmApi.VERSION);
    this.helperClassPredicate = helperClassPredicate;
    this.isAdviceClass = isAdviceClass;
  }

  Map<String, ClassRef> getReferences() {
    return references;
  }

  Set<String> getHelperClasses() {
    return helperClasses;
  }

  Set<String> getHelperSuperClasses() {
    return helperSuperClasses;
  }

  VirtualFieldMappings getVirtualFieldMappings() {
    return virtualFieldMappingsBuilder.build();
  }

  private void addExtendsReference(ClassRef ref) {
    addReference(ref);
    if (helperClassPredicate.isHelperClass(ref.getClassName())) {
      helperSuperClasses.add(ref.getClassName());
    }
  }

  private void addReference(ClassRef ref) {
    if (!ref.getClassName().startsWith("java.")) {
      ClassRef reference = references.get(ref.getClassName());
      if (null == reference) {
        references.put(ref.getClassName(), ref);
      } else {
        references.put(ref.getClassName(), reference.merge(ref));
      }
    }
    if (helperClassPredicate.isHelperClass(ref.getClassName())) {
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
          ClassRef.builder(fixedSuperClassName).addSource(refSourceClassName).build());

      List<String> fixedInterfaceNames = new ArrayList<>(interfaces.length);
      for (String interfaceName : interfaces) {
        String fixedInterfaceName = Utils.getClassName(interfaceName);
        fixedInterfaceNames.add(fixedInterfaceName);

        addExtendsReference(
            ClassRef.builder(fixedInterfaceName).addSource(refSourceClassName).build());
      }

      addReference(
          ClassRef.builder(refSourceClassName)
              .addSource(refSourceClassName)
              .setSuperClassName(fixedSuperClassName)
              .addInterfaceNames(fixedInterfaceNames)
              .addFlag(computeTypeManifestationFlag(access))
              .build());
    }

    super.visit(version, access, name, signature, superName, interfaces);
  }

  @Override
  public FieldVisitor visitField(
      int access, String name, String descriptor, String signature, Object value) {
    // Additional references we could check
    // - annotations on field

    Type fieldType = Type.getType(descriptor);

    // remember that this field was declared in the currently visited helper class
    addReference(
        ClassRef.builder(refSourceClassName)
            .addSource(refSourceClassName)
            .addField(new Source[0], new Flag[0], name, fieldType, /* isFieldDeclared= */ true)
            .build());

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

      addReference(
          ClassRef.builder(refSourceClassName)
              .addSource(refSourceClassName)
              .addMethod(
                  new Source[0],
                  new Flag[] {visibilityFlag, ownershipFlag, manifestationFlag},
                  name,
                  methodType.getReturnType(),
                  methodType.getArgumentTypes())
              .build());
    }

    MethodVisitor methodVisitor =
        super.visitMethod(access, name, descriptor, signature, exceptions);
    MethodVisitor methodNode =
        new MethodNode(AsmApi.VERSION, access, name, descriptor, signature, exceptions) {
          @Override
          public void visitEnd() {
            super.visitEnd();

            boolean skip = false;
            if (invisibleAnnotations != null) {
              for (AnnotationNode annotationNode : invisibleAnnotations) {
                if (Type.getDescriptor(NoMuzzle.class).equals(annotationNode.desc)) {
                  skip = true;
                  break;
                }
              }
            }
            MethodVisitor target =
                skip ? methodVisitor : new AdviceReferenceMethodVisitor(methodVisitor);
            if (target != null) {
              accept(target);
            }
          }
        };
    // Additional references we could check
    // - Classes in signature (return type, params) and visible from this package
    return new VirtualFieldCollectingMethodVisitor(methodNode);
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
      super(AsmApi.VERSION, methodVisitor);
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
          ClassRef.builder(ownerType.getClassName())
              .addSource(refSourceClassName, currentLineNumber)
              .addFlag(computeMinimumClassAccess(refSourceType, ownerType))
              .addField(
                  new Source[] {new Source(refSourceClassName, currentLineNumber)},
                  fieldFlags.toArray(new Flag[0]),
                  name,
                  fieldType,
                  /* isFieldDeclared= */ false)
              .build());

      Type underlyingFieldType = underlyingType(Type.getType(descriptor));
      if (underlyingFieldType.getSort() == Type.OBJECT) {
        addReference(
            ClassRef.builder(underlyingFieldType.getClassName())
                .addSource(refSourceClassName, currentLineNumber)
                .addFlag(computeMinimumClassAccess(refSourceType, underlyingFieldType))
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

      Type ownerType =
          owner.startsWith("[")
              ? underlyingType(Type.getType(owner))
              : Type.getType("L" + owner + ";");

      // method invoked on a primitive array type
      if (ownerType.getSort() != Type.OBJECT) {
        super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
        return;
      }

      { // ref for method return type
        Type returnType = underlyingType(methodType.getReturnType());
        if (returnType.getSort() == Type.OBJECT) {
          addReference(
              ClassRef.builder(returnType.getClassName())
                  .addSource(refSourceClassName, currentLineNumber)
                  .addFlag(computeMinimumClassAccess(refSourceType, returnType))
                  .build());
        }
      }
      // refs for method param types
      for (Type paramType : methodType.getArgumentTypes()) {
        paramType = underlyingType(paramType);
        if (paramType.getSort() == Type.OBJECT) {
          addReference(
              ClassRef.builder(paramType.getClassName())
                  .addSource(refSourceClassName, currentLineNumber)
                  .addFlag(computeMinimumClassAccess(refSourceType, paramType))
                  .build());
        }
      }

      List<Flag> methodFlags = new ArrayList<>();
      methodFlags.add(
          opcode == Opcodes.INVOKESTATIC ? OwnershipFlag.STATIC : OwnershipFlag.NON_STATIC);
      methodFlags.add(computeMinimumMethodAccess(refSourceType, ownerType));

      addReference(
          ClassRef.builder(ownerType.getClassName())
              .addSource(refSourceClassName, currentLineNumber)
              .addFlag(isInterface ? ManifestationFlag.INTERFACE : ManifestationFlag.NON_INTERFACE)
              .addFlag(computeMinimumClassAccess(refSourceType, ownerType))
              .addMethod(
                  new Source[] {new Source(refSourceClassName, currentLineNumber)},
                  methodFlags.toArray(new Flag[0]),
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
            ClassRef.builder(typeObj.getClassName())
                .addSource(refSourceClassName, currentLineNumber)
                .addFlag(computeMinimumClassAccess(refSourceType, typeObj))
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
          ClassRef.builder(Utils.getClassName(bootstrapMethodHandle.getOwner()))
              .addSource(refSourceClassName, currentLineNumber)
              .addFlag(
                  computeMinimumClassAccess(
                      refSourceType, Type.getObjectType(bootstrapMethodHandle.getOwner())))
              .build());
      for (Object arg : bootstrapMethodArguments) {
        if (arg instanceof Handle) {
          Handle handle = (Handle) arg;
          ClassRefBuilder classRefBuilder =
              ClassRef.builder(Utils.getClassName(handle.getOwner()))
                  .addSource(refSourceClassName, currentLineNumber)
                  .addFlag(
                      computeMinimumClassAccess(
                          refSourceType, Type.getObjectType(handle.getOwner())));

          if (handle.getTag() == Opcodes.H_INVOKEVIRTUAL
              || handle.getTag() == Opcodes.H_INVOKESTATIC
              || handle.getTag() == Opcodes.H_INVOKESPECIAL
              || handle.getTag() == Opcodes.H_NEWINVOKESPECIAL
              || handle.getTag() == Opcodes.H_INVOKEINTERFACE) {
            Type methodType = Type.getMethodType(handle.getDesc());
            Type ownerType = Type.getObjectType(handle.getOwner());
            List<Flag> methodFlags = new ArrayList<>();
            methodFlags.add(
                handle.getTag() == Opcodes.H_INVOKESTATIC
                    ? OwnershipFlag.STATIC
                    : OwnershipFlag.NON_STATIC);
            methodFlags.add(computeMinimumMethodAccess(refSourceType, ownerType));

            classRefBuilder.addMethod(
                new Source[] {new Source(refSourceClassName, currentLineNumber)},
                methodFlags.toArray(new Flag[0]),
                handle.getName(),
                methodType.getReturnType(),
                methodType.getArgumentTypes());
            addReference(classRefBuilder.build());
          }
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
              ClassRef.builder(type.getClassName())
                  .addSource(refSourceClassName, currentLineNumber)
                  .addFlag(computeMinimumClassAccess(refSourceType, type))
                  .build());
        }
      }
      super.visitLdcInsn(value);
    }
  }

  private class VirtualFieldCollectingMethodVisitor extends MethodVisitor {
    // this data structure will remember last two LDC <class> instructions before
    // VirtualField.find() call
    private final EvictingQueue<Type> lastTwoClassConstants = EvictingQueue.create(2);

    VirtualFieldCollectingMethodVisitor(MethodVisitor methodVisitor) {
      super(AsmApi.VERSION, methodVisitor);
    }

    @Override
    public void visitInsn(int opcode) {
      registerOpcode(opcode, null);
      super.visitInsn(opcode);
    }

    @Override
    public void visitIntInsn(int opcode, int operand) {
      registerOpcode(opcode, null);
      super.visitIntInsn(opcode, operand);
    }

    @Override
    public void visitVarInsn(int opcode, int var) {
      registerOpcode(opcode, null);
      super.visitVarInsn(opcode, var);
    }

    @Override
    public void visitTypeInsn(int opcode, String type) {
      registerOpcode(opcode, null);
      super.visitTypeInsn(opcode, type);
    }

    @Override
    public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
      registerOpcode(opcode, null);
      super.visitFieldInsn(opcode, owner, name, descriptor);
    }

    @Override
    public void visitMethodInsn(
        int opcode, String owner, String name, String descriptor, boolean isInterface) {

      String getVirtualFieldDescriptor =
          Type.getMethodDescriptor(
              Type.getType(VirtualField.class),
              Type.getType(Class.class),
              Type.getType(Class.class));

      Type methodType = Type.getMethodType(descriptor);
      Type ownerType = Type.getType("L" + owner + ";");

      // remember used context classes if this is an VirtualField.find() call
      if ("io.opentelemetry.instrumentation.api.util.VirtualField".equals(ownerType.getClassName())
          && "find".equals(name)
          && methodType.getDescriptor().equals(getVirtualFieldDescriptor)) {
        // in case of invalid scenario (not using .class ref directly) don't store anything and
        // clear the last LDC <class> stack
        // note that FieldBackedProvider also check for an invalid context call in the runtime
        if (lastTwoClassConstants.remainingCapacity() == 0) {
          Type type = lastTwoClassConstants.poll();
          Type fieldType = lastTwoClassConstants.poll();

          if (type.getSort() != Type.OBJECT) {
            throw new MuzzleCompilationException(
                "Invalid VirtualField#find(Class, Class) usage: you cannot pass array or primitive types as the field owner type");
          }
          if (fieldType.getSort() != Type.OBJECT && fieldType.getSort() != Type.ARRAY) {
            throw new MuzzleCompilationException(
                "Invalid VirtualField#find(Class, Class) usage: you cannot pass primitive types as the field type");
          }

          virtualFieldMappingsBuilder.register(type.getClassName(), fieldType.getClassName());
        } else {
          throw new MuzzleCompilationException(
              "Invalid VirtualField#find(Class, Class) usage: you cannot pass variables,"
                  + " method parameters, compute classes; class references need to be passed"
                  + " directly to the find() method");
        }
      }

      registerOpcode(opcode, null);
      super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
    }

    @Override
    public void visitJumpInsn(int opcode, Label label) {
      registerOpcode(opcode, null);
      super.visitJumpInsn(opcode, label);
    }

    @Override
    public void visitLdcInsn(Object value) {
      registerOpcode(Opcodes.LDC, value);
      super.visitLdcInsn(value);
    }

    private void registerOpcode(int opcode, Object value) {
      // check if this is an LDC <class> instruction; if so, remember the class that was used
      // we need to remember last two LDC <class> instructions that were executed before
      // VirtualField.get() call
      if (opcode == Opcodes.LDC) {
        if (value instanceof Type) {
          Type type = (Type) value;
          lastTwoClassConstants.add(type);
          return;
        }
      }

      // instruction other than LDC <class> visited; pop the first element if present - this will
      // prevent adding wrong context key pairs in case of an invalid scenario
      lastTwoClassConstants.poll();
    }
  }
}
