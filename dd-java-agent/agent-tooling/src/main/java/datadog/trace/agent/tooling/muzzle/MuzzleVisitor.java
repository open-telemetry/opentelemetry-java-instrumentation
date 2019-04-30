package datadog.trace.agent.tooling.muzzle;

import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.agent.tooling.Utils;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.bytebuddy.ClassFileVersion;
import net.bytebuddy.asm.AsmVisitorWrapper;
import net.bytebuddy.description.field.FieldDescription;
import net.bytebuddy.description.field.FieldList;
import net.bytebuddy.description.method.MethodList;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.Implementation;
import net.bytebuddy.jar.asm.ClassVisitor;
import net.bytebuddy.jar.asm.ClassWriter;
import net.bytebuddy.jar.asm.FieldVisitor;
import net.bytebuddy.jar.asm.Label;
import net.bytebuddy.jar.asm.MethodVisitor;
import net.bytebuddy.jar.asm.Opcodes;
import net.bytebuddy.jar.asm.Type;
import net.bytebuddy.pool.TypePool;

/** Visit a class and add: a private instrumenationMuzzle field and getter */
public class MuzzleVisitor implements AsmVisitorWrapper {
  public static final String MUZZLE_FIELD_NAME = "instrumentationMuzzle";
  public static final String MUZZLE_METHOD_NAME = "getInstrumentationMuzzle";

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
    return new InsertSafetyMatcher(
        classVisitor,
        implementationContext.getClassFileVersion().isAtLeast(ClassFileVersion.JAVA_V6));
  }

  public static class InsertSafetyMatcher extends ClassVisitor {

    private final boolean frames;

    private String instrumentationClassName;
    private Instrumenter.Default instrumenter;

    public InsertSafetyMatcher(ClassVisitor classVisitor, boolean frames) {
      super(Opcodes.ASM7, classVisitor);
      this.frames = frames;
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
      try {
        instrumenter =
            (Instrumenter.Default)
                MuzzleVisitor.class
                    .getClassLoader()
                    .loadClass(Utils.getClassName(instrumentationClassName))
                    .getDeclaredConstructor()
                    .newInstance();
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
      super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public FieldVisitor visitField(
        int access, String name, String descriptor, String signature, Object value) {
      if (MUZZLE_FIELD_NAME.equals(name)) {
        // muzzle field has been generated
        // by previous compilation
        // ignore and recompute in visitEnd
        return null;
      }
      return super.visitField(access, name, descriptor, signature, value);
    }

    @Override
    public MethodVisitor visitMethod(
        final int access,
        final String name,
        final String descriptor,
        final String signature,
        final String[] exceptions) {
      if (MUZZLE_METHOD_NAME.equals(name)) {
        // muzzle getter has been generated
        // by previous compilation
        // ignore and recompute in visitEnd
        return null;
      }
      MethodVisitor methodVisitor =
          super.visitMethod(access, name, descriptor, signature, exceptions);
      if ("<init>".equals(name)) {
        methodVisitor = new InitializeFieldVisitor(methodVisitor);
      }
      return methodVisitor;
    }

    public Reference[] generateReferences() {
      // track sources we've generated references from to avoid recursion
      final Set<String> referenceSources = new HashSet<>();
      final Map<String, Reference> references = new HashMap<>();
      final Set<String> adviceClassNames = new HashSet<>();

      for (String adviceClassName : instrumenter.transformers().values()) {
        adviceClassNames.add(adviceClassName);
      }

      for (String adviceClass : adviceClassNames) {
        if (!referenceSources.contains(adviceClass)) {
          referenceSources.add(adviceClass);
          for (Map.Entry<String, Reference> entry :
              ReferenceCreator.createReferencesFrom(
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
         * protected synchronized ReferenceMatcher getInstrumentationMuzzle() {
         *   if (null == this.instrumentationMuzzle) {
         *     this.instrumentationMuzzle = new ReferenceMatcher(this.helperClassNames(),
         *                                                       new Reference[]{
         *                                                                       //reference builders
         *                                                                       });
         *   }
         *   return this.instrumentationMuzzle;
         * }
         */
        try {
          final MethodVisitor mv =
              super.visitMethod(
                  Opcodes.ACC_PROTECTED + Opcodes.ACC_SYNCHRONIZED,
                  MUZZLE_METHOD_NAME,
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
              MUZZLE_FIELD_NAME,
              "Ldatadog/trace/agent/tooling/muzzle/ReferenceMatcher;");
          mv.visitJumpInsn(Opcodes.IF_ACMPNE, ret);

          mv.visitVarInsn(Opcodes.ALOAD, 0);

          mv.visitTypeInsn(Opcodes.NEW, "datadog/trace/agent/tooling/muzzle/ReferenceMatcher");
          mv.visitInsn(Opcodes.DUP);

          mv.visitVarInsn(Opcodes.ALOAD, 0);
          mv.visitMethodInsn(
              Opcodes.INVOKEVIRTUAL,
              instrumentationClassName,
              "helperClassNames",
              "()[Ljava/lang/String;",
              false);

          final Reference[] references = generateReferences();
          mv.visitLdcInsn(references.length);
          mv.visitTypeInsn(Opcodes.ANEWARRAY, "datadog/trace/agent/tooling/muzzle/Reference");

          for (int i = 0; i < references.length; ++i) {
            mv.visitInsn(Opcodes.DUP);
            mv.visitLdcInsn(i);
            mv.visitTypeInsn(Opcodes.NEW, "datadog/trace/agent/tooling/muzzle/Reference$Builder");
            mv.visitInsn(Opcodes.DUP);
            mv.visitLdcInsn(references[i].getClassName());
            mv.visitMethodInsn(
                Opcodes.INVOKESPECIAL,
                "datadog/trace/agent/tooling/muzzle/Reference$Builder",
                "<init>",
                "(Ljava/lang/String;)V",
                false);
            for (Reference.Source source : references[i].getSources()) {
              mv.visitLdcInsn(source.getName());
              mv.visitLdcInsn(source.getLine());
              mv.visitMethodInsn(
                  Opcodes.INVOKEVIRTUAL,
                  "datadog/trace/agent/tooling/muzzle/Reference$Builder",
                  "withSource",
                  "(Ljava/lang/String;I)Ldatadog/trace/agent/tooling/muzzle/Reference$Builder;",
                  false);
            }
            for (Reference.Flag flag : references[i].getFlags()) {
              mv.visitFieldInsn(
                  Opcodes.GETSTATIC,
                  "datadog/trace/agent/tooling/muzzle/Reference$Flag",
                  flag.name(),
                  "Ldatadog/trace/agent/tooling/muzzle/Reference$Flag;");
              mv.visitMethodInsn(
                  Opcodes.INVOKEVIRTUAL,
                  "datadog/trace/agent/tooling/muzzle/Reference$Builder",
                  "withFlag",
                  "(Ldatadog/trace/agent/tooling/muzzle/Reference$Flag;)Ldatadog/trace/agent/tooling/muzzle/Reference$Builder;",
                  false);
            }
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
            for (Reference.Field field : references[i].getFields()) {
              { // sources
                mv.visitLdcInsn(field.getSources().size());
                mv.visitTypeInsn(
                    Opcodes.ANEWARRAY, "datadog/trace/agent/tooling/muzzle/Reference$Source");

                int j = 0;
                for (Reference.Source source : field.getSources()) {
                  mv.visitInsn(Opcodes.DUP);
                  mv.visitLdcInsn(j);

                  mv.visitTypeInsn(
                      Opcodes.NEW, "datadog/trace/agent/tooling/muzzle/Reference$Source");
                  mv.visitInsn(Opcodes.DUP);
                  mv.visitLdcInsn(source.getName());
                  mv.visitLdcInsn(source.getLine());
                  mv.visitMethodInsn(
                      Opcodes.INVOKESPECIAL,
                      "datadog/trace/agent/tooling/muzzle/Reference$Source",
                      "<init>",
                      "(Ljava/lang/String;I)V",
                      false);

                  mv.visitInsn(Opcodes.AASTORE);
                  ++j;
                }
              }

              { // flags
                mv.visitLdcInsn(field.getFlags().size());
                mv.visitTypeInsn(
                    Opcodes.ANEWARRAY, "datadog/trace/agent/tooling/muzzle/Reference$Flag");

                int j = 0;
                for (Reference.Flag flag : field.getFlags()) {
                  mv.visitInsn(Opcodes.DUP);
                  mv.visitLdcInsn(j);
                  mv.visitFieldInsn(
                      Opcodes.GETSTATIC,
                      "datadog/trace/agent/tooling/muzzle/Reference$Flag",
                      flag.name(),
                      "Ldatadog/trace/agent/tooling/muzzle/Reference$Flag;");
                  mv.visitInsn(Opcodes.AASTORE);
                  ++j;
                }
              }

              mv.visitLdcInsn(field.getName());

              { // field type
                mv.visitLdcInsn(field.getType().getDescriptor());
                mv.visitMethodInsn(
                    Opcodes.INVOKESTATIC,
                    Type.getInternalName(Type.class),
                    "getType",
                    Type.getMethodDescriptor(Type.class.getMethod("getType", String.class)),
                    false);
              }

              mv.visitMethodInsn(
                  Opcodes.INVOKEVIRTUAL,
                  "datadog/trace/agent/tooling/muzzle/Reference$Builder",
                  "withField",
                  Type.getMethodDescriptor(
                      Reference.Builder.class.getMethod(
                          "withField",
                          Reference.Source[].class,
                          Reference.Flag[].class,
                          String.class,
                          Type.class)),
                  false);
            }
            for (Reference.Method method : references[i].getMethods()) {
              mv.visitLdcInsn(method.getSources().size());
              mv.visitTypeInsn(
                  Opcodes.ANEWARRAY, "datadog/trace/agent/tooling/muzzle/Reference$Source");
              int j = 0;
              for (Reference.Source source : method.getSources()) {
                mv.visitInsn(Opcodes.DUP);
                mv.visitLdcInsn(j);

                mv.visitTypeInsn(
                    Opcodes.NEW, "datadog/trace/agent/tooling/muzzle/Reference$Source");
                mv.visitInsn(Opcodes.DUP);
                mv.visitLdcInsn(source.getName());
                mv.visitLdcInsn(source.getLine());
                mv.visitMethodInsn(
                    Opcodes.INVOKESPECIAL,
                    "datadog/trace/agent/tooling/muzzle/Reference$Source",
                    "<init>",
                    "(Ljava/lang/String;I)V",
                    false);

                mv.visitInsn(Opcodes.AASTORE);
                ++j;
              }

              mv.visitLdcInsn(method.getFlags().size());
              mv.visitTypeInsn(
                  Opcodes.ANEWARRAY, "datadog/trace/agent/tooling/muzzle/Reference$Flag");
              j = 0;
              for (Reference.Flag flag : method.getFlags()) {
                mv.visitInsn(Opcodes.DUP);
                mv.visitLdcInsn(j);
                mv.visitFieldInsn(
                    Opcodes.GETSTATIC,
                    "datadog/trace/agent/tooling/muzzle/Reference$Flag",
                    flag.name(),
                    "Ldatadog/trace/agent/tooling/muzzle/Reference$Flag;");
                mv.visitInsn(Opcodes.AASTORE);
                ++j;
              }

              mv.visitLdcInsn(method.getName());

              { // return type
                mv.visitLdcInsn(method.getReturnType().getDescriptor());
                mv.visitMethodInsn(
                    Opcodes.INVOKESTATIC,
                    Type.getInternalName(Type.class),
                    "getType",
                    Type.getMethodDescriptor(Type.class.getMethod("getType", String.class)),
                    false);
              }

              mv.visitLdcInsn(method.getParameterTypes().size());
              mv.visitTypeInsn(Opcodes.ANEWARRAY, Type.getInternalName(Type.class));
              j = 0;
              for (Type parameterType : method.getParameterTypes()) {
                mv.visitInsn(Opcodes.DUP);
                mv.visitLdcInsn(j);

                mv.visitLdcInsn(parameterType.getDescriptor());
                mv.visitMethodInsn(
                    Opcodes.INVOKESTATIC,
                    Type.getInternalName(Type.class),
                    "getType",
                    Type.getMethodDescriptor(Type.class.getMethod("getType", String.class)),
                    false);

                mv.visitInsn(Opcodes.AASTORE);
                j++;
              }

              mv.visitMethodInsn(
                  Opcodes.INVOKEVIRTUAL,
                  "datadog/trace/agent/tooling/muzzle/Reference$Builder",
                  "withMethod",
                  Type.getMethodDescriptor(
                      Reference.Builder.class.getMethod(
                          "withMethod",
                          Reference.Source[].class,
                          Reference.Flag[].class,
                          String.class,
                          Type.class,
                          Type[].class)),
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
              "([Ljava/lang/String;[Ldatadog/trace/agent/tooling/muzzle/Reference;)V",
              false);
          mv.visitFieldInsn(
              Opcodes.PUTFIELD,
              instrumentationClassName,
              MUZZLE_FIELD_NAME,
              "Ldatadog/trace/agent/tooling/muzzle/ReferenceMatcher;");

          mv.visitLabel(ret);
          if (frames) {
            mv.visitFrame(Opcodes.F_SAME, 1, null, 0, null);
          }
          mv.visitVarInsn(Opcodes.ALOAD, 0);
          mv.visitFieldInsn(
              Opcodes.GETFIELD,
              instrumentationClassName,
              MUZZLE_FIELD_NAME,
              "Ldatadog/trace/agent/tooling/muzzle/ReferenceMatcher;");
          mv.visitInsn(Opcodes.ARETURN);
          mv.visitLabel(finish);

          mv.visitLocalVariable(
              "this", "L" + instrumentationClassName + ";", null, start, finish, 0);
          mv.visitMaxs(0, 0); // recomputed
          mv.visitEnd();
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      }

      super.visitField(
          Opcodes.ACC_PRIVATE + Opcodes.ACC_VOLATILE,
          MUZZLE_FIELD_NAME,
          Type.getDescriptor(ReferenceMatcher.class),
          null,
          null);
      super.visitEnd();
    }

    /** Append a field initializer to the end of a method. */
    public class InitializeFieldVisitor extends MethodVisitor {
      public InitializeFieldVisitor(MethodVisitor methodVisitor) {
        super(Opcodes.ASM7, methodVisitor);
      }

      @Override
      public void visitInsn(final int opcode) {
        if (opcode == Opcodes.RETURN) {
          super.visitVarInsn(Opcodes.ALOAD, 0);
          super.visitInsn(Opcodes.ACONST_NULL);
          super.visitFieldInsn(
              Opcodes.PUTFIELD,
              instrumentationClassName,
              MUZZLE_FIELD_NAME,
              Type.getDescriptor(ReferenceMatcher.class));
        }
        super.visitInsn(opcode);
      }
    }
  }
}
