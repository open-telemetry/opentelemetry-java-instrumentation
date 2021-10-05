/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.field;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isAbstract;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;

import io.opentelemetry.instrumentation.api.caching.Cache;
import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.instrumentation.api.field.VirtualField;
import io.opentelemetry.javaagent.bootstrap.InstrumentationHolder;
import io.opentelemetry.javaagent.bootstrap.VirtualFieldInstalledMarker;
import io.opentelemetry.javaagent.tooling.HelperInjector;
import io.opentelemetry.javaagent.tooling.TransformSafeLogger;
import io.opentelemetry.javaagent.tooling.Utils;
import io.opentelemetry.javaagent.tooling.instrumentation.InstrumentationModuleInstaller;
import io.opentelemetry.javaagent.tooling.muzzle.VirtualFieldMappings;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.ProtectionDomain;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.ClassFileVersion;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.AsmVisitorWrapper;
import net.bytebuddy.description.field.FieldDescription;
import net.bytebuddy.description.field.FieldList;
import net.bytebuddy.description.method.MethodList;
import net.bytebuddy.description.modifier.SyntheticState;
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
 * A {@link VirtualFieldImplementationInstaller} which stores context in a field that is injected
 * into a class and falls back to global map if field was not injected.
 *
 * <p>This is accomplished by
 *
 * <ol>
 *   <li>Injecting a Dynamic Interface that provides getter and setter for context field
 *   <li>Applying Dynamic Interface to a type needing context, implementing interface methods and
 *       adding context storage field
 *   <li>Injecting a Dynamic Class created from {@link VirtualFieldImplementationTemplate} to use
 *       injected field or fall back to a static map
 *   <li>Rewriting calls to the context-store to access the specific dynamic {@link
 *       VirtualFieldImplementationTemplate}
 * </ol>
 *
 * <p>Example:<br>
 * <em>VirtualField.find(Runnable.class, RunnableState.class)</em><br>
 * is rewritten to:<br>
 * <em>FieldBackedImplementation$VirtualField$Runnable$RunnableState12345.getVirtualField(Runnable.class,
 * RunnableState.class)</em>
 */
public class FieldBackedImplementationInstaller implements VirtualFieldImplementationInstaller {

  private static final TransformSafeLogger logger =
      TransformSafeLogger.getLogger(FieldBackedImplementationInstaller.class);

  /**
   * Note: the value here has to be inside on of the prefixes in {@link
   * io.opentelemetry.javaagent.tooling.Constants#BOOTSTRAP_PACKAGE_PREFIXES}. This ensures that
   * 'isolating' (or 'module') classloaders like jboss and osgi see injected classes. This works
   * because we instrument those classloaders to load everything inside bootstrap packages.
   */
  private static final String DYNAMIC_CLASSES_PACKAGE =
      "io.opentelemetry.javaagent.bootstrap.instrumentation.context.";

  private static final String INSTALLED_FIELDS_MARKER_CLASS_NAME =
      Utils.getInternalName(VirtualFieldInstalledMarker.class);

  private static final Method FIND_VIRTUAL_FIELD_METHOD;
  private static final Method FIND_VIRTUAL_FIELD_IMPL_METHOD;

