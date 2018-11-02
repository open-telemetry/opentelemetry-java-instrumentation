package datadog.trace.agent.tooling.context;

import static datadog.trace.agent.tooling.ClassLoaderMatcher.BOOTSTRAP_CLASSLOADER;

import datadog.trace.agent.tooling.HelperInjector;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.agent.tooling.Utils;
import datadog.trace.bootstrap.ContextStore;
import datadog.trace.bootstrap.InstrumentationContext;
import datadog.trace.bootstrap.WeakMap;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import jdk.internal.org.objectweb.asm.ClassWriter;
import lombok.extern.slf4j.Slf4j;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.AsmVisitorWrapper;
import net.bytebuddy.description.field.FieldDescription;
import net.bytebuddy.description.field.FieldList;
import net.bytebuddy.description.method.MethodList;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.implementation.Implementation;
import net.bytebuddy.jar.asm.ClassVisitor;
import net.bytebuddy.jar.asm.Label;
import net.bytebuddy.jar.asm.MethodVisitor;
import net.bytebuddy.jar.asm.Opcodes;
import net.bytebuddy.jar.asm.Type;
import net.bytebuddy.pool.TypePool;
import net.bytebuddy.utility.JavaModule;

/**
 * InstrumentationContextProvider which stores context in a global map.
 *
 * <p>This is accomplished by
 *
 * <ol>
 *   <li>Injecting a Dynamic Class created from {@link MapHolder} to store a static map
 *   <li>Rewritting calls to the context-store to access the specific dynamic {@link MapHolder}
 * </ol>
 *
 * Storing the map on a dynamic class and doing bytecode rewrites allows for a 1-pass lookup.
 * Without bytecode transformations a 2-pass lookup would be required.
 *
 * <p>Example:<br>
 * <em>InstrumentationContext.get(runnableInstance, Runnable.class, RunnableState.class)")</em><br>
 * is rewritten to:<br>
 * <em>RunnableInstrumentation$ContextStore$RunnableState12345.getMapHolder(runnableRunnable.class,
 * RunnableState.class)</em>
 */
@Slf4j
public class MapBackedProvider implements InstrumentationContextProvider {
  private static final Method contextGetMethod;
  private static final Method mapGetMethod;

  static {
    try {
      contextGetMethod = InstrumentationContext.class.getMethod("get", Class.class, Class.class);
      mapGetMethod = MapHolder.class.getMethod("getMapHolder", Class.class, Class.class);
    } catch (final Exception e) {
      throw new IllegalStateException(e);
    }
  }

  /** dynamic-class-name -> dynamic-class-bytes */
  private final AtomicReference<Map<String, byte[]>> dynamicClasses = new AtomicReference<>(null);

  /** key-class-name -> dynamic-class-name */
  private final AtomicReference<Map<String, String>> dynamicClassNames =
      new AtomicReference<>(null);

  private final Instrumenter.Default instrumenter;

  public MapBackedProvider(final Instrumenter.Default instrumenter) {
    this.instrumenter = instrumenter;
  }

  @Override
  public AgentBuilder.Identified.Extendable instrumentationTransformer(
      AgentBuilder.Identified.Extendable builder) {
    if (instrumenter.contextStore().size() > 0) {
      builder =
          builder.transform(
              new AgentBuilder.Transformer() {
                @Override
                public DynamicType.Builder<?> transform(
                    final DynamicType.Builder<?> builder,
                    final TypeDescription typeDescription,
                    final ClassLoader classLoader,
                    final JavaModule module) {
                  return builder.visit(getInstrumentationVisitor());
                }
              });
      builder =
          builder.transform(
              new AgentBuilder.Transformer() {
                final HelperInjector injector = new HelperInjector(dynamicClasses());

                @Override
                public DynamicType.Builder<?> transform(
                    final DynamicType.Builder<?> builder,
                    final TypeDescription typeDescription,
                    final ClassLoader classLoader,
                    final JavaModule module) {
                  return injector.transform(
                      builder,
                      typeDescription,
                      // dynamic map classes will always go to the bootstrap
                      BOOTSTRAP_CLASSLOADER,
                      module);
                }
              });
    }
    return builder;
  }

