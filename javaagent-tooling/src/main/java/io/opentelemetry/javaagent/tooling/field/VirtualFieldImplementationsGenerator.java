/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.field;

import static io.opentelemetry.javaagent.tooling.field.GeneratedVirtualFieldNames.getRealGetterName;
import static io.opentelemetry.javaagent.tooling.field.GeneratedVirtualFieldNames.getRealSetterName;
import static io.opentelemetry.javaagent.tooling.field.GeneratedVirtualFieldNames.getVirtualFieldImplementationClassName;

import io.opentelemetry.instrumentation.api.caching.Cache;
import io.opentelemetry.instrumentation.api.field.VirtualField;
import io.opentelemetry.javaagent.tooling.Utils;
import io.opentelemetry.javaagent.tooling.muzzle.VirtualFieldMappings;
import java.util.HashMap;
import java.util.Map;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.ClassFileVersion;
import net.bytebuddy.asm.AsmVisitorWrapper;
import net.bytebuddy.description.field.FieldDescription;
import net.bytebuddy.description.field.FieldList;
import net.bytebuddy.description.method.MethodList;
import net.bytebuddy.description.modifier.SyntheticState;
import net.bytebuddy.description.modifier.TypeManifestation;
import net.bytebuddy.description.modifier.Visibility;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.implementation.Implementation;
import net.bytebuddy.jar.asm.ClassVisitor;
import net.bytebuddy.jar.asm.ClassWriter;
import net.bytebuddy.jar.asm.Label;
import net.bytebuddy.jar.asm.MethodVisitor;
import net.bytebuddy.jar.asm.Opcodes;
import net.bytebuddy.pool.TypePool;

final class VirtualFieldImplementationsGenerator {

  private final ByteBuddy byteBuddy;

  VirtualFieldImplementationsGenerator(ByteBuddy byteBuddy) {
    this.byteBuddy = byteBuddy;
  }

  VirtualFieldImplementations generateClasses(
      VirtualFieldMappings virtualFieldMappings, FieldAccessorInterfaces fieldAccessorInterfaces) {
    Map<String, DynamicType.Unloaded<?>> virtualFieldImplementations =
        new HashMap<>(virtualFieldMappings.size());
    for (Map.Entry<String, String> entry : virtualFieldMappings.entrySet()) {
      DynamicType.Unloaded<?> type =
          makeVirtualFieldImplementationClass(
              entry.getKey(), entry.getValue(), fieldAccessorInterfaces);
      virtualFieldImplementations.put(type.getTypeDescription().getName(), type);
    }
    return new VirtualFieldImplementations(virtualFieldImplementations);
  }

  /**
   * Generate an 'implementation' of a context store class for given key class name and context
   * class name.
   *
   * @param typeName key class name
   * @param fieldTypeName context class name
   * @return unloaded dynamic type containing generated class
   */
  private DynamicType.Unloaded<?> makeVirtualFieldImplementationClass(
      String typeName, String fieldTypeName, FieldAccessorInterfaces fieldAccessorInterfaces) {
    return byteBuddy
        .rebase(VirtualFieldImplementationTemplate.class)
        .modifiers(Visibility.PUBLIC, TypeManifestation.FINAL, SyntheticState.SYNTHETIC)
        .name(getVirtualFieldImplementationClassName(typeName, fieldTypeName))
        .visit(
            getVirtualFieldImplementationVisitor(typeName, fieldTypeName, fieldAccessorInterfaces))
        .make();
  }

