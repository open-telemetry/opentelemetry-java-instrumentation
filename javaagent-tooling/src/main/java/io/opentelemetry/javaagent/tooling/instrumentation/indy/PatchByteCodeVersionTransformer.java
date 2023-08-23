/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.instrumentation.indy;

import static org.objectweb.asm.ClassWriter.COMPUTE_FRAMES;

import java.security.ProtectionDomain;
import net.bytebuddy.ClassFileVersion;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
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
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.JSRInlinerAdapter;

/**
 * Patches the class file version to 51 (Java 7) in order to support injecting {@code INVOKEDYNAMIC}
 * instructions via {@link Advice.WithCustomMapping#bootstrap} which is important for indy plugins.
 */
public class PatchByteCodeVersionTransformer implements AgentBuilder.Transformer {

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

    if (isAtLeastJava7(typeDescription)) {
      // we can avoid the expensive stack frame re-computation if stack frames are already present
      // in the bytecode.
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

            return new ClassVisitor(Opcodes.ASM7, classVisitor) {

              @Override
              public void visit(
                  int version,
                  int access,
                  String name,
                  String signature,
                  String superName,
                  String[] interfaces) {

                super.visit(Opcodes.V1_7, access, name, signature, superName, interfaces);
              }

              @Override
              public MethodVisitor visitMethod(
                  int access,
                  String name,
                  String descriptor,
                  String signature,
                  String[] exceptions) {

                MethodVisitor methodVisitor =
                    super.visitMethod(access, name, descriptor, signature, exceptions);
                return new JSRInlinerAdapter(
                    methodVisitor, access, name, descriptor, signature, exceptions);
              }
            };
          }

          @Override
          public int mergeWriter(int flags) {
            // class files with version < Java 7 don't require a stack frame map
            // as we're patching the version to at least 7, we have to compute the frames
            return flags | COMPUTE_FRAMES;
          }
        });
  }
}