  private AsmVisitorWrapper getInstrumentationVisitor() {
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
        generateMapHolderClasses();
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
                if (Utils.getInternalName(contextGetMethod.getDeclaringClass().getName())
                        .equals(owner)
                    && contextGetMethod.getName().equals(name)
                    && Type.getMethodDescriptor(contextGetMethod).equals(descriptor)) {
                  log.debug("Found context-store access in {}", instrumenter.getClass().getName());
                  if ((insnStack[0] == Opcodes.INVOKESTATIC
                          && insnStack[1] == Opcodes.LDC
                          && insnStack[2] == Opcodes.LDC)
                      && (stack[0] instanceof Type && stack[1] instanceof Type)) {
                    final String contextClassName = ((Type) stack[0]).getClassName();
                    final String keyClassName = ((Type) stack[1]).getClassName();
                    final String mapHolderClass = dynamicClassNames.get().get(keyClassName);
                    log.debug(
                        "Rewriting context-store map fetch for instrumenter {}: {} -> {}",
                        instrumenter.getClass().getName(),
                        keyClassName,
                        contextClassName);
                    if (mapHolderClass == null) {
                      throw new IllegalStateException(
                          String.format(
                              "Incorrect Context Api Usage detected. Cannot find map holder class for %s. Was that class defined in contextStore for instrumentation %s?",
                              keyClassName, instrumenter.getClass().getName()));
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
                        Utils.getInternalName(mapHolderClass),
                        mapGetMethod.getName(),
                        Type.getMethodDescriptor(mapGetMethod),
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

  private Map<String, byte[]> dynamicClasses() {
    return createOrGetDynamicClasses();
  }

  @Override
  public AgentBuilder.Identified.Extendable additionalInstrumentation(
      final AgentBuilder.Identified.Extendable builder) {
    return builder;
  }

  private Map<String, byte[]> createOrGetDynamicClasses() {
    if (dynamicClasses.get() == null) {
      generateMapHolderClasses();
    }
    return dynamicClasses.get();
  }

  private synchronized void generateMapHolderClasses() {
    if (dynamicClasses.get() == null) {
      final Map<String, byte[]> dynamicClasses = new HashMap<>(instrumenter.contextStore().size());
      final Map<String, String> dynamicClassNames =
          new HashMap<>(instrumenter.contextStore().size());
      for (final String keyClassName : instrumenter.contextStore().keySet()) {
        final String dynamicClassName =
            getClass().getName() + "$ContextStore" + keyClassName.replaceAll("\\.", "\\$");
        dynamicClassNames.put(keyClassName, dynamicClassName);
        dynamicClasses.put(dynamicClassName, makeMapHolderClass(dynamicClassName));
      }
      this.dynamicClassNames.compareAndSet(null, dynamicClassNames);
      this.dynamicClasses.compareAndSet(null, dynamicClasses);
    }
  }

  private byte[] makeMapHolderClass(final String className) {
    return new ByteBuddy()
        .rebase(MapHolder.class)
        .modifiers(Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL)
        .name(className)
        .make()
        .getBytes();
  }

  /** Template class used to generate the class holding the global map. */
  private static final class MapHolder implements ContextStore<Object, Object> {
    private static final MapHolder INSTANCE = new MapHolder(WeakMap.Provider.newWeakMap());

    private final WeakMap map;

    private MapHolder(final WeakMap map) {
      this.map = map;
    }

    @Override
    public Object get(final Object key) {
      return map.get(key);
    }

    @Override
    public Object putIfAbsent(final Object key, final Object context) {
      Object existingContext = map.get(key);
      if (null != existingContext) {
        return existingContext;
      }
      synchronized (map) {
        existingContext = map.get(key);
        if (null != existingContext) {
          return existingContext;
        }
        map.put(key, context);
        return context;
      }
    }

    @Override
    public Object putIfAbsent(final Object key, final ContextStore.Factory<Object> contextFactory) {
      Object existingContext = map.get(key);
      if (null != existingContext) {
        return existingContext;
      }
      synchronized (map) {
        existingContext = map.get(key);
        if (null != existingContext) {
          return existingContext;
        }
        final Object context = contextFactory.create();
        map.put(key, context);
        return context;
      }
    }

    @Override
    public void put(final Object key, final Object context) {
      map.put(key, context);
    }

    public static ContextStore getMapHolder(final Class keyClass, final Class contextClass) {
      // We do not actually check the keyClass here - but that should be fine since compiler would
      // check things for us.
      return INSTANCE;
    }
  }
}
