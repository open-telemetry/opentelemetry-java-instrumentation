/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.muzzle.generation;

import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.muzzle.ClassRef;
import io.opentelemetry.javaagent.extension.muzzle.ClassRefBuilder;
import io.opentelemetry.javaagent.extension.muzzle.FieldRef;
import io.opentelemetry.javaagent.extension.muzzle.Flag;
import io.opentelemetry.javaagent.extension.muzzle.MethodRef;
import io.opentelemetry.javaagent.extension.muzzle.Source;
import io.opentelemetry.javaagent.tooling.muzzle.ContextStoreMappings;
import io.opentelemetry.javaagent.tooling.muzzle.HelperResource;
import io.opentelemetry.javaagent.tooling.muzzle.HelperResourceBuilderImpl;
import io.opentelemetry.javaagent.tooling.muzzle.ReferenceCollector;
import java.net.URLClassLoader;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.bytebuddy.asm.AsmVisitorWrapper;
import net.bytebuddy.description.field.FieldDescription;
import net.bytebuddy.description.field.FieldList;
import net.bytebuddy.description.method.MethodList;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.Implementation;
import net.bytebuddy.jar.asm.ClassVisitor;
import net.bytebuddy.jar.asm.ClassWriter;
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
final class MuzzleCodeGenerator implements AsmVisitorWrapper {
  private static final Logger logger = LoggerFactory.getLogger(MuzzleCodeGenerator.class);

  private static final String MUZZLE_REFERENCES_METHOD_NAME = "getMuzzleReferences";
  private static final String MUZZLE_HELPER_CLASSES_METHOD_NAME = "getMuzzleHelperClassNames";
  private static final String MUZZLE_CONTEXT_STORE_CLASSES_METHOD_NAME =
      "registerMuzzleContextStoreClasses";
  private final URLClassLoader classLoader;

