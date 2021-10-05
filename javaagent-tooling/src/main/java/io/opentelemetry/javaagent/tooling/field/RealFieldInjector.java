/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.field;

import static io.opentelemetry.javaagent.tooling.field.GeneratedVirtualFieldNames.getRealFieldName;
import static io.opentelemetry.javaagent.tooling.field.GeneratedVirtualFieldNames.getRealGetterName;
import static io.opentelemetry.javaagent.tooling.field.GeneratedVirtualFieldNames.getRealSetterName;

import io.opentelemetry.javaagent.bootstrap.VirtualFieldInstalledMarker;
import io.opentelemetry.javaagent.tooling.Utils;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import net.bytebuddy.asm.AsmVisitorWrapper;
import net.bytebuddy.description.field.FieldDescription;
import net.bytebuddy.description.field.FieldList;
import net.bytebuddy.description.method.MethodList;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.Implementation;
import net.bytebuddy.jar.asm.ClassVisitor;
import net.bytebuddy.jar.asm.ClassWriter;
import net.bytebuddy.jar.asm.FieldVisitor;
import net.bytebuddy.jar.asm.MethodVisitor;
import net.bytebuddy.jar.asm.Opcodes;
import net.bytebuddy.pool.TypePool;

final class RealFieldInjector implements AsmVisitorWrapper {

  private static final String INSTALLED_FIELDS_MARKER_CLASS_NAME =
      Utils.getInternalName(VirtualFieldInstalledMarker.class);

  private final FieldAccessorInterfaces fieldAccessorInterfaces;
  private final String typeName;
  private final String fieldTypeName;

  RealFieldInjector(
      FieldAccessorInterfaces fieldAccessorInterfaces, String typeName, String fieldTypeName) {
    this.fieldAccessorInterfaces = fieldAccessorInterfaces;
    this.typeName = typeName;
    this.fieldTypeName = fieldTypeName;
  }

  @Override
  public int mergeWriter(int flags) {
    return flags | ClassWriter.COMPUTE_MAXS;
  }

  @Override
  public int mergeReader(int flags) {
    return flags;
  }

  @Override
  public ClassVisitor wrap(
      TypeDescription instrumentedType,
      ClassVisitor classVisitor,
      Implementation.Context implementationContext,
      TypePool typePool,
      FieldList<FieldDescription.InDefinedShape> fields,
      MethodList<?> methods,
      int writerFlags,
      int readerFlags) {

    return new ClassVisitor(Opcodes.ASM7, classVisitor) {
      // We are using Object class name instead of fieldTypeName here because this gets
      // injected onto Bootstrap classloader where context class may be unavailable
      private final TypeDescription fieldType = new TypeDescription.ForLoadedType(Object.class);
      private final String fieldName = getRealFieldName(typeName);
      private final String getterMethodName = getRealGetterName(typeName);
      private final String setterMethodName = getRealSetterName(typeName);
      private final TypeDescription interfaceType =
          fieldAccessorInterfaces.find(typeName, fieldTypeName);
      private boolean foundField = false;
      private boolean foundGetter = false;
      private boolean foundSetter = false;

      @Override
      public void visit(
          int version,
          int access,
          String name,
          String signature,
          String superName,
          String[] interfaces) {
        if (interfaces == null) {
          interfaces = new String[] {};
        }
        Set<String> set = new LinkedHashSet<>(Arrays.asList(interfaces));
        set.add(INSTALLED_FIELDS_MARKER_CLASS_NAME);
        set.add(interfaceType.getInternalName());
        super.visit(version, access, name, signature, superName, set.toArray(new String[] {}));
      }

      @Override
      public FieldVisitor visitField(
          int access, String name, String descriptor, String signature, Object value) {
        if (name.equals(fieldName)) {
          foundField = true;
        }
        return super.visitField(access, name, descriptor, signature, value);
      }

      @Override
      public MethodVisitor visitMethod(
          int access, String name, String descriptor, String signature, String[] exceptions) {
        if (name.equals(getterMethodName)) {
          foundGetter = true;
        }
        if (name.equals(setterMethodName)) {
          foundSetter = true;
        }
        return super.visitMethod(access, name, descriptor, signature, exceptions);
      }

      @Override
      public void visitEnd() {
        // Checking only for field existence is not enough as libraries like CGLIB only copy
        // public/protected methods and not fields (neither public nor private ones) when
        // they enhance a class.
        // For this reason we check separately for the field and for the two accessors.
        if (!foundField) {
          cv.visitField(
              // Field should be transient to avoid being serialized with the object.
              Opcodes.ACC_PRIVATE | Opcodes.ACC_TRANSIENT | Opcodes.ACC_SYNTHETIC,
              fieldName,
              fieldType.getDescriptor(),
              null,
              null);
        }
        if (!foundGetter) {
          addGetter();
        }
        if (!foundSetter) {
          addSetter();
        }
        super.visitEnd();
      }

      // just 'standard' getter implementation
      private void addGetter() {
        MethodVisitor mv = getAccessorMethodVisitor(getterMethodName);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitFieldInsn(
            Opcodes.GETFIELD,
            instrumentedType.getInternalName(),
            fieldName,
            fieldType.getDescriptor());
        mv.visitInsn(Opcodes.ARETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
      }

      // just 'standard' setter implementation
      private void addSetter() {
        MethodVisitor mv = getAccessorMethodVisitor(setterMethodName);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitFieldInsn(
            Opcodes.PUTFIELD,
            instrumentedType.getInternalName(),
            fieldName,
            fieldType.getDescriptor());
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
      }

      private MethodVisitor getAccessorMethodVisitor(String methodName) {
        return cv.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_SYNTHETIC,
            methodName,
            Utils.getMethodDefinition(interfaceType, methodName).getDescriptor(),
            null,
            null);
      }
    };
  }
}
