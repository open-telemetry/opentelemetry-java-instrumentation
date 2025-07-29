/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package context;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.test.utils.ClasspathUtils;
import io.opentelemetry.instrumentation.test.utils.GcUtils;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.javaagent.testing.common.TestAgentListenerAccess;
import java.lang.instrument.ClassDefinition;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import library.KeyClass;
import library.UntransformableKeyClass;
import net.bytebuddy.agent.ByteBuddyAgent;
import net.sf.cglib.proxy.Enhancer;
import net.sf.cglib.proxy.MethodInterceptor;
import net.sf.cglib.proxy.MethodProxy;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

// this test is run using
//   -Dotel.instrumentation.context-test-instrumentation.enabled=true
// (see integration-tests.gradle)
class FieldBackedImplementationTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @BeforeAll
  static void setUp() {
    TestAgentListenerAccess.addSkipErrorCondition(
        (typeName, throwable) ->
            typeName.startsWith("library.Incorrect")
                && throwable.getMessage().startsWith("Incorrect Context Api Usage detected."));
    TestAgentListenerAccess.addSkipTransformationCondition(
        typeName -> typeName != null && typeName.endsWith("UntransformableKeyClass"));
  }

  private static Stream<Arguments> provideKeyClassParameters() {
    return Stream.of(Arguments.of(KeyClass.class), Arguments.of(UntransformableKeyClass.class));
  }

  @ParameterizedTest
  @MethodSource("provideKeyClassParameters")
  void structureModified(Class<KeyClass> keyClass) throws Exception {
    boolean shouldModifyStructure = !keyClass.equals(UntransformableKeyClass.class);
    boolean hasField = false;
    boolean isPrivate = false;
    boolean isTransient = false;
    boolean isSynthetic = false;
    for (Field field : keyClass.getDeclaredFields()) {
      if (field.getName().startsWith("__opentelemetry")) {
        isPrivate = Modifier.isPrivate(field.getModifiers());
        isTransient = Modifier.isTransient(field.getModifiers());
        isSynthetic = field.isSynthetic();
        hasField = true;
        break;
      }
    }

    boolean hasMarkerInterface = false;
    boolean hasAccessorInterface = false;
    boolean accessorInterfaceIsSynthetic = false;
    for (Class<?> iface : keyClass.getInterfaces()) {
      if ("io.opentelemetry.javaagent.bootstrap.VirtualFieldInstalledMarker"
          .equals(iface.getName())) {
        hasMarkerInterface = true;
      }
      if (iface
          .getName()
          .startsWith("io.opentelemetry.javaagent.bootstrap.field.VirtualFieldAccessor$")) {
        hasAccessorInterface = true;
        accessorInterfaceIsSynthetic = iface.isSynthetic();
      }
    }

    assertThat(hasField).isEqualTo(shouldModifyStructure);
    assertThat(isPrivate).isEqualTo(shouldModifyStructure);
    assertThat(isTransient).isEqualTo(shouldModifyStructure);
    assertThat(isSynthetic).isEqualTo(shouldModifyStructure);
    assertThat(hasMarkerInterface).isEqualTo(shouldModifyStructure);
    assertThat(hasAccessorInterface).isEqualTo(shouldModifyStructure);
    assertThat(accessorInterfaceIsSynthetic).isEqualTo(shouldModifyStructure);
    assertThat(keyClass.getConstructor().newInstance().isInstrumented())
        .isEqualTo(shouldModifyStructure);
  }

  @Test
  void multipleFieldsInjected() {
    List<Field> fields = new ArrayList<>();
    for (Field field : KeyClass.class.getDeclaredFields()) {
      if (field.getName().startsWith("__opentelemetry")) {
        fields.add(field);
      }
    }

    List<Class<?>> interfaces = new ArrayList<>();
    for (Class<?> iface : KeyClass.class.getInterfaces()) {
      if (iface
          .getName()
          .startsWith("io.opentelemetry.javaagent.bootstrap.field.VirtualFieldAccessor$")) {
        interfaces.add(iface);
      }
    }

    assertThat(fields.size()).isEqualTo(3);
    assertThat(fields)
        .allSatisfy(
            field -> {
              assertThat(Modifier.isPrivate(field.getModifiers())).isTrue();
              assertThat(Modifier.isTransient(field.getModifiers())).isTrue();
              assertThat(field.isSynthetic()).isTrue();
            });

    assertThat(interfaces.size()).isEqualTo(3);
    assertThat(interfaces).allSatisfy(iface -> assertThat(iface.isSynthetic()).isTrue());
  }

  @ParameterizedTest
  @MethodSource("provideKeyClassParameters")
  void instanceState(Class<KeyClass> keyClass) throws Exception {
    KeyClass instance1 = keyClass.getConstructor().newInstance();
    KeyClass instance2 = keyClass.getConstructor().newInstance();

    // correct api usage stores state in map
    instance1.incrementContextCount();

    assertThat(instance1.incrementContextCount()).isEqualTo(2);
    assertThat(instance2.incrementContextCount()).isEqualTo(1);
  }

  @ParameterizedTest
  @MethodSource("provideKeyClassParameters")
  void modifyInstanceState(Class<KeyClass> keyClass) throws Exception {
    KeyClass instance1 = keyClass.getConstructor().newInstance();
    instance1.putContextCount(10);

    assertThat(instance1.getContextCount()).isEqualTo(10);
  }

  @ParameterizedTest
  @MethodSource("provideKeyClassParameters")
  void removeInstanceState(Class<KeyClass> keyClass) throws Exception {
    KeyClass instance1 = keyClass.getConstructor().newInstance();
    instance1.putContextCount(10);
    instance1.removeContextCount();

    assertThat(instance1.getContextCount()).isEqualTo(0);
  }

  @Test
  void cglibProxy() {
    // works with cglib enhanced instances which duplicates context getter and setter methods
    Enhancer enhancer = new Enhancer();
    enhancer.setSuperclass(KeyClass.class);
    enhancer.setCallback(
        new MethodInterceptor() {
          @Override
          public Object intercept(
              Object instance, Method method, Object[] arguments, MethodProxy methodProxy)
              throws Throwable {
            return methodProxy.invokeSuper(instance, arguments);
          }
        });

    assertThat(enhancer.create()).isInstanceOf(KeyClass.class);
  }

  @ParameterizedTest
  @MethodSource("provideKeyClassParameters")
  @SuppressWarnings("UnnecessaryAsync")
  void instanceStateGc(Class<KeyClass> keyClass) throws Exception {
    // backing map should not create strong refs to key class instances
    AtomicReference<KeyClass> keyValue =
        new AtomicReference<>(keyClass.getConstructor().newInstance());
    int count = keyValue.get().incrementContextCount();
    WeakReference<KeyClass> instanceRef = new WeakReference<>(keyValue.get());
    keyValue.set(null);
    GcUtils.awaitGc(instanceRef, Duration.ofSeconds(10));

    assertThat(instanceRef.get()).isNull();
    assertThat(count).isEqualTo(1);
  }

  @Test
  void retransform() throws Exception {
    // context classes are retransform safe
    ByteBuddyAgent.install();
    ByteBuddyAgent.getInstrumentation().retransformClasses(KeyClass.class);
    ByteBuddyAgent.getInstrumentation().retransformClasses(UntransformableKeyClass.class);

    assertThat(new KeyClass().isInstrumented()).isTrue();
    assertThat(new UntransformableKeyClass().isInstrumented()).isFalse();
    assertThat(new KeyClass().incrementContextCount()).isEqualTo(1);
    assertThat(new UntransformableKeyClass().incrementContextCount()).isEqualTo(1);
  }

  // NB: This test will fail if some other agent is also running that modifies the class structure
  // in a way that is incompatible with redefining the class back to its original bytecode.
  // A likely culprit is jacoco if you start seeing failure here due to a change make sure jacoco
  // exclusion is working.
  @Test
  void redefine() throws Exception {
    // context classes are redefine safe
    ByteBuddyAgent.install();
    ByteBuddyAgent.getInstrumentation()
        .redefineClasses(
            new ClassDefinition(KeyClass.class, ClasspathUtils.convertToByteArray(KeyClass.class)));
    ByteBuddyAgent.getInstrumentation()
        .redefineClasses(
            new ClassDefinition(
                UntransformableKeyClass.class,
                ClasspathUtils.convertToByteArray(UntransformableKeyClass.class)));

    assertThat(new KeyClass().isInstrumented()).isTrue();
    assertThat(new UntransformableKeyClass().isInstrumented()).isFalse();
    assertThat(new KeyClass().incrementContextCount()).isEqualTo(1);
    assertThat(new UntransformableKeyClass().incrementContextCount()).isEqualTo(1);
  }
}
