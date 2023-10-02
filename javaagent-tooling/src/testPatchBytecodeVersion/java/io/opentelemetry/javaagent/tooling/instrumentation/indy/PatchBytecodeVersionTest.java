/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.instrumentation.indy;

import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import net.bytebuddy.ClassFileVersion;
import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.agent.builder.ResettableClassFileTransformer;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.utility.JavaModule;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class PatchBytecodeVersionTest {

  private static final String BYTEBUDDY_DUMP = "net.bytebuddy.dump";
  public static final String ORIGINAL_SUFFIX = "-original";
  public static final String CLASSNAME_PREFIX = "oldbytecode_";
  private static ResettableClassFileTransformer transformer;

  private static final Logger logger = LoggerFactory.getLogger(PatchBytecodeVersionTest.class);

  private static Path tempDir;

  @BeforeAll
  static void setUp(@TempDir Path temp) {

    assertThat(temp).isEmptyDirectory();
    tempDir = temp;
    System.setProperty(BYTEBUDDY_DUMP, temp.toString());

    AgentBuilder builder =
        new AgentBuilder.Default()
            .disableClassFormatChanges()
            .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
            .with(
                new AgentBuilder.Listener.Adapter() {
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
            .type(nameStartsWith(CLASSNAME_PREFIX))
            .transform(new PatchByteCodeVersionTransformer())
            .transform(transformerFor(isMethod().and(named("toString"))));

    ByteBuddyAgent.install();
    transformer = builder.installOn(ByteBuddyAgent.getInstrumentation());
  }

  @AfterAll
  static void tearDown() {
    transformer.reset(
        ByteBuddyAgent.getInstrumentation(), AgentBuilder.RedefinitionStrategy.RETRANSFORMATION);

    System.clearProperty(BYTEBUDDY_DUMP);
  }

  @ParameterizedTest
  @MethodSource("bytecodeVersions")
  void upgradeBytecode(ClassFileVersion version) {

    String className = CLASSNAME_PREFIX + version.getMinorMajorVersion();

    int startCount = PatchTestAdvice.invocationCount.get();
    assertThat(PatchTestAdvice.invocationCount.get()).isEqualTo(startCount);

    assertThat(OldBytecode.generateAndRun(className, version)).isEqualTo("toString");

    assertThat(PatchTestAdvice.invocationCount.get()).isEqualTo(startCount + 1);

    Path instrumentedClass;
    Path instrumentedClassOriginal;

    try (Stream<Path> files =
        Files.find(
            tempDir,
            1,
            (path, attr) -> {
              String fileName = path.getFileName().toString();
              return Files.isRegularFile(path)
                  && fileName.startsWith(className)
                  && fileName.contains(ORIGINAL_SUFFIX);
            })) {

      instrumentedClassOriginal = files.findFirst().orElseThrow(IllegalStateException::new);
      String upgradedClassFileName =
          instrumentedClassOriginal.getFileName().toString().replace(ORIGINAL_SUFFIX, "");
      instrumentedClass = instrumentedClassOriginal.resolveSibling(upgradedClassFileName);

    } catch (IOException e) {
      throw new IllegalStateException(e);
    }

    assertThat(instrumentedClass).exists();
    assertThat(instrumentedClassOriginal).exists();

    assertThat(getBytecodeVersion(instrumentedClassOriginal))
        .describedAs(
            "expected original bytecode for class '%s' in '%s' should have been compiled for %s",
            className, instrumentedClassOriginal.toAbsolutePath(), version)
        .isEqualTo(version);

    if (version.isLessThan(ClassFileVersion.JAVA_V7)) {
      assertThat(getBytecodeVersion(instrumentedClass))
          .describedAs(
              "expected instrumented bytecode for class '%s' in '%s' should have been upgraded to Java 7",
              className, instrumentedClass.toAbsolutePath())
          .isEqualTo(ClassFileVersion.JAVA_V7);
    } else {
      assertThat(getBytecodeVersion(instrumentedClass))
          .describedAs("original bytecode version shouldn't be altered")
          .isEqualTo(version);
    }
  }

  static List<ClassFileVersion> bytecodeVersions() {
    return Arrays.asList(
        ClassFileVersion.JAVA_V1,
        ClassFileVersion.JAVA_V2,
        ClassFileVersion.JAVA_V3,
        ClassFileVersion.JAVA_V4,
        ClassFileVersion.JAVA_V5,
        ClassFileVersion.JAVA_V6,
        // Java 7 and later should not be upgraded
        ClassFileVersion.JAVA_V7,
        ClassFileVersion.JAVA_V8);
  }

  private static ClassFileVersion getBytecodeVersion(Path file) {
    try {
      return ClassFileVersion.ofClassFile(Files.readAllBytes(file));
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private static AgentBuilder.Transformer.ForAdvice transformerFor(
      ElementMatcher.Junction<MethodDescription> methodMatcher) {
    return new AgentBuilder.Transformer.ForAdvice()
        .with(
            new AgentBuilder.LocationStrategy.Simple(
                ClassFileLocator.ForClassLoader.of(PatchTestAdvice.class.getClassLoader())))
        .advice(methodMatcher, PatchTestAdvice.class.getName());
  }
}
