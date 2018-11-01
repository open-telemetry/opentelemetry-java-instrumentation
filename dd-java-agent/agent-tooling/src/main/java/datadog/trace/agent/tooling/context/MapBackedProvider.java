package datadog.trace.agent.tooling.context;

import static datadog.trace.agent.tooling.ClassLoaderMatcher.BOOTSTRAP_CLASSLOADER;

import datadog.trace.agent.tooling.HelperInjector;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.agent.tooling.Utils;
import datadog.trace.bootstrap.InstrumentationContext;
import datadog.trace.bootstrap.WeakMap;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
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
 *   <li>Injecting a Dynamic Class to store a static map
 *   <li>Rewritting calls to the context-store to access the map on the dynamic class
 * </ol>
 *
 * Storing the map on a dynamic class and doing bytecode rewrites allows for a 1-pass lookup.
 * Without bytecode transformations a 2-pass lookup would be required.
 *
 * <p>Example:<br>
 * <em>InstrumentationContext.get(runnableInstance, Runnable.class, RunnableState.class)")</em><br>
 * is rewritten to:<br>
 * <em>RunnableInstrumentation$ContextStore$RunnableState12345.getOrCreate(runnableInstance,
 * Runnable.class, RunnableState.class)</em>
 *
 * <p>Map lookup implementation defined in template class: {@link MapHolder#getOrCreate(Object,
 * Class, Class)}
 */
@Slf4j
public class MapBackedProvider implements InstrumentationContextProvider {
  private static final Method contextGetMethod;
  private static final Method mapGetMethod;

