package datadog.trace.agent.tooling.context;

import static datadog.trace.agent.tooling.ByteBuddyElementMatchers.safeHasSuperType;
import static datadog.trace.agent.tooling.ClassLoaderMatcher.BOOTSTRAP_CLASSLOADER;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;

import datadog.trace.agent.tooling.HelperInjector;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.agent.tooling.Utils;
import datadog.trace.api.Config;
import datadog.trace.bootstrap.ContextStore;
import datadog.trace.bootstrap.FieldBackedContextStoreAppliedMarker;
import datadog.trace.bootstrap.InstrumentationContext;
import datadog.trace.bootstrap.WeakMap;
import java.lang.reflect.Method;
import java.security.ProtectionDomain;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.ClassFileVersion;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.AsmVisitorWrapper;
import net.bytebuddy.description.field.FieldDescription;
import net.bytebuddy.description.field.FieldList;
import net.bytebuddy.description.method.MethodList;
import net.bytebuddy.description.modifier.TypeManifestation;
import net.bytebuddy.description.modifier.Visibility;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.implementation.Implementation;
import net.bytebuddy.jar.asm.ClassVisitor;
import net.bytebuddy.jar.asm.ClassWriter;
import net.bytebuddy.jar.asm.FieldVisitor;
import net.bytebuddy.jar.asm.Label;
import net.bytebuddy.jar.asm.MethodVisitor;
import net.bytebuddy.jar.asm.Opcodes;
import net.bytebuddy.jar.asm.Type;
import net.bytebuddy.pool.TypePool;
import net.bytebuddy.utility.JavaModule;

/**
 * InstrumentationContextProvider which stores context in a field that is injected into a class and
 * falls back to global map if field was not injected.
 *
 * <p>This is accomplished by
 *
 * <ol>
 *   <li>Injecting a Dynamic Interface that provides getter and setter for context field
 *   <li>Applying Dynamic Interface to a type needing context, implementing interface methods and
 *       adding context storage field
 *   <li>Injecting a Dynamic Class created from {@link ContextStoreImplementationTemplate} to use
 *       injected field or fall back to a static map
 *   <li>Rewritting calls to the context-store to access the specific dynamic {@link
 *       ContextStoreImplementationTemplate}
 * </ol>
 *
 * <p>Example:<br>
 * <em>InstrumentationContext.get(Runnable.class, RunnableState.class)")</em><br>
 * is rewritten to:<br>
 * <em>FieldBackedProvider$ContextStore$Runnable$RunnableState12345.getContextStore(runnableRunnable.class,
 * RunnableState.class)</em>
 */
@Slf4j
public class FieldBackedProvider implements InstrumentationContextProvider {

  /**
   * Note: the value here has to be inside on of the prefixes in
   * datadog.trace.agent.tooling.Utils#BOOTSTRAP_PACKAGE_PREFIXES. This ensures that 'isolating' (or
   * 'module') classloaders like jboss and osgi see injected classes. This works because we
   * instrument those classloaders to load everything inside bootstrap packages.
   */
  private static final String DYNAMIC_CLASSES_PACKAGE =
      "datadog.trace.bootstrap.instrumentation.context.";

  private static final String INJECTED_FIELDS_MARKER_CLASS_NAME =
      Utils.getInternalName(FieldBackedContextStoreAppliedMarker.class.getName());

  private static final Method CONTEXT_GET_METHOD;
  private static final Method GET_CONTEXT_STORE_METHOD;

