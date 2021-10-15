/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling;

import static org.objectweb.asm.Opcodes.ACC_FINAL;
import static org.objectweb.asm.Opcodes.ACC_PRIVATE;
import static org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static org.objectweb.asm.Opcodes.ACC_STATIC;
import static org.objectweb.asm.Opcodes.ACC_SUPER;
import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.DUP;
import static org.objectweb.asm.Opcodes.GETSTATIC;
import static org.objectweb.asm.Opcodes.ILOAD;
import static org.objectweb.asm.Opcodes.INVOKESPECIAL;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;
import static org.objectweb.asm.Opcodes.IRETURN;
import static org.objectweb.asm.Opcodes.NEW;
import static org.objectweb.asm.Opcodes.PUTSTATIC;
import static org.objectweb.asm.Opcodes.RETURN;
import static org.objectweb.asm.Opcodes.V1_5;

import io.opentelemetry.javaagent.bootstrap.AgentClassLoader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

/**
 * Helper class for generating our own copy of sun.misc.Unsafe that delegates to
 * jdk.internal.misc.Unsafe. Used when jdk provided sun.misc.Unsafe is not available which can
 * happen when running modular application without jdk.unsupported module.
 */
class SunMiscUnsafeGenerator {
  private static final String UNSAFE_NAME = "sun/misc/Unsafe";
  private static final String UNSAFE_DESC = "L" + UNSAFE_NAME + ";";

  private final Class<?> internalUnsafeClass;
  private final List<FieldDescriptor> fields = new ArrayList<>();
  private final List<MethodDescriptor> methods = new ArrayList<>();

  public SunMiscUnsafeGenerator(Class<?> internalUnsafeClass) {
    this.internalUnsafeClass = internalUnsafeClass;

    addFields();
    addMethods();
  }

  private void addFields() {
    // fields that our unsafe class will contain
    addField("INVALID_FIELD_OFFSET", int.class);
    addField("ARRAY_BOOLEAN_BASE_OFFSET", int.class);
    addField("ARRAY_BYTE_BASE_OFFSET", int.class);
    addField("ARRAY_SHORT_BASE_OFFSET", int.class);
    addField("ARRAY_CHAR_BASE_OFFSET", int.class);
    addField("ARRAY_INT_BASE_OFFSET", int.class);
    addField("ARRAY_LONG_BASE_OFFSET", int.class);
    addField("ARRAY_FLOAT_BASE_OFFSET", int.class);
    addField("ARRAY_DOUBLE_BASE_OFFSET", int.class);
    addField("ARRAY_OBJECT_BASE_OFFSET", int.class);
    addField("ARRAY_BOOLEAN_INDEX_SCALE", int.class);
    addField("ARRAY_BYTE_INDEX_SCALE", int.class);
    addField("ARRAY_SHORT_INDEX_SCALE", int.class);
    addField("ARRAY_CHAR_INDEX_SCALE", int.class);
    addField("ARRAY_INT_INDEX_SCALE", int.class);
    addField("ARRAY_LONG_INDEX_SCALE", int.class);
    addField("ARRAY_FLOAT_INDEX_SCALE", int.class);
    addField("ARRAY_DOUBLE_INDEX_SCALE", int.class);
    addField("ARRAY_OBJECT_INDEX_SCALE", int.class);
    addField("ADDRESS_SIZE", int.class);
  }

  private boolean hasSuitableField(String name, Class<?> type) {
    try {
      Field field = internalUnsafeClass.getDeclaredField(name);
      return Modifier.isPublic(field.getModifiers()) && field.getType() == type;
    } catch (NoSuchFieldException exception) {
      return false;
    }
  }

  private void addField(String name, Class<?> type) {
    if (!hasSuitableField(name, type)) {
      throw new IllegalStateException(
          "Could not find suitable field for " + name + " " + Type.getDescriptor(type));
    }
    fields.add(new FieldDescriptor(name, type));
  }

