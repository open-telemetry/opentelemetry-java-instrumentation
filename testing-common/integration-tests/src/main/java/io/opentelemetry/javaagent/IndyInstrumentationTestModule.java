/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent;

import static net.bytebuddy.matcher.ElementMatchers.named;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.extension.instrumentation.internal.ExperimentalInstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.internal.injection.ClassInjector;
import io.opentelemetry.javaagent.extension.instrumentation.internal.injection.InjectionMode;
import java.util.Collections;
import java.util.List;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned.ToArguments.ToArgument;
import net.bytebuddy.asm.Advice.AssignReturned.ToFields.ToField;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class IndyInstrumentationTestModule extends InstrumentationModule
    implements ExperimentalInstrumentationModule {

  public IndyInstrumentationTestModule() {
    super("indy-test");
  }

  @Override
  public boolean isIndyModule() {
    return true;
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return Collections.singletonList(new Instrumentation());
  }

  @Override
  public boolean isHelperClass(String className) {
    return className.equals(LocalHelper.class.getName());
  }

  @Override
  public List<String> getAdditionalHelperClassNames() {
    // TODO: should not be needed as soon as we automatically add proxied classes to the muzzle root
    // set
    return Collections.singletonList("indy.ProxyMe");
  }

  @Override
  public void injectClasses(ClassInjector injector) {
    injector.proxyBuilder("indy.ProxyMe", "foo.bar.Proxy").inject(InjectionMode.CLASS_AND_RESOURCE);
  }

  public static class Instrumentation implements TypeInstrumentation {

    @Override
    public ElementMatcher<TypeDescription> typeMatcher() {
      return named("indy.IndyInstrumentationTest");
    }

    @Override
    public void transform(TypeTransformer transformer) {
      String prefix = getClass().getName();
      transformer.applyAdviceToMethod(
          named("assignToFieldViaReturn"), prefix + "$AssignFieldViaReturnAdvice");
      transformer.applyAdviceToMethod(
          named("assignToFieldViaArray"), prefix + "$AssignFieldViaArrayAdvice");
      transformer.applyAdviceToMethod(
          named("assignToArgumentViaReturn"), prefix + "$AssignArgumentViaReturnAdvice");
      transformer.applyAdviceToMethod(
          named("assignToArgumentViaArray"), prefix + "$AssignArgumentViaArrayAdvice");
      transformer.applyAdviceToMethod(
          named("assignToReturnViaReturn"), prefix + "$AssignReturnViaReturnAdvice");
      transformer.applyAdviceToMethod(
          named("assignToReturnViaArray"), prefix + "$AssignReturnViaArrayAdvice");
      transformer.applyAdviceToMethod(named("getHelperClass"), prefix + "$GetHelperClassAdvice");
      transformer.applyAdviceToMethod(named("exceptionPlease"), prefix + "$ThrowExceptionAdvice");
      transformer.applyAdviceToMethod(
          named("noExceptionPlease"), prefix + "$SuppressExceptionAdvice");
      transformer.applyAdviceToMethod(
          named("instrumentWithErasedTypes"), prefix + "$SignatureErasureAdvice");
    }

    @SuppressWarnings({"unused"})
    public static class AssignFieldViaReturnAdvice {

      @Advice.OnMethodEnter(inline = false)
      @Advice.AssignReturned.ToFields(@ToField(value = "privateField"))
      public static String onEnter(@Advice.Argument(0) String toAssign) {
        return toAssign;
      }
    }

    @SuppressWarnings({"unused"})
    public static class AssignFieldViaArrayAdvice {

      @Advice.OnMethodEnter(inline = false)
      @Advice.AssignReturned.ToFields(@ToField(value = "privateField", index = 1))
      public static Object[] onEnter(@Advice.Argument(0) String toAssign) {
        return new Object[] {"ignoreme", toAssign};
      }
    }

    @SuppressWarnings({"unused"})
    public static class AssignArgumentViaReturnAdvice {

      @Advice.OnMethodEnter(inline = false)
      @Advice.AssignReturned.ToArguments(@ToArgument(value = 0))
      public static String onEnter(@Advice.Argument(1) String toAssign) {
        return toAssign;
      }
    }

    @SuppressWarnings({"unused"})
    public static class AssignArgumentViaArrayAdvice {

      @Advice.OnMethodEnter(inline = false)
      @Advice.AssignReturned.ToArguments(@ToArgument(value = 0, index = 1))
      public static Object[] onEnter(@Advice.Argument(1) String toAssign) {
        return new Object[] {"ignoreme", toAssign};
      }
    }

    @SuppressWarnings({"unused"})
    public static class AssignReturnViaReturnAdvice {

      @Advice.OnMethodExit(inline = false)
      @Advice.AssignReturned.ToReturned
      public static String onExit(@Advice.Argument(0) String toAssign) {
        return toAssign;
      }
    }

    @SuppressWarnings({"unused"})
    public static class AssignReturnViaArrayAdvice {

      @Advice.OnMethodExit(inline = false)
      @Advice.AssignReturned.ToReturned(index = 1)
      public static Object[] onExit(@Advice.Argument(0) String toAssign) {
        return new Object[] {"ignoreme", toAssign};
      }
    }

    @SuppressWarnings({"unused"})
    public static class GetHelperClassAdvice {

      @Advice.OnMethodExit(inline = false)
      @Advice.AssignReturned.ToReturned
      public static Class<?> onExit(@Advice.Argument(0) boolean localHelper) {
        if (localHelper) {
          return LocalHelper.class;
        } else {
          return GlobalHelper.class;
        }
      }
    }

    @SuppressWarnings({"unused", "ThrowSpecificExceptions"})
    public static class ThrowExceptionAdvice {
      @Advice.OnMethodExit(inline = false)
      public static void onMethodExit() {
        throw new RuntimeException("This exception should not be suppressed");
      }
    }

    @SuppressWarnings({"unused", "ThrowSpecificExceptions"})
    public static class SuppressExceptionAdvice {
      @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
      public static void onMethodEnter() {
        throw new RuntimeException("This exception should be suppressed");
      }

      @Advice.AssignReturned.ToReturned
      @Advice.OnMethodExit(
          suppress = Throwable.class,
          onThrowable = Throwable.class,
          inline = false)
      public static void onMethodExit(@Advice.Thrown Throwable throwable) {
        throw new RuntimeException("This exception should be suppressed");
      }
    }

    @SuppressWarnings({"unused", "ThrowSpecificExceptions"})
    public static class SignatureErasureAdvice {
      @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
      public static LocalHelper onMethodEnter() {
        return new LocalHelper();
      }

      @Advice.AssignReturned.ToReturned
      @Advice.OnMethodExit(
          suppress = Throwable.class,
          onThrowable = Throwable.class,
          inline = false)
      public static LocalHelper onMethodExit(@Advice.Enter LocalHelper enterVal) {
        return enterVal;
      }
    }
  }

  public static class GlobalHelper {}

  public static class LocalHelper {}
}
