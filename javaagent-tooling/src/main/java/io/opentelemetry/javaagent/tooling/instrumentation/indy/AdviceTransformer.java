/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.instrumentation.indy;

import io.opentelemetry.javaagent.extension.instrumentation.internal.AsmApi;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
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
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Transform inline advice to delegating advice. This transformation is best effort, it isn't able
 * to transform all the advices.
 */
class AdviceTransformer {
  private static final Type OBJECT_TYPE = Type.getType(Object.class);
  private static final Type OBJECT_ARRAY_TYPE = Type.getType(Object[].class);

  static final Type ADVICE_ON_METHOD_ENTER = Type.getType(Advice.OnMethodEnter.class);
  static final Type ADVICE_ON_METHOD_EXIT = Type.getType(Advice.OnMethodExit.class);

  static byte[] transform(byte[] bytes) {
    ClassReader cr = new ClassReader(bytes);
    ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS);
    ClassNode classNode = new ClassNode();
    cr.accept(classNode, ClassReader.EXPAND_FRAMES);

    // skip the class if there aren't any methods with advice annotations or these annotations have
    // set inline = false
    if (!hasInlineAdvice(classNode)) {
      classNode.accept(cw);
      return cw.toByteArray();
    }

    // Advices already using Advice.AssignReturned are assumed to be already compatible
    // Those won't be transformed except for setting inline to false
    boolean justDelegateAdvice = usesAssignReturned(classNode);

    // sort enter advice method before exit advice
    classNode.methods.sort(
        Comparator.comparingInt(
            (methodNode) -> {
              if (isEnterAdvice(methodNode)) {
                return 1;
              } else if (isExitAdvice(methodNode)) {
                return 2;
              }
              return 0;
            }));

