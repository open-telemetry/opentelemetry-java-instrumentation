package dd.test

import dd.trace.Instrumenter
import io.opentracing.Tracer
import io.opentracing.util.GlobalTracer
import net.bytebuddy.agent.ByteBuddyAgent
import net.bytebuddy.agent.builder.AgentBuilder

import java.lang.reflect.Field

import static net.bytebuddy.agent.builder.AgentBuilder.RedefinitionStrategy.Listener.StreamWriting.toSystemError
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith
import static org.assertj.core.api.Assertions.assertThat

class TestUtils {

  static addByteBuddyAgent() {
    AgentBuilder builder =
      new AgentBuilder.Default()
        .disableClassFormatChanges()
        .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
        .with(toSystemError())
        .ignore(nameStartsWith("dd.inst"))

    def instrumenters = ServiceLoader.load(Instrumenter)
    for (final Instrumenter instrumenter : instrumenters) {
      System.err.println("Instrumenting with " + instrumenter)
      builder = instrumenter.instrument(builder)
    }
    builder.installOn(ByteBuddyAgent.install())
  }

  static addTracer(Tracer tracer) {
    try {
      Class.forName("com.datadoghq.agent.InstrumentationRulesManager")
        .getMethod("registerClassLoad")
        .invoke(null)
    } catch (ClassNotFoundException e) {
    }
    try {
      GlobalTracer.register(tracer)
    } catch (final Exception e) {
      // Force it anyway using reflection
      final Field field = GlobalTracer.getDeclaredField("tracer")
      field.setAccessible(true)
      field.set(null, tracer)
    }
    assertThat(GlobalTracer.isRegistered()).isTrue()
  }
}