  private void addMethods() {
    // methods that our unsafe class will contain
    addMethod(
        "compareAndSwapObject",
        boolean.class,
        Object.class,
        long.class,
        Object.class,
        Object.class);
    addMethod("compareAndSwapInt", boolean.class, Object.class, long.class, int.class, int.class);
    addMethod(
        "compareAndSwapLong", boolean.class, Object.class, long.class, long.class, long.class);
    addMethod("putOrderedObject", void.class, Object.class, long.class, Object.class);
    addMethod("putOrderedInt", void.class, Object.class, long.class, int.class);
    addMethod("putOrderedLong", void.class, Object.class, long.class, long.class);
    addMethod("allocateInstance", Object.class, Class.class);
    addMethod("loadFence", void.class);
    addMethod("storeFence", void.class);
    addMethod("fullFence", void.class);
    addMethod("getObject", Object.class, Object.class, long.class);
    addMethod("putObject", void.class, Object.class, long.class, Object.class);
    addMethod("getBoolean", boolean.class, Object.class, long.class);
    addMethod("putBoolean", void.class, Object.class, long.class, boolean.class);
    addMethod("getByte", byte.class, long.class);
    addMethod("getByte", byte.class, Object.class, long.class);
    addMethod("putByte", void.class, long.class, byte.class);
    addMethod("putByte", void.class, Object.class, long.class, byte.class);
    addMethod("getShort", short.class, long.class);
    addMethod("getShort", short.class, Object.class, long.class);
    addMethod("putShort", void.class, long.class, short.class);
    addMethod("putShort", void.class, Object.class, long.class, short.class);
    addMethod("getChar", char.class, long.class);
    addMethod("getChar", char.class, Object.class, long.class);
    addMethod("putChar", void.class, Object.class, long.class, char.class);
    addMethod("putChar", void.class, long.class, char.class);
    addMethod("getInt", int.class, Object.class, long.class);
    addMethod("getInt", int.class, long.class);
    addMethod("putInt", void.class, long.class, int.class);
    addMethod("putInt", void.class, Object.class, long.class, int.class);
    addMethod("getLong", long.class, long.class);
    addMethod("getLong", long.class, Object.class, long.class);
    addMethod("putLong", void.class, long.class, long.class);
    addMethod("putLong", void.class, Object.class, long.class, long.class);
    addMethod("getFloat", float.class, long.class);
    addMethod("getFloat", float.class, Object.class, long.class);
    addMethod("putFloat", void.class, Object.class, long.class, float.class);
    addMethod("putFloat", void.class, long.class, float.class);
    addMethod("getDouble", double.class, Object.class, long.class);
    addMethod("getDouble", double.class, long.class);
    addMethod("putDouble", void.class, Object.class, long.class, double.class);
    addMethod("putDouble", void.class, long.class, double.class);
    addMethod("getObjectVolatile", Object.class, Object.class, long.class);
    addMethod("putObjectVolatile", void.class, Object.class, long.class, Object.class);
    addMethod("getBooleanVolatile", boolean.class, Object.class, long.class);
    addMethod("putBooleanVolatile", void.class, Object.class, long.class, boolean.class);
    addMethod("getByteVolatile", byte.class, Object.class, long.class);
    addMethod("putByteVolatile", void.class, Object.class, long.class, byte.class);
    addMethod("getShortVolatile", short.class, Object.class, long.class);
    addMethod("putShortVolatile", void.class, Object.class, long.class, short.class);
    addMethod("getCharVolatile", char.class, Object.class, long.class);
    addMethod("putCharVolatile", void.class, Object.class, long.class, char.class);
    addMethod("getIntVolatile", int.class, Object.class, long.class);
    addMethod("putIntVolatile", void.class, Object.class, long.class, int.class);
    addMethod("getLongVolatile", long.class, Object.class, long.class);
    addMethod("putLongVolatile", void.class, Object.class, long.class, long.class);
    addMethod("getFloatVolatile", float.class, Object.class, long.class);
    addMethod("putFloatVolatile", void.class, Object.class, long.class, float.class);
    addMethod("getDoubleVolatile", double.class, Object.class, long.class);
    addMethod("putDoubleVolatile", void.class, Object.class, long.class, double.class);
    addMethod("getAndAddInt", int.class, Object.class, long.class, int.class);
    addMethod("getAndAddLong", long.class, Object.class, long.class, long.class);
    addMethod("getAndSetInt", int.class, Object.class, long.class, int.class);
    addMethod("getAndSetLong", long.class, Object.class, long.class, long.class);
    addMethod("getAndSetObject", Object.class, Object.class, long.class, Object.class);
    addMethod("park", void.class, boolean.class, long.class);
    addMethod("unpark", void.class, Object.class);
    addMethod("throwException", void.class, Throwable.class);
    addMethod("objectFieldOffset", long.class, Field.class);
    addMethod("staticFieldBase", Object.class, Field.class);
    addMethod("staticFieldOffset", long.class, Field.class);
    addMethod("shouldBeInitialized", boolean.class, Class.class);
    addMethod("ensureClassInitialized", void.class, Class.class);
    addMethod("getAddress", long.class, long.class);
    addMethod("putAddress", void.class, long.class, long.class);
    addMethod("allocateMemory", long.class, long.class);
    addMethod("reallocateMemory", long.class, long.class, long.class);
    addMethod("setMemory", void.class, long.class, long.class, byte.class);
    addMethod("setMemory", void.class, Object.class, long.class, long.class, byte.class);
    addMethod("copyMemory", void.class, long.class, long.class, long.class);
    addMethod(
        "copyMemory", void.class, Object.class, long.class, Object.class, long.class, long.class);
    addMethod("freeMemory", void.class, long.class);
    addMethod("arrayBaseOffset", int.class, Class.class);
    addMethod("arrayIndexScale", int.class, Class.class);
    addMethod("addressSize", int.class);
    addMethod("pageSize", int.class);
    addMethod("defineAnonymousClass", Class.class, Class.class, byte[].class, Object[].class);
    addMethod("getLoadAverage", int.class, double[].class, int.class);
    // this method is missing from internal unsafe in some jdk11 versions
    addOptionalMethod("invokeCleaner", void.class, ByteBuffer.class);
  }

