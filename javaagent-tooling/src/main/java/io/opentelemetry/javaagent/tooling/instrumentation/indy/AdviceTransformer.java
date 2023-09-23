/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.instrumentation.indy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Transform inline advice to delegating advice. This transformation is best effort, it isn't able
 * to transform all the advices.
 */
class AdviceTransformer {
  private static final Type OBJECT_TYPE = Type.getType(Object.class);
  private static final Type OBJECT_ARRAY_TYPE = Type.getType(Object[].class);

  static byte[] transform(byte[] bytes) {
    ClassReader cr = new ClassReader(bytes);
    ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS);
    ClassVisitor cv =
        new ClassVisitor(Opcodes.ASM9, cw) {

          @Override
          public MethodVisitor visitMethod(
              int access, String name, String descriptor, String signature, String[] exceptions) {
            ClassVisitor classVisitor = this.cv;
            return new MethodNode(api, access, name, descriptor, signature, exceptions) {
              @Override
              public void visitEnd() {
                super.visitEnd();

                instrument(this, classVisitor);
              }
            };
          }
        };
    cr.accept(cv, ClassReader.EXPAND_FRAMES);
    return cw.toByteArray();
  }

  // method argument annotated with Advice.Argument or Advice.Return
  private static class OutputArgument {
    // index of the method argument with the annotation
    final int adviceIndex;
    // value of the annotation or -1 if Advice.Return
    final int methodIndex;

    OutputArgument(int adviceIndex, int methodIndex) {
      this.adviceIndex = adviceIndex;
      this.methodIndex = methodIndex;
    }
  }

  // method argument annotated with Advice.Local
  private static class AdviceLocal {
    // index of the method argument with the annotation
    final int adviceIndex;
    // value of the Advice.Local annotation
    final String name;

    AdviceLocal(int adviceIndex, String name) {
      this.adviceIndex = adviceIndex;
      this.name = name;
    }
  }

  private static final Type ADVICE_ARGUMENT = Type.getType(Advice.Argument.class);

  /** List of arguments annotated with {@code @Advice.Argument(readOnly = false)}. */
  private static List<OutputArgument> getWritableArguments(MethodNode source) {
    List<OutputArgument> result = new ArrayList<>();
    if (source.visibleParameterAnnotations != null) {
      int i = 0;
      for (List<AnnotationNode> list : source.visibleParameterAnnotations) {
        for (AnnotationNode annotationNode : list) {
          Type annotationType = Type.getType(annotationNode.desc);
          if (ADVICE_ARGUMENT.equals(annotationType) && isWriteable(annotationNode)) {
            Object value = getAnnotationValue(annotationNode);
            if (value instanceof Integer) {
              result.add(new OutputArgument(i, (Integer) value));
            }
          }
        }
        i++;
      }
    }

    return result;
  }

  private static final Type ADVICE_RETURN = Type.getType(Advice.Return.class);

  /** Argument annotated with {@code @Advice.Return(readOnly = false)} or {@code null}. */
  private static OutputArgument getWritableReturnValue(MethodNode source) {
    if (source.visibleParameterAnnotations != null) {
      int i = 0;
      for (List<AnnotationNode> list : source.visibleParameterAnnotations) {
        for (AnnotationNode annotationNode : list) {
          Type annotationType = Type.getType(annotationNode.desc);
          if (ADVICE_RETURN.equals(annotationType) && isWriteable(annotationNode)) {
            return new OutputArgument(i, -1);
          }
        }
        i++;
      }
    }

    return null;
  }

  private static final Type ADVICE_LOCAL = Type.getType(Advice.Local.class);

  /** List of arguments annotated with {@code @Advice.Local}. */
  private static List<AdviceLocal> getLocals(MethodNode source) {
    List<AdviceLocal> result = new ArrayList<>();
    if (source.visibleParameterAnnotations != null) {
      int i = 0;
      for (List<AnnotationNode> list : source.visibleParameterAnnotations) {
        for (AnnotationNode annotationNode : list) {
          Type annotationType = Type.getType(annotationNode.desc);
          if (ADVICE_LOCAL.equals(annotationType)) {
            Object value = getAnnotationValue(annotationNode);
            if (value instanceof String) {
              result.add(new AdviceLocal(i, (String) value));
            }
          }
        }
        i++;
      }
    }

    return result;
  }

  private static final Type ADVICE_ENTER = Type.getType(Advice.OnMethodEnter.class);

  private static boolean isEnterAdvice(MethodNode source) {
    return hasAnnotation(source, ADVICE_ENTER);
  }

  private static final Type ADVICE_EXIT = Type.getType(Advice.OnMethodExit.class);

  private static boolean isExitAdvice(MethodNode source) {
    return hasAnnotation(source, ADVICE_EXIT);
  }

  private static boolean hasAnnotation(MethodNode source, Type type) {
    if (source.visibleAnnotations != null) {
      for (AnnotationNode annotationNode : source.visibleAnnotations) {
        Type annotationType = Type.getType(annotationNode.desc);
        if (type.equals(annotationType)) {
          return true;
        }
      }
    }

    return false;
  }

  /**
   * Transform arguments annotated with {@code @Advice.Argument(readOnly = false)}.
   *
   * <pre>{@code
   * void foo(@Advice.Argument(value = 0, readOnly = false) T1 foo, @Advice.Argument(value = 0, readOnly = false) T2 bar)
   * }</pre>
   *
   * <p>is transformed to
   *
   * <pre>{@code
   * @Advice.AssignReturned.ToArguments({
   *   @Advice.AssignReturned.ToArguments.ToArgument(value = 0, index = 0, typing = DYNAMIC),
   *   @Advice.AssignReturned.ToArguments.ToArgument(value = 1, index = 1, typing = DYNAMIC)
   * })
   * void foo(@Advice.Argument(value = 0, readOnly = true) T1 foo, @Advice.Argument(value = 0, readOnly = true) T2 bar) {
   *   ...
   *   Object[] result = new Object[2];
   *   result[0] = foo;
   *   result[1] = bar;
   *   return result;
   * }
   * }</pre>
   */
  private static MethodVisitor instrumentWritableArguments(
      MethodVisitor target,
      MethodNode source,
      List<OutputArgument> writableArguments,
      int returnIndex) {
    MethodVisitor result =
        new MethodVisitor(Opcodes.ASM9, target) {
          @Override
          public void visitCode() {
            AnnotationVisitor av =
                visitAnnotation(Type.getDescriptor(Advice.AssignReturned.ToArguments.class), true);
            AnnotationVisitor valueArrayVisitor = av.visitArray("value");
            for (int i = 0; i < writableArguments.size(); i++) {
              OutputArgument argument = writableArguments.get(i);
              AnnotationVisitor valueVisitor =
                  valueArrayVisitor.visitAnnotation(
                      null, Type.getDescriptor(Advice.AssignReturned.ToArguments.ToArgument.class));
              valueVisitor.visit("value", argument.methodIndex);
              valueVisitor.visit("index", returnIndex + i);
              valueVisitor.visitEnum(
                  "typing", Type.getDescriptor(Assigner.Typing.class), "DYNAMIC");
              valueVisitor.visitEnd();
            }
            valueArrayVisitor.visitEnd();
            av.visitEnd();
            super.visitCode();
          }

          @Override
          public void visitInsn(int opcode) {
            if (Opcodes.ARETURN == opcode) {
              // expecting object array on stack
              GeneratorAdapter ga =
                  new GeneratorAdapter(mv, source.access, source.name, source.desc);
              Type[] argumentTypes = ga.getArgumentTypes();
              for (int i = 0; i < writableArguments.size(); i++) {
                OutputArgument argument = writableArguments.get(i);
                ga.dup();
                ga.push(returnIndex + i);
                ga.loadArg(argument.adviceIndex);
                ga.box(argumentTypes[argument.adviceIndex]);
                ga.arrayStore(OBJECT_TYPE);
              }
            }
            super.visitInsn(opcode);
          }
        };
    result = makeReadOnly(Advice.Argument.class, result);
    return result;
  }

  /**
   * Transform arguments annotated with {@code @Advice.Return(readOnly = false)}.
   *
   * <pre>{@code
   * void foo(@Advice.Return(readOnly = false) T1 foo)
   * }</pre>
   *
   * <p>is transformed to
   *
   * <pre>{@code
   * @Advice.AssignReturned.ToReturned(index = 0, typing = DYNAMIC)
   * Object[] foo(@Advice.Return(readOnly = true) T foo) {
   *   ...
   *   return new Object[] { foo };
   * }
   * }</pre>
   */
  @SuppressWarnings("UnusedVariable")
  private static MethodVisitor instrumentWritableReturn(
      MethodVisitor target, MethodNode source, OutputArgument writableReturn, int returnIndex) {
    MethodVisitor result =
        new MethodVisitor(Opcodes.ASM9, target) {
          @Override
          public void visitCode() {
            AnnotationVisitor av =
                visitAnnotation(Type.getDescriptor(Advice.AssignReturned.ToReturned.class), true);
            av.visit("index", returnIndex);
            av.visitEnum("typing", Type.getDescriptor(Assigner.Typing.class), "DYNAMIC");
            av.visitEnd();
            super.visitCode();
          }

          @Override
          public void visitInsn(int opcode) {
            if (Opcodes.ARETURN == opcode) {
              // expecting object array on stack
              GeneratorAdapter ga =
                  new GeneratorAdapter(mv, source.access, source.name, source.desc);
              Type[] argumentTypes = ga.getArgumentTypes();
              ga.dup();
              ga.push(returnIndex);
              ga.loadArg(writableReturn.adviceIndex);
              ga.box(argumentTypes[writableReturn.adviceIndex]);
              ga.arrayStore(OBJECT_TYPE);
            }
            super.visitInsn(opcode);
          }
        };
    result = makeReadOnly(Advice.Return.class, result);
    return result;
  }

  /**
   * Transform arguments annotated with {@code @Advice.Local}.
   *
   * <pre>{@code
   * void foo(@Advice.Local("foo") T1 foo, @Advice.Local("bar") T1 bar)
   * }</pre>
   *
   * <p>for enter advice is transformed to
   *
   * <pre>{@code
   * Object[] foo(@Advice.Unused Object foo, @Advice.Unused Object bar) {
   *   ...
   *   Map result = new HashMap();
   *   result.put("foo", foo);
   *   result.put("bar", bar);
   *   return new Object[] { result };
   * }
   * }</pre>
   *
   * <p>and for exit advice is transformed to
   *
   * <pre>{@code
   * void foo(@Advice.Unused Object foo, @Advice.Unused Object bar, @Advice.Enter Object[] array) {
   *   Map map = (Map) array[0];
   *   foo = (T1) map.get("foo");
   *   bar = (T2) map.get("bar");
   *   ...
   * }
   * }</pre>
   */
  private static MethodVisitor instrumentAdviceLocals(
      boolean isEnterAdvice,
      MethodVisitor target,
      MethodNode source,
      String originalDesc,
      List<AdviceLocal> adviceLocals,
      int returnIndex) {
    AtomicReference<GeneratorAdapter> generatorRef = new AtomicReference<>();
    AtomicInteger dataIndex = new AtomicInteger();

    target =
        new MethodVisitor(Opcodes.ASM9, target) {
          @Override
          public AnnotationVisitor visitParameterAnnotation(
              int parameter, String descriptor, boolean visible) {
            // replace @Advice.Local with @Advice.Unused
            if (Type.getDescriptor(Advice.Local.class).equals(descriptor)) {
              descriptor = Type.getDescriptor(Advice.Unused.class);
            }
            return super.visitParameterAnnotation(parameter, descriptor, visible);
          }

          @Override
          public void visitAnnotableParameterCount(int parameterCount, boolean visible) {
            if (!isEnterAdvice) {
              parameterCount++;
            }
            super.visitAnnotableParameterCount(parameterCount, visible);
          }

          @Override
          public void visitInsn(int opcode) {
            if (isEnterAdvice && Opcodes.ARETURN == opcode) {
              // expecting object array on stack
              GeneratorAdapter ga = generatorRef.get();
              // duplicate array
              ga.dup();
              // push array index for the map
              ga.push(returnIndex);
              Type hashMapType = Type.getType(HashMap.class);
              Type[] argumentTypes = ga.getArgumentTypes();
              ga.newInstance(hashMapType);
              ga.dup();
              ga.invokeConstructor(hashMapType, Method.getMethod("void <init>()"));
              // stack: array, array, array index for map, map
              for (AdviceLocal adviceLocal : adviceLocals) {
                ga.dup();
                ga.push(adviceLocal.name);
                ga.loadArg(adviceLocal.adviceIndex);
                ga.box(argumentTypes[adviceLocal.adviceIndex]);
                ga.invokeVirtual(
                    hashMapType,
                    Method.getMethod("java.lang.Object put(java.lang.Object, java.lang.Object)"));
                // por return value of Map.put
                ga.pop();
              }
              // stack: array, array, array index for map, map
              // store map in the array
              ga.arrayStore(OBJECT_TYPE);
              // stack: array
            }
            super.visitInsn(opcode);
          }

          @Override
          public void visitCode() {
            super.visitCode();
            GeneratorAdapter ga = generatorRef.get();
            Type[] argumentTypes = ga.getArgumentTypes();

            if (isEnterAdvice) {
              // we have change the type fo method arguments annotated with @Advice.Local to Object
              // here we'll load the argument, cast it to its actual type, and store it back
              for (AdviceLocal adviceLocal : adviceLocals) {
                ga.loadArg(adviceLocal.adviceIndex);
                ga.checkCast(argumentTypes[adviceLocal.adviceIndex]);
                ga.storeArg(adviceLocal.adviceIndex);
              }
              return;
            }

            // the index of last argument where Map is inserted (argumentTypes array does not
            // contain the Map)
            int lastArgumentIndex = argumentTypes.length;
            AnnotationVisitor av =
                mv.visitParameterAnnotation(
                    lastArgumentIndex, Type.getDescriptor(Advice.Enter.class), true);
            av.visitEnd();

            Type mapType = Type.getType(Map.class);
            // load object array
            ga.loadLocal(dataIndex.get(), OBJECT_ARRAY_TYPE);
            ga.dup();
            // we want the last element of the array
            ga.arrayLength();
            ga.visitInsn(Opcodes.ICONST_1);
            ga.visitInsn(Opcodes.ISUB);
            // load map
            ga.arrayLoad(mapType);
            for (AdviceLocal adviceLocal : adviceLocals) {
              // duplicate Map
              ga.dup();
              ga.push(adviceLocal.name);
              ga.invokeInterface(
                  mapType, Method.getMethod("java.lang.Object get(java.lang.Object)"));
              ga.unbox(argumentTypes[adviceLocal.adviceIndex]);
              ga.storeArg(adviceLocal.adviceIndex);
            }
            // pop Map
            ga.pop();
          }
        };

    // pretend that this method still takes the original arguments
    GeneratorAdapter ga =
        new GeneratorAdapter(target, Opcodes.ACC_STATIC, source.name, originalDesc);
    generatorRef.set(ga);
    if (!isEnterAdvice) {
      // for exit advice create a new local for the Object array we added as the last method
      // argument
      dataIndex.set(ga.newLocal(OBJECT_ARRAY_TYPE));
    }

    return ga;
  }

  private static void instrument(MethodNode methodNode, ClassVisitor classVisitor) {
    String originalDescriptor = methodNode.desc;
    String[] exceptionsArray = methodNode.exceptions.toArray(new String[0]);

    // only instrument if method returns void, in most of the instrumentations we need to change
    // the return type of the method which will only work if the method returns void
    if (Type.VOID_TYPE.equals(Type.getReturnType(methodNode.desc))) {
      List<OutputArgument> writableArguments = getWritableArguments(methodNode);
      OutputArgument writableReturn = getWritableReturnValue(methodNode);
      List<AdviceLocal> adviceLocals = getLocals(methodNode);
      boolean isEnterAdvice = isEnterAdvice(methodNode);

      if (!writableArguments.isEmpty()
          || writableReturn != null
          || (!adviceLocals.isEmpty() && isEnterAdvice)) {
        Type[] argumentTypes = Type.getArgumentTypes(methodNode.desc);
        if (!adviceLocals.isEmpty() && isEnterAdvice) {
          // Set type of arguments annotated with @Advice.Local to Object. These arguments are
          // likely
          // to be helper classes which currently breaks because the invokedynamic call in advised
          // class needs access to the parameter types of the advice method.
          for (AdviceLocal adviceLocal : adviceLocals) {
            argumentTypes[adviceLocal.adviceIndex] = OBJECT_TYPE;
          }
        }

        methodNode.desc = Type.getMethodDescriptor(OBJECT_ARRAY_TYPE, argumentTypes);

        MethodNode tmp =
            new MethodNode(
                methodNode.access,
                methodNode.name,
                methodNode.desc,
                methodNode.signature,
                exceptionsArray);
        MethodVisitor mv =
            instrumentOurParameters(
                tmp,
                methodNode,
                originalDescriptor,
                writableArguments,
                writableReturn,
                adviceLocals);
        methodNode.accept(mv);

        methodNode = tmp;
        adviceLocals = getLocals(methodNode);
      }

      // this is the only transformation that does not change the return type of the advice method,
      // thus it is also the only transformation that can be applied on top of the other transforms
      if (!adviceLocals.isEmpty() && isExitAdvice(methodNode)) {
        // Set type of arguments annotated with @Advice.Local to Object. These arguments are likely
        // to be helper classes which currently breaks because the invokedynamic call in advised
        // class needs access to the parameter types of the advice method.
        Type[] newArgumentTypes = Type.getArgumentTypes(methodNode.desc);
        for (AdviceLocal adviceLocal : adviceLocals) {
          newArgumentTypes[adviceLocal.adviceIndex] = OBJECT_TYPE;
        }
        List<Type> typeList = new ArrayList<>(Arrays.asList(newArgumentTypes));
        // add Object array as the last argument, this array is used to pass info from the enter
        // advice
        typeList.add(OBJECT_ARRAY_TYPE);

        methodNode.desc =
            Type.getMethodDescriptor(
                Type.getReturnType(methodNode.desc), typeList.toArray(new Type[0]));

        MethodNode tmp =
            new MethodNode(
                methodNode.access,
                methodNode.name,
                methodNode.desc,
                methodNode.signature,
                exceptionsArray);
        MethodVisitor mv =
            instrumentAdviceLocals(false, tmp, methodNode, originalDescriptor, adviceLocals, -1);
        methodNode.accept(mv);

        methodNode = tmp;
      }
    }

    MethodVisitor mv =
        classVisitor.visitMethod(
            methodNode.access,
            methodNode.name,
            methodNode.desc,
            methodNode.signature,
            exceptionsArray);
    mv = delegateAdvice(mv);

    methodNode.accept(mv);
  }

  private static MethodVisitor instrumentOurParameters(
      MethodVisitor target,
      MethodNode source,
      String originalDesc,
      List<OutputArgument> writableArguments,
      OutputArgument writableReturn,
      List<AdviceLocal> adviceLocals) {

    int returnArraySize = 0;
    if (writableReturn != null) {
      target = instrumentWritableReturn(target, source, writableReturn, returnArraySize);
      returnArraySize++;
    }
    if (!writableArguments.isEmpty()) {
      target = instrumentWritableArguments(target, source, writableArguments, returnArraySize);
      returnArraySize += writableArguments.size();
    }
    if (!adviceLocals.isEmpty() && isEnterAdvice(source)) {
      target =
          instrumentAdviceLocals(true, target, source, originalDesc, adviceLocals, returnArraySize);
      returnArraySize++;
    }
    target = addReturnArray(target, returnArraySize);

    return target;
  }

  /** Return the value of the {@code readOnly} attribute of the annotation. */
  private static boolean isWriteable(AnnotationNode annotationNode) {
    if (annotationNode.values != null && !annotationNode.values.isEmpty()) {
      List<Object> values = annotationNode.values;
      for (int i = 0; i < values.size(); i += 2) {
        String attributeName = (String) values.get(i);
        Object attributeValue = values.get(i + 1);
        if ("readOnly".equals(attributeName)) {
          return Boolean.FALSE.equals(attributeValue);
        }
      }
    }

    return false;
  }

  /** Return the value of the {@code value} attribute of the annotation. */
  private static Object getAnnotationValue(AnnotationNode annotationNode) {
    if (annotationNode.values != null && !annotationNode.values.isEmpty()) {
      List<Object> values = annotationNode.values;
      for (int i = 0; i < values.size(); i += 2) {
        String attributeName = (String) values.get(i);
        Object attributeValue = values.get(i + 1);
        if ("value".equals(attributeName)) {
          return attributeValue;
        }
      }
    }

    return null;
  }

  private static MethodVisitor addReturnArray(MethodVisitor target, int returnArraySize) {
    return new MethodVisitor(Opcodes.ASM9, target) {
      @Override
      public void visitInsn(int opcode) {
        if (Opcodes.RETURN == opcode) {
          GeneratorAdapter ga = new GeneratorAdapter(mv, 0, null, "()V");
          ga.push(returnArraySize);
          ga.newArray(OBJECT_TYPE);
          opcode = Opcodes.ARETURN;
        }
        super.visitInsn(opcode);
      }
    };
  }

  /**
   * If method is annotated with {@link Advice.OnMethodEnter} or {@link Advice.OnMethodExit} set
   * {@code inline} attribute on the annotation to {@code false}.
   */
  private static MethodVisitor delegateAdvice(MethodVisitor target) {
    return new MethodVisitor(Opcodes.ASM9, target) {
      @Override
      public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
        AnnotationVisitor av = super.visitAnnotation(descriptor, visible);
        Type type = Type.getType(descriptor);
        if (!Type.getType(Advice.OnMethodEnter.class).equals(type)
            && !Type.getType(Advice.OnMethodExit.class).equals(type)) {
          return av;
        }
        return new AnnotationVisitor(api, av) {
          boolean hasInline = false;

          @Override
          public void visit(String name, Object value) {
            if ("inline".equals(name)) {
              value = Boolean.FALSE;
              hasInline = true;
            }
            super.visit(name, value);
          }

          @Override
          public void visitEnd() {
            if (!hasInline) {
              visit("inline", Boolean.FALSE);
            }
            super.visitEnd();
          }
        };
      }
    };
  }

  /** If annotation has {@code readOnly} attribute set it to {@code true}. */
  private static MethodVisitor makeReadOnly(Class<?> annotationType, MethodVisitor target) {
    return new MethodVisitor(Opcodes.ASM9, target) {

      @Override
      public AnnotationVisitor visitParameterAnnotation(
          int parameter, String descriptor, boolean visible) {
        AnnotationVisitor av = super.visitParameterAnnotation(parameter, descriptor, visible);
        Type type = Type.getType(descriptor);
        if (!Type.getType(annotationType).equals(type)) {
          return av;
        }
        return new AnnotationVisitor(api, av) {
          @Override
          public void visit(String name, Object value) {
            if ("readOnly".equals(name)) {
              value = Boolean.TRUE;
            }
            super.visit(name, value);
          }
        };
      }
    };
  }

  private AdviceTransformer() {}
}
