/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.internal.cache.weaklockfree.WeakConcurrentMapCleaner;
import io.opentelemetry.javaagent.bootstrap.AgentInitializer;
import io.opentelemetry.javaagent.bootstrap.AgentStarter;
import io.opentelemetry.javaagent.extension.instrumentation.internal.AsmApi;
import io.opentelemetry.javaagent.tooling.config.EarlyInitAgentConfig;
import java.io.File;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.security.ProtectionDomain;
import java.util.ServiceLoader;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/**
 * Main entry point into code that is running inside agent class loader, used reflectively from
 * {@code io.opentelemetry.javaagent.bootstrap.AgentInitializer}.
 */
public class AgentStarterImpl implements AgentStarter {
  private final Instrumentation instrumentation;
  private final File javaagentFile;
  private final boolean isSecurityManagerSupportEnabled;
  private ClassLoader extensionClassLoader;

  public AgentStarterImpl(
      Instrumentation instrumentation,
      File javaagentFile,
      boolean isSecurityManagerSupportEnabled) {
    this.instrumentation = instrumentation;
    this.javaagentFile = javaagentFile;
    this.isSecurityManagerSupportEnabled = isSecurityManagerSupportEnabled;
  }

  @Override
  public boolean delayStart() {
    LaunchHelperClassFileTransformer transformer = new LaunchHelperClassFileTransformer();
    instrumentation.addTransformer(transformer, true);

    try {
      Class<?> clazz = Class.forName("sun.launcher.LauncherHelper", false, null);
      if (transformer.transformed) {
        // LauncherHelper was loaded and got transformed
        return transformer.hookInserted;
      }
      // LauncherHelper was already loaded before we set up transformer
      instrumentation.retransformClasses(clazz);
      return transformer.hookInserted;
    } catch (ClassNotFoundException | UnmodifiableClassException ignore) {
      // ignore
    } finally {
      instrumentation.removeTransformer(transformer);
    }

    return false;
  }

  @Override
  public void start() {
    init();

    EarlyInitAgentConfig earlyConfig = EarlyInitAgentConfig.create();
    extensionClassLoader = createExtensionClassLoader(getClass().getClassLoader(), earlyConfig);

    String loggerImplementationName = earlyConfig.getString("otel.javaagent.logging");
    // default to the built-in stderr slf4j-simple logger
    if (loggerImplementationName == null) {
      loggerImplementationName = "simple";
    }

    LoggingCustomizer loggingCustomizer = null;
    for (LoggingCustomizer customizer :
        ServiceLoader.load(LoggingCustomizer.class, extensionClassLoader)) {
      if (customizer.name().equalsIgnoreCase(loggerImplementationName)) {
        loggingCustomizer = customizer;
        break;
      }
    }
    // unsupported logger implementation; defaulting to noop
    if (loggingCustomizer == null) {
      logUnrecognizedLoggerImplWarning(loggerImplementationName);
      loggingCustomizer = new NoopLoggingCustomizer();
    }

    Throwable startupError = null;
    try {
      loggingCustomizer.init(earlyConfig);
      earlyConfig.logEarlyConfigErrorsIfAny();

      AgentInstaller.installBytebuddyAgent(instrumentation, extensionClassLoader, earlyConfig);
      WeakConcurrentMapCleaner.start();

      // LazyStorage reads system properties. Initialize it here where we have permissions to avoid
      // failing permission checks when it is initialized from user code.
      if (System.getSecurityManager() != null) {
        Context.current();
      }
    } catch (Throwable t) {
      // this is logged below and not rethrown to avoid logging it twice
      startupError = t;
    }
    if (startupError == null) {
      loggingCustomizer.onStartupSuccess();
    } else {
      loggingCustomizer.onStartupFailure(startupError);
    }
  }

  private void init() {
    instrumentInetAddress();
  }

  private void instrumentInetAddress() {
    InetAddressClassFileTransformer transformer = new InetAddressClassFileTransformer();
    instrumentation.addTransformer(transformer, true);

    try {
      Class<?> clazz = Class.forName("java.net.InetAddress", false, null);
      if (transformer.transformed) {
        // InetAddress was loaded and got transformed
        return;
      }
      // InetAddress was already loaded before we set up transformer
      instrumentation.retransformClasses(clazz);
    } catch (ClassNotFoundException | UnmodifiableClassException ignore) {
      // ignore
    } finally {
      instrumentation.removeTransformer(transformer);
    }
  }

  @SuppressWarnings("SystemOut")
  private static void logUnrecognizedLoggerImplWarning(String loggerImplementationName) {
    System.err.println(
        "Unrecognized value of 'otel.javaagent.logging': '"
            + loggerImplementationName
            + "'. The agent will use the no-op implementation.");
  }

  @Override
  public ClassLoader getExtensionClassLoader() {
    return extensionClassLoader;
  }

  private ClassLoader createExtensionClassLoader(
      ClassLoader agentClassLoader, EarlyInitAgentConfig earlyConfig) {
    return ExtensionClassLoader.getInstance(
        agentClassLoader, javaagentFile, isSecurityManagerSupportEnabled, earlyConfig);
  }

