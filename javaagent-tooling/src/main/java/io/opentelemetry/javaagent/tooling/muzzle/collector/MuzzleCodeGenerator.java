/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.muzzle.collector;

import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.muzzle.Reference;
import io.opentelemetry.javaagent.tooling.Utils;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class generates the actual implementation of the {@link
 * InstrumentationModule#getMuzzleReferences()} method. It collects references from all advice
 * classes defined in an instrumentation and writes them as Java bytecode in the generated {@link
 * InstrumentationModule#getMuzzleReferences()} method.
 *
 * <p>This class is run at compile time by the {@link MuzzleCodeGenerationPlugin} ByteBuddy plugin.
 */
class MuzzleCodeGenerator implements AsmVisitorWrapper {
  private static final Logger log = LoggerFactory.getLogger(MuzzleCodeGenerator.class);

  private static final String MUZZLE_REFERENCES_FIELD_NAME = "muzzleReferences";
  private static final String MUZZLE_REFERENCES_METHOD_NAME = "getMuzzleReferences";
  private static final String MUZZLE_HELPER_CLASSES_METHOD_NAME = "getMuzzleHelperClassNames";
  private static final String MUZZLE_CONTEXT_STORE_CLASSES_METHOD_NAME =
      "getMuzzleContextStoreClasses";

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
    return new GenerateMuzzleMethodsAndFields(
        classVisitor,
        implementationContext.getClassFileVersion().isAtLeast(ClassFileVersion.JAVA_V6));
  }

  private static class GenerateMuzzleMethodsAndFields extends ClassVisitor {

    private final boolean frames;

    private String instrumentationClassName;
    private InstrumentationModule instrumentationModule;

    private boolean generateReferencesField = true;
    private boolean generateReferencesMethod = true;
    private boolean generateHelperClassNamesMethod = true;
    private boolean generateContextStoreClassesMethod = true;

    public GenerateMuzzleMethodsAndFields(ClassVisitor classVisitor, boolean frames) {
      super(Opcodes.ASM7, classVisitor);
      this.frames = frames;
    }

    @Override
    public void visit(
        int version,
        int access,
        String name,
        String signature,
        String superName,
        String[] interfaces) {
      this.instrumentationClassName = name;
      try {
        instrumentationModule =
            (InstrumentationModule)
                MuzzleCodeGenerator.class
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
      if (MUZZLE_REFERENCES_FIELD_NAME.equals(name)) {
        generateReferencesField = false;
        log.info(
            "The '{}' field was already found in class '{}'. Muzzle will not generate it again",
            MUZZLE_REFERENCES_FIELD_NAME,
            instrumentationClassName);
      }
      return super.visitField(access, name, descriptor, signature, value);
    }

    @Override
    public MethodVisitor visitMethod(
        int access, String name, String descriptor, String signature, String[] exceptions) {
      if (MUZZLE_REFERENCES_METHOD_NAME.equals(name)) {
        generateReferencesMethod = false;
        log.info(
            "The '{}' method was already found in class '{}'. Muzzle will not generate it again",
            MUZZLE_REFERENCES_METHOD_NAME,
            instrumentationClassName);
      }
      if (MUZZLE_HELPER_CLASSES_METHOD_NAME.equals(name)) {
        generateHelperClassNamesMethod = false;
        log.info(
            "The '{}' method was already found in class '{}'. Muzzle will not generate it again",
            MUZZLE_HELPER_CLASSES_METHOD_NAME,
            instrumentationClassName);
      }
      if (MUZZLE_CONTEXT_STORE_CLASSES_METHOD_NAME.equals(name)) {
        generateContextStoreClassesMethod = false;
        log.info(
            "The '{}' method was already found in class '{}'. Muzzle will not generate it again",
            MUZZLE_CONTEXT_STORE_CLASSES_METHOD_NAME,
            instrumentationClassName);
      }
      MethodVisitor methodVisitor =
          super.visitMethod(access, name, descriptor, signature, exceptions);
      if ("<init>".equals(name)) {
        methodVisitor = new InitializeReferencesField(methodVisitor);
      }
      return methodVisitor;
    }

    @Override
    public void visitEnd() {
      ReferenceCollector collector = collectReferences();
      if (generateReferencesField) {
        generateMuzzleReferencesField();
      }
      if (generateReferencesMethod) {
        generateMuzzleReferencesMethod(collector);
      }
      if (generateHelperClassNamesMethod) {
        generateMuzzleHelperClassNamesMethod(collector);
      }
      if (generateContextStoreClassesMethod) {
        generateMuzzleContextStoreClassesMethod(collector);
      }
      super.visitEnd();
    }

    private ReferenceCollector collectReferences() {
      Set<String> adviceClassNames =
          instrumentationModule.typeInstrumentations().stream()
              .flatMap(typeInstrumentation -> typeInstrumentation.transformers().values().stream())
              .collect(Collectors.toSet());

      ReferenceCollector collector = new ReferenceCollector(instrumentationModule::isHelperClass);
      for (String adviceClass : adviceClassNames) {
        collector.collectReferencesFromAdvice(adviceClass);
      }
      for (String resource : instrumentationModule.helperResourceNames()) {
        collector.collectReferencesFromResource(resource);
      }
      collector.prune();
      return collector;
    }

    private void generateMuzzleHelperClassNamesMethod(ReferenceCollector collector) {
      /*
       * public String[] getMuzzleHelperClassNames() {
       *   return new String[] {
       *     // sorted helper class names
       *   };
       * }
       */
      MethodVisitor mv =
          super.visitMethod(
              Opcodes.ACC_PUBLIC,
              MUZZLE_HELPER_CLASSES_METHOD_NAME,
              "()[Ljava/lang/String;",
              null,
              null);
      mv.visitCode();

      List<String> helperClassNames = collector.getSortedHelperClasses();

      mv.visitLdcInsn(helperClassNames.size());
      // stack: size
      mv.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/String");
      // stack: array
      for (int i = 0; i < helperClassNames.size(); ++i) {
        String helperClassName = helperClassNames.get(i);

        mv.visitInsn(Opcodes.DUP);
        // stack: array, array
        mv.visitLdcInsn(i);
        // stack: array, array, i
        mv.visitLdcInsn(helperClassName);
        // stack: array, array, i, helperClassName
        mv.visitInsn(Opcodes.AASTORE);
        // stack: array
      }
      mv.visitInsn(Opcodes.ARETURN);

      mv.visitMaxs(0, 0);
      mv.visitEnd();
    }

    private void generateMuzzleReferencesMethod(ReferenceCollector collector) {
      Type referenceType = Type.getType(Reference.class);
      Type referenceArrayType = Type.getType(Reference[].class);
      Type referenceBuilderType = Type.getType(Reference.Builder.class);
      Type referenceFlagType = Type.getType(Reference.Flag.class);
      Type referenceSourceType = Type.getType(Reference.Source.class);
      Type stringType = Type.getType(String.class);
      Type typeType = Type.getType(Type.class);

      /*
       * public synchronized Reference[] getMuzzleReferences() {
       *   if (null == this.muzzleReferences) {
       *     this.muzzleReferences = new Reference[] {
       *                               // reference builders
       *                             };
       *   }
       *   return this.muzzleReferences;
       * }
       */
      try {
        MethodVisitor mv =
            super.visitMethod(
                Opcodes.ACC_PUBLIC + Opcodes.ACC_SYNCHRONIZED,
                MUZZLE_REFERENCES_METHOD_NAME,
                Type.getMethodDescriptor(referenceArrayType),
                null,
                null);

        mv.visitCode();
        Label start = new Label();
        Label ret = new Label();
        Label finish = new Label();

        mv.visitLabel(start);
        mv.visitInsn(Opcodes.ACONST_NULL);
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitFieldInsn(
            Opcodes.GETFIELD,
            instrumentationClassName,
            MUZZLE_REFERENCES_FIELD_NAME,
            referenceArrayType.getDescriptor());
        mv.visitJumpInsn(Opcodes.IF_ACMPNE, ret);

        mv.visitVarInsn(Opcodes.ALOAD, 0);

        Reference[] references = collector.getReferences().values().toArray(new Reference[0]);
        mv.visitLdcInsn(references.length);
        mv.visitTypeInsn(Opcodes.ANEWARRAY, referenceType.getInternalName());

        for (int i = 0; i < references.length; ++i) {
          mv.visitInsn(Opcodes.DUP);
          mv.visitLdcInsn(i);
          mv.visitTypeInsn(Opcodes.NEW, referenceBuilderType.getInternalName());
          mv.visitInsn(Opcodes.DUP);
          mv.visitLdcInsn(references[i].getClassName());
          mv.visitMethodInsn(
              Opcodes.INVOKESPECIAL,
              referenceBuilderType.getInternalName(),
              "<init>",
              "(Ljava/lang/String;)V",
              false);
          for (Reference.Source source : references[i].getSources()) {
            mv.visitLdcInsn(source.getName());
            mv.visitLdcInsn(source.getLine());
            mv.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                referenceBuilderType.getInternalName(),
                "withSource",
                Type.getMethodDescriptor(referenceBuilderType, stringType, Type.INT_TYPE),
                false);
          }
          for (Reference.Flag flag : references[i].getFlags()) {
            String enumClassName = getEnumClassInternalName(flag);
            mv.visitFieldInsn(
                Opcodes.GETSTATIC, enumClassName, flag.name(), "L" + enumClassName + ";");
            mv.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                referenceBuilderType.getInternalName(),
                "withFlag",
                Type.getMethodDescriptor(referenceBuilderType, referenceFlagType),
                false);
          }
          if (null != references[i].getSuperName()) {
            mv.visitLdcInsn(references[i].getSuperName());
            mv.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                referenceBuilderType.getInternalName(),
                "withSuperName",
                Type.getMethodDescriptor(referenceBuilderType, stringType),
                false);
          }
          for (String interfaceName : references[i].getInterfaces()) {
            mv.visitLdcInsn(interfaceName);
            mv.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                referenceBuilderType.getInternalName(),
                "withInterface",
                Type.getMethodDescriptor(referenceBuilderType, stringType),
                false);
          }
          for (Reference.Field field : references[i].getFields()) {
            { // sources
              mv.visitLdcInsn(field.getSources().size());
              mv.visitTypeInsn(Opcodes.ANEWARRAY, referenceSourceType.getInternalName());

              int j = 0;
              for (Reference.Source source : field.getSources()) {
                mv.visitInsn(Opcodes.DUP);
                mv.visitLdcInsn(j);

                mv.visitTypeInsn(Opcodes.NEW, referenceSourceType.getInternalName());
                mv.visitInsn(Opcodes.DUP);
                mv.visitLdcInsn(source.getName());
                mv.visitLdcInsn(source.getLine());
                mv.visitMethodInsn(
                    Opcodes.INVOKESPECIAL,
                    referenceSourceType.getInternalName(),
                    "<init>",
                    "(Ljava/lang/String;I)V",
                    false);

                mv.visitInsn(Opcodes.AASTORE);
                ++j;
              }
            }

            { // flags
              mv.visitLdcInsn(field.getFlags().size());
              mv.visitTypeInsn(Opcodes.ANEWARRAY, referenceFlagType.getInternalName());

              int j = 0;
              for (Reference.Flag flag : field.getFlags()) {
                mv.visitInsn(Opcodes.DUP);
                mv.visitLdcInsn(j);
                String enumClassName = getEnumClassInternalName(flag);
                mv.visitFieldInsn(
                    Opcodes.GETSTATIC, enumClassName, flag.name(), "L" + enumClassName + ";");
                mv.visitInsn(Opcodes.AASTORE);
                ++j;
              }
            }

            mv.visitLdcInsn(field.getName());

            { // field type
              mv.visitLdcInsn(field.getDescriptor());
              mv.visitMethodInsn(
                  Opcodes.INVOKESTATIC,
                  typeType.getInternalName(),
                  "getType",
                  Type.getMethodDescriptor(typeType, stringType),
                  false);
            }

            // declared flag
            mv.visitLdcInsn(field.isDeclared());

            mv.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                referenceBuilderType.getInternalName(),
                "withField",
                Type.getMethodDescriptor(
                    Reference.Builder.class.getMethod(
                        "withField",
                        Reference.Source[].class,
                        Reference.Flag[].class,
                        String.class,
                        Type.class,
                        boolean.class)),
                false);
          }
          for (Reference.Method method : references[i].getMethods()) {
            mv.visitLdcInsn(method.getSources().size());
            mv.visitTypeInsn(Opcodes.ANEWARRAY, referenceSourceType.getInternalName());
            int j = 0;
            for (Reference.Source source : method.getSources()) {
              mv.visitInsn(Opcodes.DUP);
              mv.visitLdcInsn(j);

              mv.visitTypeInsn(Opcodes.NEW, referenceSourceType.getInternalName());
              mv.visitInsn(Opcodes.DUP);
              mv.visitLdcInsn(source.getName());
              mv.visitLdcInsn(source.getLine());
              mv.visitMethodInsn(
                  Opcodes.INVOKESPECIAL,
                  referenceSourceType.getInternalName(),
                  "<init>",
                  "(Ljava/lang/String;I)V",
                  false);

              mv.visitInsn(Opcodes.AASTORE);
              ++j;
            }

            mv.visitLdcInsn(method.getFlags().size());
            mv.visitTypeInsn(Opcodes.ANEWARRAY, referenceFlagType.getInternalName());
            j = 0;
            for (Reference.Flag flag : method.getFlags()) {
              mv.visitInsn(Opcodes.DUP);
              mv.visitLdcInsn(j);
              String enumClassName = getEnumClassInternalName(flag);
              mv.visitFieldInsn(
                  Opcodes.GETSTATIC, enumClassName, flag.name(), "L" + enumClassName + ";");
              mv.visitInsn(Opcodes.AASTORE);
              ++j;
            }

            mv.visitLdcInsn(method.getName());

            {
              // method return and parameter types must be passed by Type.getType() call to be
              // properly shaded
              Type methodType = Type.getMethodType(method.getDescriptor());

              // return type
              mv.visitLdcInsn(methodType.getReturnType().getDescriptor());
              mv.visitMethodInsn(
                  Opcodes.INVOKESTATIC,
                  typeType.getInternalName(),
                  "getType",
                  Type.getMethodDescriptor(typeType, stringType),
                  false);

              mv.visitLdcInsn(methodType.getArgumentTypes().length);
              mv.visitTypeInsn(Opcodes.ANEWARRAY, typeType.getInternalName());
              j = 0;
              for (Type parameterType : methodType.getArgumentTypes()) {
                mv.visitInsn(Opcodes.DUP);
                mv.visitLdcInsn(j);

                mv.visitLdcInsn(parameterType.getDescriptor());
                mv.visitMethodInsn(
                    Opcodes.INVOKESTATIC,
                    typeType.getInternalName(),
                    "getType",
                    Type.getMethodDescriptor(typeType, stringType),
                    false);

                mv.visitInsn(Opcodes.AASTORE);
                j++;
              }
            }

            mv.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                referenceBuilderType.getInternalName(),
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
              referenceBuilderType.getInternalName(),
              "build",
              Type.getMethodDescriptor(referenceType),
              false);
          mv.visitInsn(Opcodes.AASTORE);
        }

        mv.visitFieldInsn(
            Opcodes.PUTFIELD,
            instrumentationClassName,
            MUZZLE_REFERENCES_FIELD_NAME,
            referenceArrayType.getDescriptor());

        mv.visitLabel(ret);
        if (frames) {
          mv.visitFrame(Opcodes.F_SAME, 1, null, 0, null);
        }
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitFieldInsn(
            Opcodes.GETFIELD,
            instrumentationClassName,
            MUZZLE_REFERENCES_FIELD_NAME,
            referenceArrayType.getDescriptor());
        mv.visitInsn(Opcodes.ARETURN);
        mv.visitLabel(finish);

        mv.visitLocalVariable("this", "L" + instrumentationClassName + ";", null, start, finish, 0);
        mv.visitMaxs(0, 0); // recomputed
        mv.visitEnd();
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }

    private void generateMuzzleReferencesField() {
      super.visitField(
          Opcodes.ACC_PRIVATE + Opcodes.ACC_VOLATILE,
          MUZZLE_REFERENCES_FIELD_NAME,
          Type.getDescriptor(Reference[].class),
          null,
          null);
    }

    private void generateMuzzleContextStoreClassesMethod(ReferenceCollector collector) {
      /*
       * public Map<String, String> getMuzzleContextStoreClasses() {
       *   Map<String, String> contextStore = new HashMap();
       *   contextStore.put(..., ...);
       *   return contextStore;
       * }
       */
      MethodVisitor mv =
          super.visitMethod(
              Opcodes.ACC_PUBLIC,
              MUZZLE_CONTEXT_STORE_CLASSES_METHOD_NAME,
              "()Ljava/util/Map;",
              null,
              null);
      mv.visitCode();

      Map<String, String> contextStoreClasses = collector.getContextStoreClasses();

      mv.visitTypeInsn(Opcodes.NEW, "java/util/HashMap");
      // stack: map
      mv.visitInsn(Opcodes.DUP);
      // stack: map, map
      mv.visitLdcInsn(contextStoreClasses.size());
      // stack: map, map, size
      mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/util/HashMap", "<init>", "(I)V", false);
      // stack: map
      mv.visitVarInsn(Opcodes.ASTORE, 1);
      // stack: <empty>

      contextStoreClasses.forEach(
          (className, contextClassName) -> {
            mv.visitVarInsn(Opcodes.ALOAD, 1);
            // stack: map
            mv.visitLdcInsn(className);
            // stack: map, className
            mv.visitLdcInsn(contextClassName);
            // stack: map, className, contextClassName
            mv.visitMethodInsn(
                Opcodes.INVOKEINTERFACE,
                "java/util/Map",
                "put",
                "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                true);
            // stack: previousValue
            mv.visitInsn(Opcodes.POP);
            // stack: <empty>
          });

      mv.visitVarInsn(Opcodes.ALOAD, 1);
      // stack: map
      mv.visitInsn(Opcodes.ARETURN);

      mv.visitMaxs(0, 0);
      mv.visitEnd();
    }

    private static final Pattern ANONYMOUS_ENUM_CONSTANT_CLASS =
        Pattern.compile("(?<enumClass>.*)\\$[0-9]+$");

    // drops "$1" suffix for enum constants that override/implement super class methods
    private String getEnumClassInternalName(Reference.Flag flag) {
      String fullInternalName = Utils.getInternalName(flag.getClass());
      Matcher m = ANONYMOUS_ENUM_CONSTANT_CLASS.matcher(fullInternalName);
      return m.matches() ? m.group("enumClass") : fullInternalName;
    }

    /** Appends the {@code Reference[]} field initialization at the end of a method/constructor. */
    private class InitializeReferencesField extends MethodVisitor {
      public InitializeReferencesField(MethodVisitor methodVisitor) {
        super(Opcodes.ASM7, methodVisitor);
      }

      @Override
      public void visitInsn(int opcode) {
        if (opcode == Opcodes.RETURN) {
          super.visitVarInsn(Opcodes.ALOAD, 0);
          super.visitInsn(Opcodes.ACONST_NULL);
          super.visitFieldInsn(
              Opcodes.PUTFIELD,
              instrumentationClassName,
              MUZZLE_REFERENCES_FIELD_NAME,
              Type.getDescriptor(Reference[].class));
        }
        super.visitInsn(opcode);
      }
    }
  }
}
