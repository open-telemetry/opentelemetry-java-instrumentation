package datadog.trace.agent.test.context

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
      .transform(new AgentBuilder.Transformer() {
      @Override
      DynamicType.Builder<?> transform(DynamicType.Builder<?> builder, TypeDescription typeDescription, ClassLoader classLoader, JavaModule module) {
        return builder.visit(contextProvider.getInstrumentationVisitor())
      }
    })

    ByteBuddyAgent.install()
    transformer = builder.installOn(ByteBuddyAgent.getInstrumentation())
  }

  def cleanupSpec() {
    transformer.reset(ByteBuddyAgent.getInstrumentation(), AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
  }

  def "correct api usage stores state in map"() {
    setup:
    ClassToRemap instance1 = new ClassToRemap()
    ClassToRemap.mapObject(instance1)
    ClassToRemap.mapObject(instance1)
    ClassToRemap instance2 = new ClassToRemap()
    ClassToRemap.mapObject(instance1)
    ClassToRemap instance3 = new ClassToRemap()

    expect:
    ClassToRemap.mapObject(instance1) == 3
    ClassToRemap.mapObject(instance2) == 2
    ClassToRemap.mapObject(instance3) == 1
  }


  // TODO
  def "incorrect api usage fails instrumentation"() {
    setup:
    println "loaded class: " + BadClassToRemap.getName()
    expect:
    1 == 1
    // Instrumentation remapper does not apply.
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
}