  private static class LaunchHelperClassFileTransformer implements ClassFileTransformer {
    boolean hookInserted = false;
    boolean transformed = false;

    @Override
    public byte[] transform(
        ClassLoader loader,
        String className,
        Class<?> classBeingRedefined,
        ProtectionDomain protectionDomain,
        byte[] classfileBuffer) {
      if (!"sun/launcher/LauncherHelper".equals(className)) {
        return null;
      }
      transformed = true;
      ClassReader cr = new ClassReader(classfileBuffer);
      ClassWriter cw = new ClassWriter(cr, 0);
      ClassVisitor cv =
          new ClassVisitor(AsmApi.VERSION, cw) {
            @Override
            public MethodVisitor visitMethod(
                int access, String name, String descriptor, String signature, String[] exceptions) {
              MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
              if ("checkAndLoadMain".equals(name)) {
                return new MethodVisitor(api, mv) {
                  @Override
                  public void visitCode() {
                    super.visitCode();
                    hookInserted = true;
                    mv.visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        Type.getInternalName(AgentInitializer.class),
                        "delayedStartHook",
                        "()V",
                        false);
                  }
                };
              }
              return mv;
            }
          };
      cr.accept(cv, 0);

      return hookInserted ? cw.toByteArray() : null;
    }
  }

  private static class InetAddressClassFileTransformer implements ClassFileTransformer {
    boolean hookInserted = false;
    boolean transformed = false;
    boolean wrapperMethodCreated = false;

    private static void createWrapperMethod(ClassWriter cw) {
      /*
       private static boolean isAgentAndVmBooted();
       Code:
          0: invokestatic  #X                 // Method io/opentelemetry/javaagent/bootstrap/AgentInitializer/isAgentStarted:()Z
          3: ifeq          16
          6: invokestatic  #Y                 // Method jdk/internal/misc/VM.isBooted:()Z
          9: ifeq          16
         12: iconst_1
         13: goto          17
         16: iconst_0
         17: ireturn
      */

      String descriptor = Type.getMethodDescriptor(Type.BOOLEAN_TYPE);
      Label elseLabel = new Label();
      Label endLabel = new Label();

      MethodVisitor mv =
          cw.visitMethod(
              Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC,
              "isAgentAndVmBooted",
              descriptor,
              null,
              null);
      mv.visitCode();

      mv.visitMethodInsn(
          Opcodes.INVOKESTATIC,
          Type.getInternalName(AgentInitializer.class),
          "isAgentStarted",
          descriptor,
          false);
      mv.visitJumpInsn(Opcodes.IFEQ, elseLabel);
      mv.visitMethodInsn(
          Opcodes.INVOKESTATIC, "jdk/internal/misc/VM", "isBooted", descriptor, false);
      mv.visitJumpInsn(Opcodes.IFEQ, elseLabel);
      mv.visitInsn(Opcodes.ICONST_1);
      mv.visitJumpInsn(Opcodes.GOTO, endLabel);
      mv.visitLabel(elseLabel);
      mv.visitInsn(Opcodes.ICONST_0);
      mv.visitLabel(endLabel);
      mv.visitInsn(Opcodes.IRETURN);

      mv.visitMaxs(0, 0);
      mv.visitEnd();
    }

    @Override
    public byte[] transform(
        ClassLoader loader,
        String className,
        Class<?> classBeingRedefined,
        ProtectionDomain protectionDomain,
        byte[] classfileBuffer) {
      if (!"java/net/InetAddress".equals(className)) {
        return null;
      }
      transformed = true;
      ClassReader cr = new ClassReader(classfileBuffer);
      ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
      ClassVisitor cv =
          new ClassVisitor(AsmApi.VERSION, cw) {
            @Override
            public MethodVisitor visitMethod(
                int access, String name, String descriptor, String signature, String[] exceptions) {
              MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
              // We don't want to patch "jdk.internal.misc.VM.isBooted" call in our wrapper
              if ("isAgentAndVmBooted".equals(name)) {
                return mv;
              }
              return new MethodVisitor(api, mv) {
                @Override
                public void visitMethodInsn(
                    int opcode,
                    String ownerClassName,
                    String methodName,
                    String descriptor,
                    boolean isInterface) {
                  if ("jdk/internal/misc/VM".equals(ownerClassName)
                      && "isBooted".equals(methodName)) {
                    // Create wrapper method only once
                    if (!wrapperMethodCreated) {
                      createWrapperMethod(cw);
                      wrapperMethodCreated = true;
                    }
                    super.visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        "java/net/InetAddress",
                        "isAgentAndVmBooted",
                        "()Z",
                        isInterface);
                    hookInserted = true;
                  } else {
                    super.visitMethodInsn(
                        opcode, ownerClassName, methodName, descriptor, isInterface);
                  }
                }
              };
            }
          };

      cr.accept(cv, 0);

      return hookInserted ? cw.toByteArray() : null;
    }
  }
}
