/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.bytebuddy;

import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.javaagent.tooling.instrumentation.indy.PatchByteCodeVersionTransformer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.agent.builder.ResettableClassFileTransformer;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.utility.JavaModule;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.Opcodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class PatchBytecodeVersionTest {

  private static final String BYTEBUDDY_DUMP = "net.bytebuddy.dump";
  private static ResettableClassFileTransformer transformer;

  private static final Logger logger = LoggerFactory.getLogger(PatchBytecodeVersionTest.class);

  private static Path tempDir;

  @BeforeAll
  static void setUp(@TempDir Path temp) {

    assertThat(temp).isEmptyDirectory();
    tempDir = temp;
    System.setProperty(BYTEBUDDY_DUMP, temp.toString());

    AgentBuilder builder = new AgentBuilder.Default().disableClassFormatChanges()
        .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
        .with(new AgentBuilder.Listener.Adapter() {
          @Override
          public void onError(
              String typeName,
              ClassLoader classLoader,
              JavaModule module,
              boolean loaded,
              Throwable throwable) {
            logger.error("Transformation error", throwable);
          }
        })
        // commons lang 3
        .type(named("org.apache.commons.lang3.StringUtils"))
        .transform(new PatchByteCodeVersionTransformer())
        .transform(transformerFor(isMethod().and(named("startsWith"))))
        // servlet 2.5
        .type(named("javax.servlet.GenericServlet"))
        .transform(new PatchByteCodeVersionTransformer())
        .transform(transformerFor(isMethod().and(named("init"))));

    ByteBuddyAgent.install();
    transformer = builder.installOn(ByteBuddyAgent.getInstrumentation());
  }

  @AfterAll
  static void tearDown() {
    transformer.reset(
        ByteBuddyAgent.getInstrumentation(),
        AgentBuilder.RedefinitionStrategy.RETRANSFORMATION);

    System.clearProperty(BYTEBUDDY_DUMP);
  }

  @Test
  void patchJava6() {
    testVersionUpgrade(Opcodes.V1_6,
        () -> StringUtils.startsWith("", ""),
        2,
        "org.apache.commons.lang3.StringUtils");
  }

  @Test
  void patchJava5() {
    testVersionUpgrade(Opcodes.V1_5,
        () -> {
          try {
            new HttpServlet() {}.init();
          } catch (ServletException e) {
            throw new RuntimeException(e);
          }
        },
        1,
        "javax.servlet.GenericServlet");
  }

  private static void testVersionUpgrade(int originalVersion, Runnable task,
      int expectedCountIncrement,
      String className) {
    if (originalVersion >= Opcodes.V1_7) {
      throw new IllegalArgumentException("must use pre-java7 bytecode");
    }

    int startCount = PatchTestAdvice.invocationCount.get();
    assertThat(PatchTestAdvice.invocationCount.get()).isEqualTo(startCount);

    task.run();

    assertThat(PatchTestAdvice.invocationCount.get()).isEqualTo(
        startCount + expectedCountIncrement);

    Path instrumentedClass = null;
    Path instrumentedClassOriginal = null;
    try (Stream<Path> files = Files.find(tempDir, 1,
        (path, attr) -> Files.isRegularFile(path) && path.getFileName().toString().startsWith(
            className))) {

      for (Path path : files.collect(Collectors.toList())) {
        if (path.getFileName().toString().contains("-original")) {
          instrumentedClassOriginal = path;
        } else {
          instrumentedClass = path;
        }
      }

    } catch (IOException e) {
      throw new IllegalStateException(e);
    }

    assertThat(instrumentedClass).exists();
    assertThat(instrumentedClassOriginal).exists();

    assertThat(getBytecodeVersion(instrumentedClassOriginal))
        .describedAs(
            "expected original bytecode for class '%s' should have been compiled for Java %d, see folder %s for bytecode dumps",
            className, 7 - (Opcodes.V1_7 - originalVersion), tempDir)
        .isEqualTo(originalVersion);

    assertThat(getBytecodeVersion(instrumentedClass))
        .describedAs(
            "expected instrumented bytecode for class '%s' should have been upgraded to Java 7, see folder %s for bytecode dumps",
            className, tempDir)
        .isEqualTo(Opcodes.V1_7);
  }

  private static int getBytecodeVersion(Path file) {
    byte[] bytecode;
    try {
      bytecode = Files.readAllBytes(file);
      return bytecode[7];
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }

  }

  private static AgentBuilder.Transformer.ForAdvice transformerFor(
      ElementMatcher.Junction<MethodDescription> methodMatcher) {
    return new AgentBuilder.Transformer.ForAdvice()
        .with(new AgentBuilder.LocationStrategy.Simple(
            ClassFileLocator.ForClassLoader.of(PatchTestAdvice.class.getClassLoader())))
        .advice(methodMatcher, PatchTestAdvice.class.getName());
  }
}
