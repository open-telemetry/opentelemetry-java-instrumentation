/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import io.opentelemetry.javaagent.muzzle.AcceptableVersions
import io.opentelemetry.javaagent.muzzle.MuzzleDirective
import io.opentelemetry.javaagent.muzzle.MuzzleExtension
import io.opentelemetry.javaagent.muzzle.matcher.MuzzleGradlePluginUtil
import org.apache.maven.repository.internal.MavenRepositorySystemUtils
import org.eclipse.aether.RepositorySystem
import org.eclipse.aether.RepositorySystemSession
import org.eclipse.aether.artifact.Artifact
import org.eclipse.aether.artifact.DefaultArtifact
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory
import org.eclipse.aether.repository.LocalRepository
import org.eclipse.aether.repository.RemoteRepository
import org.eclipse.aether.resolution.VersionRangeRequest
import org.eclipse.aether.resolution.VersionRangeResult
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory
import org.eclipse.aether.spi.connector.transport.TransporterFactory
import org.eclipse.aether.transport.http.HttpTransporterFactory
import org.eclipse.aether.version.Version
import java.net.URL
import java.net.URLClassLoader
import java.util.stream.StreamSupport

plugins {
  `java-library`

  id("com.github.johnrengelman.shadow")
}

// Select a random set of versions to test
val RANGE_COUNT_LIMIT = Integer.getInteger("otel.javaagent.muzzle.versions.limit", 10)

val muzzleConfig = extensions.create<MuzzleExtension>("muzzle")

val muzzleTooling: Configuration by configurations.creating {
  isCanBeConsumed = false
  isCanBeResolved = true
}

val muzzleBootstrap: Configuration by configurations.creating {
  isCanBeConsumed = false
  isCanBeResolved = true
}

val shadowModule by tasks.registering(ShadowJar::class) {
  from(tasks.jar)

  configurations = listOf(project.configurations.runtimeClasspath.get())

  archiveFileName.set("module-for-muzzle-check.jar")

  dependsOn(tasks.jar)
}

val shadowMuzzleTooling by tasks.registering(ShadowJar::class) {
  configurations = listOf(muzzleTooling)

  archiveFileName.set("tooling-for-muzzle-check.jar")
}

val shadowMuzzleBootstrap by tasks.registering(ShadowJar::class) {
  configurations = listOf(muzzleBootstrap)

  // exclude the agent part of the javaagent-extension-api
  exclude("io/opentelemetry/javaagent/extension/**")

  archiveFileName.set("bootstrap-for-muzzle-check.jar")
}

// this is a copied from io.opentelemetry.instrumentation.javaagent-shadowing for now at least to
// avoid publishing io.opentelemetry.instrumentation.javaagent-shadowing publicly
tasks.withType<ShadowJar>().configureEach {
  mergeServiceFiles()
  // Merge any AWS SDK service files that may be present (too bad they didn't just use normal
  // service loader...)
  mergeServiceFiles("software/amazon/awssdk/global/handlers")

  exclude("**/module-info.class")

  // Prevents conflict with other SLF4J instances. Important for premain.
  relocate("org.slf4j", "io.opentelemetry.javaagent.slf4j")
  // rewrite dependencies calling Logger.getLogger
  relocate("java.util.logging.Logger", "io.opentelemetry.javaagent.bootstrap.PatchLogger")

  // prevents conflict with library instrumentation
  relocate("io.opentelemetry.instrumentation", "io.opentelemetry.javaagent.shaded.instrumentation")

  // relocate(OpenTelemetry API)
  relocate("io.opentelemetry.api", "io.opentelemetry.javaagent.shaded.io.opentelemetry.api")
  relocate("io.opentelemetry.semconv", "io.opentelemetry.javaagent.shaded.io.opentelemetry.semconv")
  relocate("io.opentelemetry.context", "io.opentelemetry.javaagent.shaded.io.opentelemetry.context")

  // relocate(the OpenTelemetry extensions that are used by instrumentation modules)
  // these extensions live in the AgentClassLoader, and are injected into the user's class loader
  // by the instrumentation modules that use them
  relocate("io.opentelemetry.extension.aws", "io.opentelemetry.javaagent.shaded.io.opentelemetry.extension.aws")
  relocate("io.opentelemetry.extension.kotlin", "io.opentelemetry.javaagent.shaded.io.opentelemetry.extension.kotlin")

  // this is for instrumentation on opentelemetry-api itself
  relocate("application.io.opentelemetry", "io.opentelemetry")

  // this is for instrumentation on java.util.logging (since java.util.logging itself is shaded above)
  relocate("application.java.util.logging", "java.util.logging")
}

