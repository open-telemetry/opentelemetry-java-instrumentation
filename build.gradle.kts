import java.time.Duration

plugins {
  id("idea")

  id("io.github.gradle-nexus.publish-plugin")
  id("otel.spotless-conventions")
  /* workaround for
  What went wrong:
  Could not determine the dependencies of task ':smoke-tests-otel-starter:spring-boot-3.2:bootJar'.
  > Could not create task ':smoke-tests-otel-starter:spring-boot-3.2:collectReachabilityMetadata'.
  > Cannot set the value of task ':smoke-tests-otel-starter:spring-boot-3.2:collectReachabilityMetadata' property 'metadataService' of type org.graalvm.buildtools.gradle.internal.GraalVMReachabilityMetadataService using a provider of type org.graalvm.buildtools.gradle.internal.GraalVMReachabilityMetadataService.

  See https://github.com/gradle/gradle/issues/17559#issuecomment-1327991512
   */
  id("org.graalvm.buildtools.native") apply false
}

apply(from = "version.gradle.kts")

nexusPublishing {
  packageGroup.set("io.opentelemetry")

  repositories {
    sonatype {
      username.set(System.getenv("SONATYPE_USER"))
      password.set(System.getenv("SONATYPE_KEY"))
    }
  }

  connectTimeout.set(Duration.ofMinutes(5))
  clientTimeout.set(Duration.ofMinutes(5))

  transitionCheckOptions {
    // We have many artifacts so Maven Central takes a long time on its compliance checks. This sets
    // the timeout for waiting for the repository to close to a comfortable 50 minutes.
    maxRetries.set(300)
    delayBetween.set(Duration.ofSeconds(10))
  }
}

description = "OpenTelemetry instrumentations for Java"

if (project.findProperty("skipTests") as String? == "true") {
  subprojects {
    tasks.withType<Test>().configureEach {
      enabled = false
    }
  }
}

if (gradle.startParameter.taskNames.contains("listTestsInPartition")) {
  tasks {
    val listTestsInPartition by registering {
      group = "Help"
      description = "List test tasks in given partition"

      // total of 4 partitions (see modulo 4 below)
      var testPartition = (project.findProperty("testPartition") as String?)?.toInt()
      if (testPartition == null) {
        throw GradleException("Test partition must be specified")
      } else if (testPartition < 0 || testPartition >= 4) {
        throw GradleException("Invalid test partition")
      }

      val partitionTasks = ArrayList<Test>()
      var testPartitionCounter = 0
      subprojects {
        // relying on predictable ordering of subprojects
        // (see https://docs.gradle.org/current/dsl/org.gradle.api.Project.html#N14CB4)
        // since we are splitting these tasks across different github action jobs
        val enabled = testPartitionCounter++ % 4 == testPartition
        if (enabled) {
          tasks.withType<Test>().configureEach {
            partitionTasks.add(this)
          }
        }
      }

      doLast {
        File("test-tasks.txt").printWriter().use { writer ->
          partitionTasks.forEach { task ->
            var taskPath = task.project.path + ":" + task.name
            // smoke tests are run separately
            // :instrumentation:test runs all instrumentation tests
            if (taskPath != ":smoke-tests:test" && taskPath != ":instrumentation:test") {
              writer.println(taskPath)
            }
          }
        }
      }

      // disable all tasks to stop build
      subprojects {
        tasks.configureEach {
          enabled = false
        }
      }
    }
  }

  // disable all tasks to stop build
  project.tasks.configureEach {
    if (this.name != "listTestsInPartition") {
      enabled = false
    }
  }
}

tasks {
  val generateFossaConfiguration by registering {
    group = "Help"
    description = "Generate .fossa.yml configuration file"

    doLast {
      File(".fossa.yml").printWriter().use { writer ->
        writer.println("version: 3")
        writer.println()
        writer.println("targets:")
        writer.println("  only:")
        writer.println("    # only scanning the modules which are published")
        writer.println("    # (as opposed to internal testing modules")
        rootProject.subprojects
          .sortedBy { it.findProperty("archivesName") as String? }
          .filter { !it.name.startsWith("bom") }
          .filter { it.plugins.hasPlugin("maven-publish") }
          .forEach {
            writer.println("    - type: gradle")
            writer.println("      path: ./")
            writer.println("      target: '${it.path}'")
          }
        writer.println()
        writer.println("experimental:")
        writer.println("  gradle:")
        writer.println("    configurations-only:")
        writer.println("      # consumer will only be exposed to these dependencies")
        writer.println("      - runtimeClasspath")
      }
    }
  }
}
