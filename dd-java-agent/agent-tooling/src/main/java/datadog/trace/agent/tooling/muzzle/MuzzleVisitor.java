package datadog.trace.agent.tooling.muzzle;

import java.util.Collection;
import java.util.HashSet;
import net.bytebuddy.asm.AsmVisitorWrapper;
import net.bytebuddy.description.field.FieldDescription;
import net.bytebuddy.description.field.FieldList;
import net.bytebuddy.description.method.MethodList;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.Implementation;
import net.bytebuddy.jar.asm.*;
import net.bytebuddy.pool.TypePool;

/**
 * Add a instrumentationMuzzle field and for each decorator add a final transformer to assert the
 * classpath is safe instrument.
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
    public InsertSafetyMatcher(ClassVisitor classVisitor) {
      super(Opcodes.ASM6, classVisitor);
    }

    public String instrumentationClassName;

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

    @Override
    public void visitEnd() {
      super.visitField(
          Opcodes.ACC_PUBLIC,
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
          }
          // add last LDC/ToString to adivce list
        } else if (name.equals("asDecorator")) {
          this.visitVarInsn(Opcodes.ALOAD, 0);
          this.visitFieldInsn(
              Opcodes.GETFIELD,
              instrumentationClassName,
              "instrumentationMuzzle",
              Type.getDescriptor(ReferenceMatcher.class));
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
          mv.visitTypeInsn(Opcodes.NEW, "datadog/trace/agent/tooling/muzzle/ReferenceMatcher");
          mv.visitInsn(Opcodes.DUP);
          mv.visitMethodInsn(
              Opcodes.INVOKESPECIAL,
              "datadog/trace/agent/tooling/muzzle/ReferenceMatcher",
              "<init>",
              "()V",
              false);
          super.visitFieldInsn(
              Opcodes.PUTFIELD,
              instrumentationClassName.replace('.', '/'),
              "instrumentationMuzzle",
              Type.getDescriptor(ReferenceMatcher.class));
        }
        super.visitInsn(opcode);
      }
    }
  }
}
