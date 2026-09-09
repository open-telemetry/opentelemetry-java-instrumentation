/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.mcp.v0_14;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class McpJavaSdkInstrumentationModule extends InstrumentationModule {

  public McpJavaSdkInstrumentationModule() {
    super("mcp-java-sdk", "mcp-java-sdk-0.14");
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    return hasClassesNamed("io.modelcontextprotocol.spec.McpClientSession");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new McpClientSessionInstrumentation());
  }
}
