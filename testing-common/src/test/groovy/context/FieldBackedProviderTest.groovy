/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package context

import static context.ContextTestInstrumentationModule.IncorrectCallUsageKeyClass
import static context.ContextTestInstrumentationModule.IncorrectContextClassUsageKeyClass
import static context.ContextTestInstrumentationModule.IncorrectKeyClassUsageKeyClass
import static context.ContextTestInstrumentationModule.KeyClass
import static context.ContextTestInstrumentationModule.UntransformableKeyClass

import io.opentelemetry.instrumentation.test.AgentTestRunner
import io.opentelemetry.instrumentation.test.utils.ClasspathUtils
import io.opentelemetry.instrumentation.util.gc.GcUtils
import java.lang.instrument.ClassDefinition
import java.lang.ref.WeakReference
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.concurrent.atomic.AtomicReference
import java.util.function.BiFunction
import java.util.function.Function
import net.bytebuddy.agent.ByteBuddyAgent
import net.sf.cglib.proxy.Enhancer
import net.sf.cglib.proxy.MethodInterceptor
import net.sf.cglib.proxy.MethodProxy
import spock.lang.Ignore
import spock.lang.Requires

// FIXME (trask)
@Ignore
class FieldBackedProviderTest extends AgentTestRunner {

  static {
    System.setProperty("otel.instrumentation.context-test-instrumentation.enabled", "true")
  }

  @Override
  protected List<BiFunction<String, Throwable, Boolean>> skipErrorConditions() {
    return [
      new BiFunction<String, Throwable, Boolean>() {
        @Override
        Boolean apply(String s, Throwable throwable) {
          return typeName.startsWith(ContextTestInstrumentationModule.getName() + '$Incorrect') && throwable.getMessage().startsWith("Incorrect Context Api Usage detected.")
        }
      }
    ]
  }

  @Override
  protected List<Function<String, Boolean>> skipTransformationConditions() {
    return Collections.singletonList(new Function<String, Boolean>() {
      @Override
      Boolean apply(String s) {
        return s != null && s.endsWith("UntransformableKeyClass")
      }
    })
  }

  def "#keyClassName structure modified = #shouldModifyStructure"() {
    setup:
    boolean hasField = false
    boolean isPrivate = false
    boolean isTransient = false
    for (Field field : keyClass.getDeclaredFields()) {
      if (field.getName().startsWith("__opentelemetry")) {
        isPrivate = Modifier.isPrivate(field.getModifiers())
        isTransient = Modifier.isTransient(field.getModifiers())
        hasField = true
        break
      }
    }

    boolean hasMarkerInterface = false
    boolean hasAccessorInterface = false
    for (Class inter : keyClass.getInterfaces()) {
      if (inter.getName() == 'io.opentelemetry.javaagent.bootstrap.FieldBackedContextStoreAppliedMarker') {
        hasMarkerInterface = true
      }
      if (inter.getName().startsWith('io.opentelemetry.javaagent.bootstrap.instrumentation.context.FieldBackedProvider$ContextAccessor')) {
        hasAccessorInterface = true
      }
    }

    expect:
    hasField == shouldModifyStructure
    isPrivate == shouldModifyStructure
    isTransient == shouldModifyStructure
    hasMarkerInterface == shouldModifyStructure
    hasAccessorInterface == shouldModifyStructure
    keyClass.newInstance().isInstrumented() == shouldModifyStructure

    where:
    keyClass                | keyClassName             | shouldModifyStructure
    KeyClass                | keyClass.getSimpleName() | true
    UntransformableKeyClass | keyClass.getSimpleName() | false
  }

  def "correct api usage stores state in map"() {
    when:
    instance1.incrementContextCount()

    then:
    instance1.incrementContextCount() == 2
    instance2.incrementContextCount() == 1

    where:
    instance1                     | instance2
    new KeyClass()                | new KeyClass()
    new UntransformableKeyClass() | new UntransformableKeyClass()
  }

  def "get/put test"() {
    when:
    instance1.putContextCount(10)

    then:
    instance1.getContextCount() == 10

    where:
    instance1                     | _
    new KeyClass()                | _
    new UntransformableKeyClass() | _
  }

  def "remove test"() {
    given:
    instance1.putContextCount(10)

    when:
    instance1.removeContextCount()

    then:
    instance1.getContextCount() == 0

    where:
    instance1                     | _
    new KeyClass()                | _
    new UntransformableKeyClass() | _
  }