  private static List<String> getNameCandidates(String name) {
    if (name.startsWith("compareAndSwap")) {
      name = name.replace("compareAndSwap", "compareAndSet");
    } else if (name.startsWith("putOrdered")) {
      name = name.replace("putOrdered", "put") + "Release";
    }
    if (name.contains("Object")) {
      String alternativeName = name.replace("Object", "Reference");
      return Arrays.asList(name, alternativeName);
    }

    return Collections.singletonList(name);
  }

  private void addOptionalMethod(String name, Class<?> returnType, Class<?>... parameterTypes) {
    addMethod(name, true, getNameCandidates(name), returnType, parameterTypes);
  }

  private void addMethod(String name, Class<?> returnType, Class<?>... parameterTypes) {
    addMethod(name, false, getNameCandidates(name), returnType, parameterTypes);
  }

  private void addMethod(
      String name,
      boolean optional,
      List<String> targetNameCandidates,
      Class<?> returnType,
      Class<?>... parameterTypes) {
    String targetName = null;
    for (String candidate : targetNameCandidates) {
      if (hasSuitableMethod(candidate, returnType, parameterTypes)) {
        targetName = candidate;
        break;
      }
    }
    if (targetName == null) {
      if (optional) {
        return;
      }
      throw new IllegalStateException(
          "Could not find suitable method for "
              + name
              + " "
              + Type.getMethodDescriptor(Type.getType(returnType), toTypes(parameterTypes)));
    }
    methods.add(new MethodDescriptor(name, targetName, returnType, parameterTypes));
  }

  private boolean hasSuitableMethod(String name, Class<?> returnType, Class<?>... parameterTypes) {
    try {
      Method method = internalUnsafeClass.getDeclaredMethod(name, parameterTypes);
      return Modifier.isPublic(method.getModifiers()) && method.getReturnType() == returnType;
    } catch (NoSuchMethodException e) {
      return false;
    }
  }

  private static Type[] toTypes(Class<?>... classes) {
    Type[] result = new Type[classes.length];
    for (int i = 0; i < classes.length; i++) {
      result[i] = Type.getType(classes[i]);
    }
    return result;
  }

  private static class FieldDescriptor {
    final String name;
    final Class<?> type;

    FieldDescriptor(String name, Class<?> type) {
      this.name = name;
      this.type = type;
    }
  }

  private static class MethodDescriptor {
    final String name;
    final String targetName;
    final Class<?> returnType;
    final Class<?>[] parameterTypes;

    MethodDescriptor(
        String name, String targetName, Class<?> returnType, Class<?>[] parameterTypes) {
      this.name = name;
      this.targetName = targetName;
      this.returnType = returnType;
      this.parameterTypes = parameterTypes;
    }
  }

