/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kotlinxcoroutines.instrumentationannotations;

import io.opentelemetry.javaagent.extension.instrumentation.internal.AsmApi;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/**
 * Converts compressed frames (F_FULL, F_SAME etc.) into expanded frames (F_NEW). Using this visitor
 * should give the same result as using ClassReader.EXPAND_FRAMES.
 */
class ExpandFramesClassVisitor extends ClassVisitor {
  private String className;

  ExpandFramesClassVisitor(ClassVisitor classVisitor) {
    super(AsmApi.VERSION, classVisitor);
  }

  @Override
  public void visit(
      int version,
      int access,
      String name,
      String signature,
      String superName,
      String[] interfaces) {
    super.visit(version, access, name, signature, superName, interfaces);
    className = name;
  }

  @Override
  public MethodVisitor visitMethod(
      int access, String name, String descriptor, String signature, String[] exceptions) {
    MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
    return new ExpandFramesMethodVisitor(mv, className, name, access, descriptor);
  }

  private static class ExpandFramesMethodVisitor extends MethodVisitor {
    final List<Object> currentLocals = new ArrayList<>();
    final List<Object> currentStack = new ArrayList<>();

    ExpandFramesMethodVisitor(
        MethodVisitor mv, String className, String methodName, int access, String descriptor) {
      super(AsmApi.VERSION, mv);
      if ("<init>".equals(methodName)) {
        currentLocals.add(Opcodes.UNINITIALIZED_THIS);
      } else if (!Modifier.isStatic(access)) {
        currentLocals.add(className);
      }
      for (Type type : Type.getArgumentTypes(descriptor)) {
        switch (type.getSort()) {
          case Type.BOOLEAN:
          case Type.BYTE:
          case Type.CHAR:
          case Type.INT:
          case Type.SHORT:
            currentLocals.add(Opcodes.INTEGER);
            break;
          case Type.DOUBLE:
            currentLocals.add(Opcodes.DOUBLE);
            break;
          case Type.FLOAT:
            currentLocals.add(Opcodes.FLOAT);
            break;
          case Type.LONG:
            currentLocals.add(Opcodes.LONG);
            break;
          case Type.ARRAY:
          case Type.OBJECT:
            currentLocals.add(type.getInternalName());
            break;
          default:
            throw new IllegalStateException("Unexpected type " + type.getSort() + " " + type);
        }
      }
    }

    private static void copy(Object[] array, int count, List<Object> list) {
      list.clear();
      for (int i = 0; i < count; i++) {
        list.add(array[i]);
      }
    }

    @Override
    public void visitFrame(int type, int numLocal, Object[] local, int numStack, Object[] stack) {
      switch (type) {
        // An expanded frame.
        case Opcodes.F_NEW:
        // A compressed frame with complete frame data.
        case Opcodes.F_FULL:
          copy(local, numLocal, currentLocals);
          copy(stack, numStack, currentStack);
          break;
        // A compressed frame with exactly the same locals as the previous frame and with an empty
        // stack.
        case Opcodes.F_SAME:
          currentStack.clear();
          break;
        // A compressed frame with exactly the same locals as the previous frame and with a single
        // value on the stack.
        case Opcodes.F_SAME1:
          currentStack.clear();
          currentStack.add(stack[0]);
          break;
        // A compressed frame where locals are the same as the locals in the previous frame,
        // except that additional 1-3 locals are defined, and with an empty stack.
        case Opcodes.F_APPEND:
          currentStack.clear();
          for (int i = 0; i < numLocal; i++) {
            currentLocals.add(local[i]);
          }
          break;
        // A compressed frame where locals are the same as the locals in the previous frame,
        // except that the last 1-3 locals are absent and with an empty stack.
        case Opcodes.F_CHOP:
          currentStack.clear();
          for (Iterator<Object> iterator =
                  currentLocals.listIterator(currentLocals.size() - numLocal);
              iterator.hasNext(); ) {
            iterator.next();
            iterator.remove();
          }
          break;
        default:
          throw new IllegalStateException("Unexpected frame type " + type);
      }

      // visit expanded frame
      super.visitFrame(
          Opcodes.F_NEW,
          currentLocals.size(),
          currentLocals.toArray(),
          currentStack.size(),
          currentStack.toArray());
    }
  }
}