    TransformationContext context = new TransformationContext();
    if (justDelegateAdvice) {
      context.disableReturnTypeChange();
    }
    ClassVisitor cv =
        new ClassVisitor(AsmApi.VERSION, cw) {

          @Override
          public MethodVisitor visitMethod(
              int access, String name, String descriptor, String signature, String[] exceptions) {
            ClassVisitor classVisitor = this.cv;
            return new MethodNode(api, access, name, descriptor, signature, exceptions) {
              @Override
              public void visitEnd() {
                super.visitEnd();
                if (justDelegateAdvice) {
                  applyAdviceDelegation(
                      context, this, classVisitor, exceptions.toArray(new String[0]));
                } else {
                  instrument(context, this, classVisitor);
                }
              }
            };
          }
        };
    classNode.accept(cv);
    return cw.toByteArray();
  }

  private static boolean hasInlineAdvice(ClassNode classNode) {
    for (MethodNode mn : classNode.methods) {
      if (hasInlineAdvice(mn)) {
        return true;
      }
    }

    return false;
  }

  private static boolean hasInlineAdvice(MethodNode methodNode) {
    return hasInlineAdvice(methodNode, ADVICE_ON_METHOD_ENTER)
        || hasInlineAdvice(methodNode, ADVICE_ON_METHOD_EXIT);
  }

  private static boolean hasInlineAdvice(MethodNode methodNode, Type type) {
    AnnotationNode annotationNode = getAnnotationNode(methodNode, type);
    if (annotationNode != null) {
      // delegating advice has attribute "inline" = false
      // all other advice is inline
      return !Boolean.FALSE.equals(getAttributeValue(annotationNode, "inline"));
    }
    return false;
  }

  // method argument annotated with Advice.Argument or Advice.Return
  private static class OutputArgument {
    // index of the method argument with the annotation
    final int adviceIndex;
    // value of the annotation or -1 if Advice.Return or Advice.Enter
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
      for (int i = 0; i < source.visibleParameterAnnotations.length; i++) {
        List<AnnotationNode> list = source.visibleParameterAnnotations[i];
        if (list == null) {
          continue;
        }

        for (AnnotationNode annotationNode : list) {
          Type annotationType = Type.getType(annotationNode.desc);
          if (ADVICE_ARGUMENT.equals(annotationType) && isWriteable(annotationNode)) {
            Object value = getAnnotationValue(annotationNode);
            if (value instanceof Integer) {
              result.add(new OutputArgument(i, (Integer) value));
            }
          }
        }
      }
    }

    return result;
  }

  private static final Type ADVICE_RETURN = Type.getType(Advice.Return.class);

  /** Argument annotated with {@code @Advice.Return(readOnly = false)} or {@code null}. */
  private static OutputArgument getWritableReturnValue(MethodNode source) {
    if (source.visibleParameterAnnotations != null) {
      for (int i = 0; i < source.visibleParameterAnnotations.length; i++) {
        List<AnnotationNode> list = source.visibleParameterAnnotations[i];
        if (list == null) {
          continue;
        }

        for (AnnotationNode annotationNode : list) {
          Type annotationType = Type.getType(annotationNode.desc);
          if (ADVICE_RETURN.equals(annotationType) && isWriteable(annotationNode)) {
            return new OutputArgument(i, -1);
          }
        }
      }
    }

    return null;
  }

  private static final Type ADVICE_ENTER = Type.getType(Advice.Enter.class);

  /** Argument annotated with {@code @Advice.Enter} or {@code null}. */
  private static OutputArgument getEnterArgument(MethodNode source) {
    Type[] argumentTypes = Type.getArgumentTypes(source.desc);
    if (source.visibleParameterAnnotations != null) {
      for (int i = 0; i < source.visibleParameterAnnotations.length; i++) {
        List<AnnotationNode> list = source.visibleParameterAnnotations[i];
        if (list == null) {
          continue;
        }

        for (AnnotationNode annotationNode : list) {
          Type annotationType = Type.getType(annotationNode.desc);
          if (ADVICE_ENTER.equals(annotationType)
              && argumentTypes[i].getDescriptor().length() > 1) {
            return new OutputArgument(i, -1);
          }
        }
      }
    }

    return null;
  }

  private static final Type ADVICE_LOCAL = Type.getType(Advice.Local.class);

  /** List of arguments annotated with {@code @Advice.Local}. */
  private static List<AdviceLocal> getLocals(MethodNode source) {
    List<AdviceLocal> result = new ArrayList<>();
    if (source.visibleParameterAnnotations != null) {
      for (int i = 0; i < source.visibleParameterAnnotations.length; i++) {
        List<AnnotationNode> list = source.visibleParameterAnnotations[i];
        if (list == null) {
          continue;
        }

        for (AnnotationNode annotationNode : list) {
          Type annotationType = Type.getType(annotationNode.desc);
          if (ADVICE_LOCAL.equals(annotationType)) {
            Object value = getAnnotationValue(annotationNode);
            if (value instanceof String) {
              result.add(new AdviceLocal(i, (String) value));
            }
          }
        }
      }
    }

    return result;
  }

  private static final Type ADVICE_ASSIGN_RETURNED_TO_RETURNED =
      Type.getType(Advice.AssignReturned.ToReturned.class);
  private static final Type ADVICE_ASSIGN_RETURNED_TO_ARGUMENTS =
      Type.getType(Advice.AssignReturned.ToArguments.class);
  private static final Type ADVICE_ASSIGN_RETURNED_TO_FIELDS =
      Type.getType(Advice.AssignReturned.ToFields.class);
  private static final Type ADVICE_ASSIGN_RETURNED_TO_ALL_ARGUMENTS =
      Type.getType(Advice.AssignReturned.ToAllArguments.class);

  private static boolean usesAssignReturned(MethodNode source) {
    return hasAnnotation(source, ADVICE_ASSIGN_RETURNED_TO_RETURNED)
        || hasAnnotation(source, ADVICE_ASSIGN_RETURNED_TO_ARGUMENTS)
        || hasAnnotation(source, ADVICE_ASSIGN_RETURNED_TO_FIELDS)
        || hasAnnotation(source, ADVICE_ASSIGN_RETURNED_TO_ALL_ARGUMENTS);
  }

  private static boolean usesAssignReturned(ClassNode classNode) {
    for (MethodNode mn : classNode.methods) {
      if (usesAssignReturned(mn)) {
        return true;
      }
    }
    return false;
  }

  private static boolean isEnterAdvice(MethodNode source) {
    return hasAnnotation(source, ADVICE_ON_METHOD_ENTER);
  }

  private static boolean isExitAdvice(MethodNode source) {
    return hasAnnotation(source, ADVICE_ON_METHOD_EXIT);
  }

  private static AnnotationNode getAnnotationNode(MethodNode source, Type type) {
    if (source.visibleAnnotations != null) {
      for (AnnotationNode annotationNode : source.visibleAnnotations) {
        Type annotationType = Type.getType(annotationNode.desc);
        if (type.equals(annotationType)) {
          return annotationNode;
        }
      }
    }

    return null;
  }

  static boolean hasAnnotation(MethodNode source, Type type) {
    return getAnnotationNode(source, type) != null;
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
        new MethodVisitor(AsmApi.VERSION, target) {
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
  private static MethodVisitor instrumentWritableReturn(
      MethodVisitor target, MethodNode source, OutputArgument writableReturn, int returnIndex) {
    MethodVisitor result =
        new MethodVisitor(AsmApi.VERSION, target) {
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
   * Transform arguments annotated with {@code @Advice.Local} and {@code @Advice.Enter}.
   *
   * <pre>{@code
   * void foo(@Advice.Local("foo") T1 foo, @Advice.Local("bar") T2 bar)
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
      OutputArgument enterArgument,
      int returnIndex) {
    AtomicReference<GeneratorAdapter> generatorRef = new AtomicReference<>();
    AtomicInteger dataIndex = new AtomicInteger();

    target =
        new MethodVisitor(AsmApi.VERSION, target) {
          @Override
          public AnnotationVisitor visitParameterAnnotation(
              int parameter, String descriptor, boolean visible) {
            // replace @Advice.Local with @Advice.Unused
            if (Type.getDescriptor(Advice.Local.class).equals(descriptor)) {
              descriptor = Type.getDescriptor(Advice.Unused.class);
            }
            // replace @Advice.Enter with @Advice.Unused
            if (enterArgument != null
                && enterArgument.adviceIndex == parameter
                && Type.getDescriptor(Advice.Enter.class).equals(descriptor)) {
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
                // pop return value of Map.put
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
              // we have changed the type fo method arguments annotated with @Advice.Local to Object
              // here we'll load the argument, cast it to its actual type, and store it back
              for (AdviceLocal adviceLocal : adviceLocals) {
                ga.loadArg(adviceLocal.adviceIndex);
                ga.checkCast(argumentTypes[adviceLocal.adviceIndex]);
                ga.storeArg(adviceLocal.adviceIndex);
              }
              return;
            }

            // the index of last argument where object array returned from enter advice is inserted
            // (argumentTypes array does not contain the object array)
            int lastArgumentIndex = argumentTypes.length;
            AnnotationVisitor av =
                mv.visitParameterAnnotation(
                    lastArgumentIndex, Type.getDescriptor(Advice.Enter.class), true);
            av.visitEnd();

            // load object array
            ga.loadLocal(dataIndex.get(), OBJECT_ARRAY_TYPE);
            if (enterArgument != null) {
              // value for @Advice.Enter is stored as the first element
              ga.dup();
              ga.push(0);
              Type type = argumentTypes[enterArgument.adviceIndex];
              ga.arrayLoad(type);
              ga.checkCast(type);
              ga.storeArg(enterArgument.adviceIndex);
            }

            // object array on stack
            ga.dup();
            Type mapType = Type.getType(Map.class);
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

  private static void instrument(
      TransformationContext context, MethodNode methodNode, ClassVisitor classVisitor) {
    String originalDescriptor = methodNode.desc;
    String[] exceptionsArray = methodNode.exceptions.toArray(new String[0]);

    List<OutputArgument> writableArguments = getWritableArguments(methodNode);
    OutputArgument writableReturn = getWritableReturnValue(methodNode);
    OutputArgument enterArgument = getEnterArgument(methodNode);
    List<AdviceLocal> adviceLocals = getLocals(methodNode);
    boolean isEnterAdvice = isEnterAdvice(methodNode);
    boolean isExitAdvice = isExitAdvice(methodNode);
    Type returnType = Type.getReturnType(methodNode.desc);

    // currently we don't support rewriting enter advice returning a primitive type
    if (isEnterAdvice
        && !(returnType.getSort() == Type.VOID
            || returnType.getSort() == Type.OBJECT
            || returnType.getSort() == Type.ARRAY)) {
      context.disableReturnTypeChange();
    }
    // context is shared by enter and exit advice, if entry advice was rejected don't attempt to
    // rewrite usages of @Advice.Enter in the exit advice
    if (!context.canChangeReturnType()) {
      enterArgument = null;
    }

    if (context.canChangeReturnType() || (isExitAdvice && Type.VOID_TYPE.equals(returnType))) {
      if (!writableArguments.isEmpty()
          || writableReturn != null
          || !Type.VOID_TYPE.equals(returnType)
          || (!adviceLocals.isEmpty() && isEnterAdvice)) {
        Type[] argumentTypes = Type.getArgumentTypes(methodNode.desc);
        if (!adviceLocals.isEmpty() && isEnterAdvice) {
          // Set type of arguments annotated with @Advice.Local to Object. These arguments are
          // likely to be helper classes which currently breaks because the invokedynamic call in
          // advised class needs access to the parameter types of the advice method.
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
                context,
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
      if ((!adviceLocals.isEmpty() || enterArgument != null) && isExitAdvice) {
        // Set type of arguments annotated with @Advice.Local to Object. These arguments are likely
        // to be helper classes which currently breaks because the invokedynamic call in advised
        // class needs access to the parameter types of the advice method.
        Type[] newArgumentTypes = Type.getArgumentTypes(methodNode.desc);
        for (AdviceLocal adviceLocal : adviceLocals) {
          newArgumentTypes[adviceLocal.adviceIndex] = OBJECT_TYPE;
        }
        if (enterArgument != null) {
          newArgumentTypes[enterArgument.adviceIndex] = OBJECT_TYPE;
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
            instrumentAdviceLocals(
                false, tmp, methodNode, originalDescriptor, adviceLocals, enterArgument, -1);
        methodNode.accept(mv);

        methodNode = tmp;
      }
    }

    applyAdviceDelegation(context, methodNode, classVisitor, exceptionsArray);
  }

  private static void applyAdviceDelegation(
      TransformationContext context,
      MethodNode methodNode,
      ClassVisitor classVisitor,
      String[] exceptionsArray) {
    MethodVisitor mv =
        classVisitor.visitMethod(
            methodNode.access,
            methodNode.name,
            methodNode.desc,
            methodNode.signature,
            exceptionsArray);
    mv = delegateAdvice(context, mv);

    methodNode.accept(mv);
  }

  private static MethodVisitor instrumentOurParameters(
      TransformationContext context,
      MethodVisitor target,
      MethodNode source,
      String originalDesc,
      List<OutputArgument> writableArguments,
      OutputArgument writableReturn,
      List<AdviceLocal> adviceLocals) {

    // position 0 in enter advice is reserved for the return value of the method
    // to avoid complicating things further we aren't going to figure out whether it is really used
    int returnArraySize = isEnterAdvice(source) ? 1 : 0;
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
          instrumentAdviceLocals(
              true, target, source, originalDesc, adviceLocals, null, returnArraySize);
      returnArraySize++;
    }
    target = addReturnArray(context, target, returnArraySize);

    return target;
  }

  /** Return the value of the {@code readOnly} attribute of the annotation. */
  private static boolean isWriteable(AnnotationNode annotationNode) {
    Object value = getAttributeValue(annotationNode, "readOnly");
    return Boolean.FALSE.equals(value);
  }

  private static Object getAttributeValue(AnnotationNode annotationNode, String attributeName) {
    if (annotationNode.values != null && !annotationNode.values.isEmpty()) {
      List<Object> values = annotationNode.values;
      for (int i = 0; i < values.size(); i += 2) {
        String name = (String) values.get(i);
        Object value = values.get(i + 1);
        if (attributeName.equals(name)) {
          return value;
        }
      }
    }

    return null;
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

  private static MethodVisitor addReturnArray(
      TransformationContext context, MethodVisitor target, int returnArraySize) {
    return new MethodVisitor(AsmApi.VERSION, target) {
      @Override
      public void visitInsn(int opcode) {
        if (Opcodes.RETURN == opcode) {
          // change the return value of the method to Object[]
          GeneratorAdapter ga = new GeneratorAdapter(mv, 0, null, "()V");
          ga.push(returnArraySize);
          ga.newArray(OBJECT_TYPE);
          opcode = Opcodes.ARETURN;
        } else if (context.canChangeReturnType() && Opcodes.ARETURN == opcode) {
          // change the return value of the method to Object[] that on the 0 index contains the
          // original return value

          // stack: original return value
          GeneratorAdapter ga = new GeneratorAdapter(mv, 0, null, "()V");
          ga.push(returnArraySize);
          ga.newArray(OBJECT_TYPE);
          // stack: original return value, array
          ga.dupX1();
          // stack: array, original return value, array
          ga.swap();
          // stack: array, array, original return value
          ga.push(0); // original return value is stored as the first element
          ga.swap();
          ga.arrayStore(OBJECT_TYPE);
          // stack: array
        }
        super.visitInsn(opcode);
      }
    };
  }

  /**
   * If method is annotated with {@link Advice.OnMethodEnter} or {@link Advice.OnMethodExit} set
   * {@code inline} attribute on the annotation to {@code false}. If method is annotated with {@link
   * Advice.OnMethodEnter} and has {@code skipOn} attribute set {@code skipOnIndex} attribute on the
   * annotation to {@code 0}.
   */
  private static MethodVisitor delegateAdvice(TransformationContext context, MethodVisitor target) {
    return new MethodVisitor(AsmApi.VERSION, target) {
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
          boolean hasSkipOn = false;

          @Override
          public void visit(String name, Object value) {
            if ("inline".equals(name)) {
              value = false;
              hasInline = true;
            } else if ("skipOn".equals(name) && value != void.class) {
              hasSkipOn = true;
            }
            super.visit(name, value);
          }

          @Override
          public void visitEnd() {
            if (!hasInline) {
              visit("inline", false);
            }
            if (context.canChangeReturnType() && hasSkipOn) {
              visit("skipOnIndex", 0);
            }
            super.visitEnd();
          }
        };
      }
    };
  }

  /** If annotation has {@code readOnly} attribute set it to {@code true}. */
  private static MethodVisitor makeReadOnly(Class<?> annotationType, MethodVisitor target) {
    return new MethodVisitor(AsmApi.VERSION, target) {

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
              value = true;
            }
            super.visit(name, value);
          }
        };
      }
    };
  }

  private static class TransformationContext {
    private boolean canChangeReturnType = true;

    void disableReturnTypeChange() {
      canChangeReturnType = false;
    }

    boolean canChangeReturnType() {
      return canChangeReturnType;
    }
  }

  private AdviceTransformer() {}
}
