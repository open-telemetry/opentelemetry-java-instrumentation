/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.ktor.v2_0;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class KtorInstrumentationModule extends InstrumentationModule {

  public KtorInstrumentationModule() {
    super("ktor", "ktor-2.0");
  }

  @Override
  public boolean isHelperClass(String className) {
    return className.startsWith("io.opentelemetry.extension.kotlin.");
  }

  @Override
  public boolean isIndyModule() {
    // java.lang.LinkageError: loader constraint violation in interface itable initialization for
    // class
    // io.opentelemetry.javaagent.shaded.instrumentation.ktor.v2_0.client.KtorClientTracing$Companion: when selecting method 'java.lang.Object io.ktor.client.plugins.HttpClientPlugin.prepare(kotlin.jvm.functions.Function1)' the class loader 'app' for super interface io.ktor.client.plugins.HttpClientPlugin, and the class loader io.opentelemetry.javaagent.tooling.instrumentation.indy.InstrumentationModuleClassLoader @2565a7d0 of the selected method's class, io.opentelemetry.javaagent.shaded.instrumentation.ktor.v2_0.client.KtorClientTracing$Companion have different Class objects for the type kotlin.jvm.functions.Function1 used in the signature (io.ktor.client.plugins.HttpClientPlugin is in unnamed module of loader 'app'; io.opentelemetry.javaagent.shaded.instrumentation.ktor.v2_0.client.KtorClientTracing$Companion is in unnamed module of loader io.opentelemetry.javaagent.tooling.instrumentation.indy.InstrumentationModuleClassLoader @2565a7d0, parent loader io.opentelemetry.javaagent.bootstrap.AgentClassLoader @ea30797)
    return false;
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(new ServerInstrumentation(), new HttpClientInstrumentation());
  }
}
