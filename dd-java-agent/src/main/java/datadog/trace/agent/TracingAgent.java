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
package datadog.trace.agent;

import datadog.opentracing.DDTraceOTInfo;
import datadog.trace.agent.tooling.AgentInstaller;
import datadog.trace.api.DDTraceAnnotationsInfo;
import io.opentracing.Tracer;
import io.opentracing.contrib.tracerresolver.TracerResolver;
import io.opentracing.util.GlobalTracer;
import java.lang.instrument.Instrumentation;
import lombok.extern.slf4j.Slf4j;

/** Entry point for initializing the agent. */
@Slf4j
public class TracingAgent {
  public static void premain(final String agentArgs, final Instrumentation inst) throws Exception {
    log.debug("Using premain for loading {}", TracingAgent.class.getName());
    AgentInstaller.installBytebuddyAgent(inst);
    logVersionInfo();
    registerGlobalTracer();
  }

  public static void agentmain(final String agentArgs, final Instrumentation inst)
      throws Exception {
    log.debug("Using agentmain for loading {}", TracingAgent.class.getName());
    AgentInstaller.installBytebuddyAgent(inst);
    logVersionInfo();
    registerGlobalTracer();
  }

  private static void logVersionInfo() {
    // version classes log important info
    // in static initializers
    DDJavaAgentInfo.VERSION.toString();
    DDTraceOTInfo.VERSION.toString();
    DDTraceAnnotationsInfo.VERSION.toString();
  }

  /** Register a global tracer if no global tracer is already registered. */
  private static synchronized void registerGlobalTracer() {
    if (!GlobalTracer.isRegistered()) {
      // Try to obtain a tracer using the TracerResolver
      final Tracer resolved = TracerResolver.resolveTracer();
      if (resolved != null) {
        try {
          GlobalTracer.register(resolved);
        } catch (final RuntimeException re) {
          log.warn("Failed to register tracer '" + resolved + "'", re);
        }
      } else {
        log.warn("Failed to resolve dd tracer");
      }
    }
  }
}