val compileMuzzle by tasks.registering {
  dependsOn(shadowMuzzleBootstrap)
  dependsOn(shadowMuzzleTooling)
  dependsOn(tasks.named("classes"))
}

val muzzle by tasks.registering {
  group = "Muzzle"
  description = "Run instrumentation muzzle on compile time dependencies"
  dependsOn(compileMuzzle)
}

tasks.register("printMuzzleReferences") {
  group = "Muzzle"
  description = "Print references created by instrumentation muzzle"
  dependsOn(compileMuzzle)
  dependsOn(shadowModule)
  doLast {
    val instrumentationCL = createInstrumentationClassloader()
    withContextClassLoader(instrumentationCL) {
      MuzzleGradlePluginUtil.printMuzzleReferences(instrumentationCL)
    }
  }
}

val projectRepositories = mutableListOf<RemoteRepository>().apply {
  // Manually add mavenCentral until https://github.com/gradle/gradle/issues/17295
  // Adding mavenLocal is much more complicated but hopefully isn't required for normal usage of
  // Muzzle.
  add(
    RemoteRepository.Builder(
      "MavenCentral", "default", "https://repo.maven.apache.org/maven2/")
      .build())
  for (repository in repositories) {
    if (repository is MavenArtifactRepository) {
      add(
        RemoteRepository.Builder(
          repository.getName(),
          "default",
          repository.url.toString())
          .build())
    }
  }
}.toList()

val hasRelevantTask = gradle.startParameter.taskNames.any {
  // removing leading ':' if present
  val taskName = it.removePrefix(":")
  val projectPath = project.path.substring(1)
  // Either the specific muzzle task in this project or a top level muzzle task.
  taskName == "${projectPath}:muzzle" || taskName.startsWith("instrumentation:muzzle")
}

if (hasRelevantTask) {
  val system = newRepositorySystem()
  val session = newRepositorySystemSession(system)

  afterEvaluate {
    var runAfter = muzzle

    for (muzzleDirective in muzzleConfig.directives.get()) {
      logger.info("configured ${muzzleDirective}")

      if (muzzleDirective.coreJdk.get()) {
        runAfter = addMuzzleTask(muzzleDirective, null, runAfter)
      } else {
        for (singleVersion in muzzleDirectiveToArtifacts(muzzleDirective, system, session)) {
          runAfter = addMuzzleTask(muzzleDirective, singleVersion, runAfter)
        }
        if (muzzleDirective.assertInverse.get()) {
          for (inverseDirective in inverseOf(muzzleDirective, system, session)) {
            for (singleVersion in muzzleDirectiveToArtifacts(inverseDirective, system, session)) {
              runAfter = addMuzzleTask(inverseDirective, singleVersion, runAfter)
            }
          }
        }
      }
    }
  }
}

fun createInstrumentationClassloader(): ClassLoader {
  logger.info("Creating instrumentation class loader for: $path")
  val muzzleShadowJar = shadowModule.get().archiveFile.get()
  val muzzleToolingShadowJar = shadowMuzzleTooling.get().archiveFile.get()
  return classpathLoader(files(muzzleShadowJar, muzzleToolingShadowJar), ClassLoader.getPlatformClassLoader())
}

