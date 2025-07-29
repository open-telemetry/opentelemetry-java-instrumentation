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
  id("com.gradleup.shadow")
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

  // rewrite dependencies calling Logger.getLogger
  relocate("java.util.logging.Logger", "io.opentelemetry.javaagent.bootstrap.PatchLogger")

  if (project.findProperty("disableShadowRelocate") != "true") {
    // prevents conflict with library instrumentation, since these classes live in the bootstrap class loader
    relocate("io.opentelemetry.instrumentation", "io.opentelemetry.javaagent.shaded.instrumentation") {
      // Exclude resource providers since they live in the agent class loader
      exclude("io.opentelemetry.instrumentation.resources.*")
      exclude("io.opentelemetry.instrumentation.spring.resources.*")
    }

    // relocate(OpenTelemetry API) since these classes live in the bootstrap class loader
    relocate("io.opentelemetry.api", "io.opentelemetry.javaagent.shaded.io.opentelemetry.api")
    relocate("io.opentelemetry.semconv", "io.opentelemetry.javaagent.shaded.io.opentelemetry.semconv")
    relocate("io.opentelemetry.context", "io.opentelemetry.javaagent.shaded.io.opentelemetry.context")
    relocate("io.opentelemetry.common", "io.opentelemetry.javaagent.shaded.io.opentelemetry.common")
  }

  // relocate(the OpenTelemetry extensions that are used by instrumentation modules)
  // these extensions live in the AgentClassLoader, and are injected into the user's class loader
  // by the instrumentation modules that use them
  relocate("io.opentelemetry.contrib.awsxray", "io.opentelemetry.javaagent.shaded.io.opentelemetry.contrib.awsxray")
  relocate("io.opentelemetry.extension.kotlin", "io.opentelemetry.javaagent.shaded.io.opentelemetry.extension.kotlin")

  // this is for instrumentation of opentelemetry-api and opentelemetry-instrumentation-api
  relocate("application.io.opentelemetry", "io.opentelemetry")
  relocate("application.io.opentelemetry.instrumentation.api", "io.opentelemetry.instrumentation.api")

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
    MuzzleGradlePluginUtil.printMuzzleReferences(instrumentationCL)
  }
}

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

    // the project repositories need to be retrieved after evaluation, before that the list is just empty
    val projectRepositories = getProjectRepositories(project)

    for (muzzleDirective in muzzleConfig.directives.get()) {
      logger.info("configured $muzzleDirective")

      if (muzzleDirective.coreJdk.get()) {
        runAfter = addMuzzleTask(muzzleDirective, null, runAfter)
      } else {
        for (singleVersion in muzzleDirectiveToArtifacts(muzzleDirective, system, session, projectRepositories)) {
          runAfter = addMuzzleTask(muzzleDirective, singleVersion, runAfter)
        }
        if (muzzleDirective.assertInverse.get()) {
          for (inverseDirective in inverseOf(muzzleDirective, system, session, projectRepositories)) {
            for (singleVersion in muzzleDirectiveToArtifacts(inverseDirective, system, session, projectRepositories)) {
              runAfter = addMuzzleTask(inverseDirective, singleVersion, runAfter)
            }
          }
        }
      }
    }
  }
}

fun getProjectRepositories(project: Project): List<RemoteRepository> {
  val projectRepositories = project.repositories
    .filterIsInstance<MavenArtifactRepository>()
    .map {
      RemoteRepository.Builder(
        it.name,
        "default",
        it.url.toString())
        .build()
    }
  // dependencyResolutionManagement.repositories are not being added to project.repositories,
  // they need to be queries separately
  if (projectRepositories.isEmpty()) {
    // Manually add mavenCentral until https://github.com/gradle/gradle/issues/17295
    // Adding mavenLocal is much more complicated but hopefully isn't required for normal usage of
    // Muzzle.
    return listOf(RemoteRepository.Builder(
      "MavenCentral", "default", "https://repo.maven.apache.org/maven2/")
      .build())
  }
  return projectRepositories
}

fun createInstrumentationClassloader(): ClassLoader {
  logger.info("Creating instrumentation class loader for: $path")
  val muzzleShadowJar = shadowModule.get().archiveFile.get()
  val muzzleToolingShadowJar = shadowMuzzleTooling.get().archiveFile.get()
  return classpathLoader(files(muzzleShadowJar, muzzleToolingShadowJar), ClassLoader.getPlatformClassLoader())
}

fun classpathLoader(classpath: FileCollection, parent: ClassLoader): ClassLoader {
  logger.info("Adding to class loader:")
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
  val muzzleRepo = layout.buildDirectory.dir("muzzleRepo")
  val localRepo = LocalRepository(muzzleRepo.get().asFile)
  return MavenRepositorySystemUtils.newSession().apply {
    localRepositoryManager = system.newLocalRepositoryManager(this, localRepo)
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
      MuzzleGradlePluginUtil.assertInstrumentationMuzzled(instrumentationCL, userCL,
        muzzleDirective.excludedInstrumentationNames.get(), muzzleDirective.assertPass.get())
    }
  }

  runAfter.configure { finalizedBy(muzzleTask) }
  return muzzleTask
}

fun createClassLoaderForTask(muzzleTaskConfiguration: Configuration): ClassLoader {
  logger.info("Creating user class loader for muzzle check")
  val muzzleBootstrapShadowJar = shadowMuzzleBootstrap.get().archiveFile.get()
  return classpathLoader(muzzleTaskConfiguration + files(muzzleBootstrapShadowJar), ClassLoader.getPlatformClassLoader())
}

fun inverseOf(muzzleDirective: MuzzleDirective, system: RepositorySystem, session: RepositorySystemSession, repos: List<RemoteRepository>): Set<MuzzleDirective> {
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
      name.set(muzzleDirective.name)
      group.set(muzzleDirective.group)
      module.set(muzzleDirective.module)
      classifier.set(muzzleDirective.classifier)
      versions.set(version)
      assertPass.set(!muzzleDirective.assertPass.get())
      additionalDependencies.set(muzzleDirective.additionalDependencies)
      excludedDependencies.set(muzzleDirective.excludedDependencies)
      excludedInstrumentationNames.set(muzzleDirective.excludedInstrumentationNames)
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

fun muzzleDirectiveToArtifacts(muzzleDirective: MuzzleDirective, system: RepositorySystem, session: RepositorySystemSession, repos: List<RemoteRepository>) = sequence<Artifact> {
  val directiveArtifact: Artifact = DefaultArtifact(
    muzzleDirective.group.get(),
    muzzleDirective.module.get(),
    muzzleDirective.classifier.get(),
    "jar",
    muzzleDirective.versions.get())

  val rangeRequest = VersionRangeRequest().apply {
    repositories = repos
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
