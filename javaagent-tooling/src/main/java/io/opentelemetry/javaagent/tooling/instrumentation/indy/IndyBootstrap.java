package io.opentelemetry.javaagent.tooling.instrumentation.indy;

import io.opentelemetry.javaagent.bootstrap.CallDepth;
import io.opentelemetry.javaagent.bootstrap.IndyBootstrapDispatcher;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.utility.JavaConstant;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nullable;

public class IndyBootstrap {

  private static final Logger logger = Logger.getLogger(IndyBootstrap.class.getName());

  private static final Method indyBootstrapMethod;

  private static final Method bootstrapLoggingMethod;

  private static IndyModuleRegistry moduleRegistry;

  private static final CallDepth callDepth = CallDepth.forClass(IndyBootstrap.class);

  static {
    try {
      indyBootstrapMethod = IndyBootstrapDispatcher.class.getMethod("bootstrap", MethodHandles.Lookup.class, String.class, MethodType.class, Object[].class);
      bootstrapLoggingMethod = IndyBootstrapDispatcher.class.getMethod("logAdviceException", Throwable.class);

      IndyBootstrapDispatcher.logAdviceException = IndyBootstrap.class.getMethod("logExceptionThrownByAdvice", Throwable.class);
      IndyBootstrapDispatcher.bootstrap = IndyBootstrap.class.getMethod("bootstrap", MethodHandles.Lookup.class, String.class, MethodType.class, Object[].class);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private IndyBootstrap(){}

  public static void init(IndyModuleRegistry moduleRegistry) {
    IndyBootstrap.moduleRegistry = moduleRegistry;
  }

  public static Method getIndyBootstrapMethod() {
    return indyBootstrapMethod;
  }

  public static Method getExceptionHandlerMethod() {
    return bootstrapLoggingMethod;
  }


  @Nullable
  public static ConstantCallSite bootstrap(MethodHandles.Lookup lookup,
      String adviceMethodName,
      MethodType adviceMethodType,
      Object... args) {

    if (System.getSecurityManager() == null) {
      return internalBootstrap(lookup, adviceMethodName, adviceMethodType, args);
    }

    // callsite resolution needs privileged access to call Class#getClassLoader() and MethodHandles$Lookup#findStatic
    return AccessController.doPrivileged(new PrivilegedAction<ConstantCallSite>() {
      @Override
      public ConstantCallSite run() {
        return internalBootstrap(lookup, adviceMethodName, adviceMethodType, args);
      }
    });
  }

  private static ConstantCallSite internalBootstrap(MethodHandles.Lookup lookup, String adviceMethodName, MethodType adviceMethodType, Object[] args) {
    try {
      if (callDepth.getAndIncrement() > 0) {
        // avoid re-entrancy and stack overflow errors
        // may happen when bootstrapping an instrumentation that also gets triggered during the bootstrap
        // for example, adding correlation ids to the thread context when executing logger.debug.
        logger.log(Level.WARNING, "Nested instrumented invokedynamic instruction linkage detected", new Throwable());
        return null;
      }
      //See the getAdviceBootstrapArguments-method for where these arguments come from
      String moduleClassName = (String) args[0];
      String adviceClassName = (String) args[1];

      InstrumentationModuleClassLoader instrumentationClassloader =
          moduleRegistry.getInstrumentationClassloader(moduleClassName, lookup.lookupClass().getClassLoader());

      Class<?> adviceClass = instrumentationClassloader.loadClass(adviceClassName);
      MethodHandle methodHandle = instrumentationClassloader.getLookup().findStatic(adviceClass, adviceMethodName, adviceMethodType);
      return new ConstantCallSite(methodHandle);
    } catch (Exception e) {
      logger.log(Level.SEVERE, e.getMessage(), e);
      return null;
    } finally {
      callDepth.decrementAndGet();
    }
  }

  public static void logExceptionThrownByAdvice(Throwable exception) {
    try {
      try {
        logger.log(Level.SEVERE,"Advice threw an exception, this should never happen!", exception);
      } catch (StackOverflowError e) {
        //try to print on a different thread. We have to pray that the stack size is still enough to submit the task
//        Executor exec = fallbackLogExecutor;
//        if (exec != null) {
//          exec.execute(new Runnable() {
//            @Override
//            public void run() {
//              logger().error("Advice threw an exception, this should never happen! Exception is logged on different thread due to stackoverflow. ", exception);
//            }
//          });
//        }
      }
    } catch (Throwable t) {
      //we were unable to print the exception (e.g. due to OOM / StackOverflow). Not much we can do here.
    }
  }

  static Advice.BootstrapArgumentResolver.Factory getAdviceBootstrapArguments(InstrumentationModule instrumentationModule) {
    String moduleName = instrumentationModule.getClass().getName();
    return (adviceMethod, exit) -> (instrumentedType, instrumentedMethod) -> Arrays.asList(
        JavaConstant.Simple.ofLoaded(moduleName),
        JavaConstant.Simple.ofLoaded(adviceMethod.getDeclaringType().getName())
    );
  }
}