  def "works with cglib enhanced instances which duplicates context getter and setter methods"() {
    setup:
    Enhancer enhancer = new Enhancer()
    enhancer.setSuperclass(KeyClass)
    enhancer.setCallback(new MethodInterceptor() {
      @Override
      Object intercept(Object arg0, Method arg1, Object[] arg2,
                       MethodProxy arg3) throws Throwable {
        return arg3.invokeSuper(arg0, arg2)
      }
    })

    when:
    (KeyClass) enhancer.create()

    then:
    noExceptionThrown()
  }

  def "backing map should not create strong refs to key class instances #keyValue.get().getClass().getName()"() {
    when:
    int count = keyValue.get().incrementContextCount()
    WeakReference<KeyClass> instanceRef = new WeakReference(keyValue.get())
    keyValue.set(null)
    GcUtils.awaitGc(instanceRef)

    then:
    instanceRef.get() == null
    count == 1

    where:
    keyValue                                           | _
    new AtomicReference(new KeyClass())                | _
    new AtomicReference(new UntransformableKeyClass()) | _
  }

  def "context classes are retransform safe"() {
    when:
    ByteBuddyAgent.getInstrumentation().retransformClasses(KeyClass)
    ByteBuddyAgent.getInstrumentation().retransformClasses(UntransformableKeyClass)

    then:
    new KeyClass().isInstrumented()
    !new UntransformableKeyClass().isInstrumented()
    new KeyClass().incrementContextCount() == 1
    new UntransformableKeyClass().incrementContextCount() == 1
  }

  // NB: This test will fail if some other agent is also running that modifies the class structure
  // in a way that is incompatible with redefining the class back to its original bytecode.
  // A likely culprit is jacoco if you start seeing failure here due to a change make sure jacoco
  // exclusion is working.
  def "context classes are redefine safe"() {
    when:
    ByteBuddyAgent.getInstrumentation().redefineClasses(new ClassDefinition(KeyClass, ClasspathUtils.convertToByteArray(KeyClass)))
    ByteBuddyAgent.getInstrumentation().redefineClasses(new ClassDefinition(UntransformableKeyClass, ClasspathUtils.convertToByteArray(UntransformableKeyClass)))

    then:
    new KeyClass().isInstrumented()
    !new UntransformableKeyClass().isInstrumented()
    new KeyClass().incrementContextCount() == 1
    new UntransformableKeyClass().incrementContextCount() == 1
  }

  def "incorrect key class usage fails at class load time"() {
    expect:
    !new IncorrectKeyClassUsageKeyClass().isInstrumented()
  }

  def "incorrect context class usage fails at class load time"() {
    expect:
    !new IncorrectContextClassUsageKeyClass().isInstrumented()
  }

  def "incorrect call usage fails at class load time"() {
    expect:
    !new IncorrectCallUsageKeyClass().isInstrumented()
  }
}

/**
 * Make sure that fields not get injected into the class if it is disabled via system properties.
 *
 * Unfortunately we cannot set system properties here early enough for AgentTestRunner to see.
 * Instead we have to configure this via Gradle. Ideally we should not have to do this.
 */
@Requires({ "false" == System.getProperty("otel.javaagent.runtime.context.field.injection") })
class FieldBackedProviderFieldInjectionDisabledTest extends AgentTestRunner {

  static {
    System.setProperty("otel.instrumentation.context-test-instrumentation.enabled", "true")
  }

  @Override
  protected List<BiFunction<String, Throwable, Boolean>> skipErrorConditions() {
    return [
      new BiFunction<String, Throwable, Boolean>() {
        @Override
        Boolean apply(String typeName, Throwable throwable) {
          return typeName.startsWith(ContextTestInstrumentationModule.getName() + '$Incorrect') && throwable.getMessage().startsWith("Incorrect Context Api Usage detected.")
        }
      }
    ]
  }

  def "Check that structure is not modified when structure modification is disabled"() {
    setup:
    def keyClass = ContextTestInstrumentationModule.DisabledKeyClass
    boolean hasField = false
    for (Field field : keyClass.getDeclaredFields()) {
      if (field.getName().startsWith("__opentelemetry")) {
        hasField = true
        break
      }
    }

    boolean hasMarkerInterface = false
    boolean hasAccessorInterface = false
    for (Class inter : keyClass.getInterfaces()) {
      if (inter.getName() == 'io.opentelemetry.javaagent.bootstrap.FieldBackedContextStoreAppliedMarker') {
        hasMarkerInterface = true
      }
      if (inter.getName().startsWith('io.opentelemetry.javaagent.bootstrap.instrumentation.context.FieldBackedProvider$ContextAccessor')) {
        hasAccessorInterface = true
      }
    }

    expect:
    hasField == false
    hasMarkerInterface == false
    hasAccessorInterface == false
    keyClass.newInstance().isInstrumented() == true
  }

}
