/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.field;

import io.opentelemetry.instrumentation.api.field.VirtualField;
import io.opentelemetry.javaagent.tooling.TransformSafeLogger;
import io.opentelemetry.javaagent.tooling.Utils;
import io.opentelemetry.javaagent.tooling.muzzle.VirtualFieldMappings;
import java.lang.reflect.Method;
import net.bytebuddy.asm.AsmVisitorWrapper;
import net.bytebuddy.description.field.FieldDescription;
import net.bytebuddy.description.field.FieldList;
import net.bytebuddy.description.method.MethodList;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.Implementation;
import net.bytebuddy.jar.asm.ClassVisitor;
import net.bytebuddy.jar.asm.ClassWriter;
import net.bytebuddy.jar.asm.Label;
import net.bytebuddy.jar.asm.MethodVisitor;
import net.bytebuddy.jar.asm.Opcodes;
import net.bytebuddy.jar.asm.Type;
import net.bytebuddy.pool.TypePool;

final class VirtualFieldFindRewriter implements AsmVisitorWrapper {

  private static final TransformSafeLogger logger =
      TransformSafeLogger.getLogger(VirtualFieldFindRewriter.class);

  private static final Method FIND_VIRTUAL_FIELD_METHOD;
  private static final Method FIND_VIRTUAL_FIELD_IMPL_METHOD;

  static {
    try {
      FIND_VIRTUAL_FIELD_METHOD = VirtualField.class.getMethod("find", Class.class, Class.class);
      FIND_VIRTUAL_FIELD_IMPL_METHOD =
          VirtualFieldImplementationsGenerator.VirtualFieldImplementationTemplate.class.getMethod(
              "getVirtualField", Class.class, Class.class);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private final Class<?> instrumentationModuleClass;
  private final VirtualFieldMappings virtualFieldMappings;
  private final VirtualFieldImplementations virtualFieldImplementations;

  public VirtualFieldFindRewriter(
      Class<?> instrumentationModuleClass,
      VirtualFieldMappings virtualFieldMappings,
      VirtualFieldImplementations virtualFieldImplementations) {
    this.instrumentationModuleClass = instrumentationModuleClass;
    this.virtualFieldMappings = virtualFieldMappings;
    this.virtualFieldImplementations = virtualFieldImplementations;
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
      @Override
      public MethodVisitor visitMethod(
          int access, String name, String descriptor, String signature, String[] exceptions) {
        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
        return new MethodVisitor(Opcodes.ASM7, mv) {
          /** The most recent objects pushed to the stack. */
          private final Object[] stack = {null, null};
          /** Most recent instructions. */
          private final int[] insnStack = {-1, -1, -1};

          @Override
          public void visitMethodInsn(
              int opcode, String owner, String name, String descriptor, boolean isInterface) {
            pushOpcode(opcode);
            if (Utils.getInternalName(FIND_VIRTUAL_FIELD_METHOD.getDeclaringClass()).equals(owner)
                && FIND_VIRTUAL_FIELD_METHOD.getName().equals(name)
                && Type.getMethodDescriptor(FIND_VIRTUAL_FIELD_METHOD).equals(descriptor)) {
              logger.trace(
                  "Found VirtualField#find() access in {}", instrumentationModuleClass.getName());
              /*
              The idea here is that the rest if this method visitor collects last three instructions in `insnStack`
              variable. Once we get here we check if those last three instructions constitute call that looks like
              `VirtualField.find(K.class, V.class)`. If it does the inside of this if rewrites it to call
              dynamically injected context store implementation instead.
               */
              if ((insnStack[0] == Opcodes.INVOKESTATIC
                      && insnStack[1] == Opcodes.LDC
                      && insnStack[2] == Opcodes.LDC)
                  && (stack[0] instanceof Type && stack[1] instanceof Type)) {
                String fieldTypeName = ((Type) stack[0]).getClassName();
                String typeName = ((Type) stack[1]).getClassName();
                TypeDescription virtualFieldImplementationClass =
                    virtualFieldImplementations.find(typeName, fieldTypeName);
                if (logger.isTraceEnabled()) {
                  logger.trace(
                      "Rewriting VirtualField#find() for instrumenter {}: {} -> {}",
                      instrumentationModuleClass.getName(),
                      typeName,
                      fieldTypeName);
                }
                if (virtualFieldImplementationClass == null) {
                  throw new IllegalStateException(
                      String.format(
                          "Incorrect VirtualField usage detected. Cannot find implementation for VirtualField<%s, %s>. Was that field registered in %s#registerMuzzleVirtualFields()?",
                          typeName, fieldTypeName, instrumentationModuleClass.getName()));
                }
                if (!virtualFieldMappings.hasMapping(typeName, fieldTypeName)) {
                  throw new IllegalStateException(
                      String.format(
                          "Incorrect VirtualField usage detected. Cannot find mapping for VirtualField<%s, %s>. Was that field registered in %s#registerMuzzleVirtualFields()?",
                          typeName, fieldTypeName, instrumentationModuleClass.getName()));
                }
                // stack: fieldType | type
                mv.visitMethodInsn(
                    Opcodes.INVOKESTATIC,
                    virtualFieldImplementationClass.getInternalName(),
                    FIND_VIRTUAL_FIELD_IMPL_METHOD.getName(),
                    Type.getMethodDescriptor(FIND_VIRTUAL_FIELD_IMPL_METHOD),
                    /* isInterface= */ false);
                return;
              }
              throw new IllegalStateException(
                  "Incorrect VirtualField usage detected. Type and field type must be class-literals. Example of correct usage: VirtualField.find(Runnable.class, RunnableContext.class)");
            } else {
              super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
            }
          }

          /** Tracking the most recently used opcodes to assert proper api usage. */
          private void pushOpcode(int opcode) {
            System.arraycopy(insnStack, 0, insnStack, 1, insnStack.length - 1);
            insnStack[0] = opcode;
          }

          /** Tracking the most recently pushed objects on the stack to assert proper api usage. */
          private void pushStack(Object o) {
            System.arraycopy(stack, 0, stack, 1, stack.length - 1);
            stack[0] = o;
          }

          @Override
          public void visitInsn(int opcode) {
            pushOpcode(opcode);
            super.visitInsn(opcode);
          }

          @Override
          public void visitJumpInsn(int opcode, Label label) {
            pushOpcode(opcode);
            super.visitJumpInsn(opcode, label);
          }

          @Override
          public void visitIntInsn(int opcode, int operand) {
            pushOpcode(opcode);
            super.visitIntInsn(opcode, operand);
          }

          @Override
          public void visitVarInsn(int opcode, int var) {
            pushOpcode(opcode);
            pushStack(var);
            super.visitVarInsn(opcode, var);
          }

          @Override
          public void visitLdcInsn(Object value) {
            pushOpcode(Opcodes.LDC);
            pushStack(value);
            super.visitLdcInsn(value);
          }
        };
      }
    };
  }
}
