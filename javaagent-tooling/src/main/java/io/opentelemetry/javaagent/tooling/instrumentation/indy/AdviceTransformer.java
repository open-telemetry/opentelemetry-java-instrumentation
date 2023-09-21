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
      MethodVisitor target, MethodNode source, List<OutputArgument> writableArguments) {
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
              valueVisitor.visit("index", i);
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
            if (Opcodes.RETURN == opcode) {
              GeneratorAdapter ga =
                  new GeneratorAdapter(mv, source.access, source.name, source.desc);
              Type[] argumentTypes = ga.getArgumentTypes();
              ga.push(writableArguments.size());
              ga.newArray(OBJECT_TYPE);
              for (int i = 0; i < writableArguments.size(); i++) {
                OutputArgument argument = writableArguments.get(i);
                ga.dup();
                ga.push(i);
                ga.loadArg(argument.adviceIndex);
                ga.box(argumentTypes[argument.adviceIndex]);
                ga.arrayStore(OBJECT_TYPE);
              }
              mv.visitInsn(Opcodes.ARETURN);
              return;
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
   * @Advice.AssignReturned.ToReturned(typing = DYNAMIC)
   * T foo(@Advice.Return(readOnly = true) T foo) {
   *   ...
   *   return foo;
   * }
   * }</pre>
   */
  private static MethodVisitor instrumentWritableReturn(
      MethodVisitor target, MethodNode source, OutputArgument writableReturn) {
    MethodVisitor result =
        new MethodVisitor(Opcodes.ASM9, target) {
          @Override
          public void visitCode() {
            AnnotationVisitor av =
                visitAnnotation(Type.getDescriptor(Advice.AssignReturned.ToReturned.class), true);
            av.visitEnum("typing", Type.getDescriptor(Assigner.Typing.class), "DYNAMIC");
            av.visitEnd();
            super.visitCode();
          }

          @Override
          public void visitInsn(int opcode) {
            if (Opcodes.RETURN == opcode) {
              GeneratorAdapter ga =
                  new GeneratorAdapter(mv, source.access, source.name, source.desc);
              Type[] argumentTypes = ga.getArgumentTypes();
              ga.loadArg(writableReturn.adviceIndex);
              ga.box(argumentTypes[writableReturn.adviceIndex]);
              mv.visitInsn(Opcodes.ARETURN);
              return;
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
   * Map foo(@Advice.Unused Object foo, @Advice.Unused Object bar) {
   *   ...
   *   Map result = new HashMap();
   *   result.put("foo", foo);
   *   result.put("bar", bar);
   *   return foo;
   * }
   * }</pre>
   *
   * <p>and for exit advice is transformed to
   *
   * <pre>{@code
   * void foo(@Advice.Unused Object foo, @Advice.Unused Object bar, @Advice.Enter Map map) {
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
      List<AdviceLocal> adviceLocals) {
    AtomicReference<GeneratorAdapter> generatorRef = new AtomicReference<>();
    AtomicInteger mapIndex = new AtomicInteger();

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
            if (isEnterAdvice && Opcodes.RETURN == opcode) {
              GeneratorAdapter ga = generatorRef.get();
              Type hashMapType = Type.getType(HashMap.class);
              Type[] argumentTypes = ga.getArgumentTypes();
              ga.newInstance(hashMapType);
              ga.dup();
              ga.invokeConstructor(hashMapType, Method.getMethod("void <init>()"));
              for (AdviceLocal adviceLocal : adviceLocals) {
                ga.dup();
                ga.push(adviceLocal.name);
                ga.loadArg(adviceLocal.adviceIndex);
                ga.box(argumentTypes[adviceLocal.adviceIndex]);
                ga.invokeVirtual(
                    hashMapType,
                    Method.getMethod("java.lang.Object put(java.lang.Object, java.lang.Object)"));
                ga.pop();
              }
              mv.visitInsn(Opcodes.ARETURN);
              return;
            }
            super.visitInsn(opcode);
          }

          @Override
          public void visitCode() {
            if (isEnterAdvice) {
              super.visitCode();
              return;
            }
            GeneratorAdapter ga = generatorRef.get();
            Type[] argumentTypes = ga.getArgumentTypes();
            // the index of last argument where Map is inserted (argumentTypes array does not
            // contain the Map)
            int lastArgumentIndex = argumentTypes.length;
            AnnotationVisitor av =
                mv.visitParameterAnnotation(
                    lastArgumentIndex, Type.getDescriptor(Advice.Enter.class), true);
            av.visitEnd();

            Type mapType = Type.getType(Map.class);
            // load Map
            ga.loadLocal(mapIndex.get(), mapType);
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
      // for exit advice create a new local for the map we added as the last method argument
      mapIndex.set(ga.newLocal(Type.getType(Map.class)));
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

      if (!writableArguments.isEmpty()) {
        methodNode.desc =
            Type.getMethodDescriptor(
                Type.getType(Object[].class), Type.getArgumentTypes(methodNode.desc));
      } else if (writableReturn != null) {
        methodNode.desc =
            Type.getMethodDescriptor(
                Type.getType(Object.class), Type.getArgumentTypes(methodNode.desc));
      } else if (!adviceLocals.isEmpty() && isEnterAdvice(methodNode)) {
        // Set type of arguments annotated with @Advice.Local to Object. These arguments are likely
        // to be helper classes which currently breaks because the invokedynamic call in advised
        // class needs access to the parameter types of the advice method.
        Type[] newArgumentTypes = Type.getArgumentTypes(methodNode.desc);
        for (AdviceLocal adviceLocal : adviceLocals) {
          newArgumentTypes[adviceLocal.adviceIndex] = OBJECT_TYPE;
        }
        methodNode.desc = Type.getMethodDescriptor(Type.getType(Map.class), newArgumentTypes);
      }

      if (!originalDescriptor.equals(methodNode.desc)) {
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
        // add Map as the last argument
        typeList.add(Type.getType(Map.class));

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
            instrumentAdviceLocals(false, tmp, methodNode, originalDescriptor, adviceLocals);
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

    if (!writableArguments.isEmpty()) {
      target = instrumentWritableArguments(target, source, writableArguments);
    } else if (writableReturn != null) {
      target = instrumentWritableReturn(target, source, writableReturn);
    } else if (!adviceLocals.isEmpty() && isEnterAdvice(source)) {
      target = instrumentAdviceLocals(true, target, source, originalDesc, adviceLocals);
    }

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