  /**
   * Returns a visitor that 'fills in' missing methods into concrete implementation of {@link
   * VirtualFieldImplementationsGenerator.VirtualFieldImplementationTemplate} for given key class
   * name and context class name.
   *
   * @param typeName key class name
   * @param fieldTypeName context class name
   * @return visitor that adds implementation for methods that need to be generated
   */
  private AsmVisitorWrapper getVirtualFieldImplementationVisitor(
      String typeName, String fieldTypeName, FieldAccessorInterfaces fieldAccessorInterfaces) {
    return new AsmVisitorWrapper() {

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

          private final TypeDescription accessorInterface =
              fieldAccessorInterfaces.find(typeName, fieldTypeName);
          private final String accessorInterfaceInternalName = accessorInterface.getInternalName();
          private final String instrumentedTypeInternalName = instrumentedType.getInternalName();
          private final boolean frames =
              implementationContext.getClassFileVersion().isAtLeast(ClassFileVersion.JAVA_V6);

          @Override
          public MethodVisitor visitMethod(
              int access, String name, String descriptor, String signature, String[] exceptions) {
            if ("realGet".equals(name)) {
              generateRealGetMethod(name);
              return null;
            } else if ("realPut".equals(name)) {
              generateRealPutMethod(name);
              return null;
            } else {
              return super.visitMethod(access, name, descriptor, signature, exceptions);
            }
          }

          /**
           * Provides implementation for {@code realGet} method that looks like below.
           *
           * <blockquote>
           *
           * <pre>
           * private Object realGet(final Object key) {
           *   if (key instanceof $accessorInterfaceInternalName) {
           *     return (($accessorInterfaceInternalName) key).$getterName();
           *   } else {
           *     return mapGet(key);
           *   }
           * }
           * </pre>
           *
           * </blockquote>
           *
           * @param name name of the method being visited
           */
          private void generateRealGetMethod(String name) {
            String getterName = getRealGetterName(typeName, fieldTypeName);
            Label elseLabel = new Label();
            MethodVisitor mv = getMethodVisitor(name);
            mv.visitCode();
            mv.visitVarInsn(Opcodes.ALOAD, 1);
            mv.visitTypeInsn(Opcodes.INSTANCEOF, accessorInterfaceInternalName);
            mv.visitJumpInsn(Opcodes.IFEQ, elseLabel);
            mv.visitVarInsn(Opcodes.ALOAD, 1);
            mv.visitTypeInsn(Opcodes.CHECKCAST, accessorInterfaceInternalName);
            mv.visitMethodInsn(
                Opcodes.INVOKEINTERFACE,
                accessorInterfaceInternalName,
                getterName,
                Utils.getMethodDefinition(accessorInterface, getterName).getDescriptor(),
                /* isInterface= */ true);
            mv.visitInsn(Opcodes.ARETURN);
            mv.visitLabel(elseLabel);
            if (frames) {
              mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
            }
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitVarInsn(Opcodes.ALOAD, 1);
            mv.visitMethodInsn(
                Opcodes.INVOKESPECIAL,
                instrumentedTypeInternalName,
                "mapGet",
                Utils.getMethodDefinition(instrumentedType, "mapGet").getDescriptor(),
                /* isInterface= */ false);
            mv.visitInsn(Opcodes.ARETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
          }

          /**
           * Provides implementation for {@code realPut} method that looks like below.
           *
           * <blockquote>
           *
           * <pre>
           * private void realPut(final Object key, final Object value) {
           *   if (key instanceof $accessorInterfaceInternalName) {
           *     (($accessorInterfaceInternalName) key).$setterName(value);
           *   } else {
           *     mapPut(key, value);
           *   }
           * }
           * </pre>
           *
           * </blockquote>
           *
           * @param name name of the method being visited
           */
          private void generateRealPutMethod(String name) {
            String setterName = getRealSetterName(typeName, fieldTypeName);
            Label elseLabel = new Label();
            Label endLabel = new Label();
            MethodVisitor mv = getMethodVisitor(name);
            mv.visitCode();
            mv.visitVarInsn(Opcodes.ALOAD, 1);
            mv.visitTypeInsn(Opcodes.INSTANCEOF, accessorInterfaceInternalName);
            mv.visitJumpInsn(Opcodes.IFEQ, elseLabel);
            mv.visitVarInsn(Opcodes.ALOAD, 1);
            mv.visitTypeInsn(Opcodes.CHECKCAST, accessorInterfaceInternalName);
            mv.visitVarInsn(Opcodes.ALOAD, 2);
            mv.visitMethodInsn(
                Opcodes.INVOKEINTERFACE,
                accessorInterfaceInternalName,
                setterName,
                Utils.getMethodDefinition(accessorInterface, setterName).getDescriptor(),
                /* isInterface= */ true);
            mv.visitJumpInsn(Opcodes.GOTO, endLabel);
            mv.visitLabel(elseLabel);
            if (frames) {
              mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
            }
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitVarInsn(Opcodes.ALOAD, 1);
            mv.visitVarInsn(Opcodes.ALOAD, 2);
            mv.visitMethodInsn(
                Opcodes.INVOKESPECIAL,
                instrumentedTypeInternalName,
                "mapPut",
                Utils.getMethodDefinition(instrumentedType, "mapPut").getDescriptor(),
                /* isInterface= */ false);
            mv.visitLabel(endLabel);
            if (frames) {
              mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
            }
            mv.visitInsn(Opcodes.RETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
          }

          private MethodVisitor getMethodVisitor(String methodName) {
            return cv.visitMethod(
                Opcodes.ACC_PRIVATE,
                methodName,
                Utils.getMethodDefinition(instrumentedType, methodName).getDescriptor(),
                null,
                null);
          }
        };
      }
    };
  }

  /**
   * Template class used to generate the class that accesses stored context using either key
   * instance's own injected field or global hash map if field is not available.
   */
  // Called from generated code
  @SuppressWarnings({"UnusedMethod", "UnusedVariable", "MethodCanBeStatic"})
  static final class VirtualFieldImplementationTemplate extends VirtualField<Object, Object> {
    private static final VirtualFieldImplementationTemplate INSTANCE =
        new VirtualFieldImplementationTemplate(Cache.newBuilder().setWeakKeys().build());

    private final Cache<Object, Object> map;

    private VirtualFieldImplementationTemplate(Cache<Object, Object> map) {
      this.map = map;
    }

    @Override
    public Object get(Object object) {
      return realGet(object);
    }

    @Override
    public void set(Object object, Object fieldValue) {
      realPut(object, fieldValue);
    }

    private Object realGet(Object key) {
      // to be generated
      return null;
    }

    private void realPut(Object key, Object value) {
      // to be generated
    }

    private Object mapGet(Object key) {
      return map.get(key);
    }

    private void mapPut(Object key, Object value) {
      if (value == null) {
        map.remove(key);
      } else {
        map.put(key, value);
      }
    }

    public static VirtualField getVirtualField(Class keyClass, Class contextClass) {
      // We do not actually check the keyClass here - but that should be fine since compiler would
      // check things for us.
      return INSTANCE;
    }
  }
}
