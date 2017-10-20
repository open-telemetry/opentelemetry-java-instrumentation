/*
 * Copyright 2017 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.datadoghq.agent;

import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;

import com.datadoghq.agent.instrumentation.Instrumenter;
import java.lang.instrument.Instrumentation;
import java.util.ServiceLoader;
import lombok.extern.slf4j.Slf4j;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.utility.JavaModule;

/**
 * This class provides a wrapper around the ByteMan agent, to establish required system properties
 * and the manager class.
 */
@Slf4j
public class TracingAgent {

  public static void premain(String agentArgs, final Instrumentation inst) throws Exception {
    addByteBuddy(inst);
    agentArgs = addManager(agentArgs);
    log.debug("Using premain for loading {}", TracingAgent.class.getSimpleName());
    org.jboss.byteman.agent.Main.premain(agentArgs, inst);
  }

  public static void agentmain(String agentArgs, final Instrumentation inst) throws Exception {
    addByteBuddy(inst);
    agentArgs = addManager(agentArgs);
    log.debug("Using agentmain for loading {}", TracingAgent.class.getSimpleName());
    org.jboss.byteman.agent.Main.agentmain(agentArgs, inst);
  }

  protected static String addManager(String agentArgs) {
    if (agentArgs == null || agentArgs.trim().isEmpty()) {
      agentArgs = "";
    } else {
      agentArgs += ",";
    }
    agentArgs += "manager:" + AgentRulesManager.class.getName();
    log.debug("Agent args=: {}", agentArgs);
    return agentArgs;
  }

  public static void addByteBuddy(final Instrumentation inst) {

    AgentBuilder agentBuilder =
        new AgentBuilder.Default()
            .disableClassFormatChanges()
            .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
            .with(new Listener())
            .ignore(nameStartsWith("com.datadoghq.agent.integration"));

    for (final Instrumenter instrumenter : ServiceLoader.load(Instrumenter.class)) {
      agentBuilder = instrumenter.instrument(agentBuilder);
    }

    agentBuilder.installOn(inst);
  }

  @Slf4j
  static class Listener implements AgentBuilder.Listener {

    @Override
    public void onError(
        final String typeName,
        final ClassLoader classLoader,
        final JavaModule module,
        final boolean loaded,
        final Throwable throwable) {
      log.warn("Failed to handle " + typeName + " for transformation", throwable);
    }

    @Override
    public void onTransformation(
        final TypeDescription typeDescription,
        final ClassLoader classLoader,
        final JavaModule module,
        final boolean loaded,
        final DynamicType dynamicType) {
      log.debug("Transformed {0}", typeDescription);
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
}