  static {
    try {
      CONTEXT_GET_METHOD = InstrumentationContext.class.getMethod("get", Class.class, Class.class);
      GET_CONTEXT_STORE_METHOD =
          ContextStoreImplementationTemplate.class.getMethod(
              "getContextStore", Class.class, Class.class);
    } catch (final Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private final Instrumenter.Default instrumenter;
  private final ByteBuddy byteBuddy;

  /** fields-accessor-interface-name -> fields-accessor-interface-dynamic-type */
  private final Map<String, DynamicType.Unloaded<?>> fieldAccessorInterfaces;

  private final AgentBuilder.Transformer fieldAccessorInterfacesInjector;

  /** context-store-type-name -> context-store-type-name-dynamic-type */
  private final Map<String, DynamicType.Unloaded<?>> contextStoreImplementations;

  private final AgentBuilder.Transformer contextStoreImplementationsInjector;

  private final boolean fieldInjectionEnabled;

  public FieldBackedProvider(final Instrumenter.Default instrumenter) {
    this.instrumenter = instrumenter;
    byteBuddy = new ByteBuddy();
    fieldAccessorInterfaces = generateFieldAccessorInterfaces();
    fieldAccessorInterfacesInjector = bootstrapHelperInjector(fieldAccessorInterfaces.values());
    contextStoreImplementations = generateContextStoreImplementationClasses();
    contextStoreImplementationsInjector =
        bootstrapHelperInjector(contextStoreImplementations.values());
    fieldInjectionEnabled = Config.get().isRuntimeContextFieldInjection();
  }

  @Override
  public AgentBuilder.Identified.Extendable instrumentationTransformer(
      AgentBuilder.Identified.Extendable builder) {
    if (instrumenter.contextStore().size() > 0) {
      /**
       * Install transformer that rewrites accesses to context store with specialized bytecode that
       * invokes appropriate storage implementation.
       */
      builder =
          builder.transform(getTransformerForASMVisitor(getContextStoreReadsRewritingVisitor()));
      builder = injectHelpersIntoBootstrapClassloader(builder);
    }
    return builder;
  }

  private AsmVisitorWrapper getContextStoreReadsRewritingVisitor() {
    return new AsmVisitorWrapper() {
      @Override
      public int mergeWriter(final int flags) {
        return flags | ClassWriter.COMPUTE_MAXS;
      }

      @Override
      public int mergeReader(final int flags) {
        return flags;
      }

      @Override
      public ClassVisitor wrap(
          final TypeDescription instrumentedType,
          final ClassVisitor classVisitor,
          final Implementation.Context implementationContext,
          final TypePool typePool,
          final FieldList<FieldDescription.InDefinedShape> fields,
          final MethodList<?> methods,
          final int writerFlags,
          final int readerFlags) {
        return new ClassVisitor(Opcodes.ASM7, classVisitor) {
          @Override
          public void visit(
              final int version,
              final int access,
              final String name,
              final String signature,
              final String superName,
              final String[] interfaces) {
            super.visit(version, access, name, signature, superName, interfaces);
          }

          @Override
          public MethodVisitor visitMethod(
              final int access,
              final String name,
              final String descriptor,
              final String signature,
              final String[] exceptions) {
            final MethodVisitor mv =
                super.visitMethod(access, name, descriptor, signature, exceptions);
            return new MethodVisitor(Opcodes.ASM7, mv) {
              /** The most recent objects pushed to the stack. */
              private final Object[] stack = {null, null};
              /** Most recent instructions. */
              private final int[] insnStack = {-1, -1, -1};

              @Override
              public void visitMethodInsn(
                  final int opcode,
                  final String owner,
                  final String name,
                  final String descriptor,
                  final boolean isInterface) {
                pushOpcode(opcode);
                if (Utils.getInternalName(CONTEXT_GET_METHOD.getDeclaringClass().getName())
                        .equals(owner)
                    && CONTEXT_GET_METHOD.getName().equals(name)
                    && Type.getMethodDescriptor(CONTEXT_GET_METHOD).equals(descriptor)) {
                  log.debug("Found context-store access in {}", instrumenter.getClass().getName());
                  /*
                  The idea here is that the rest if this method visitor collects last three instructions in `insnStack`
                  variable. Once we get here we check if those last three instructions constitute call that looks like
                  `InstrumentationContext.get(K.class, V.class)`. If it does the inside of this if rewrites it to call
                  dynamically injected context store implementation instead.
                   */
                  if ((insnStack[0] == Opcodes.INVOKESTATIC
                          && insnStack[1] == Opcodes.LDC
                          && insnStack[2] == Opcodes.LDC)
                      && (stack[0] instanceof Type && stack[1] instanceof Type)) {
                    final String contextClassName = ((Type) stack[0]).getClassName();
                    final String keyClassName = ((Type) stack[1]).getClassName();
                    final TypeDescription contextStoreImplementationClass =
                        getContextStoreImplementation(keyClassName, contextClassName);
                    log.debug(
                        "Rewriting context-store map fetch for instrumenter {}: {} -> {}",
                        instrumenter.getClass().getName(),
                        keyClassName,
                        contextClassName);
                    if (contextStoreImplementationClass == null) {
                      throw new IllegalStateException(
                          String.format(
                              "Incorrect Context Api Usage detected. Cannot find map holder class for %s context %s. Was that class defined in contextStore for instrumentation %s?",
                              keyClassName, contextClassName, instrumenter.getClass().getName()));
                    }
                    if (!contextClassName.equals(instrumenter.contextStore().get(keyClassName))) {
                      throw new IllegalStateException(
                          String.format(
                              "Incorrect Context Api Usage detected. Incorrect context class %s, expected %s for instrumentation %s",
                              contextClassName,
                              instrumenter.contextStore().get(keyClassName),
                              instrumenter.getClass().getName()));
                    }
                    // stack: contextClass | keyClass
                    mv.visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        contextStoreImplementationClass.getInternalName(),
                        GET_CONTEXT_STORE_METHOD.getName(),
                        Type.getMethodDescriptor(GET_CONTEXT_STORE_METHOD),
                        false);
                    return;
                  }
                  throw new IllegalStateException(
                      "Incorrect Context Api Usage detected. Key and context class must be class-literals. Example of correct usage: InstrumentationContext.get(Runnable.class, RunnableContext.class)");
                } else {
                  super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                }
              }

              /** Tracking the most recently used opcodes to assert proper api usage. */
              private void pushOpcode(final int opcode) {
                System.arraycopy(insnStack, 0, insnStack, 1, insnStack.length - 1);
                insnStack[0] = opcode;
              }

              /**
               * Tracking the most recently pushed objects on the stack to assert proper api usage.
               */
              private void pushStack(final Object o) {
                System.arraycopy(stack, 0, stack, 1, stack.length - 1);
                stack[0] = o;
              }

              @Override
              public void visitInsn(final int opcode) {
                pushOpcode(opcode);
                super.visitInsn(opcode);
              }

              @Override
              public void visitJumpInsn(final int opcode, final Label label) {
                pushOpcode(opcode);
                super.visitJumpInsn(opcode, label);
              }

              @Override
              public void visitIntInsn(final int opcode, final int operand) {
                pushOpcode(opcode);
                super.visitIntInsn(opcode, operand);
              }

              @Override
              public void visitVarInsn(final int opcode, final int var) {
                pushOpcode(opcode);
                pushStack(var);
                super.visitVarInsn(opcode, var);
              }

              @Override
              public void visitLdcInsn(final Object value) {
                pushOpcode(Opcodes.LDC);
                pushStack(value);
                super.visitLdcInsn(value);
              }
            };
          }
        };
      }
    };
  }

  private AgentBuilder.Identified.Extendable injectHelpersIntoBootstrapClassloader(
      AgentBuilder.Identified.Extendable builder) {
    /**
     * We inject into bootstrap classloader because field accessor interfaces are needed by context
     * store implementations. Unfortunately this forces us to remove stored type checking because
     * actual classes may not be available at this point.
     */
    builder = builder.transform(fieldAccessorInterfacesInjector);

    /**
     * We inject context store implementation into bootstrap classloader because same implementation
     * may be used by different instrumentations and it has to use same static map in case of
     * fallback to map-backed storage.
     */
    builder = builder.transform(contextStoreImplementationsInjector);
    return builder;
  }

  /** Get transformer that forces helper injection onto bootstrap classloader. */
  private AgentBuilder.Transformer bootstrapHelperInjector(
      final Collection<DynamicType.Unloaded<?>> helpers) {
    return new AgentBuilder.Transformer() {
      final HelperInjector injector = HelperInjector.forDynamicTypes(helpers);

      @Override
      public DynamicType.Builder<?> transform(
          final DynamicType.Builder<?> builder,
          final TypeDescription typeDescription,
          final ClassLoader classLoader,
          final JavaModule module) {
        return injector.transform(
            builder,
            typeDescription,
            // context store implementation classes will always go to the bootstrap
            BOOTSTRAP_CLASSLOADER,
            module);
      }
    };
  }

  @Override
  public AgentBuilder.Identified.Extendable additionalInstrumentation(
      AgentBuilder.Identified.Extendable builder) {

    if (fieldInjectionEnabled) {
      for (final Map.Entry<String, String> entry : instrumenter.contextStore().entrySet()) {
        /**
         * For each context store defined in a current instrumentation we create an agent builder
         * that injects necessary fields.
         */
        builder =
            builder
                .type(
                    safeHasSuperType(named(entry.getKey())).and(not(isInterface())),
                    instrumenter.classLoaderMatcher())
                .and(safeToInjectFieldsMatcher())
                .transform(AgentBuilder.Transformer.NoOp.INSTANCE);

        /**
         * We inject helpers here as well as when instrumentation is applied to ensure that helpers
         * are present even if instrumented classes are not loaded, but classes with state fields
         * added are loaded (e.g. sun.net.www.protocol.https.HttpsURLConnectionImpl).
         */
        builder = injectHelpersIntoBootstrapClassloader(builder);

        builder =
            builder.transform(
                getTransformerForASMVisitor(
                    getFieldInjectionVisitor(entry.getKey(), entry.getValue())));
      }
    }
    return builder;
  }

  private static AgentBuilder.RawMatcher safeToInjectFieldsMatcher() {
    return new AgentBuilder.RawMatcher() {
      @Override
      public boolean matches(
          final TypeDescription typeDescription,
          final ClassLoader classLoader,
          final JavaModule module,
          final Class<?> classBeingRedefined,
          final ProtectionDomain protectionDomain) {
        /**
         * The idea here is that we can add fields if class is just being loaded
         * (classBeingRedefined == null) and we have to add same fields again if class we added
         * fields before is being transformed again. Note: here we assume that Class#getInterfaces()
         * returns list of interfaces defined immediately on a given class, not inherited from its
         * parents. It looks like current JVM implementation does exactly this but javadoc is not
         * explicit about that.
         */
        return classBeingRedefined == null
            || Arrays.asList(classBeingRedefined.getInterfaces())
                .contains(FieldBackedContextStoreAppliedMarker.class);
      }
    };
  }

  private AsmVisitorWrapper getFieldInjectionVisitor(
      final String keyClassName, final String contextClassName) {
    return new AsmVisitorWrapper() {

      @Override
      public int mergeWriter(final int flags) {
        return flags | ClassWriter.COMPUTE_MAXS;
      }

      @Override
      public int mergeReader(final int flags) {
        return flags;
      }

      @Override
      public ClassVisitor wrap(
          final TypeDescription instrumentedType,
          final ClassVisitor classVisitor,
          final Implementation.Context implementationContext,
          final TypePool typePool,
          final FieldList<FieldDescription.InDefinedShape> fields,
          final MethodList<?> methods,
          final int writerFlags,
          final int readerFlags) {
        return new ClassVisitor(Opcodes.ASM7, classVisitor) {
          // We are using Object class name instead of contextClassName here because this gets
          // injected onto Bootstrap classloader where context class may be unavailable
          private final TypeDescription contextType =
              new TypeDescription.ForLoadedType(Object.class);
          private final String fieldName = getContextFieldName(keyClassName);
          private final String getterMethodName = getContextGetterName(keyClassName);
          private final String setterMethodName = getContextSetterName(keyClassName);
          private final TypeDescription interfaceType =
              getFieldAccessorInterface(keyClassName, contextClassName);
          private boolean foundField = false;
          private boolean foundGetter = false;
          private boolean foundSetter = false;

          @Override
          public void visit(
              final int version,
              final int access,
              final String name,
              final String signature,
              final String superName,
              String[] interfaces) {
            if (interfaces == null) {
              interfaces = new String[] {};
            }
            final Set<String> set = new LinkedHashSet<>(Arrays.asList(interfaces));
            set.add(INJECTED_FIELDS_MARKER_CLASS_NAME);
            set.add(interfaceType.getInternalName());
            super.visit(version, access, name, signature, superName, set.toArray(new String[] {}));
          }

          @Override
          public FieldVisitor visitField(
              final int access,
              final String name,
              final String descriptor,
              final String signature,
              final Object value) {
            if (name.equals(fieldName)) {
              foundField = true;
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
            if (name.equals(getterMethodName)) {
              foundGetter = true;
            }
            if (name.equals(setterMethodName)) {
              foundSetter = true;
            }
            return super.visitMethod(access, name, descriptor, signature, exceptions);
          }

          @Override
          public void visitEnd() {
            // Checking only for field existence is not enough as libraries like CGLIB only copy
            // public/protected methods and not fields (neither public nor private ones) when
            // they enhance a class.
            // For this reason we check separately for the field and for the two accessors.
            if (!foundField) {
              cv.visitField(
                  // Field should be transient to avoid being serialized with the object.
                  Opcodes.ACC_PRIVATE | Opcodes.ACC_TRANSIENT,
                  fieldName,
                  contextType.getDescriptor(),
                  null,
                  null);
            }
            if (!foundGetter) {
              addGetter();
            }
            if (!foundSetter) {
              addSetter();
            }
            super.visitEnd();
          }

          /** Just 'standard' getter implementation */
          private void addGetter() {
            final MethodVisitor mv = getAccessorMethodVisitor(getterMethodName);
            mv.visitCode();
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitFieldInsn(
                Opcodes.GETFIELD,
                instrumentedType.getInternalName(),
                fieldName,
                contextType.getDescriptor());
            mv.visitInsn(Opcodes.ARETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
          }

          /** Just 'standard' setter implementation */
          private void addSetter() {
            final MethodVisitor mv = getAccessorMethodVisitor(setterMethodName);
            mv.visitCode();
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitVarInsn(Opcodes.ALOAD, 1);
            mv.visitFieldInsn(
                Opcodes.PUTFIELD,
                instrumentedType.getInternalName(),
                fieldName,
                contextType.getDescriptor());
            mv.visitInsn(Opcodes.RETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
          }

          private MethodVisitor getAccessorMethodVisitor(final String methodName) {
            return cv.visitMethod(
                Opcodes.ACC_PUBLIC,
                methodName,
                Utils.getMethodDefinition(interfaceType, methodName).getDescriptor(),
                null,
                null);
          }
        };
      }
    };
  }

  private TypeDescription getContextStoreImplementation(
      final String keyClassName, final String contextClassName) {
    final DynamicType.Unloaded<?> type =
        contextStoreImplementations.get(
            getContextStoreImplementationClassName(keyClassName, contextClassName));
    if (type == null) {
      return null;
    } else {
      return type.getTypeDescription();
    }
  }

  private Map<String, DynamicType.Unloaded<?>> generateContextStoreImplementationClasses() {
    final Map<String, DynamicType.Unloaded<?>> contextStoreImplementations =
        new HashMap<>(instrumenter.contextStore().size());
    for (final Map.Entry<String, String> entry : instrumenter.contextStore().entrySet()) {
      final DynamicType.Unloaded<?> type =
          makeContextStoreImplementationClass(entry.getKey(), entry.getValue());
      contextStoreImplementations.put(type.getTypeDescription().getName(), type);
    }
    return Collections.unmodifiableMap(contextStoreImplementations);
  }

  /**
   * Generate an 'implementation' of a context store classfor given key class name and context class
   * name
   *
   * @param keyClassName key class name
   * @param contextClassName context class name
   * @return unloaded dynamic type containing generated class
   */
  private DynamicType.Unloaded<?> makeContextStoreImplementationClass(
      final String keyClassName, final String contextClassName) {
    return byteBuddy
        .rebase(ContextStoreImplementationTemplate.class)
        .modifiers(Visibility.PUBLIC, TypeManifestation.FINAL)
        .name(getContextStoreImplementationClassName(keyClassName, contextClassName))
        .visit(getContextStoreImplementationVisitor(keyClassName, contextClassName))
        .make();
  }

  /**
   * Returns a visitor that 'fills in' missing methods into concrete implementation of
   * ContextStoreImplementationTemplate for given key class name and context class name
   *
   * @param keyClassName key class name
   * @param contextClassName context class name
   * @return visitor that adds implementation for methods that need to be generated
   */
  private AsmVisitorWrapper getContextStoreImplementationVisitor(
      final String keyClassName, final String contextClassName) {
    return new AsmVisitorWrapper() {

      @Override
      public int mergeWriter(final int flags) {
        return flags | ClassWriter.COMPUTE_MAXS;
      }

      @Override
      public int mergeReader(final int flags) {
        return flags;
      }

      @Override
      public ClassVisitor wrap(
          final TypeDescription instrumentedType,
          final ClassVisitor classVisitor,
          final Implementation.Context implementationContext,
          final TypePool typePool,
          final FieldList<FieldDescription.InDefinedShape> fields,
          final MethodList<?> methods,
          final int writerFlags,
          final int readerFlags) {
        return new ClassVisitor(Opcodes.ASM7, classVisitor) {

          private final TypeDescription accessorInterface =
              getFieldAccessorInterface(keyClassName, contextClassName);
          private final String accessorInterfaceInternalName = accessorInterface.getInternalName();
          private final String instrumentedTypeInternalName = instrumentedType.getInternalName();
          private final boolean frames =
              implementationContext.getClassFileVersion().isAtLeast(ClassFileVersion.JAVA_V6);

          @Override
          public MethodVisitor visitMethod(
              final int access,
              final String name,
              final String descriptor,
              final String signature,
              final String[] exceptions) {
            if ("realGet".equals(name)) {
              generateRealGetMethod(name);
              return null;
            } else if ("realPut".equals(name)) {
              generateRealPutMethod(name);
              return null;
            } else if ("realSynchronizeInstance".equals(name)) {
              generateRealSynchronizeInstanceMethod(name);
              return null;
            } else {
              return super.visitMethod(access, name, descriptor, signature, exceptions);
            }
          }

          /**
           * Provides implementation for {@code realGet} method that looks like this
           *
           * <blockquote>
           *
           * <pre>
           * private Object realGet(final Object key) {
           *   if (key instanceof $accessorInterfaceInternalName) {
           *     return (($accessorInterfaceInternalName) key).$getterName();
           *   } else {
           *     return mapGet(key);
           *   }
           * }
           * </pre>
           *
           * </blockquote>
           *
           * @param name name of the method being visited
           */
          private void generateRealGetMethod(final String name) {
            final String getterName = getContextGetterName(keyClassName);
            final Label elseLabel = new Label();
            final MethodVisitor mv = getMethodVisitor(name);
            mv.visitCode();
            mv.visitVarInsn(Opcodes.ALOAD, 1);
            mv.visitTypeInsn(Opcodes.INSTANCEOF, accessorInterfaceInternalName);
            mv.visitJumpInsn(Opcodes.IFEQ, elseLabel);
            mv.visitVarInsn(Opcodes.ALOAD, 1);
            mv.visitTypeInsn(Opcodes.CHECKCAST, accessorInterfaceInternalName);
            mv.visitMethodInsn(
                Opcodes.INVOKEINTERFACE,
                accessorInterfaceInternalName,
                getterName,
                Utils.getMethodDefinition(accessorInterface, getterName).getDescriptor(),
                true);
            mv.visitInsn(Opcodes.ARETURN);
            mv.visitLabel(elseLabel);
            if (frames) {
              mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
            }
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitVarInsn(Opcodes.ALOAD, 1);
            mv.visitMethodInsn(
                Opcodes.INVOKESPECIAL,
                instrumentedTypeInternalName,
                "mapGet",
                Utils.getMethodDefinition(instrumentedType, "mapGet").getDescriptor(),
                false);
            mv.visitInsn(Opcodes.ARETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
          }

          /**
           * Provides implementation for {@code realPut} method that looks like this
           *
           * <blockquote>
           *
           * <pre>
           * private void realPut(final Object key, final Object value) {
           *   if (key instanceof $accessorInterfaceInternalName) {
           *     (($accessorInterfaceInternalName) key).$setterName(value);
           *   } else {
           *     mapPut(key, value);
           *   }
           * }
           * </pre>
           *
           * </blockquote>
           *
           * @param name name of the method being visited
           */
          private void generateRealPutMethod(final String name) {
            final String setterName = getContextSetterName(keyClassName);
            final Label elseLabel = new Label();
            final Label endLabel = new Label();
            final MethodVisitor mv = getMethodVisitor(name);
            mv.visitCode();
            mv.visitVarInsn(Opcodes.ALOAD, 1);
            mv.visitTypeInsn(Opcodes.INSTANCEOF, accessorInterfaceInternalName);
            mv.visitJumpInsn(Opcodes.IFEQ, elseLabel);
            mv.visitVarInsn(Opcodes.ALOAD, 1);
            mv.visitTypeInsn(Opcodes.CHECKCAST, accessorInterfaceInternalName);
            mv.visitVarInsn(Opcodes.ALOAD, 2);
            mv.visitMethodInsn(
                Opcodes.INVOKEINTERFACE,
                accessorInterfaceInternalName,
                setterName,
                Utils.getMethodDefinition(accessorInterface, setterName).getDescriptor(),
                true);
            mv.visitJumpInsn(Opcodes.GOTO, endLabel);
            mv.visitLabel(elseLabel);
            if (frames) {
              mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
            }
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitVarInsn(Opcodes.ALOAD, 1);
            mv.visitVarInsn(Opcodes.ALOAD, 2);
            mv.visitMethodInsn(
                Opcodes.INVOKESPECIAL,
                instrumentedTypeInternalName,
                "mapPut",
                Utils.getMethodDefinition(instrumentedType, "mapPut").getDescriptor(),
                false);
            mv.visitLabel(endLabel);
            if (frames) {
              mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
            }
            mv.visitInsn(Opcodes.RETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
          }

          /**
           * Provides implementation for {@code realSynchronizeInstance} method that looks like this
           *
           * <blockquote>
           *
           * <pre>
           * private Object realSynchronizeInstance(final Object key) {
           *   if (key instanceof $accessorInterfaceInternalName) {
           *     return key;
           *   } else {
           *     return mapSynchronizeInstance(key);
           *   }
           * }
           * </pre>
           *
           * </blockquote>
           *
           * @param name name of the method being visited
           */
          private void generateRealSynchronizeInstanceMethod(final String name) {
            final MethodVisitor mv = getMethodVisitor(name);
            mv.visitCode();
            mv.visitVarInsn(Opcodes.ALOAD, 1);
            mv.visitTypeInsn(Opcodes.INSTANCEOF, accessorInterfaceInternalName);
            final Label elseLabel = new Label();
            mv.visitJumpInsn(Opcodes.IFEQ, elseLabel);
            mv.visitVarInsn(Opcodes.ALOAD, 1);
            mv.visitInsn(Opcodes.ARETURN);
            mv.visitLabel(elseLabel);
            if (frames) {
              mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
            }
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitVarInsn(Opcodes.ALOAD, 1);
            mv.visitMethodInsn(
                Opcodes.INVOKESPECIAL,
                instrumentedTypeInternalName,
                "mapSynchronizeInstance",
                Utils.getMethodDefinition(instrumentedType, "mapSynchronizeInstance")
                    .getDescriptor(),
                false);
            mv.visitInsn(Opcodes.ARETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
          }

          private MethodVisitor getMethodVisitor(final String methodName) {
            return cv.visitMethod(
                Opcodes.ACC_PRIVATE,
                methodName,
                Utils.getMethodDefinition(instrumentedType, methodName).getDescriptor(),
                null,
                null);
          }
        };
      }
    };
  }

  /**
   * Template class used to generate the class that accesses stored context using either key
   * instance's own injected field or global hash map if field is not available.
   */
  private static final class ContextStoreImplementationTemplate
      implements ContextStore<Object, Object> {
    private static final ContextStoreImplementationTemplate INSTANCE =
        new ContextStoreImplementationTemplate(WeakMap.Provider.newWeakMap());

    private final WeakMap map;

    private ContextStoreImplementationTemplate(final WeakMap map) {
      this.map = map;
    }

    @Override
    public Object get(final Object key) {
      return realGet(key);
    }

    @Override
    public Object putIfAbsent(final Object key, final Object context) {
      Object existingContext = realGet(key);
      if (null != existingContext) {
        return existingContext;
      }
      synchronized (realSynchronizeInstance(key)) {
        existingContext = realGet(key);
        if (null != existingContext) {
          return existingContext;
        }
        realPut(key, context);
        return context;
      }
    }

    @Override
    public Object putIfAbsent(final Object key, final Factory<Object> contextFactory) {
      Object existingContext = realGet(key);
      if (null != existingContext) {
        return existingContext;
      }
      synchronized (realSynchronizeInstance(key)) {
        existingContext = realGet(key);
        if (null != existingContext) {
          return existingContext;
        }
        final Object context = contextFactory.create();
        realPut(key, context);
        return context;
      }
    }

    @Override
    public void put(final Object key, final Object context) {
      realPut(key, context);
    }

    private Object realGet(final Object key) {
      // to be generated
      return null;
    }

    private void realPut(final Object key, final Object value) {
      // to be generated
    }

    private Object realSynchronizeInstance(final Object key) {
      // to be generated
      return null;
    }

    private Object mapGet(final Object key) {
      return map.get(key);
    }

    private void mapPut(final Object key, final Object value) {
      map.put(key, value);
    }

    private Object mapSynchronizeInstance(final Object key) {
      return map;
    }

    public static ContextStore getContextStore(final Class keyClass, final Class contextClass) {
      // We do not actually check the keyClass here - but that should be fine since compiler would
      // check things for us.
      return INSTANCE;
    }
  }

  private TypeDescription getFieldAccessorInterface(
      final String keyClassName, final String contextClassName) {
    final DynamicType.Unloaded<?> type =
        fieldAccessorInterfaces.get(
            getContextAccessorInterfaceName(keyClassName, contextClassName));
    if (type == null) {
      return null;
    } else {
      return type.getTypeDescription();
    }
  }

  private Map<String, DynamicType.Unloaded<?>> generateFieldAccessorInterfaces() {
    final Map<String, DynamicType.Unloaded<?>> fieldAccessorInterfaces =
        new HashMap<>(instrumenter.contextStore().size());
    for (final Map.Entry<String, String> entry : instrumenter.contextStore().entrySet()) {
      final DynamicType.Unloaded<?> type =
          makeFieldAccessorInterface(entry.getKey(), entry.getValue());
      fieldAccessorInterfaces.put(type.getTypeDescription().getName(), type);
    }
    return Collections.unmodifiableMap(fieldAccessorInterfaces);
  }

  /**
   * Generate an interface that provides field accessor methods for given key class name and context
   * class name
   *
   * @param keyClassName key class name
   * @param contextClassName context class name
   * @return unloaded dynamic type containing generated interface
   */
  private DynamicType.Unloaded<?> makeFieldAccessorInterface(
      final String keyClassName, final String contextClassName) {
    // We are using Object class name instead of contextClassName here because this gets injected
    // onto Bootstrap classloader where context class may be unavailable
    final TypeDescription contextType = new TypeDescription.ForLoadedType(Object.class);
    return byteBuddy
        .makeInterface()
        .name(getContextAccessorInterfaceName(keyClassName, contextClassName))
        .defineMethod(getContextGetterName(keyClassName), contextType, Visibility.PUBLIC)
        .withoutCode()
        .defineMethod(getContextSetterName(keyClassName), TypeDescription.VOID, Visibility.PUBLIC)
        .withParameter(contextType, "value")
        .withoutCode()
        .make();
  }

  private AgentBuilder.Transformer getTransformerForASMVisitor(final AsmVisitorWrapper visitor) {
    return new AgentBuilder.Transformer() {
      @Override
      public DynamicType.Builder<?> transform(
          final DynamicType.Builder<?> builder,
          final TypeDescription typeDescription,
          final ClassLoader classLoader,
          final JavaModule module) {
        return builder.visit(visitor);
      }
    };
  }

  private String getContextStoreImplementationClassName(
      final String keyClassName, final String contextClassName) {
    return DYNAMIC_CLASSES_PACKAGE
        + getClass().getSimpleName()
        + "$ContextStore$"
        + Utils.converToInnerClassName(keyClassName)
        + "$"
        + Utils.converToInnerClassName(contextClassName);
  }

  private String getContextAccessorInterfaceName(
      final String keyClassName, final String contextClassName) {
    return DYNAMIC_CLASSES_PACKAGE
        + getClass().getSimpleName()
        + "$ContextAccessor$"
        + Utils.converToInnerClassName(keyClassName)
        + "$"
        + Utils.converToInnerClassName(contextClassName);
  }

  private static String getContextFieldName(final String keyClassName) {
    return "__datadogContext$" + Utils.converToInnerClassName(keyClassName);
  }

  private static String getContextGetterName(final String keyClassName) {
    return "get" + getContextFieldName(keyClassName);
  }

  private static String getContextSetterName(final String key) {
    return "set" + getContextFieldName(key);
  }
}
