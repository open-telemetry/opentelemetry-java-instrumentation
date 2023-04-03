/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package test.io.opentelemetry.javaagent.instrumentation.vaadin;

import io.opentelemetry.javaagent.instrumentation.vaadin.AbstractVaadin14Test;
import java.io.File;
import java.net.URISyntaxException;
import net.bytebuddy.asm.Advice;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class Vaadin14LatestTest extends AbstractVaadin14Test {

  static class UpdatePackageAdvice {
    @Advice.OnMethodEnter(skipOn = Advice.OnDefaultValue.class)
    public static Object onEnter() {
      return null;
    }

    @SuppressWarnings("UnusedVariable")
    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(@Advice.Return(readOnly = false) boolean result) {
      result = false;
    }
  }

  @BeforeAll
  @Override
  protected void setup() throws URISyntaxException {
    // Prevent vaadin from regenerating package.json & pnpm-lock.yaml
    // Vaadin adds a hash to package.json that includes path to node_modules directory, so it won't
    // be same on all computers. To avoid vaadin replacing our provided package.json we suppress the
    // package.json modification check.
    /*
    Instrumentation instrumentation = ByteBuddyAgent.install();
    new AgentBuilder.Default()
        .type(named("com.vaadin.flow.server.frontend.TaskUpdatePackages"))
        .transform(
            new AgentBuilder.Transformer.ForAdvice()
                .advice(
                    named("updatePackageJsonDependencies"), UpdatePackageAdvice.class.getName()))
        .installOn(instrumentation);

     */

    super.setup();
  }

  @Override
  protected void prepareVaadinBaseDir(File baseDir) {
    // copyResource("/pnpm/package.json", baseDir);
    // copyResource("/pnpm/pnpm-lock.yaml", baseDir);
  }
}
