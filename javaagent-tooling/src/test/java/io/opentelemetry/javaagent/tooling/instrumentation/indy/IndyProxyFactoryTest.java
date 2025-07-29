/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.instrumentation.indy;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.javaagent.bootstrap.InstrumentationProxy;
import io.opentelemetry.javaagent.tooling.instrumentation.indy.dummies.DummyAnnotation;
import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.utility.JavaConstant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class IndyProxyFactoryTest {

  private static IndyProxyFactory proxyFactory;

  @BeforeAll
  public static void init() throws Exception {
    Method bootstrap =
        IndyProxyFactoryTest.class.getMethod(
            "indyBootstrap",
            MethodHandles.Lookup.class,
            String.class,
            MethodType.class,
            Object[].class);
    proxyFactory = new IndyProxyFactory(bootstrap, IndyProxyFactoryTest::bootstrapArgsGenerator);
  }

  public static CallSite indyBootstrap(
      MethodHandles.Lookup lookup, String methodName, MethodType methodType, Object... args) {

    try {
      String delegateClassName = (String) args[0];
      String kind = (String) args[1];

      Class<?> proxiedClass = Class.forName(delegateClassName);

      MethodHandle target;

      switch (kind) {
        case "static":
          target = MethodHandles.publicLookup().findStatic(proxiedClass, methodName, methodType);
          break;
        case "constructor":
          target =
              MethodHandles.publicLookup()
                  .findConstructor(proxiedClass, methodType.changeReturnType(void.class))
                  .asType(methodType);
          break;
        case "virtual":
          target =
              MethodHandles.publicLookup()
                  .findVirtual(proxiedClass, methodName, methodType.dropParameterTypes(0, 1))
                  .asType(methodType);
          break;
        default:
          throw new IllegalStateException("unknown kind");
      }
      return new ConstantCallSite(target);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private static List<JavaConstant> bootstrapArgsGenerator(
      TypeDescription proxiedType, MethodDescription.InDefinedShape proxiedMethod) {
    String kind = "virtual";
    if (proxiedMethod.isConstructor()) {
      kind = "constructor";
    } else if (proxiedMethod.isStatic()) {
      kind = "static";
    }
    return Arrays.asList(
        JavaConstant.Simple.ofLoaded(proxiedType.getName()), JavaConstant.Simple.ofLoaded(kind));
  }

  public static class StatefulObj {

    static StatefulObj lastCreatedInstance;

    int counter = 0;

    public StatefulObj() {
      lastCreatedInstance = this;
    }

    public void increaseCounter() {
      counter++;
    }
  }

  @Test
  void verifyDelegateInstantiation() throws Exception {
    Class<?> proxy = generateProxy(StatefulObj.class);
    Constructor<?> ctor = proxy.getConstructor();
    Method increaseCounter = proxy.getMethod("increaseCounter");

    Object proxyA = ctor.newInstance();
    StatefulObj delegateA = StatefulObj.lastCreatedInstance;

    Object proxyB = ctor.newInstance();
    StatefulObj delegateB = StatefulObj.lastCreatedInstance;

    assertThat(delegateA).isNotNull();
    assertThat(delegateB).isNotNull();
    assertThat(delegateA).isNotSameAs(delegateB);

    increaseCounter.invoke(proxyA);
    assertThat(delegateA.counter).isEqualTo(1);
    assertThat(delegateB.counter).isEqualTo(0);

    increaseCounter.invoke(proxyB);
    increaseCounter.invoke(proxyB);
    assertThat(delegateA.counter).isEqualTo(1);
    assertThat(delegateB.counter).isEqualTo(2);
  }

  public static class UtilityWithPrivateCtor {

    private UtilityWithPrivateCtor() {}

    public static String utilityMethod() {
      return "util";
    }
  }

  @Test
  void proxyClassWithoutConstructor() throws Exception {
    Class<?> proxy = generateProxy(UtilityWithPrivateCtor.class);

    // Not legal in Java code but legal in JVM bytecode
    assertThat(proxy.getConstructors()).isEmpty();

    assertThat(proxy.getMethod("utilityMethod").invoke(null)).isEqualTo("util");
  }

  @DummyAnnotation("type")
  public static class AnnotationRetention {

    @DummyAnnotation("constructor")
    public AnnotationRetention(@DummyAnnotation("constructor_param") String someValue) {}

    @DummyAnnotation("virtual")
    public void virtualMethod(@DummyAnnotation("virtual_param") String someValue) {}

    @DummyAnnotation("static")
    public static void staticMethod(@DummyAnnotation("static_param") String someValue) {}
  }

  @Test
  void verifyAnnotationsRetained() throws Exception {

    Class<?> proxy = generateProxy(AnnotationRetention.class);

    assertThat(proxy.getAnnotation(DummyAnnotation.class))
        .isNotNull()
        .extracting(DummyAnnotation::value)
        .isEqualTo("type");

    Constructor<?> ctor = proxy.getConstructor(String.class);
    assertThat(ctor.getAnnotation(DummyAnnotation.class))
        .isNotNull()
        .extracting(DummyAnnotation::value)
        .isEqualTo("constructor");
    assertThat(ctor.getParameters()[0].getAnnotation(DummyAnnotation.class))
        .isNotNull()
        .extracting(DummyAnnotation::value)
        .isEqualTo("constructor_param");

    Method virtualMethod = proxy.getMethod("virtualMethod", String.class);
    assertThat(virtualMethod.getAnnotation(DummyAnnotation.class))
        .isNotNull()
        .extracting(DummyAnnotation::value)
        .isEqualTo("virtual");
    assertThat(virtualMethod.getParameters()[0].getAnnotation(DummyAnnotation.class))
        .isNotNull()
        .extracting(DummyAnnotation::value)
        .isEqualTo("virtual_param");

    Method staticMethod = proxy.getMethod("staticMethod", String.class);
    assertThat(staticMethod.getAnnotation(DummyAnnotation.class))
        .isNotNull()
        .extracting(DummyAnnotation::value)
        .isEqualTo("static");
    assertThat(staticMethod.getParameters()[0].getAnnotation(DummyAnnotation.class))
        .isNotNull()
        .extracting(DummyAnnotation::value)
        .isEqualTo("static_param");

    staticMethod.invoke(null, "blub");
    virtualMethod.invoke(ctor.newInstance("bla"), "blub");
  }

  public static class CustomSuperClass {

    int inheritedFromSuperclassCount = 0;

    protected void overrideMe() {}

    public void inheritedFromSuperclass() {
      inheritedFromSuperclassCount++;
    }
  }

  public static interface CustomSuperInterface extends Runnable {

    default void inheritedDefault() {
      if (this instanceof WithSuperTypes) {
        ((WithSuperTypes) this).inheritedDefaultCount++;
      }
    }
  }

  public static class WithSuperTypes extends CustomSuperClass
      implements CustomSuperInterface, Callable<String> {

    static WithSuperTypes lastCreatedInstance;

    public WithSuperTypes() {
      lastCreatedInstance = this;
    }

    int runInvocCount = 0;
    int callInvocCount = 0;
    int overrideMeInvocCount = 0;

    int inheritedDefaultCount = 0;

    @Override
    public void run() {
      runInvocCount++;
    }

    @Override
    public String call() throws Exception {
      callInvocCount++;
      return "foo";
    }

    @Override
    public void overrideMe() {
      overrideMeInvocCount++;
    }
  }

  @Test
  @SuppressWarnings("unchecked")
  void verifySuperTypes() throws Exception {
    Object proxy = generateProxy(WithSuperTypes.class).getConstructor().newInstance();
    WithSuperTypes proxied = WithSuperTypes.lastCreatedInstance;

    ((Runnable) proxy).run();
    assertThat(proxied.runInvocCount).isEqualTo(1);

    ((Callable<String>) proxy).call();
    assertThat(proxied.callInvocCount).isEqualTo(1);

    ((CustomSuperClass) proxy).overrideMe();
    assertThat(proxied.overrideMeInvocCount).isEqualTo(1);

    // Non-overidden, inherited methods are not proxied
    ((CustomSuperClass) proxy).inheritedFromSuperclass();
    assertThat(proxied.inheritedFromSuperclassCount).isEqualTo(0);
    ((CustomSuperInterface) proxy).inheritedDefault();
    assertThat(proxied.inheritedDefaultCount).isEqualTo(0);
  }

  @SuppressWarnings({"unused", "MethodCanBeStatic"})
  public static class IgnoreNonPublicMethods {

    public IgnoreNonPublicMethods() {}

    protected IgnoreNonPublicMethods(int arg) {}

    IgnoreNonPublicMethods(int arg1, int arg2) {}

    private IgnoreNonPublicMethods(int arg1, int arg2, int arg3) {}

    public void publicMethod() {}

    public static void publicStaticMethod() {}

    protected void protectedMethod() {}

    protected static void protectedStaticMethod() {}

    void packageMethod() {}

    static void packageStaticMethod() {}

    private void privateMethod() {}

    private static void privateStaticMethod() {}
  }

  @Test
  void verifyNonPublicMembersIgnored() {
    Class<?> proxy = generateProxy(IgnoreNonPublicMethods.class);

    assertThat(proxy.getConstructors()).hasSize(1);
    assertThat(proxy.getDeclaredMethods())
        .hasSize(3)
        .anySatisfy(method -> assertThat(method.getName()).isEqualTo("publicMethod"))
        .anySatisfy(method -> assertThat(method.getName()).isEqualTo("publicStaticMethod"))
        // IndyProxy implementation visible but later hidden by reflection instrumentation
        .anySatisfy(
            method -> {
              assertThat(method.getName()).isEqualTo(IndyProxyFactory.PROXY_DELEGATE_NAME);
              assertThat(method.isSynthetic()).isTrue();
            });
  }

  @Test
  void verifyProxyClass() throws Exception {
    Class<?> proxyType = generateProxy(ProxyUnwrapTest.class);
    assertThat(proxyType)
        .isNotInstanceOf(InstrumentationProxy.class)
        .isNotInstanceOf(ProxyUnwrapTest.class);

    assertThat(InstrumentationProxy.class.isAssignableFrom(proxyType))
        .describedAs("proxy class can be cast to IndyProxy")
        .isTrue();

    Object proxyInstance = proxyType.getConstructor().newInstance();
    Object proxyDelegate = ((InstrumentationProxy) proxyInstance).__getIndyProxyDelegate();
    assertThat(proxyDelegate).isInstanceOf(ProxyUnwrapTest.class);
  }

  @SuppressWarnings("all")
  public static class ProxyUnwrapTest {
    public ProxyUnwrapTest() {}

    public void sampleMethod() {}
  }

  private static Class<?> generateProxy(Class<?> clazz) {
    DynamicType.Unloaded<?> unloaded =
        proxyFactory.generateProxy(
            TypeDescription.ForLoadedType.of(clazz), clazz.getName() + "Proxy");
    // Uncomment the following block to view the generated bytecode if needed
    // try {
    //   unloaded.saveIn(new File("build/generated_proxies"));
    // } catch (IOException e) {
    //   throw new RuntimeException(e);
    // }
    return unloaded.load(clazz.getClassLoader()).getLoaded();
  }
}
