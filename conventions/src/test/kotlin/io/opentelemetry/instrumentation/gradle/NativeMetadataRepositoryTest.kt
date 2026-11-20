/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.gradle

import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class NativeMetadataRepositoryTest {
  @TempDir
  lateinit var root: Path

  @Test
  fun `native task graph sees repository metadata for each project`() {
    val repository = root.resolve("repo")
    val metadata = repository.resolve(
      "org/graalvm/buildtools/graalvm-reachability-metadata/1.1.19/" +
        "graalvm-reachability-metadata-1.1.19-repository.zip"
    )
    Files.createDirectories(metadata.parent)
    Files.copy(Path.of(System.getProperty("nativeMetadataTestRepository")), metadata)
    zip(repository.resolve("aopalliance/aopalliance/1.0/aopalliance-1.0.jar"), emptyMap())
    zip(
      repository.resolve("org/graalvm/buildtools/junit-platform-native/1.1.19/junit-platform-native-1.1.19.jar"),
      emptyMap()
    )
    for (module in listOf("launcher", "console", "reporting")) {
      zip(
        repository.resolve("org/junit/platform/junit-platform-$module/1.0/junit-platform-$module-1.0.jar"),
        emptyMap()
      )
    }
    Files.writeString(root.resolve("settings.gradle"), "include 'first', 'second'")
    for (name in listOf("first", "second")) {
      val project = root.resolve(name)
      Files.createDirectories(project)
      Files.writeString(
        project.resolve("build.gradle"),
        """
        plugins {
          id 'java'
          id 'otel.spring-native-test-conventions' apply false
        }
        repositories {
          maven {
            url = uri('../repo')
            metadataSources { artifact() }
          }
        }
        configurations.create('aotTestCompileOnly')
        tasks.register('compileAotJava', JavaCompile)
        tasks.register('compileAotTestJava', JavaCompile)
        tasks.register('checkstyleAot')
        tasks.register('checkstyleAotTest')
        dependencies {
          testImplementation 'aopalliance:aopalliance:1.0'
          testImplementation 'org.junit.platform:junit-platform-launcher:1.0'
          testImplementation 'org.junit.platform:junit-platform-console:1.0'
          testImplementation 'org.junit.platform:junit-platform-reporting:1.0'
        }
        apply plugin: 'otel.spring-native-test-conventions'

        abstract class MetadataProbe extends DefaultTask {
          @InputFiles
          abstract ConfigurableFileCollection getMetadata()

          @TaskAction
          void checkMetadata() {
            def selected = metadata.files.find {
              new File(it, 'reachability-metadata.json').isFile() &&
                it.path.contains('aopalliance/aopalliance/1.0')
            }
            assert selected != null
            println("Selected metadata: ${'$'}selected")
          }
        }
        plugins.withId('org.graalvm.buildtools.native') {
          tasks.register('nativeTestProbe', MetadataProbe) {
            metadata.from(graalvmNative.binaries.test.configurationFileDirectories)
          }
        }
        """.trimIndent()
      )
    }

    val runner = GradleRunner.create()
      .withProjectDir(root.toFile())
      .withPluginClasspath()
    runner.withArguments("help").build()
    assertThat(root.resolve("first/build/metadata-repository")).doesNotExist()
    assertThat(root.resolve("second/build/metadata-repository")).doesNotExist()
    val result = runner.withArguments("nativeTestProbe", "--parallel", "--max-workers=2").build()

    assertThat(result.output).contains("Using GraalVM reachability metadata repository")
    val selected = result.output.lineSequence().filter { it.startsWith("Selected metadata: ") }.toList()
    assertThat(selected).hasSize(2).doesNotHaveDuplicates()
    val first = Files.list(root.resolve("first/build/metadata-repository")).use { it.findFirst().orElseThrow() }
    val second = Files.list(root.resolve("second/build/metadata-repository")).use { it.findFirst().orElseThrow() }
    assertThat(first).exists().isNotEqualTo(second)
    assertThat(second).exists()
    Files.delete(first)
    runner.build()
    assertThat(first).exists()
  }

  @Test
  fun `local archive is restored when configuration cache is reused`() {
    zip(root.resolve("repository.zip"), mapOf("example" to "metadata"))
    Files.writeString(root.resolve("settings.gradle"), "")
    Files.writeString(
      root.resolve("build.gradle"),
      """
      import io.opentelemetry.instrumentation.gradle.NativeMetadataRepository

      plugins { id 'otel.spring-native-test-conventions' apply false }

      abstract class VerifyArchive extends DefaultTask {
        @Input abstract Property<URI> getLocation()
        @TaskAction void verify() { assert new File(location.get()).isFile() }
      }

      def repository = providers.of(NativeMetadataRepository) {
        parameters.archive.set(layout.projectDirectory.file('repository.zip'))
        parameters.destination.set(layout.buildDirectory.dir('metadata-repository'))
      }
      tasks.register('verifyArchive', VerifyArchive) { location.set(repository) }
      """.trimIndent()
    )
    val runner = GradleRunner.create()
      .withProjectDir(root.toFile())
      .withPluginClasspath()
      .withArguments("verifyArchive", "--configuration-cache")
    runner.build()
    val archive = Files.list(root.resolve("build/metadata-repository")).use { it.findFirst().orElseThrow() }
    Files.delete(archive)
    assertThat(runner.build().output).contains("Configuration cache entry reused")
    assertThat(archive).exists()
  }

  private fun zip(path: Path, entries: Map<String, String>) {
    Files.createDirectories(path.parent)
    ZipOutputStream(Files.newOutputStream(path)).use { zip ->
      for ((name, content) in entries) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(content.toByteArray())
        zip.closeEntry()
      }
    }
  }
}