  private byte[] getBytes() {
    ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
    ClassVisitor cv = cw;
    cv.visit(V1_5, ACC_PUBLIC | ACC_FINAL | ACC_SUPER, UNSAFE_NAME, null, "java/lang/Object", null);

    {
      FieldVisitor fv =
          cv.visitField(ACC_PRIVATE | ACC_STATIC | ACC_FINAL, "theUnsafe", UNSAFE_DESC, null, null);
      fv.visitEnd();
    }
    {
      FieldVisitor fv =
          cv.visitField(
              ACC_PRIVATE | ACC_STATIC | ACC_FINAL,
              "theInternalUnsafe",
              Type.getDescriptor(internalUnsafeClass),
              null,
              null);
      fv.visitEnd();
    }

    {
      MethodVisitor mv = cv.visitMethod(ACC_PRIVATE, "<init>", "()V", null, null);
      mv.visitCode();
      mv.visitVarInsn(ALOAD, 0);
      mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
      mv.visitInsn(RETURN);
      mv.visitMaxs(0, 0);
      mv.visitEnd();
    }

    for (FieldDescriptor field : fields) {
      FieldVisitor fv =
          cv.visitField(
              ACC_PUBLIC | ACC_STATIC | ACC_FINAL,
              field.name,
              Type.getDescriptor(field.type),
              null,
              null);
      fv.visitEnd();
    }

    for (MethodDescriptor method : methods) {
      Type[] parameters = toTypes(method.parameterTypes);
      Type returnType = Type.getType(method.returnType);
      String descriptor = Type.getMethodDescriptor(returnType, parameters);
      MethodVisitor mv = cv.visitMethod(ACC_PUBLIC, method.name, descriptor, null, null);
      mv.visitCode();
      mv.visitFieldInsn(
          GETSTATIC, UNSAFE_NAME, "theInternalUnsafe", Type.getDescriptor(internalUnsafeClass));
      int slot = 1;
      for (Type parameter : parameters) {
        mv.visitVarInsn(parameter.getOpcode(ILOAD), slot);
        slot += parameter.getSize();
      }
      mv.visitMethodInsn(
          INVOKEVIRTUAL,
          Type.getInternalName(internalUnsafeClass),
          method.targetName,
          descriptor,
          false);
      mv.visitInsn(returnType.getOpcode(IRETURN));
      mv.visitMaxs(0, 0);
      mv.visitEnd();
    }

    {
      MethodVisitor mv = cv.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
      mv.visitCode();
      // private static final sun.misc.Unsafe theUnsafe = new Unsafe();
      mv.visitTypeInsn(NEW, UNSAFE_NAME);
      mv.visitInsn(DUP);
      mv.visitMethodInsn(INVOKESPECIAL, UNSAFE_NAME, "<init>", "()V", false);
      mv.visitFieldInsn(PUTSTATIC, UNSAFE_NAME, "theUnsafe", UNSAFE_DESC);

      // private static final jdk.internal.misc.Unsafe theInternalUnsafe =
      // jdk.internal.misc.Unsafe.getUnsafe();
      mv.visitMethodInsn(
          INVOKESTATIC,
          Type.getInternalName(internalUnsafeClass),
          "getUnsafe",
          "()" + Type.getDescriptor(internalUnsafeClass),
          false);
      mv.visitFieldInsn(
          PUTSTATIC, UNSAFE_NAME, "theInternalUnsafe", Type.getDescriptor(internalUnsafeClass));

      // initialize field value from corresponding field in internal unsafe class
      for (FieldDescriptor field : fields) {
        mv.visitFieldInsn(
            GETSTATIC,
            Type.getInternalName(internalUnsafeClass),
            field.name,
            Type.getDescriptor(field.type));
        mv.visitFieldInsn(PUTSTATIC, UNSAFE_NAME, field.name, Type.getDescriptor(field.type));
      }

      mv.visitInsn(RETURN);
      mv.visitMaxs(0, 0);
      mv.visitEnd();
    }

    cv.visitEnd();
    return cw.toByteArray();
  }

  public static void generateUnsafe(
      Class<?> internalUnsafeClass, AgentClassLoader agentClassLoader) {
    SunMiscUnsafeGenerator generator = new SunMiscUnsafeGenerator(internalUnsafeClass);
    agentClassLoader.defineClass("sun.misc.Unsafe", generator.getBytes());
  }
}
