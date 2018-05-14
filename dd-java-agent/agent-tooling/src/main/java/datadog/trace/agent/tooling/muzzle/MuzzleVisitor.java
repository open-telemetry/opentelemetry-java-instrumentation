package datadog.trace.agent.tooling.muzzle;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.bytebuddy.asm.AsmVisitorWrapper;
import net.bytebuddy.description.field.FieldDescription;
import net.bytebuddy.description.field.FieldList;
import net.bytebuddy.description.method.MethodList;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.Implementation;
import net.bytebuddy.jar.asm.*;
import net.bytebuddy.pool.TypePool;

/**
 * Visit a class and add: 1) a private instrumenationMuzzle field and getter AND 2) logic to the end
 * of the instrumentation transformer to assert classpath is safe to apply instrumentation to.
 */
public class MuzzleVisitor implements AsmVisitorWrapper {
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
    return new InsertSafetyMatcher(classVisitor);
  }

  public static class InsertSafetyMatcher extends ClassVisitor {
    private String instrumentationClassName;
    private Set<String> adviceClassNames = new HashSet<>();

    public InsertSafetyMatcher(ClassVisitor classVisitor) {
      super(Opcodes.ASM6, classVisitor);
    }

    @Override
    public void visit(
        final int version,
        final int access,
        final String name,
        final String signature,
        final String superName,
        final String[] interfaces) {
      this.instrumentationClassName = name;
      super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public MethodVisitor visitMethod(
        final int access,
        final String name,
        final String descriptor,
        final String signature,
        final String[] exceptions) {
      MethodVisitor methodVisitor =
          super.visitMethod(access, name, descriptor, signature, exceptions);
      if ("<init>".equals(name)) {
        methodVisitor = new InitializeFieldVisitor(methodVisitor);
      }
      return new InsertMuzzleTransformer(methodVisitor);
    }

    public Reference[] generateReferences() {
      // track sources we've generated references from to avoid recursion
      final Set<String> referenceSources = new HashSet<>();
      final Map<String, Reference> references = new HashMap<>();

      for (String adviceClass : adviceClassNames) {
        if (!referenceSources.contains(adviceClass)) {
          referenceSources.add(adviceClass);
          for (Map.Entry<String, Reference> entry :
              AdviceReferenceVisitor.createReferencesFrom(
                      adviceClass, ReferenceMatcher.class.getClassLoader())
                  .entrySet()) {
            if (references.containsKey(entry.getKey())) {
              references.put(
                  entry.getKey(), references.get(entry.getKey()).merge(entry.getValue()));
            } else {
              references.put(entry.getKey(), entry.getValue());
            }
          }
        }
      }
      return references.values().toArray(new Reference[0]);
    }

    @Override
    public void visitEnd() {
      { // generate getInstrumentationMuzzle method
        /*
         * private synchronized ReferenceMatcher getInstrumentationMuzzle() {
         *   if (null == this.instrumentationMuzzle) {
         *     this.instrumentationMuzzle = new ReferenceMatcher(new Reference[]{
         *                                                                        //reference builders
         *                                                                       });
         *   }
         *   return this.instrumentationMuzzle;
         * }
         */
        final MethodVisitor mv =
            visitMethod(
                Opcodes.ACC_PRIVATE + Opcodes.ACC_SYNCHRONIZED,
                "getInstrumentationMuzzle",
                "()Ldatadog/trace/agent/tooling/muzzle/ReferenceMatcher;",
                null,
                null);

        mv.visitCode();
        final Label start = new Label();
        final Label ret = new Label();
        final Label finish = new Label();

        mv.visitLabel(start);
        mv.visitInsn(Opcodes.ACONST_NULL);
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitFieldInsn(
            Opcodes.GETFIELD,
            instrumentationClassName,
            "instrumentationMuzzle",
            "Ldatadog/trace/agent/tooling/muzzle/ReferenceMatcher;");
        mv.visitJumpInsn(Opcodes.IF_ACMPNE, ret);

        final Reference[] references = generateReferences();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitTypeInsn(Opcodes.NEW, "datadog/trace/agent/tooling/muzzle/ReferenceMatcher");
        mv.visitInsn(Opcodes.DUP);
        mv.visitIntInsn(Opcodes.BIPUSH, references.length);
        mv.visitTypeInsn(Opcodes.ANEWARRAY, "datadog/trace/agent/tooling/muzzle/Reference");

        for (int i = 0; i < references.length; ++i) {
          mv.visitInsn(Opcodes.DUP);
          mv.visitIntInsn(Opcodes.BIPUSH, i);
          mv.visitTypeInsn(Opcodes.NEW, "datadog/trace/agent/tooling/muzzle/Reference$Builder");
          mv.visitInsn(Opcodes.DUP);
          mv.visitLdcInsn(references[i].getClassName());
          mv.visitMethodInsn(
              Opcodes.INVOKESPECIAL,
              "datadog/trace/agent/tooling/muzzle/Reference$Builder",
              "<init>",
              "(Ljava/lang/String;)V",
              false);
          if (null != references[i].getSuperName()) {
            mv.visitLdcInsn(references[i].getSuperName());
            mv.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                "datadog/trace/agent/tooling/muzzle/Reference$Builder",
                "withSuperName",
                "(Ljava/lang/String;)Ldatadog/trace/agent/tooling/muzzle/Reference$Builder;",
                false);
          }
          for (String interfaceName : references[i].getInterfaces()) {
            mv.visitLdcInsn(interfaceName);
            mv.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                "datadog/trace/agent/tooling/muzzle/Reference$Builder",
                "withInterface",
                "(Ljava/lang/String;)Ldatadog/trace/agent/tooling/muzzle/Reference$Builder;",
                false);
          }
          for (Reference.Source source : references[i].getSources()) {
            mv.visitLdcInsn(source.getName());
            mv.visitIntInsn(Opcodes.BIPUSH, source.getLine());
            mv.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                "datadog/trace/agent/tooling/muzzle/Reference$Builder",
                "withSource",
                "(Ljava/lang/String;I)Ldatadog/trace/agent/tooling/muzzle/Reference$Builder;",
                false);
          }
          mv.visitMethodInsn(
              Opcodes.INVOKEVIRTUAL,
              "datadog/trace/agent/tooling/muzzle/Reference$Builder",
              "build",
              "()Ldatadog/trace/agent/tooling/muzzle/Reference;",
              false);
          mv.visitInsn(Opcodes.AASTORE);
        }

        mv.visitMethodInsn(
            Opcodes.INVOKESPECIAL,
            "datadog/trace/agent/tooling/muzzle/ReferenceMatcher",
            "<init>",
            "([Ldatadog/trace/agent/tooling/muzzle/Reference;)V",
            false);
        mv.visitFieldInsn(
            Opcodes.PUTFIELD,
            instrumentationClassName,
            "instrumentationMuzzle",
            "Ldatadog/trace/agent/tooling/muzzle/ReferenceMatcher;");

        mv.visitLabel(ret);
        mv.visitFrame(Opcodes.F_SAME, 1, null, 0, null);
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitFieldInsn(
            Opcodes.GETFIELD,
            instrumentationClassName,
            "instrumentationMuzzle",
            "Ldatadog/trace/agent/tooling/muzzle/ReferenceMatcher;");
        mv.visitInsn(Opcodes.ARETURN);
        mv.visitLabel(finish);

        mv.visitLocalVariable("this", "L" + instrumentationClassName + ";", null, start, finish, 0);
        mv.visitMaxs(0, 0); // recomputed
        mv.visitEnd();
      }

      super.visitField(
          Opcodes.ACC_PRIVATE + Opcodes.ACC_VOLATILE,
          "instrumentationMuzzle",
          Type.getDescriptor(ReferenceMatcher.class),
          null,
          null);
      super.visitEnd();
    }

    /**
     * Changes this:<br>
     * &nbsp.transform(DDAdvice.create().advice(named("fooMethod", FooAdvice.class.getname())))
     * &nbsp.asDecorator(); Into this:<br>
     * &nbsp.transform(DDAdvice.create().advice(named("fooMethod", FooAdvice.class.getname())))
     * &nbsp.transform(this.instrumentationMuzzle.assertSafeTransformation("foo.package.FooAdvice"));
     * &nbsp.asDecorator(); className)
     */
    public class InsertMuzzleTransformer extends MethodVisitor {
      // it would be nice to manage the state with an enum, but that requires this class to be non-static
      private final int INIT = 0;
      // SomeClass
      private final int PREVIOUS_INSTRUCTION_LDC = 1;
      // SomeClass.getName()
      private final int PREVIOUS_INSTRUCTION_GET_CLASS_NAME = 2;

      private String lastClassLDC = null;

      private Collection<String> adviceClassNames = new HashSet<>();
      private int STATE = INIT;

      public InsertMuzzleTransformer(MethodVisitor methodVisitor) {
        super(Opcodes.ASM6, methodVisitor);
      }

      public void reset() {
        STATE = INIT;
        lastClassLDC = null;
        adviceClassNames.clear();
      }

      @Override
      public void visitMethodInsn(
          final int opcode,
          final String owner,
          final String name,
          final String descriptor,
          final boolean isInterface) {
        if (name.equals("getName")) {
          if (STATE == PREVIOUS_INSTRUCTION_LDC) {
            STATE = PREVIOUS_INSTRUCTION_GET_CLASS_NAME;
          }
        } else if (name.equals("advice")) {
          if (STATE == PREVIOUS_INSTRUCTION_GET_CLASS_NAME) {
            adviceClassNames.add(lastClassLDC);
            InsertSafetyMatcher.this.adviceClassNames.add(lastClassLDC);
          }
          // add last LDC/ToString to adivce list
        } else if (name.equals("asDecorator")) {
          this.visitVarInsn(Opcodes.ALOAD, 0);
          mv.visitMethodInsn(
              Opcodes.INVOKESPECIAL,
              instrumentationClassName,
              "getInstrumentationMuzzle",
              "()Ldatadog/trace/agent/tooling/muzzle/ReferenceMatcher;",
              false);
          mv.visitIntInsn(Opcodes.BIPUSH, adviceClassNames.size());
          mv.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/String");
          int i = 0;
          for (String adviceClassName : adviceClassNames) {
            mv.visitInsn(Opcodes.DUP);
            mv.visitIntInsn(Opcodes.BIPUSH, i);
            mv.visitLdcInsn(adviceClassName);
            mv.visitInsn(Opcodes.AASTORE);
            ++i;
          }
          mv.visitMethodInsn(
              Opcodes.INVOKEVIRTUAL,
              "datadog/trace/agent/tooling/muzzle/ReferenceMatcher",
              "assertSafeTransformation",
              "([Ljava/lang/String;)Lnet/bytebuddy/agent/builder/AgentBuilder$Transformer;",
              false);
          mv.visitMethodInsn(
              Opcodes.INVOKEINTERFACE,
              "net/bytebuddy/agent/builder/AgentBuilder$Identified$Narrowable",
              "transform",
              "(Lnet/bytebuddy/agent/builder/AgentBuilder$Transformer;)Lnet/bytebuddy/agent/builder/AgentBuilder$Identified$Extendable;",
              true);
          reset();
        } else {
          STATE = INIT;
          lastClassLDC = null;
        }
        super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
      }

      @Override
      public void visitLdcInsn(final Object value) {
        if (value instanceof Type) {
          Type type = (Type) value;
          if (type.getSort() == Type.OBJECT) {
            lastClassLDC = type.getClassName();
            STATE = PREVIOUS_INSTRUCTION_LDC;
            type.getClassName();
          }
        }
        super.visitLdcInsn(value);
      }
    }

    /** Append a field initializer to the end of a method. */
    public class InitializeFieldVisitor extends MethodVisitor {
      public InitializeFieldVisitor(MethodVisitor methodVisitor) {
        super(Opcodes.ASM6, methodVisitor);
      }

      @Override
      public void visitInsn(final int opcode) {
        if (opcode == Opcodes.RETURN) {
          super.visitVarInsn(Opcodes.ALOAD, 0);
          super.visitInsn(Opcodes.ACONST_NULL);
          super.visitFieldInsn(
              Opcodes.PUTFIELD,
              instrumentationClassName,
              "instrumentationMuzzle",
              Type.getDescriptor(ReferenceMatcher.class));
        }
        super.visitInsn(opcode);
      }
    }
  }
}