  static {
    try {
      FIND_VIRTUAL_FIELD_METHOD = VirtualField.class.getMethod("find", Class.class, Class.class);
      FIND_VIRTUAL_FIELD_IMPL_METHOD =
          VirtualFieldImplementationTemplate.class.getMethod(
              "getVirtualField", Class.class, Class.class);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private static final boolean FIELD_INJECTION_ENABLED =
      Config.get().getBoolean("otel.javaagent.experimental.field-injection.enabled", true);

  private final Class<?> instrumenterClass;
  private final ByteBuddy byteBuddy;
  private final VirtualFieldMappings virtualFieldMappings;

  // fields-accessor-interface-name -> fields-accessor-interface-dynamic-type
  private final Map<String, DynamicType.Unloaded<?>> fieldAccessorInterfaces;

  private final AgentBuilder.Transformer fieldAccessorInterfacesInjector;

  // context-store-type-name -> context-store-type-name-dynamic-type
  private final Map<String, DynamicType.Unloaded<?>> virtualFieldImplementations;

  private final AgentBuilder.Transformer virtualFieldImplementationsInjector;

  private final Instrumentation instrumentation;

  public FieldBackedImplementationInstaller(
      Class<?> instrumenterClass, VirtualFieldMappings virtualFieldMappings) {
    this.instrumenterClass = instrumenterClass;
    this.virtualFieldMappings = virtualFieldMappings;
    // This class is used only when running with javaagent, thus this calls is safe
    this.instrumentation = InstrumentationHolder.getInstrumentation();

    byteBuddy = new ByteBuddy();
    fieldAccessorInterfaces = generateFieldAccessorInterfaces();
    fieldAccessorInterfacesInjector = bootstrapHelperInjector(fieldAccessorInterfaces.values());
    virtualFieldImplementations = generateVirtualFieldImplementationClasses();
    virtualFieldImplementationsInjector =
        bootstrapHelperInjector(virtualFieldImplementations.values());
  }

  public static <Q extends K, K, C> VirtualField<Q, C> findVirtualField(
      Class<K> type, Class<C> fieldType) {
    try {
      String virtualFieldImplClassName =
          getVirtualFieldImplementationClassName(type.getName(), fieldType.getName());
      Class<?> contextStoreClass = Class.forName(virtualFieldImplClassName, false, null);
      Method method = contextStoreClass.getMethod("getVirtualField", Class.class, Class.class);
      return (VirtualField<Q, C>) method.invoke(null, type, fieldType);
    } catch (ClassNotFoundException exception) {
      throw new IllegalStateException("VirtualField not found", exception);
    } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException exception) {
      throw new IllegalStateException("Failed to get VirtualField", exception);
    }
  }

  @Override
  public AgentBuilder.Identified.Extendable rewriteVirtualFieldsCalls(
      AgentBuilder.Identified.Extendable builder) {
    if (!virtualFieldMappings.isEmpty()) {
      /*
       * Install transformer that rewrites accesses to context store with specialized bytecode that
       * invokes appropriate storage implementation.
       */
      builder =
          builder.transform(getTransformerForAsmVisitor(getVirtualFieldFindsRewritingVisitor()));
      builder = injectHelpersIntoBootstrapClassloader(builder);
    }
    return builder;
  }

  private AsmVisitorWrapper getVirtualFieldFindsRewritingVisitor() {
    return new AsmVisitorWrapper() {
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
        return new ClassVisitor(Opcodes.ASM7, classVisitor) {
          @Override
          public MethodVisitor visitMethod(
              int access, String name, String descriptor, String signature, String[] exceptions) {
            MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
            return new MethodVisitor(Opcodes.ASM7, mv) {
              /** The most recent objects pushed to the stack. */
              private final Object[] stack = {null, null};
              /** Most recent instructions. */
              private final int[] insnStack = {-1, -1, -1};

              @Override
              public void visitMethodInsn(
                  int opcode, String owner, String name, String descriptor, boolean isInterface) {
                pushOpcode(opcode);
                if (Utils.getInternalName(FIND_VIRTUAL_FIELD_METHOD.getDeclaringClass())
                        .equals(owner)
                    && FIND_VIRTUAL_FIELD_METHOD.getName().equals(name)
                    && Type.getMethodDescriptor(FIND_VIRTUAL_FIELD_METHOD).equals(descriptor)) {
                  logger.trace(
                      "Found VirtualField#find() access in {}", instrumenterClass.getName());
                  /*
                  The idea here is that the rest if this method visitor collects last three instructions in `insnStack`
                  variable. Once we get here we check if those last three instructions constitute call that looks like
                  `VirtualField.find(K.class, V.class)`. If it does the inside of this if rewrites it to call
                  dynamically injected context store implementation instead.
                   */
                  if ((insnStack[0] == Opcodes.INVOKESTATIC
                          && insnStack[1] == Opcodes.LDC
                          && insnStack[2] == Opcodes.LDC)
                      && (stack[0] instanceof Type && stack[1] instanceof Type)) {
                    String fieldTypeName = ((Type) stack[0]).getClassName();
                    String typeName = ((Type) stack[1]).getClassName();
                    TypeDescription virtualFieldImplementationClass =
                        getVirtualFieldImplementation(typeName, fieldTypeName);
                    if (logger.isTraceEnabled()) {
                      logger.trace(
                          "Rewriting VirtualField#find() for instrumenter {}: {} -> {}",
                          instrumenterClass.getName(),
                          typeName,
                          fieldTypeName);
                    }
                    if (virtualFieldImplementationClass == null) {
                      throw new IllegalStateException(
                          String.format(
                              "Incorrect VirtualField usage detected. Cannot find implementation for VirtualField<%s, %s>. Was that field registered in %s#registerMuzzleVirtualFields()?",
                              typeName, fieldTypeName, instrumenterClass.getName()));
                    }
                    if (!virtualFieldMappings.hasMapping(typeName, fieldTypeName)) {
                      throw new IllegalStateException(
                          String.format(
                              "Incorrect VirtualField usage detected. Cannot find mapping for VirtualField<%s, %s>. Was that field registered in %s#registerMuzzleVirtualFields()?",
                              typeName, fieldTypeName, instrumenterClass.getName()));
                    }
                    // stack: fieldType | type
                    mv.visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        virtualFieldImplementationClass.getInternalName(),
                        FIND_VIRTUAL_FIELD_IMPL_METHOD.getName(),
                        Type.getMethodDescriptor(FIND_VIRTUAL_FIELD_IMPL_METHOD),
                        /* isInterface= */ false);
                    return;
                  }
                  throw new IllegalStateException(
                      "Incorrect VirtualField usage detected. Type and field type must be class-literals. Example of correct usage: VirtualField.find(Runnable.class, RunnableContext.class)");
                } else {
                  super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                }
              }

              /** Tracking the most recently used opcodes to assert proper api usage. */
              private void pushOpcode(int opcode) {
                System.arraycopy(insnStack, 0, insnStack, 1, insnStack.length - 1);
                insnStack[0] = opcode;
              }

              /**
               * Tracking the most recently pushed objects on the stack to assert proper api usage.
               */
              private void pushStack(Object o) {
                System.arraycopy(stack, 0, stack, 1, stack.length - 1);
                stack[0] = o;
              }

              @Override
              public void visitInsn(int opcode) {
                pushOpcode(opcode);
                super.visitInsn(opcode);
              }

              @Override
              public void visitJumpInsn(int opcode, Label label) {
                pushOpcode(opcode);
                super.visitJumpInsn(opcode, label);
              }

              @Override
              public void visitIntInsn(int opcode, int operand) {
                pushOpcode(opcode);
                super.visitIntInsn(opcode, operand);
              }

              @Override
              public void visitVarInsn(int opcode, int var) {
                pushOpcode(opcode);
                pushStack(var);
                super.visitVarInsn(opcode, var);
              }

              @Override
              public void visitLdcInsn(Object value) {
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
    /*
     * We inject into bootstrap classloader because field accessor interfaces are needed by virtual
     * field implementations. Unfortunately this forces us to remove stored type checking because
     * actual classes may not be available at this point.
     */
    builder = builder.transform(fieldAccessorInterfacesInjector);

    /*
     * We inject virtual field implementations into bootstrap classloader because same implementation
     * may be used by different instrumentations and it has to use same static map in case of
     * fallback to map-backed storage.
     */
    builder = builder.transform(virtualFieldImplementationsInjector);
    return builder;
  }

  /** Get transformer that forces helper injection onto bootstrap classloader. */
  private AgentBuilder.Transformer bootstrapHelperInjector(
      Collection<DynamicType.Unloaded<?>> helpers) {
    // TODO: Better to pass through the context of the Instrumenter
    return new AgentBuilder.Transformer() {
      final HelperInjector injector =
          HelperInjector.forDynamicTypes(getClass().getSimpleName(), helpers, instrumentation);

      @Override
      public DynamicType.Builder<?> transform(
          DynamicType.Builder<?> builder,
          TypeDescription typeDescription,
          ClassLoader classLoader,
          JavaModule module) {
        return injector.transform(
            builder,
            typeDescription,
            // virtual field implementation classes will always go to the bootstrap
            null,
            module);
      }
    };
  }

  /*
  Set of pairs (type name, field type name) for which we have matchers installed.
  We use this to make sure we do not install matchers repeatedly for cases when same
  context class is used by multiple instrumentations.
   */
  private static final Set<Map.Entry<String, String>> INSTALLED_VIRTUAL_FIELD_MATCHERS =
      new HashSet<>();

  /** Clear set that prevents multiple matchers for same context class. */
  public static void resetContextMatchers() {
    synchronized (INSTALLED_VIRTUAL_FIELD_MATCHERS) {
      INSTALLED_VIRTUAL_FIELD_MATCHERS.clear();
    }
  }

  @Override
  public AgentBuilder.Identified.Extendable installFields(
      AgentBuilder.Identified.Extendable builder) {

    if (FIELD_INJECTION_ENABLED) {
      for (Map.Entry<String, String> entry : virtualFieldMappings.entrySet()) {
        /*
         * For each virtual field defined in a current instrumentation we create an agent builder
         * that injects necessary fields.
         * Note: this synchronization should not have any impact on performance
         * since this is done when agent builder is being made, it doesn't affect actual
         * class transformation.
         */
        synchronized (INSTALLED_VIRTUAL_FIELD_MATCHERS) {
          if (INSTALLED_VIRTUAL_FIELD_MATCHERS.contains(entry)) {
            logger.trace("Skipping builder for {} {}", instrumenterClass.getName(), entry);
            continue;
          }

          logger.trace("Making builder for {} {}", instrumenterClass.getName(), entry);
          INSTALLED_VIRTUAL_FIELD_MATCHERS.add(entry);

          /*
           * For each virtual field defined in a current instrumentation we create an agent builder
           * that injects necessary fields.
           */
          builder =
              builder
                  .type(not(isAbstract()).and(hasSuperType(named(entry.getKey()))))
                  .and(safeToInjectFieldsMatcher())
                  .and(InstrumentationModuleInstaller.NOT_DECORATOR_MATCHER)
                  .transform(NoOpTransformer.INSTANCE);

          /*
           * We inject helpers here as well as when instrumentation is applied to ensure that
           * helpers are present even if instrumented classes are not loaded, but classes with state
           * fields added are loaded (e.g. sun.net.www.protocol.https.HttpsURLConnectionImpl).
           */
          builder = injectHelpersIntoBootstrapClassloader(builder);

          builder =
              builder.transform(
                  getTransformerForAsmVisitor(
                      getFieldInjectionVisitor(entry.getKey(), entry.getValue())));
        }
      }
    }
    return builder;
  }

  private static AgentBuilder.RawMatcher safeToInjectFieldsMatcher() {
    return new AgentBuilder.RawMatcher() {
      @Override
      public boolean matches(
          TypeDescription typeDescription,
          ClassLoader classLoader,
          JavaModule module,
          Class<?> classBeingRedefined,
          ProtectionDomain protectionDomain) {
        /*
         * The idea here is that we can add fields if class is just being loaded
         * (classBeingRedefined == null) and we have to add same fields again if class we added
         * fields before is being transformed again. Note: here we assume that Class#getInterfaces()
         * returns list of interfaces defined immediately on a given class, not inherited from its
         * parents. It looks like current JVM implementation does exactly this but javadoc is not
         * explicit about that.
         */
        return classBeingRedefined == null
            || Arrays.asList(classBeingRedefined.getInterfaces())
                .contains(VirtualFieldInstalledMarker.class);
      }
    };
  }

  private AsmVisitorWrapper getFieldInjectionVisitor(String typeName, String fieldTypeName) {
    return new AsmVisitorWrapper() {

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
        return new ClassVisitor(Opcodes.ASM7, classVisitor) {
          // We are using Object class name instead of fieldTypeName here because this gets
          // injected onto Bootstrap classloader where context class may be unavailable
          private final TypeDescription contextType =
              new TypeDescription.ForLoadedType(Object.class);
          private final String fieldName = getRealFieldName(typeName);
          private final String getterMethodName = getRealGetterName(typeName);
          private final String setterMethodName = getRealSetterName(typeName);
          private final TypeDescription interfaceType =
              getFieldAccessorInterface(typeName, fieldTypeName);
          private boolean foundField = false;
          private boolean foundGetter = false;
          private boolean foundSetter = false;

          @Override
          public void visit(
              int version,
              int access,
              String name,
              String signature,
              String superName,
              String[] interfaces) {
            if (interfaces == null) {
              interfaces = new String[] {};
            }
            Set<String> set = new LinkedHashSet<>(Arrays.asList(interfaces));
            set.add(INSTALLED_FIELDS_MARKER_CLASS_NAME);
            set.add(interfaceType.getInternalName());
            super.visit(version, access, name, signature, superName, set.toArray(new String[] {}));
          }

          @Override
          public FieldVisitor visitField(
              int access, String name, String descriptor, String signature, Object value) {
            if (name.equals(fieldName)) {
              foundField = true;
            }
            return super.visitField(access, name, descriptor, signature, value);
          }

          @Override
          public MethodVisitor visitMethod(
              int access, String name, String descriptor, String signature, String[] exceptions) {
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
                  Opcodes.ACC_PRIVATE | Opcodes.ACC_TRANSIENT | Opcodes.ACC_SYNTHETIC,
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

          // just 'standard' getter implementation
          private void addGetter() {
            MethodVisitor mv = getAccessorMethodVisitor(getterMethodName);
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

          // just 'standard' setter implementation
          private void addSetter() {
            MethodVisitor mv = getAccessorMethodVisitor(setterMethodName);
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

          private MethodVisitor getAccessorMethodVisitor(String methodName) {
            return cv.visitMethod(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_SYNTHETIC,
                methodName,
                Utils.getMethodDefinition(interfaceType, methodName).getDescriptor(),
                null,
                null);
          }
        };
      }
    };
  }

  private TypeDescription getVirtualFieldImplementation(
      String keyClassName, String contextClassName) {
    DynamicType.Unloaded<?> type =
        virtualFieldImplementations.get(
            getVirtualFieldImplementationClassName(keyClassName, contextClassName));
    if (type == null) {
      return null;
    } else {
      return type.getTypeDescription();
    }
  }

  private Map<String, DynamicType.Unloaded<?>> generateVirtualFieldImplementationClasses() {
    Map<String, DynamicType.Unloaded<?>> virtualFieldImplementations =
        new HashMap<>(virtualFieldMappings.size());
    for (Map.Entry<String, String> entry : virtualFieldMappings.entrySet()) {
      DynamicType.Unloaded<?> type =
          makeVirtualFieldImplementationClass(entry.getKey(), entry.getValue());
      virtualFieldImplementations.put(type.getTypeDescription().getName(), type);
    }
    return Collections.unmodifiableMap(virtualFieldImplementations);
  }

  /**
   * Generate an 'implementation' of a context store class for given key class name and context
   * class name.
   *
   * @param keyClassName key class name
   * @param contextClassName context class name
   * @return unloaded dynamic type containing generated class
   */
  private DynamicType.Unloaded<?> makeVirtualFieldImplementationClass(
      String keyClassName, String contextClassName) {
    return byteBuddy
        .rebase(VirtualFieldImplementationTemplate.class)
        .modifiers(Visibility.PUBLIC, TypeManifestation.FINAL, SyntheticState.SYNTHETIC)
        .name(getVirtualFieldImplementationClassName(keyClassName, contextClassName))
        .visit(getVirtualFieldImplementationVisitor(keyClassName, contextClassName))
        .make();
  }

  /**
   * Returns a visitor that 'fills in' missing methods into concrete implementation of {@link
   * VirtualFieldImplementationTemplate} for given key class name and context class name.
   *
   * @param keyClassName key class name
   * @param contextClassName context class name
   * @return visitor that adds implementation for methods that need to be generated
   */
  private AsmVisitorWrapper getVirtualFieldImplementationVisitor(
      String keyClassName, String contextClassName) {
    return new AsmVisitorWrapper() {

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
        return new ClassVisitor(Opcodes.ASM7, classVisitor) {

          private final TypeDescription accessorInterface =
              getFieldAccessorInterface(keyClassName, contextClassName);
          private final String accessorInterfaceInternalName = accessorInterface.getInternalName();
          private final String instrumentedTypeInternalName = instrumentedType.getInternalName();
          private final boolean frames =
              implementationContext.getClassFileVersion().isAtLeast(ClassFileVersion.JAVA_V6);

          @Override
          public MethodVisitor visitMethod(
              int access, String name, String descriptor, String signature, String[] exceptions) {
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
           * Provides implementation for {@code realGet} method that looks like below.
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
          private void generateRealGetMethod(String name) {
            String getterName = getRealGetterName(keyClassName);
            Label elseLabel = new Label();
            MethodVisitor mv = getMethodVisitor(name);
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
                /* isInterface= */ true);
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
                /* isInterface= */ false);
            mv.visitInsn(Opcodes.ARETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
          }

          /**
           * Provides implementation for {@code realPut} method that looks like below.
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
          private void generateRealPutMethod(String name) {
            String setterName = getRealSetterName(keyClassName);
            Label elseLabel = new Label();
            Label endLabel = new Label();
            MethodVisitor mv = getMethodVisitor(name);
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
                /* isInterface= */ true);
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
                /* isInterface= */ false);
            mv.visitLabel(endLabel);
            if (frames) {
              mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
            }
            mv.visitInsn(Opcodes.RETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
          }

          /**
           * Provides implementation for {@code realSynchronizeInstance} method that looks like
           * below.
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
          private void generateRealSynchronizeInstanceMethod(String name) {
            MethodVisitor mv = getMethodVisitor(name);
            mv.visitCode();
            mv.visitVarInsn(Opcodes.ALOAD, 1);
            mv.visitTypeInsn(Opcodes.INSTANCEOF, accessorInterfaceInternalName);
            Label elseLabel = new Label();
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
                /* isInterface= */ false);
            mv.visitInsn(Opcodes.ARETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
          }

          private MethodVisitor getMethodVisitor(String methodName) {
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
  // Called from generated code
  @SuppressWarnings({"UnusedMethod", "UnusedVariable", "MethodCanBeStatic"})
  private static final class VirtualFieldImplementationTemplate
      extends VirtualField<Object, Object> {
    private static final VirtualFieldImplementationTemplate INSTANCE =
        new VirtualFieldImplementationTemplate(Cache.newBuilder().setWeakKeys().build());

    private final Cache<Object, Object> map;

    private VirtualFieldImplementationTemplate(Cache<Object, Object> map) {
      this.map = map;
    }

    @Override
    public Object get(Object object) {
      return realGet(object);
    }

    @Override
    public Object computeIfNull(Object object, Supplier<Object> fieldValueSupplier) {
      Object existingContext = realGet(object);
      if (null != existingContext) {
        return existingContext;
      }
      synchronized (realSynchronizeInstance(object)) {
        existingContext = realGet(object);
        if (null != existingContext) {
          return existingContext;
        }
        Object context = fieldValueSupplier.get();
        realPut(object, context);
        return context;
      }
    }

    @Override
    public void set(Object object, Object fieldValue) {
      realPut(object, fieldValue);
    }

    private Object realGet(Object key) {
      // to be generated
      return null;
    }

    private void realPut(Object key, Object value) {
      // to be generated
    }

    private Object realSynchronizeInstance(Object key) {
      // to be generated
      return null;
    }

    private Object mapGet(Object key) {
      return map.get(key);
    }

    private void mapPut(Object key, Object value) {
      if (value == null) {
        map.remove(key);
      } else {
        map.put(key, value);
      }
    }

    private Object mapSynchronizeInstance(Object key) {
      return map;
    }

    public static VirtualField getVirtualField(Class keyClass, Class contextClass) {
      // We do not actually check the keyClass here - but that should be fine since compiler would
      // check things for us.
      return INSTANCE;
    }
  }

  private TypeDescription getFieldAccessorInterface(String keyClassName, String contextClassName) {
    DynamicType.Unloaded<?> type =
        fieldAccessorInterfaces.get(
            getContextAccessorInterfaceName(keyClassName, contextClassName));
    if (type == null) {
      return null;
    } else {
      return type.getTypeDescription();
    }
  }

  private Map<String, DynamicType.Unloaded<?>> generateFieldAccessorInterfaces() {
    Map<String, DynamicType.Unloaded<?>> fieldAccessorInterfaces =
        new HashMap<>(virtualFieldMappings.size());
    for (Map.Entry<String, String> entry : virtualFieldMappings.entrySet()) {
      DynamicType.Unloaded<?> type = makeFieldAccessorInterface(entry.getKey(), entry.getValue());
      fieldAccessorInterfaces.put(type.getTypeDescription().getName(), type);
    }
    return Collections.unmodifiableMap(fieldAccessorInterfaces);
  }

  /**
   * Generate an interface that provides field accessor methods for given key class name and context
   * class name.
   *
   * @param keyClassName key class name
   * @param contextClassName context class name
   * @return unloaded dynamic type containing generated interface
   */
  private DynamicType.Unloaded<?> makeFieldAccessorInterface(
      String keyClassName, String contextClassName) {
    // We are using Object class name instead of contextClassName here because this gets injected
    // onto Bootstrap classloader where context class may be unavailable
    TypeDescription contextType = new TypeDescription.ForLoadedType(Object.class);
    return byteBuddy
        .makeInterface()
        .merge(SyntheticState.SYNTHETIC)
        .name(getContextAccessorInterfaceName(keyClassName, contextClassName))
        .defineMethod(getRealGetterName(keyClassName), contextType, Visibility.PUBLIC)
        .withoutCode()
        .defineMethod(getRealSetterName(keyClassName), TypeDescription.VOID, Visibility.PUBLIC)
        .withParameter(contextType, "value")
        .withoutCode()
        .make();
  }

  private static AgentBuilder.Transformer getTransformerForAsmVisitor(AsmVisitorWrapper visitor) {
    return (builder, typeDescription, classLoader, module) -> builder.visit(visitor);
  }

  private static String getVirtualFieldImplementationClassName(
      String keyClassName, String contextClassName) {
    return DYNAMIC_CLASSES_PACKAGE
        + FieldBackedImplementationInstaller.class.getSimpleName()
        + "$VirtualField$"
        + Utils.convertToInnerClassName(keyClassName)
        + "$"
        + Utils.convertToInnerClassName(contextClassName);
  }

  private String getContextAccessorInterfaceName(String keyClassName, String contextClassName) {
    return DYNAMIC_CLASSES_PACKAGE
        + getClass().getSimpleName()
        + "$VirtualFieldAccessor$"
        + Utils.convertToInnerClassName(keyClassName)
        + "$"
        + Utils.convertToInnerClassName(contextClassName);
  }

  private static String getRealFieldName(String keyClassName) {
    return "__opentelemetryVirtualField$" + Utils.convertToInnerClassName(keyClassName);
  }

  private static String getRealGetterName(String keyClassName) {
    return "get" + getRealFieldName(keyClassName);
  }

  private static String getRealSetterName(String key) {
    return "set" + getRealFieldName(key);
  }

  // Originally found in AgentBuilder.Transformer.NoOp, but removed in 1.10.7
  enum NoOpTransformer implements AgentBuilder.Transformer {
    INSTANCE;

    @Override
    public DynamicType.Builder<?> transform(
        DynamicType.Builder<?> builder,
        TypeDescription typeDescription,
        ClassLoader classLoader,
        JavaModule module) {
      return builder;
    }
  }
}
