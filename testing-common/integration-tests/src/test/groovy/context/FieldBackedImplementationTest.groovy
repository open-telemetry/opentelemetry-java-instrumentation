/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package context

import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import io.opentelemetry.instrumentation.test.utils.ClasspathUtils
import io.opentelemetry.instrumentation.test.utils.GcUtils
import io.opentelemetry.javaagent.testing.common.TestAgentListenerAccess
import library.KeyClass
import library.UntransformableKeyClass
import net.bytebuddy.agent.ByteBuddyAgent
import net.sf.cglib.proxy.Enhancer
import net.sf.cglib.proxy.MethodInterceptor
import net.sf.cglib.proxy.MethodProxy
import spock.lang.Unroll

import java.lang.instrument.ClassDefinition
import java.lang.ref.WeakReference
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.concurrent.atomic.AtomicReference

// this test is run using
//   -Dotel.instrumentation.context-test-instrumentation.enabled=true
// (see integration-tests.gradle)
class FieldBackedImplementationTest extends AgentInstrumentationSpecification {

  def setupSpec() {
    TestAgentListenerAccess.addSkipErrorCondition({ typeName, throwable ->
      return typeName.startsWith('library.Incorrect') &&
        throwable.getMessage().startsWith("Incorrect Context Api Usage detected.")
    })
    TestAgentListenerAccess.addSkipTransformationCondition({ typeName ->
      return typeName != null && typeName.endsWith("UntransformableKeyClass")
    })
  }

  @Unroll
  def "#keyClassName structure modified = #shouldModifyStructure"() {
    setup:
    boolean hasField = false
    boolean isPrivate = false
    boolean isTransient = false
    boolean isSynthetic = false
    for (Field field : keyClass.getDeclaredFields()) {
      if (field.getName().startsWith("__opentelemetry")) {
        isPrivate = Modifier.isPrivate(field.getModifiers())
        isTransient = Modifier.isTransient(field.getModifiers())
        isSynthetic = field.isSynthetic()
        hasField = true
        break
      }
    }

    boolean hasMarkerInterface = false
    boolean hasAccessorInterface = false
    boolean accessorInterfaceIsSynthetic = false
    for (Class inter : keyClass.getInterfaces()) {
      if (inter.getName() == 'io.opentelemetry.javaagent.bootstrap.VirtualFieldInstalledMarker') {
        hasMarkerInterface = true
      }
      if (inter.getName().startsWith('io.opentelemetry.javaagent.bootstrap.instrumentation.context.FieldBackedImplementationInstaller$VirtualFieldAccessor')) {
        hasAccessorInterface = true
        accessorInterfaceIsSynthetic = inter.isSynthetic()
      }
    }

    expect:
    hasField == shouldModifyStructure
    isPrivate == shouldModifyStructure
    isTransient == shouldModifyStructure
    isSynthetic == shouldModifyStructure
    hasMarkerInterface == shouldModifyStructure
    hasAccessorInterface == shouldModifyStructure
    accessorInterfaceIsSynthetic == shouldModifyStructure
    keyClass.newInstance().isInstrumented() == shouldModifyStructure

    where:
    keyClass                | keyClassName             | shouldModifyStructure
    KeyClass                | keyClass.getSimpleName() | true
    UntransformableKeyClass | keyClass.getSimpleName() | false
  }

  def "multiple fields are injected"() {
    setup:
    List<Field> fields = []
    for (Field field : KeyClass.getDeclaredFields()) {
      if (field.getName().startsWith("__opentelemetry")) {
        fields.add(field)
      }
    }

    List<Class<?>> interfaces = []
    for (Class iface : KeyClass.getInterfaces()) {
      if (iface.name.startsWith('io.opentelemetry.javaagent.bootstrap.instrumentation.context.FieldBackedImplementationInstaller$VirtualFieldAccessor')) {
        interfaces.add(iface)
      }
    }

    expect:
    fields.size() == 3
    fields.forEach { field ->
      assert Modifier.isPrivate(field.modifiers)
      assert Modifier.isTransient(field.modifiers)
      assert field.synthetic
    }

    interfaces.size() == 3
    interfaces.forEach { iface ->
      assert iface.synthetic
    }
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
    ByteBuddyAgent.install()
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
    ByteBuddyAgent.install()
    ByteBuddyAgent.getInstrumentation().redefineClasses(new ClassDefinition(KeyClass, ClasspathUtils.convertToByteArray(KeyClass)))
    ByteBuddyAgent.getInstrumentation().redefineClasses(new ClassDefinition(UntransformableKeyClass, ClasspathUtils.convertToByteArray(UntransformableKeyClass)))

    then:
    new KeyClass().isInstrumented()
    !new UntransformableKeyClass().isInstrumented()
    new KeyClass().incrementContextCount() == 1
    new UntransformableKeyClass().incrementContextCount() == 1
  }
}