fun classpathLoader(classpath: FileCollection, parent: ClassLoader): ClassLoader {
  logger.info("Adding to classloader:")
  val urls: Array<URL> = StreamSupport.stream(classpath.spliterator(), false)
    .map {
      logger.info("--${it}")
      it.toURI().toURL()
    }
    .toArray(::arrayOfNulls)
  if (parent is URLClassLoader) {
    parent.urLs.forEach {
      logger.info("--${it}")
    }
  }
  return URLClassLoader(urls, parent)
}

fun newRepositorySystem(): RepositorySystem {
  return MavenRepositorySystemUtils.newServiceLocator().apply {
    addService(RepositoryConnectorFactory::class.java, BasicRepositoryConnectorFactory::class.java)
    addService(TransporterFactory::class.java, HttpTransporterFactory::class.java)
  }.run {
    getService(RepositorySystem::class.java)
  }
}

fun newRepositorySystemSession(system: RepositorySystem): RepositorySystemSession {
  val muzzleRepo = file("${buildDir}/muzzleRepo")
  val localRepo = LocalRepository(muzzleRepo)
  return MavenRepositorySystemUtils.newSession().apply {
    setLocalRepositoryManager(system.newLocalRepositoryManager(this, localRepo))
  }
}

fun addMuzzleTask(muzzleDirective: MuzzleDirective, versionArtifact: Artifact?, runAfter: TaskProvider<Task>)
  : TaskProvider<Task> {
  val taskName = if (versionArtifact == null) {
    "muzzle-Assert${muzzleDirective}"
  } else {
    StringBuilder("muzzle-Assert").apply {
      if (muzzleDirective.assertPass.get()) {
        append("Pass")
      } else {
        append("Fail")
      }
      append('-')
        .append(versionArtifact.groupId)
        .append('-')
        .append(versionArtifact.artifactId)
        .append('-')
        .append(versionArtifact.version)
      if (!muzzleDirective.name.get().isEmpty()) {
        append(muzzleDirective.nameSlug)
      }
    }.run { toString() }
  }
  val config = configurations.create(taskName)
  if (versionArtifact != null) {
    val dep = (dependencies.create(versionArtifact.run { "${groupId}:${artifactId}:${version}" }) as ModuleDependency).apply {
      isTransitive = true
      exclude("com.sun.jdmk", "jmxtools")
      exclude("com.sun.jmx", "jmxri")
      for (excluded in muzzleDirective.excludedDependencies.get()) {
        val (group, module) = excluded.split(':')
        exclude(group, module)
      }
    }
    config.dependencies.add(dep)

    for (additionalDependency in muzzleDirective.additionalDependencies.get()) {
      val additional = if (additionalDependency is String && additionalDependency.count { it == ':' } < 2) {
        // Dependency definition without version, use the artifact's version.
        "${additionalDependency}:${versionArtifact.version}"
      } else {
        additionalDependency
      }
      val additionalDep = (dependencies.create(additional) as ModuleDependency).apply {
        isTransitive = true
      }
      config.dependencies.add(additionalDep)
    }
  }

  val muzzleTask = tasks.register(taskName) {
    dependsOn(configurations.named("runtimeClasspath"))
    dependsOn(shadowModule)
    doLast {
      val instrumentationCL = createInstrumentationClassloader()
      val userCL = createClassLoaderForTask(config)
      withContextClassLoader(instrumentationCL) {
        MuzzleGradlePluginUtil.assertInstrumentationMuzzled(instrumentationCL, userCL, muzzleDirective.assertPass.get())
      }

      for (thread in Thread.getAllStackTraces().keys) {
        if (thread.contextClassLoader === instrumentationCL
          || thread.contextClassLoader === userCL) {
          throw GradleException(
            "Task ${taskName} has spawned a thread: ${thread} with classloader ${thread.contextClassLoader}. " +
              "This will prevent GC of dynamic muzzle classes. Aborting muzzle run.")
        }
      }
    }
  }

  runAfter.configure { finalizedBy(muzzleTask) }
  return muzzleTask
}

