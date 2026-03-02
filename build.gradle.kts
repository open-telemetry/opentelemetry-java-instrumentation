import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.asRequestBody
import java.time.Duration
import java.util.Base64

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

buildscript {
  dependencies {
    classpath("com.squareup.okhttp3:okhttp:5.3.2")
  }
}

apply(from = "version.gradle.kts")

nexusPublishing {
  packageGroup.set("io.opentelemetry")

  repositories {
    // see https://central.sonatype.org/publish/publish-portal-ossrh-staging-api/#configuration
    sonatype {
      nexusUrl.set(uri("https://ossrh-staging-api.central.sonatype.com/service/local/"))
      snapshotRepositoryUrl.set(uri("https://central.sonatype.com/repository/maven-snapshots/"))
      username.set(System.getenv("SONATYPE_USER"))
      password.set(System.getenv("SONATYPE_KEY"))
    }
  }

  connectTimeout.set(Duration.ofMinutes(5))
  clientTimeout.set(Duration.ofMinutes(30))

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

      val partitionTasks = ArrayList<String>()
      var testPartitionCounter = 0
      subprojects {
        // relying on predictable ordering of subprojects
        // (see https://docs.gradle.org/current/dsl/org.gradle.api.Project.html#N14CB4)
        // since we are splitting these tasks across different github action jobs
        val enabled = testPartitionCounter++ % 4 == testPartition
        if (enabled) {
          val projectPath = this.path
          tasks.withType<Test>().configureEach {
            val taskPath = projectPath + ":" + this.name
            partitionTasks.add(taskPath)
          }
        }
      }

      doLast {
        File("test-tasks.txt").printWriter().use { writer ->
          partitionTasks.forEach { taskPath ->
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
  val stableVersion = version.toString().replace("-alpha", "")

  val generateFossaConfiguration by registering {
    group = "Help"
    description = "Generate .fossa.yml configuration file"

    // Capture the project paths at configuration time to avoid serializing Gradle script objects
    val projectPaths = rootProject.subprojects
      .sortedBy { it.findProperty("archivesName") as String? }
      .filter { !it.name.startsWith("bom") }
      .filter { it.plugins.hasPlugin("maven-publish") }
      .map { it.path }

    val outputFile = layout.projectDirectory.file(".fossa.yml")
    outputs.file(outputFile)

    doLast {
      outputFile.asFile.printWriter().use { writer ->
        writer.println("version: 3")
        writer.println()
        writer.println("targets:")
        writer.println("  only:")
        writer.println("    # only scanning the modules which are published")
        writer.println("    # (as opposed to internal testing modules")
        projectPaths.forEach { path ->
          writer.println("    - type: gradle")
          writer.println("      path: ./")
          writer.println("      target: '$path'")
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

  val generateReleaseBundle by registering(Zip::class) {
    dependsOn(project.tasks.withType<PublishToMavenRepository>())
    from("releaseRepo")

    exclude("**/maven-metadata.*")
    duplicatesStrategy = DuplicatesStrategy.FAIL
    includeEmptyDirs = false
    archiveFileName.set("release-bundle-$stableVersion.zip")
  }

  val uploadReleaseBundle by registering {
    dependsOn(generateReleaseBundle)
    val bundleFile = generateReleaseBundle.flatMap { it.archiveFile }
    doFirst {
      val username = System.getenv("SONATYPE_USER") ?: throw GradleException("Sonatype user not set")
      val password = System.getenv("SONATYPE_KEY") ?: throw GradleException("Sonatype key not set")
      val token = Base64.getEncoder().encodeToString("$username:$password".toByteArray())

      var query = "?name=opentelemetry-java-instrumentation-$stableVersion"
      query += "&publishingType=AUTOMATIC"

      val bundle = bundleFile.get().asFile
      val httpClient = OkHttpClient()

      val request = okhttp3.Request.Builder()
        .url("https://central.sonatype.com/api/v1/publisher/upload$query")
        .post(
          okhttp3.MultipartBody.Builder().addFormDataPart(
            "bundle",
            bundle.name,
            bundle.asRequestBody("application/zip".toMediaType())
          ).build()
        )
        .header("authorization", "Bearer $token")
        .build()

      httpClient.newCall(request).execute().use { response ->
        if (response.code != 201) throw GradleException("Unexpected response status ${response.code} while uploading the release bundle")
        println("Uploaded deployment ${response.body.string()}")
      }
    }
  }

  val resolveLatestDepVersions by registering {
    group = "Help"
    description = "Resolve latest dependency versions and write to .github/config/latest-dep-versions.json"

    doLast {
      if (gradle.startParameter.projectProperties["testLatestDeps"] != "true" ||
        gradle.startParameter.projectProperties["resolveLatestDeps"] != "true") {
        throw GradleException("Must run with -PtestLatestDeps=true -PresolveLatestDeps=true")
      }

      val versions = sortedMapOf<String, String>()

      fun isHigherVersion(v1: String, v2: String): Boolean {
        val parts1 = v1.split(Regex("[.\\-]"))
        val parts2 = v2.split(Regex("[.\\-]"))
        for (i in 0 until maxOf(parts1.size, parts2.size)) {
          val n1 = parts1.getOrElse(i) { "0" }.toIntOrNull() ?: 0
          val n2 = parts2.getOrElse(i) { "0" }.toIntOrNull() ?: 0
          if (n1 != n2) return n1 > n2
        }
        return false
      }

      fun recordVersion(key: String, version: String) {
        val existing = versions[key]
        if (existing == null || isHigherVersion(version, existing)) {
          versions[key] = version
        }
      }

      subprojects {
        configurations
          .filter { it.isCanBeResolved && it.name.contains("test", ignoreCase = true) && it.name.endsWith("RuntimeClasspath") }
          .forEach { config ->
            try {
              config.incoming.resolutionResult.allDependencies.forEach { dep ->
                if (dep is org.gradle.api.artifacts.result.ResolvedDependencyResult) {
                  val requested = dep.requested
                  if (requested is org.gradle.api.artifacts.component.ModuleComponentSelector) {
                    val reqVersion = requested.version
                    val selectedVersion = dep.selected.moduleVersion?.version ?: return@forEach
                    if (reqVersion == "latest.release") {
                      recordVersion("${requested.group}:${requested.module}", selectedVersion)
                    } else if (reqVersion.contains("+")) {
                      recordVersion("${requested.group}:${requested.module}#$reqVersion", selectedVersion)
                    }
                  }
                }
              }
            } catch (e: Exception) {
              logger.warn("Failed to resolve ${this.path}:${config.name}: ${e.message}")
            }
          }
      }

      // Resolve Spring Boot catalog versions using a detached configuration
      listOf("3.+", "4.+").forEach { range ->
        val detached = project.configurations.detachedConfiguration(
          project.dependencies.create("org.springframework.boot:spring-boot-dependencies:$range")
        )
        detached.resolutionStrategy.componentSelection.all {
          val v = candidate.version
          if (v.contains("-alpha", true) || v.contains("-beta", true) ||
            v.contains("-rc", true) || v.contains(".rc", true) ||
            v.contains("-m", true) || v.contains(".m", true) ||
            v.contains(".alpha", true) || v.contains(".beta", true) ||
            v.contains(".cr", true)
          ) {
            reject("pre-release version")
          }
        }
        try {
          val resolvedVersion = detached.incoming.resolutionResult.rootComponent.get()
            .dependencies
            .filterIsInstance<org.gradle.api.artifacts.result.ResolvedDependencyResult>()
            .first()
            .selected.moduleVersion?.version
          if (resolvedVersion != null) {
            versions["org.springframework.boot:spring-boot-dependencies#$range"] = resolvedVersion
          }
        } catch (e: Exception) {
          logger.warn("Failed to resolve Spring Boot $range: ${e.message}")
        }
      }

      val outputFile = file(".github/config/latest-dep-versions.json")
      outputFile.parentFile.mkdirs()
      outputFile.printWriter().use { writer ->
        writer.println("{")
        val entries = versions.entries.toList()
        entries.forEachIndexed { index, (key, value) ->
          val comma = if (index < entries.size - 1) "," else ""
          writer.println("  \"$key\": \"$value\"$comma")
        }
        writer.println("}")
      }

      logger.lifecycle("Wrote ${versions.size} pinned versions to ${outputFile.path}")
    }
  }
}
