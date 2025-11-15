/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.instrumentation.indy;

import io.opentelemetry.javaagent.bootstrap.IndyBootstrapDispatcher;
import io.opentelemetry.javaagent.bootstrap.advice.AdviceForwardLookupSupplier;
import io.opentelemetry.javaagent.extension.instrumentation.internal.AsmApi;
import io.opentelemetry.javaagent.tooling.HelperInjector;
import java.security.ProtectionDomain;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import net.bytebuddy.ClassFileVersion;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.AsmVisitorWrapper;
import net.bytebuddy.description.field.FieldDescription;
import net.bytebuddy.description.field.FieldList;
import net.bytebuddy.description.method.MethodList;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.implementation.Implementation;
import net.bytebuddy.pool.TypePool;
import net.bytebuddy.utility.JavaModule;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;

/**
 * Replaces {@code INVOKEDYNAMIC} instructions used for invoking advice with {@code INVOKESTATIC}
 * instructions in a helper class that contains the original {@code INVOKEDYNAMIC} instruction in
 * classes that do not support {@code INVOKEDYNAMIC} (i.e. pre Java 7 class files).
 */
public class ForwardIndyAdviceTransformer implements AgentBuilder.Transformer {
  private static final AtomicInteger counter = new AtomicInteger();
  private static final String bootForwardClassPackage =
      AdviceForwardLookupSupplier.class.getPackage().getName();

  private final HelperInjector helperInjector;

  public ForwardIndyAdviceTransformer(HelperInjector helperInjector) {
    this.helperInjector = helperInjector;
  }

  private static boolean isAtLeastJava7(TypeDescription typeDescription) {
    ClassFileVersion classFileVersion = typeDescription.getClassFileVersion();
    return classFileVersion != null && classFileVersion.getJavaVersion() >= 7;
  }

  @Override
  public DynamicType.Builder<?> transform(
      DynamicType.Builder<?> builder,
      TypeDescription typeDescription,
      ClassLoader classLoader,
      JavaModule javaModule,
      ProtectionDomain protectionDomain) {

    // java 7+ class files already support invokedynamic
    if (isAtLeastJava7(typeDescription)) {
      return builder;
    }

    return builder.visit(
        new AsmVisitorWrapper.AbstractBase() {
          @Override
          public ClassVisitor wrap(
              TypeDescription typeDescription,
              ClassVisitor classVisitor,
              Implementation.Context context,
              TypePool typePool,
              FieldList<FieldDescription.InDefinedShape> fieldList,
              MethodList<?> methodList,
              int writerFlags,
              int readerFlags) {

            return new ClassVisitor(AsmApi.VERSION, classVisitor) {
              final Map<String, Supplier<byte[]>> injectedClasses = new HashMap<>();

              @Override
              public void visitEnd() {
                super.visitEnd();

                // inject helper classes that forward to the advice using invokedynamic
                if (!injectedClasses.isEmpty()) {
                  helperInjector.injectHelperClasses(classLoader, injectedClasses);
                }
              }

              @Override
              public MethodVisitor visitMethod(
                  int access,
                  String name,
                  String descriptor,
                  String signature,
                  String[] exceptions) {
                MethodVisitor mv =
                    super.visitMethod(access, name, descriptor, signature, exceptions);
                return new MethodVisitor(api, mv) {
                  @Override
                  public void visitInvokeDynamicInsn(
                      String name,
                      String descriptor,
                      Handle bootstrapMethodHandle,
                      Object... bootstrapMethodArguments) {
                    if (Type.getInternalName(IndyBootstrapDispatcher.class)
                        .equals(bootstrapMethodHandle.getOwner())) {

                      String adviceClassName = (String) bootstrapMethodArguments[3];
                      String forwardClassDotName =
                          classLoader == null
                              ? bootForwardClassPackage + ".Forward$$" + counter.incrementAndGet()
                              : adviceClassName + "$$Forward$$" + counter.incrementAndGet();
                      String forwardClassSlasName = forwardClassDotName.replace('.', '/');

                      Supplier<byte[]> forwardClassBytes =
                          generateForwardClass(
                              forwardClassSlasName,
                              name,
                              descriptor,
                              bootstrapMethodHandle,
                              bootstrapMethodArguments);
                      injectedClasses.put(forwardClassDotName, forwardClassBytes);

                      // replace invokedynamic with invokestatic to the generated forwarder class
                      // the forwarder class will contain the original invokedynamic instruction
                      super.visitMethodInsn(
                          Opcodes.INVOKESTATIC, forwardClassSlasName, name, descriptor, false);
                      return;
                    }

                    super.visitInvokeDynamicInsn(
                        name, descriptor, bootstrapMethodHandle, bootstrapMethodArguments);
                  }
                };
              }
            };
          }
        });
  }

  private static Supplier<byte[]> generateForwardClass(
      String forwardClassSlasName,
      String methodName,
      String methodDescriptor,
      Handle bootstrapMethodHandle,
      Object[] bootstrapMethodArguments) {
    return () -> {
      ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
      cw.visit(
          Opcodes.V1_8,
          Opcodes.ACC_PUBLIC,
          forwardClassSlasName,
          null,
          Type.getInternalName(Object.class),
          null);
      MethodVisitor mv =
          cw.visitMethod(
              Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, methodName, methodDescriptor, null, null);
      GeneratorAdapter ga =
          new GeneratorAdapter(
              mv, Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, methodName, methodDescriptor);
      ga.loadArgs();
      mv.visitInvokeDynamicInsn(
          methodName, methodDescriptor, bootstrapMethodHandle, bootstrapMethodArguments);
      ga.returnValue();
      mv.visitMaxs(0, 0);
      mv.visitEnd();
      cw.visitEnd();

      return cw.toByteArray();
    };
  }
}
