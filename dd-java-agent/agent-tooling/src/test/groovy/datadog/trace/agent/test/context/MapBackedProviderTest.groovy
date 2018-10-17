package datadog.trace.agent.test.context

import datadog.trace.agent.test.TestUtils
import datadog.trace.agent.tooling.HelperInjector
import datadog.trace.agent.tooling.Instrumenter
import datadog.trace.agent.tooling.context.MapBackedProvider
import net.bytebuddy.agent.ByteBuddyAgent
import net.bytebuddy.agent.builder.AgentBuilder
import net.bytebuddy.agent.builder.ResettableClassFileTransformer
import net.bytebuddy.description.type.TypeDescription
import net.bytebuddy.dynamic.DynamicType
import net.bytebuddy.matcher.ElementMatcher
import net.bytebuddy.utility.JavaModule
import spock.lang.Shared
import spock.lang.Specification

import java.lang.ref.WeakReference
import java.util.concurrent.Callable

import static net.bytebuddy.matcher.ElementMatchers.named

class MapBackedProviderTest extends Specification {
  @Shared
  ResettableClassFileTransformer transformer

  def setupSpec() {
    final MapBackedProvider contextProvider = new MapBackedProvider(new TestInstrumenter())

    AgentBuilder builder = new AgentBuilder.Default()
      .disableClassFormatChanges()
      .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
      .type(named("datadog.trace.agent.test.context.ClassToRemap"))
      .or(named("datadog.trace.agent.test.context.BadClassToRemap"))
      .transform(new AgentBuilder.Transformer() {
      @Override
      DynamicType.Builder<?> transform(DynamicType.Builder<?> builder, TypeDescription typeDescription, ClassLoader classLoader, JavaModule module) {
        return builder.visit(contextProvider.getInstrumentationVisitor())
      }})
    .transform(new HelperInjector(contextProvider.dynamicClasses()))
    .with(new AgentBuilder.Listener() {
      @Override
      void onDiscovery(String typeName, ClassLoader classLoader, JavaModule module, boolean loaded) { }
      @Override
      void onTransformation(TypeDescription typeDescription, ClassLoader classLoader, JavaModule module, boolean loaded, DynamicType dynamicType) {
        assert !"datadog.trace.agent.test.context.BadClassToRemap".equals(typeDescription.getName())
      }
      @Override
      void onIgnored(TypeDescription typeDescription, ClassLoader classLoader, JavaModule module, boolean loaded) { }
      @Override
      void onError(String typeName, ClassLoader classLoader, JavaModule module, boolean loaded, Throwable throwable) {
        assert "datadog.trace.agent.test.context.BadClassToRemap".equals(typeName)
        System.err.println("Exception during test")
        throwable.printStackTrace()
      }
      @Override
      void onComplete(String typeName, ClassLoader classLoader, JavaModule module, boolean loaded) { }
    })

    ByteBuddyAgent.install()
    transformer = builder.installOn(ByteBuddyAgent.getInstrumentation())
  }

  def cleanupSpec() {
    transformer.reset(ByteBuddyAgent.getInstrumentation(), AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
  }

  def "correct api usage stores state in map"() {
    when:
    ClassToRemap instance1 = new ClassToRemap()
    ClassToRemap.mapObject(instance1)
    ClassToRemap.mapObject(instance1)
    ClassToRemap instance2 = new ClassToRemap()
    ClassToRemap.mapObject(instance2)
    ClassToRemap instance3 = new ClassToRemap()

    then:
    ClassToRemap.mapObject(instance1) == 3
    ClassToRemap.mapObject(instance2) == 2
    ClassToRemap.mapObject(instance3) == 1

    when:
    Runnable r1 = new TestRunnable()
    Runnable r2 = new TestRunnable()
    ClassToRemap.mapOtherObject(r1)

    then:
    ClassToRemap.mapOtherObject(r1) == 2
    ClassToRemap.mapOtherObject(r2) == 1
  }

  def "backing map should not create strong refs to user instances"() {
    when:
    ClassToRemap instance = new ClassToRemap()
    int count = ClassToRemap.mapObject(instance)
    WeakReference<ClassToRemap> instanceRef = new WeakReference(instance)
    instance = null
    TestUtils.awaitGC(instanceRef)

    then:
    instanceRef.get() == null
    count == 1
  }

  def "incorrect api usage fails at class load time"() {
    when:
    BadClassToRemap.getName()

    then:
    noExceptionThrown()

    when:
    BadClassToRemap.unmappedObjectError(new Callable() {
      @Override
      Object call() throws Exception {
        return null
      }
    })

    then:
    thrown RuntimeException
  }

  def "context store fails if runtime types are incorrect" () {
    when:
    ClassToRemap.mapIncorrectObject()
    then:
    thrown RuntimeException
  }

  static class TestInstrumenter extends Instrumenter.Default {
    TestInstrumenter() {
      super("test")
    }

    @Override
    ElementMatcher<? super TypeDescription> typeMatcher() {
      return named("not invoked")
    }

    @Override
    Map<? extends ElementMatcher, String> transformers() {
      return Collections.emptyMap()
    }

    @Override
    Map<String, String> contextStore() {
      final Map<String, String> contextStore = new HashMap<>(2)
      contextStore.put('datadog.trace.agent.test.context.ClassToRemap', 'datadog.trace.agent.test.context.ClassToRemap$State')
      contextStore.put('java.lang.Runnable', 'datadog.trace.agent.test.context.ClassToRemap$State')
      return contextStore
    }
  }

  static class TestRunnable implements Runnable {
    @Override
    void run() {}
  }
}
