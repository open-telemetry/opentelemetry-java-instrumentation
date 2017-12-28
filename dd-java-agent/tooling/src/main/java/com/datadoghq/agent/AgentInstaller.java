package com.datadoghq.agent;

import static dd.trace.ClassLoaderMatcher.classLoaderWithName;
import static dd.trace.ClassLoaderMatcher.isReflectionClassLoader;
import static net.bytebuddy.matcher.ElementMatchers.any;
import static net.bytebuddy.matcher.ElementMatchers.isBootstrapClassLoader;
import static net.bytebuddy.matcher.ElementMatchers.nameContains;
import static net.bytebuddy.matcher.ElementMatchers.nameMatches;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;

import dd.trace.Instrumenter;
import java.lang.instrument.Instrumentation;
import java.util.ServiceLoader;
import lombok.extern.slf4j.Slf4j;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.agent.builder.ResettableClassFileTransformer;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.utility.JavaModule;

@Slf4j
public class AgentInstaller {

  /**
   * Install the core bytebuddy agent along with all implementations of {@link
   * dd.trace.Instrumenter}.
   *
   * @param inst Java Instrumentation used to install bytebuddy
   * @return the agent's class transformer
   */
  public static ResettableClassFileTransformer installBytebuddyAgent(final Instrumentation inst) {
    return installBytebuddyAgent(inst, null);
  }

  /**
   * Install the core bytebuddy agent along with all implementations of {@link
   * dd.trace.Instrumenter}.
   *
   * @param inst Java Instrumentation used to install bytebuddy
   * @param listener A bytebuddy listener to install with, or null for no listener
   * @return the agent's class transformer
   */
  public static ResettableClassFileTransformer installBytebuddyAgent(
      final Instrumentation inst, AgentBuilder.Listener listener) {
    AgentBuilder agentBuilder =
        new AgentBuilder.Default()
            .disableClassFormatChanges()
            .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
            .with(new LoggingListener())
            .ignore(nameStartsWith("com.datadoghq.agent.integration"))
            .or(nameStartsWith("dd.trace"))
            .or(nameStartsWith("dd.inst"))
            .or(nameStartsWith("dd.deps"))
            .or(nameStartsWith("java."))
            .or(nameStartsWith("com.sun."))
            .or(nameStartsWith("sun."))
            .or(nameStartsWith("jdk."))
            .or(nameStartsWith("org.aspectj."))
            .or(nameStartsWith("org.groovy."))
            .or(nameStartsWith("com.p6spy."))
            .or(nameStartsWith("org.slf4j."))
            .or(nameContains("javassist"))
            .or(nameContains(".asm."))
            .or(nameMatches("com\\.mchange\\.v2\\.c3p0\\..*Proxy"))
            .ignore(
                any(),
                isBootstrapClassLoader()
                    .or(isReflectionClassLoader())
                    .or(
                        classLoaderWithName(
                            "org.codehaus.groovy.runtime.callsite.CallSiteClassLoader")));
    if (null != listener) {
      agentBuilder = agentBuilder.with(listener);
    }
    int numInstrumenters = 0;
    for (final Instrumenter instrumenter : ServiceLoader.load(Instrumenter.class)) {
      agentBuilder = instrumenter.instrument(agentBuilder);
      log.debug("Loading instrumentation {}", instrumenter);
      numInstrumenters++;
    }
    log.debug("Installed {} instrumenter(s)", numInstrumenters);

    return agentBuilder.installOn(inst);
  }

  @Slf4j
  static class LoggingListener implements AgentBuilder.Listener {

    @Override
    public void onError(
        final String typeName,
        final ClassLoader classLoader,
        final JavaModule module,
        final boolean loaded,
        final Throwable throwable) {
      log.debug("Failed to handle " + typeName + " for transformation: " + throwable.getMessage());
    }

    @Override
    public void onTransformation(
        final TypeDescription typeDescription,
        final ClassLoader classLoader,
        final JavaModule module,
        final boolean loaded,
        final DynamicType dynamicType) {
      log.debug("Transformed {} -- {}", typeDescription, classLoader);
    }

    @Override
    public void onIgnored(
        final TypeDescription typeDescription,
        final ClassLoader classLoader,
        final JavaModule module,
        final boolean loaded) {}

    @Override
    public void onComplete(
        final String typeName,
        final ClassLoader classLoader,
        final JavaModule module,
        final boolean loaded) {}

    @Override
    public void onDiscovery(
        final String typeName,
        final ClassLoader classLoader,
        final JavaModule module,
        final boolean loaded) {}
  }

  private AgentInstaller() {}
}
