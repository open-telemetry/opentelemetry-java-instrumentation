/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.batch.v3_0;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.internal.ExperimentalInstrumentationModule;
import io.opentelemetry.javaagent.instrumentation.spring.batch.v3_0.chunk.StepBuilderInstrumentation;
import io.opentelemetry.javaagent.instrumentation.spring.batch.v3_0.item.ChunkOrientedTaskletInstrumentation;
import io.opentelemetry.javaagent.instrumentation.spring.batch.v3_0.item.JsrChunkProcessorInstrumentation;
import io.opentelemetry.javaagent.instrumentation.spring.batch.v3_0.item.SimpleChunkProcessorInstrumentation;
import io.opentelemetry.javaagent.instrumentation.spring.batch.v3_0.item.SimpleChunkProviderInstrumentation;
import io.opentelemetry.javaagent.instrumentation.spring.batch.v3_0.job.JobBuilderHelperInstrumentation;
import io.opentelemetry.javaagent.instrumentation.spring.batch.v3_0.job.JobFactoryBeanInstrumentation;
import io.opentelemetry.javaagent.instrumentation.spring.batch.v3_0.job.JobParserJobFactoryBeanInstrumentation;
import io.opentelemetry.javaagent.instrumentation.spring.batch.v3_0.step.StepBuilderHelperInstrumentation;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import java.util.Arrays;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class SpringBatchInstrumentationModule extends InstrumentationModule
    implements ExperimentalInstrumentationModule {
  public SpringBatchInstrumentationModule() {
    super("spring-batch", "spring-batch-3.0");
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    // JSR-352 Batch API
    return hasClassesNamed("org.springframework.batch.core.jsr.launch.JsrJobOperator");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return Arrays.asList(
        // job instrumentations
        new JobBuilderHelperInstrumentation(),
        new JobFactoryBeanInstrumentation(),
        new JobParserJobFactoryBeanInstrumentation(),
        // step instrumentation
        new StepBuilderHelperInstrumentation(),
        // chunk instrumentation
        new StepBuilderInstrumentation(),
        // item instrumentations
        new ChunkOrientedTaskletInstrumentation(),
        new SimpleChunkProviderInstrumentation(),
        new SimpleChunkProcessorInstrumentation(),
        new JsrChunkProcessorInstrumentation());
  }

  @Override
  public boolean defaultEnabled(ConfigProperties config) {
    // TODO: replace this with an experimental flag
    return false;
  }

  @Override
  public boolean isIndyReady() {
    return true;
  }
}
