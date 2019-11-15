package context

import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.agent.test.utils.ClasspathUtils
import datadog.trace.api.Config
import datadog.trace.util.gc.GCUtils
import net.bytebuddy.agent.ByteBuddyAgent
import net.bytebuddy.utility.JavaModule
import net.sf.cglib.proxy.Enhancer
import net.sf.cglib.proxy.MethodInterceptor
import net.sf.cglib.proxy.MethodProxy
import spock.lang.Requires

import java.lang.instrument.ClassDefinition
import java.lang.ref.WeakReference
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.concurrent.atomic.AtomicReference

import static context.ContextTestInstrumentation.IncorrectCallUsageKeyClass
import static context.ContextTestInstrumentation.IncorrectContextClassUsageKeyClass
import static context.ContextTestInstrumentation.IncorrectKeyClassUsageKeyClass
import static context.ContextTestInstrumentation.KeyClass
import static context.ContextTestInstrumentation.UntransformableKeyClass

class FieldBackedProviderTest extends AgentTestRunner {

  @Override
  boolean onInstrumentationError(
    final String typeName,
    final ClassLoader classLoader,
    final JavaModule module,
    final boolean loaded,
    final Throwable throwable) {
    // Incorrect* classes assert on incorrect api usage. Error expected.
    return !(typeName.startsWith(ContextTestInstrumentation.getName() + '$Incorrect') && throwable.getMessage().startsWith("Incorrect Context Api Usage detected."))
  }

  @Override
  protected boolean shouldTransformClass(final String className, final ClassLoader classLoader) {
    return className == null || (!className.endsWith("UntransformableKeyClass"))
  }

  def "#keyClassName structure modified = #shouldModifyStructure"() {
    setup:
    boolean hasField = false
    boolean isPrivate = false
    boolean isTransient = false
    for (Field field : keyClass.getDeclaredFields()) {
      if (field.getName().startsWith("__datadog")) {
        isPrivate = Modifier.isPrivate(field.getModifiers())
        isTransient = Modifier.isTransient(field.getModifiers())
        hasField = true
        break
      }
    }

    boolean hasMarkerInterface = false
    boolean hasAccessorInterface = false
    for (Class inter : keyClass.getInterfaces()) {
      if (inter.getName() == 'datadog.trace.bootstrap.FieldBackedContextStoreAppliedMarker') {
        hasMarkerInterface = true
      }
      if (inter.getName().startsWith('datadog.trace.bootstrap.instrumentation.context.FieldBackedProvider$ContextAccessor')) {
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
    final int count = keyValue.get().incrementContextCount()
    WeakReference<KeyClass> instanceRef = new WeakReference(keyValue.get())
    keyValue.set(null)
    GCUtils.awaitGC(instanceRef)

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
@Requires({ "true" == System.getProperty(Config.PREFIX + Config.RUNTIME_CONTEXT_FIELD_INJECTION) })
class FieldBackedProviderFieldInjectionDisabledTest extends AgentTestRunner {
  def "Check that structure is not modified when structure modification is disabled"() {
    setup:
    def keyClass = ContextTestInstrumentation.DisabledKeyClass
    boolean hasField = false
    for (Field field : keyClass.getDeclaredFields()) {
      if (field.getName().startsWith("__datadog")) {
        hasField = true
        break
      }
    }

    boolean hasMarkerInterface = false
    boolean hasAccessorInterface = false
    for (Class inter : keyClass.getInterfaces()) {
      if (inter.getName() == 'datadog.trace.bootstrap.FieldBackedContextStoreAppliedMarker') {
        hasMarkerInterface = true
      }
      if (inter.getName().startsWith('datadog.trace.bootstrap.instrumentation.context.FieldBackedProvider$ContextAccessor')) {
        hasAccessorInterface = true
      }
    }

    expect:
    Config.get().isPrioritySamplingEnabled() == false
    hasField == false
    hasMarkerInterface == false
    hasAccessorInterface == false
    keyClass.newInstance().isInstrumented() == true
  }

}