  public MuzzleCodeGenerator(URLClassLoader classLoader) {
    this.classLoader = classLoader;
  }

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
    return new GenerateMuzzleMethodsAndFields(classVisitor, classLoader);
  }

  private static class GenerateMuzzleMethodsAndFields extends ClassVisitor {

    private final URLClassLoader classLoader;
    private String instrumentationClassName;
    private InstrumentationModule instrumentationModule;

    private boolean generateReferencesMethod = true;
    private boolean generateHelperClassNamesMethod = true;
    private boolean generateContextStoreClassesMethod = true;

    public GenerateMuzzleMethodsAndFields(ClassVisitor classVisitor, URLClassLoader classLoader) {
      super(Opcodes.ASM7, classVisitor);
      this.classLoader = classLoader;
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
                classLoader
                    .loadClass(Utils.getClassName(instrumentationClassName))
                    .getDeclaredConstructor()
                    .newInstance();
      } catch (Exception e) {
        throw new IllegalStateException(e);
      }
      super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public MethodVisitor visitMethod(
        int access, String name, String descriptor, String signature, String[] exceptions) {
      if (MUZZLE_REFERENCES_METHOD_NAME.equals(name)) {
        generateReferencesMethod = false;
        logger.info(
            "The '{}' method was already found in class '{}'. Muzzle will not generate it again",
            MUZZLE_REFERENCES_METHOD_NAME,
            instrumentationClassName);
      }
      if (MUZZLE_HELPER_CLASSES_METHOD_NAME.equals(name)) {
        generateHelperClassNamesMethod = false;
        logger.info(
            "The '{}' method was already found in class '{}'. Muzzle will not generate it again",
            MUZZLE_HELPER_CLASSES_METHOD_NAME,
            instrumentationClassName);
      }
      if (MUZZLE_CONTEXT_STORE_CLASSES_METHOD_NAME.equals(name)) {
        generateContextStoreClassesMethod = false;
        logger.info(
            "The '{}' method was already found in class '{}'. Muzzle will not generate it again",
            MUZZLE_CONTEXT_STORE_CLASSES_METHOD_NAME,
            instrumentationClassName);
      }
      return super.visitMethod(access, name, descriptor, signature, exceptions);
    }

    @Override
    public void visitEnd() {
      ReferenceCollector collector = collectReferences();
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
      AdviceClassNameCollector adviceClassNameCollector = new AdviceClassNameCollector();
      for (TypeInstrumentation typeInstrumentation : instrumentationModule.typeInstrumentations()) {
        typeInstrumentation.transform(adviceClassNameCollector);
      }

      // the classloader has a parent including the Gradle classpath, such as buildSrc dependencies.
      // These may have resources take precedence over ones we define, so we need to make sure to
      // not include them when loading resources.
      // TODO analyze anew if this is needed
      ClassLoader resourceLoader = new URLClassLoader(classLoader.getURLs(), null);
      ReferenceCollector collector =
          new ReferenceCollector(instrumentationModule::isHelperClass, resourceLoader);
      for (String adviceClass : adviceClassNameCollector.getAdviceClassNames()) {
        collector.collectReferencesFromAdvice(adviceClass);
      }
      for (String resource : instrumentationModule.helperResourceNames()) {
        collector.collectReferencesFromResource(resource);
      }
      HelperResourceBuilderImpl helperResourceBuilder = new HelperResourceBuilderImpl();
      instrumentationModule.registerHelperResources(helperResourceBuilder);
      for (HelperResource resource : helperResourceBuilder.getResources()) {
        collector.collectReferencesFromResource(resource);
      }
      collector.prune();
      return collector;
    }

    private void generateMuzzleReferencesMethod(ReferenceCollector collector) {
      Type referenceType = Type.getType(ClassRef.class);
      Type referenceBuilderType = Type.getType(ClassRefBuilder.class);
      Type referenceFlagType = Type.getType(Flag.class);
      Type referenceFlagArrayType = Type.getType(Flag[].class);
      Type referenceSourceArrayType = Type.getType(Source[].class);
      Type stringType = Type.getType(String.class);
      Type typeType = Type.getType(Type.class);
      Type typeArrayType = Type.getType(Type[].class);

      /*
       * public Map<String, ClassRef> getMuzzleReferences() {
       *   Map<String, ClassRef> references = new HashMap<>(...);
       *   references.put("reference class name", ClassRef.newBuilder(...)
       *       ...
       *       .build());
       *   return references;
       * }
       */
      MethodVisitor mv =
          super.visitMethod(
              Opcodes.ACC_PUBLIC, MUZZLE_REFERENCES_METHOD_NAME, "()Ljava/util/Map;", null, null);
      mv.visitCode();

      Collection<ClassRef> references = collector.getReferences().values();

      writeNewMap(mv, references.size());
      // stack: map
      mv.visitVarInsn(Opcodes.ASTORE, 1);
      // stack: <empty>

      references.forEach(
          reference -> {
            mv.visitVarInsn(Opcodes.ALOAD, 1);
            // stack: map
            mv.visitLdcInsn(reference.getClassName());
            // stack: map, className

            mv.visitLdcInsn(reference.getClassName());
            mv.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                referenceType.getInternalName(),
                "newBuilder",
                Type.getMethodDescriptor(referenceBuilderType, stringType),
                /* isInterface= */ false);
            // stack: map, className, builder

            for (Source source : reference.getSources()) {
              mv.visitLdcInsn(source.getName());
              mv.visitLdcInsn(source.getLine());
              mv.visitMethodInsn(
                  Opcodes.INVOKEVIRTUAL,
                  referenceBuilderType.getInternalName(),
                  "addSource",
                  Type.getMethodDescriptor(referenceBuilderType, stringType, Type.INT_TYPE),
                  /* isInterface= */ false);
            }
            // stack: map, className, builder
            for (Flag flag : reference.getFlags()) {
              String enumClassName = getEnumClassInternalName(flag);
              mv.visitFieldInsn(
                  Opcodes.GETSTATIC, enumClassName, flag.name(), "L" + enumClassName + ";");
              mv.visitMethodInsn(
                  Opcodes.INVOKEVIRTUAL,
                  referenceBuilderType.getInternalName(),
                  "addFlag",
                  Type.getMethodDescriptor(referenceBuilderType, referenceFlagType),
                  /* isInterface= */ false);
            }
            // stack: map, className, builder
            if (null != reference.getSuperClassName()) {
              mv.visitLdcInsn(reference.getSuperClassName());
              mv.visitMethodInsn(
                  Opcodes.INVOKEVIRTUAL,
                  referenceBuilderType.getInternalName(),
                  "setSuperClassName",
                  Type.getMethodDescriptor(referenceBuilderType, stringType),
                  /* isInterface= */ false);
            }
            // stack: map, className, builder
            for (String interfaceName : reference.getInterfaceNames()) {
              mv.visitLdcInsn(interfaceName);
              mv.visitMethodInsn(
                  Opcodes.INVOKEVIRTUAL,
                  referenceBuilderType.getInternalName(),
                  "addInterfaceName",
                  Type.getMethodDescriptor(referenceBuilderType, stringType),
                  /* isInterface= */ false);
            }
            // stack: map, className, builder
            for (FieldRef field : reference.getFields()) {
              writeSourcesArray(mv, field.getSources());
              writeFlagsArray(mv, field.getFlags());
              // field name
              mv.visitLdcInsn(field.getName());
              writeType(mv, field.getDescriptor());
              // declared flag
              mv.visitLdcInsn(field.isDeclared());

              mv.visitMethodInsn(
                  Opcodes.INVOKEVIRTUAL,
                  referenceBuilderType.getInternalName(),
                  "addField",
                  Type.getMethodDescriptor(
                      referenceBuilderType,
                      referenceSourceArrayType,
                      referenceFlagArrayType,
                      stringType,
                      typeType,
                      Type.BOOLEAN_TYPE),
                  /* isInterface= */ false);
            }
            // stack: map, className, builder
            for (MethodRef method : reference.getMethods()) {
              writeSourcesArray(mv, method.getSources());
              writeFlagsArray(mv, method.getFlags());
              // method name
              mv.visitLdcInsn(method.getName());
              // method return and argument types
              {
                // we cannot pass the whole method descriptor string as it won't be shaded, so
                // we
                // have to pass the return and parameter types separately - strings in
                // Type.getType()
                // calls will be shaded correctly
                Type methodType = Type.getMethodType(method.getDescriptor());

                writeType(mv, methodType.getReturnType().getDescriptor());

                mv.visitLdcInsn(methodType.getArgumentTypes().length);
                mv.visitTypeInsn(Opcodes.ANEWARRAY, typeType.getInternalName());
                int i = 0;
                for (Type parameterType : methodType.getArgumentTypes()) {
                  mv.visitInsn(Opcodes.DUP);
                  mv.visitLdcInsn(i);
                  writeType(mv, parameterType.getDescriptor());
                  mv.visitInsn(Opcodes.AASTORE);
                  i++;
                }
              }

              mv.visitMethodInsn(
                  Opcodes.INVOKEVIRTUAL,
                  referenceBuilderType.getInternalName(),
                  "addMethod",
                  Type.getMethodDescriptor(
                      referenceBuilderType,
                      referenceSourceArrayType,
                      referenceFlagArrayType,
                      stringType,
                      typeType,
                      typeArrayType),
                  /* isInterface= */ false);
            }
            // stack: map, className, builder
            mv.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                referenceBuilderType.getInternalName(),
                "build",
                Type.getMethodDescriptor(referenceType),
                /* isInterface= */ false);
            // stack: map, className, classRef

            mv.visitMethodInsn(
                Opcodes.INVOKEINTERFACE,
                "java/util/Map",
                "put",
                "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                /* isInterface= */ true);
            // stack: previousValue
            mv.visitInsn(Opcodes.POP);
            // stack: <empty>
          });

      mv.visitVarInsn(Opcodes.ALOAD, 1);
      // stack: map
      mv.visitInsn(Opcodes.ARETURN);

      mv.visitMaxs(0, 0); // recomputed
      mv.visitEnd();
    }

    private static void writeNewMap(MethodVisitor mv, int size) {
      mv.visitTypeInsn(Opcodes.NEW, "java/util/HashMap");
      // stack: map
      mv.visitInsn(Opcodes.DUP);
      // stack: map, map
      // pass bigger size to avoid resizes; same formula as in e.g. HashSet(Collection)
      // 0.75 is the default load factor
      mv.visitLdcInsn((int) (size / 0.75f) + 1);
      // stack: map, map, size
      mv.visitLdcInsn(0.75f);
      // stack: map, map, size, loadFactor
      mv.visitMethodInsn(
          Opcodes.INVOKESPECIAL, "java/util/HashMap", "<init>", "(IF)V", /* isInterface= */ false);
    }

    private static void writeSourcesArray(MethodVisitor mv, Set<Source> sources) {
      Type referenceSourceType = Type.getType(Source.class);

      mv.visitLdcInsn(sources.size());
      mv.visitTypeInsn(Opcodes.ANEWARRAY, referenceSourceType.getInternalName());

      int i = 0;
      for (Source source : sources) {
        mv.visitInsn(Opcodes.DUP);
        mv.visitLdcInsn(i);

        mv.visitTypeInsn(Opcodes.NEW, referenceSourceType.getInternalName());
        mv.visitInsn(Opcodes.DUP);
        mv.visitLdcInsn(source.getName());
        mv.visitLdcInsn(source.getLine());
        mv.visitMethodInsn(
            Opcodes.INVOKESPECIAL,
            referenceSourceType.getInternalName(),
            "<init>",
            "(Ljava/lang/String;I)V",
            /* isInterface= */ false);

        mv.visitInsn(Opcodes.AASTORE);
        ++i;
      }
    }

    private static void writeFlagsArray(MethodVisitor mv, Set<Flag> flags) {
      Type referenceFlagType = Type.getType(Flag.class);

      mv.visitLdcInsn(flags.size());
      mv.visitTypeInsn(Opcodes.ANEWARRAY, referenceFlagType.getInternalName());

      int i = 0;
      for (Flag flag : flags) {
        mv.visitInsn(Opcodes.DUP);
        mv.visitLdcInsn(i);
        String enumClassName = getEnumClassInternalName(flag);
        mv.visitFieldInsn(Opcodes.GETSTATIC, enumClassName, flag.name(), "L" + enumClassName + ";");
        mv.visitInsn(Opcodes.AASTORE);
        ++i;
      }
    }

    private static final Pattern ANONYMOUS_ENUM_CONSTANT_CLASS =
        Pattern.compile("(?<enumClass>.*)\\$[0-9]+$");

    // drops "$1" suffix for enum constants that override/implement super class methods
    private static String getEnumClassInternalName(Flag flag) {
      String fullInternalName = Utils.getInternalName(flag.getClass());
      Matcher m = ANONYMOUS_ENUM_CONSTANT_CLASS.matcher(fullInternalName);
      return m.matches() ? m.group("enumClass") : fullInternalName;
    }

    private static void writeType(MethodVisitor mv, String descriptor) {
      Type typeType = Type.getType(Type.class);

      mv.visitLdcInsn(descriptor);
      mv.visitMethodInsn(
          Opcodes.INVOKESTATIC,
          typeType.getInternalName(),
          "getType",
          Type.getMethodDescriptor(typeType, Type.getType(String.class)),
          /* isInterface= */ false);
    }

    private void generateMuzzleHelperClassNamesMethod(ReferenceCollector collector) {
      /*
       * public List<String> getMuzzleHelperClassNames() {
       *   List<String> helperClassNames = new ArrayList<>(...);
       *   helperClassNames.add(...);
       *   return helperClassNames;
       * }
       */
      MethodVisitor mv =
          super.visitMethod(
              Opcodes.ACC_PUBLIC,
              MUZZLE_HELPER_CLASSES_METHOD_NAME,
              "()Ljava/util/List;",
              null,
              null);
      mv.visitCode();

      List<String> helperClassNames = collector.getSortedHelperClasses();

      mv.visitTypeInsn(Opcodes.NEW, "java/util/ArrayList");
      // stack: list
      mv.visitInsn(Opcodes.DUP);
      // stack: list, list
      mv.visitLdcInsn(helperClassNames.size());
      // stack: list, list, size
      mv.visitMethodInsn(
          Opcodes.INVOKESPECIAL, "java/util/ArrayList", "<init>", "(I)V", /* isInterface= */ false);
      // stack: list
      mv.visitVarInsn(Opcodes.ASTORE, 1);
      // stack: <empty>

      helperClassNames.forEach(
          helperClassName -> {
            mv.visitVarInsn(Opcodes.ALOAD, 1);
            // stack: list
            mv.visitLdcInsn(helperClassName);
            // stack: list, helperClassName
            mv.visitMethodInsn(
                Opcodes.INVOKEINTERFACE,
                "java/util/List",
                "add",
                "(Ljava/lang/Object;)Z",
                /* isInterface= */ true);
            // stack: added
            mv.visitInsn(Opcodes.POP);
            // stack: <empty>
          });

      mv.visitVarInsn(Opcodes.ALOAD, 1);
      // stack: list
      mv.visitInsn(Opcodes.ARETURN);

      mv.visitMaxs(0, 0);
      mv.visitEnd();
    }

    private void generateMuzzleContextStoreClassesMethod(ReferenceCollector collector) {
      /*
       * public void registerMuzzleContextStoreClasses(InstrumentationContextBuilder builder) {
       *   builder.register(..., ...);
       * }
       */
      MethodVisitor mv =
          super.visitMethod(
              Opcodes.ACC_PUBLIC,
              MUZZLE_CONTEXT_STORE_CLASSES_METHOD_NAME,
              "(Lio/opentelemetry/javaagent/extension/instrumentation/InstrumentationContextBuilder;)V",
              null,
              null);
      mv.visitCode();

      ContextStoreMappings contextStoreMappings = collector.getContextStoreMappings();

      mv.visitVarInsn(Opcodes.ALOAD, 1);
      // stack: builder
      contextStoreMappings.forEach(
          (className, contextClassName) -> {
            mv.visitLdcInsn(className);
            // stack: builder, className
            mv.visitLdcInsn(contextClassName);
            // stack: builder, className, contextClassName
            mv.visitMethodInsn(
                Opcodes.INVOKEINTERFACE,
                "io/opentelemetry/javaagent/extension/instrumentation/InstrumentationContextBuilder",
                "register",
                "(Ljava/lang/String;Ljava/lang/String;)Lio/opentelemetry/javaagent/extension/instrumentation/InstrumentationContextBuilder;",
                /* isInterface= */ true);
            // stack: builder
          });
      mv.visitInsn(Opcodes.POP);
      // stack: <empty>
      mv.visitInsn(Opcodes.RETURN);

      mv.visitMaxs(0, 0);
      mv.visitEnd();
    }
  }
}
