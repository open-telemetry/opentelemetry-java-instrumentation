/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.awssdk.v1_11;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static java.util.Collections.singletonList;
import static net.bytebuddy.matcher.ElementMatchers.named;

import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.extension.instrumentation.internal.ExperimentalInstrumentationModule;
import java.util.List;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

// TODO: Copy & paste with only trivial adaptions from v2
abstract class AbstractAwsSdkInstrumentationModule extends InstrumentationModule
    implements ExperimentalInstrumentationModule {

  protected AbstractAwsSdkInstrumentationModule(String additionalInstrumentationName) {
    super("aws-sdk", "aws-sdk-1.11", additionalInstrumentationName);
  }

  @Override
  public boolean isHelperClass(String className) {
    return className.startsWith("io.opentelemetry.contrib.awsxray.");
  }

  @Override
  public String getModuleGroup() {
    return "aws-sdk";
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    // We don't actually transform it but want to make sure we only apply the instrumentation when
    // our key dependency is present.
    return hasClassesNamed("com.amazonaws.AmazonWebServiceClient");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new ResourceInjectingTypeInstrumentation());
  }

  abstract void doTransform(TypeTransformer transformer);

  // A type instrumentation is needed to trigger resource injection.
  public class ResourceInjectingTypeInstrumentation implements TypeInstrumentation {
    @Override
    public ElementMatcher<TypeDescription> typeMatcher() {
      // This is essentially the entry point of the AWS SDK, all clients implement it. We can ensure
      // our interceptor service definition is injected as early as possible if we typematch against
      // it.
      return named("com.amazonaws.AmazonWebServiceClient");
    }

    @Override
    public void transform(TypeTransformer transformer) {
      doTransform(transformer);
    }
  }
}