  static {
    try {
      contextGetMethod =
          InstrumentationContext.class.getMethod("get", Object.class, Class.class, Class.class);
      mapGetMethod =
          MapHolder.class.getMethod("getOrCreate", Object.class, Class.class, Class.class);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  /** dynamic-class-name -> dynamic-class-bytes */
  private final AtomicReference<Map<String, byte[]>> dynamicClasses = new AtomicReference<>(null);

  /** user-class-name -> dynamic-class-name */
  private final AtomicReference<Map<String, String>> dynamicClassNames =
      new AtomicReference<>(null);

  private final Instrumenter.Default instrumenter;

  public MapBackedProvider(Instrumenter.Default instrumenter) {
    this.instrumenter = instrumenter;
  }

  public AgentBuilder.Identified.Extendable instrumentationTransformer(
      AgentBuilder.Identified.Extendable builder) {
    if (instrumenter.contextStore().size() > 0) {
      builder =
          builder.transform(
              new AgentBuilder.Transformer() {
                @Override
                public DynamicType.Builder<?> transform(
                    DynamicType.Builder<?> builder,
                    TypeDescription typeDescription,
                    ClassLoader classLoader,
                    JavaModule module) {
                  return builder.visit(getInstrumentationVisitor());
                }
              });
      builder =
          builder.transform(
              new AgentBuilder.Transformer() {
                final HelperInjector injector = new HelperInjector(dynamicClasses());

                @Override
                public DynamicType.Builder<?> transform(
                    DynamicType.Builder<?> builder,
                    TypeDescription typeDescription,
                    ClassLoader classLoader,
                    JavaModule module) {
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
      public int mergeWriter(int flags) {
        return flags | ClassWriter.COMPUTE_MAXS;
      }

      @Override
      public int mergeReader(int flags) {
        return flags;
      }

      @Override
      public ClassVisitor wrap(
          final TypeDescription instrumentedType,
          ClassVisitor classVisitor,
          Implementation.Context implementationContext,
          TypePool typePool,
          FieldList<FieldDescription.InDefinedShape> fields,
          MethodList<?> methods,
          int writerFlags,
          int readerFlags) {
        generateMapHolderClasses();
        return new ClassVisitor(Opcodes.ASM7, classVisitor) {
          @Override
          public void visit(
              int version,
              int access,
              String name,
              String signature,
              String superName,
              String[] interfaces) {
            super.visit(version, access, name, signature, superName, interfaces);
          }

          @Override
          public MethodVisitor visitMethod(
              int access, String name, String descriptor, String signature, String[] exceptions) {
            final MethodVisitor mv =
                super.visitMethod(access, name, descriptor, signature, exceptions);
            return new MethodVisitor(Opcodes.ASM7, mv) {
              /** The most recent objects pushed to the stack. */
              private final Object[] stack = {null, null};
              /** Most recent instructions. */
              private final int[] insnStack = {-1, -1, -1};

              @Override
              public void visitMethodInsn(
                  int opcode, String owner, String name, String descriptor, boolean isInterface) {
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
                    final String userClassName = ((Type) stack[1]).getClassName();
                    final String mapHolderClass = dynamicClassNames.get().get(userClassName);
                    log.debug(
                        "Rewriting context-store map fetch for instrumenter {}: {} -> {}",
                        instrumenter.getClass().getName(),
                        userClassName,
                        contextClassName);
                    if (mapHolderClass != null
                        && contextClassName.equals(
                            instrumenter.contextStore().get(userClassName))) {
                      // stack: contextClass | userClass | instance
                      mv.visitMethodInsn(
                          Opcodes.INVOKESTATIC,
                          Utils.getInternalName(mapHolderClass),
                          mapGetMethod.getName(),
                          Type.getMethodDescriptor(mapGetMethod),
                          false);
                      return;
                    }
                  }
                  throw new IllegalStateException(
                      "Incorrect Context Api Usage detected. User and context class must be class-literals. Example of correct usage: InstrumentationContext.get(runnableInstance, Runnable.class, RunnableState.class)");
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

  private Map<String, byte[]> dynamicClasses() {
    return createOrGetDynamicClasses();
  }

  @Override
  public AgentBuilder.Identified.Extendable additionalInstrumentation(
      AgentBuilder.Identified.Extendable builder) {
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
      for (final String userClassName : instrumenter.contextStore().keySet()) {
        final String dynamicClassName =
            instrumenter.getClass().getName()
                + "$ContextStore"
                + userClassName.replaceAll(".*([^\\.]+)$", "\\1")
                + UUID.randomUUID().toString().replace('-', '_');

        dynamicClassNames.put(userClassName, dynamicClassName);
        dynamicClasses.put(dynamicClassName, makeMapHolderClass(dynamicClassName));
      }
      this.dynamicClassNames.compareAndSet(null, dynamicClassNames);
      this.dynamicClasses.compareAndSet(null, dynamicClasses);
    }
  }

  private byte[] makeMapHolderClass(String className) {
    return new ByteBuddy()
        .rebase(MapHolder.class)
        .modifiers(Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL)
        .name(className)
        .make()
        .getBytes();
  }

  /** Template class used to generate the class holding the global map. */
  private static final class MapHolder {
    public static final WeakMap MAP = WeakMap.Provider.newWeakMap();

    /**
     * Fetch a context class out of the backing map. Create and return a new context class if none
     * currently exists.
     *
     * <p>This method is thread safe.
     */
    public static Object getOrCreate(Object instance, Class userClass, Class contextClass) {
      if (!userClass.isAssignableFrom(instance.getClass())) {
        throw new RuntimeException(
            "Illegal context lookup. "
                + instance.getClass().getName()
                + " cannot be cast to  "
                + userClass.getName());
      }
      Object contextInstance = MAP.get(instance);
      if (null == contextInstance) {
        synchronized (instance) {
          contextInstance = MAP.get(instance);
          if (null == contextInstance) {
            try {
              contextInstance = contextClass.newInstance();
              MAP.put(instance, contextInstance);
            } catch (Exception e) {
              throw new RuntimeException(
                  contextClass.getName() + " must define a public, no-arg constructor.", e);
            }
          }
        }
      }
      return contextInstance;
    }

    private MapHolder() {}
  }
}