fun createClassLoaderForTask(muzzleTaskConfiguration: Configuration): ClassLoader {
  logger.info("Creating user classloader for muzzle check")
  val muzzleBootstrapShadowJar = shadowMuzzleBootstrap.get().archiveFile.get()
  return classpathLoader(muzzleTaskConfiguration + files(muzzleBootstrapShadowJar), ClassLoader.getPlatformClassLoader())
}

fun inverseOf(muzzleDirective: MuzzleDirective, system: RepositorySystem, session: RepositorySystemSession): Set<MuzzleDirective> {
  val inverseDirectives = mutableSetOf<MuzzleDirective>()

  val allVersionsArtifact = DefaultArtifact(
    muzzleDirective.group.get(),
    muzzleDirective.module.get(),
    muzzleDirective.classifier.get(),
    "jar",
    "[,)")
  val directiveArtifact = DefaultArtifact(
    muzzleDirective.group.get(),
    muzzleDirective.module.get(),
    muzzleDirective.classifier.get(),
    "jar",
    muzzleDirective.versions.get())

  val repos = projectRepositories
  val allRangeRequest = VersionRangeRequest().apply {
    repositories = repos
    artifact = allVersionsArtifact
  }
  val allRangeResult = system.resolveVersionRange(session, allRangeRequest)

  val rangeRequest = VersionRangeRequest().apply {
    repositories = repos
    artifact = directiveArtifact
  }
  val rangeResult = system.resolveVersionRange(session, rangeRequest)

  allRangeResult.versions.removeAll(rangeResult.versions)

  for (version in filterVersions(allRangeResult, muzzleDirective.normalizedSkipVersions)) {
    val inverseDirective = objects.newInstance(MuzzleDirective::class).apply {
      group.set(muzzleDirective.group)
      module.set(muzzleDirective.module)
      classifier.set(muzzleDirective.classifier)
      versions.set(version)
      assertPass.set(!muzzleDirective.assertPass.get())
      excludedDependencies.set(muzzleDirective.excludedDependencies)
    }
    inverseDirectives.add(inverseDirective)
  }

  return inverseDirectives
}

fun filterVersions(range: VersionRangeResult, skipVersions: Set<String>) = sequence {
  val predicate = AcceptableVersions(skipVersions)
  if (predicate.test(range.lowestVersion)) {
    yield(range.lowestVersion.toString())
  }
  if (predicate.test(range.highestVersion)) {
    yield(range.highestVersion.toString())
  }

  val copy: List<Version> = range.versions.shuffled()
  for (version in copy) {
    if (predicate.test(version)) {
      yield(version.toString())
    }
  }
}.distinct().take(RANGE_COUNT_LIMIT)

fun muzzleDirectiveToArtifacts(muzzleDirective: MuzzleDirective, system: RepositorySystem, session: RepositorySystemSession) = sequence<Artifact> {
  val directiveArtifact: Artifact = DefaultArtifact(
    muzzleDirective.group.get(),
    muzzleDirective.module.get(),
    muzzleDirective.classifier.get(),
    "jar",
    muzzleDirective.versions.get())

  val rangeRequest = VersionRangeRequest().apply {
    repositories = projectRepositories
    artifact = directiveArtifact
  }
  val rangeResult = system.resolveVersionRange(session, rangeRequest)

  val allVersionArtifacts = filterVersions(rangeResult, muzzleDirective.normalizedSkipVersions)
    .map {
      DefaultArtifact(
        muzzleDirective.group.get(),
        muzzleDirective.module.get(),
        muzzleDirective.classifier.get(),
        "jar",
        it)
    }

  allVersionArtifacts.ifEmpty {
    throw GradleException("No muzzle artifacts found for $muzzleDirective")
  }

  yieldAll(allVersionArtifacts)
}

fun withContextClassLoader(classLoader: ClassLoader, action: () -> Unit) {
  val currentClassLoader = Thread.currentThread().contextClassLoader
  Thread.currentThread().contextClassLoader = classLoader
  try {
    action()
  } finally {
    Thread.currentThread().contextClassLoader = currentClassLoader
  }
}
