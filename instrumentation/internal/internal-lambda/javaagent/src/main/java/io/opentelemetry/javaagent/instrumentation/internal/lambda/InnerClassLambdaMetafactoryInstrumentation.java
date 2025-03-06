/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.internal.lambda;

import static net.bytebuddy.matcher.ElementMatchers.named;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.javaagent.bootstrap.DefineClassHelper;
import io.opentelemetry.javaagent.bootstrap.DefineClassHelper.Handler.DefineClassContext;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.extension.instrumentation.internal.AsmApi;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.AsmVisitorWrapper;
import net.bytebuddy.description.field.FieldDescription;
import net.bytebuddy.description.field.FieldList;
import net.bytebuddy.description.method.MethodList;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.Implementation;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.pool.TypePool;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

public class InnerClassLambdaMetafactoryInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("java.lang.invoke.InnerClassLambdaMetafactory");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyTransformer(
        (builder, typeDescription, classLoader, javaModule, protectionDomain) ->
            builder.visit(
                new AsmVisitorWrapper() {
                  @Override
                  public int mergeWriter(int flags) {
                    return flags | ClassWriter.COMPUTE_MAXS;
                  }

                  @Override
                  @CanIgnoreReturnValue
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
                    return new MetaFactoryClassVisitor(
                        classVisitor, instrumentedType.getInternalName());
                  }
                }));

    transformer.applyAdviceToMethod(
        named("spinInnerClass"),
        InnerClassLambdaMetafactoryInstrumentation.class.getName()
            + (hasInterfaceClassField() ? "$LambdaJdk17Advice" : "$LambdaAdvice"));
  }

  @SuppressWarnings("ReturnValueIgnored")
  private static boolean hasInterfaceClassField() {
    try {
      Class<?> clazz = Class.forName("java.lang.invoke.AbstractValidatingLambdaMetafactory");
      clazz.getDeclaredField("interfaceClass");
      return true;
    } catch (NoSuchFieldException exception) {
      return false;
    } catch (ClassNotFoundException exception) {
      throw new IllegalStateException(exception);
    }
  }

  private static class MetaFactoryClassVisitor extends ClassVisitor {
    private final String slashClassName;

    MetaFactoryClassVisitor(ClassVisitor cv, String slashClassName) {
      super(AsmApi.VERSION, cv);
      this.slashClassName = slashClassName;
    }

    @Override
    public MethodVisitor visitMethod(
        int access, String name, String descriptor, String signature, String[] exceptions) {
      MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
      // The version of InnerClassLambdaMetafactory used in first version of jdk8 can be seen at
      // https://hg.openjdk.java.net/jdk8/jdk8/jdk/file/687fd7c7986d/src/share/classes/java/lang/invoke/InnerClassLambdaMetafactory.java
      // Depending on jdk version we instrument either spinInnerClass or generateInnerClass.
      // We look for a call to ASM ClassWriter.toByteArray() and insert our lambda class
      // transformation after it so that defining lambda class will proceed with replaced bytecode.
      // This transformation uses ASM instead of Byte-Buddy advice because advice allows adding
      // code to the start and end of the method, but here we are modifying a call in the middle of
      // the method.
      if (("spinInnerClass".equals(name) || "generateInnerClass".equals(name))
          && "()Ljava/lang/Class;".equals(descriptor)) {
        mv =
            new MethodVisitor(api, mv) {
              @Override
              public void visitMethodInsn(
                  int opcode, String owner, String name, String descriptor, boolean isInterface) {
                super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                // if current instruction is a call to ASM ClassWriter.toByteArray() insert call to
                // our lambda transformer
                if ((opcode == Opcodes.INVOKEVIRTUAL
                        && "toByteArray".equals(name)
                        && "()[B".equals(descriptor))
                    // jdk 24
                    || (opcode == Opcodes.INVOKEINTERFACE
                        && "build".equals(name)
                        && descriptor.endsWith(")[B"))) {
                  mv.visitVarInsn(Opcodes.ALOAD, 0);
                  mv.visitFieldInsn(
                      Opcodes.GETFIELD, slashClassName, "lambdaClassName", "Ljava/lang/String;");
                  mv.visitVarInsn(Opcodes.ALOAD, 0);
                  // targetClass is used to get the ClassLoader where lambda class will be defined
                  mv.visitFieldInsn(
                      Opcodes.GETFIELD, slashClassName, "targetClass", "Ljava/lang/Class;");
                  mv.visitMethodInsn(
                      Opcodes.INVOKESTATIC,
                      Type.getInternalName(LambdaTransformerHelper.class),
                      "transform",
                      "([BLjava/lang/String;Ljava/lang/Class;)[B",
                      false);
                }
              }
            };
      }
      return mv;
    }
  }

  @SuppressWarnings("unused")
  public static class LambdaAdvice {

    @Advice.OnMethodEnter
    public static DefineClassContext onEnter(
        @Advice.FieldValue("samBase") Class<?> lambdaInterface) {
      return DefineClassHelper.beforeDefineLambdaClass(lambdaInterface);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class)
    public static void onExit(@Advice.Enter DefineClassContext context) {
      DefineClassHelper.afterDefineClass(context);
    }
  }

  @SuppressWarnings("unused")
  public static class LambdaJdk17Advice {

    @Advice.OnMethodEnter
    public static DefineClassContext onEnter(
        @Advice.FieldValue("interfaceClass") Class<?> lambdaInterface) {
      return DefineClassHelper.beforeDefineLambdaClass(lambdaInterface);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class)
    public static void onExit(@Advice.Enter DefineClassContext context) {
      DefineClassHelper.afterDefineClass(context);
    }
  }
}
