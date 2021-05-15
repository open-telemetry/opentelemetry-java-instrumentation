/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.instrumentation.gradle.muzzle.MuzzleDirective
import io.opentelemetry.instrumentation.gradle.muzzle.MuzzleExtension
import java.lang.reflect.Method
import java.security.SecureClassLoader
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Predicate
import java.util.regex.Pattern
import org.apache.maven.repository.internal.MavenRepositorySystemUtils
import org.eclipse.aether.DefaultRepositorySystemSession
import org.eclipse.aether.RepositorySystem
import org.eclipse.aether.RepositorySystemSession
import org.eclipse.aether.artifact.Artifact
import org.eclipse.aether.artifact.DefaultArtifact
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory
import org.eclipse.aether.impl.DefaultServiceLocator
import org.eclipse.aether.repository.LocalRepository
import org.eclipse.aether.repository.RemoteRepository
import org.eclipse.aether.resolution.VersionRangeRequest
import org.eclipse.aether.resolution.VersionRangeResult
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory
import org.eclipse.aether.spi.connector.transport.TransporterFactory
import org.eclipse.aether.transport.http.HttpTransporterFactory
import org.eclipse.aether.version.Version
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider

/**
 * muzzle task plugin which runs muzzle validation against a range of dependencies.
 */
class MuzzlePlugin implements Plugin<Project> {
  /**
   * Select a random set of versions to test
   */
  private static final int RANGE_COUNT_LIMIT = 10
  private static final AtomicReference<ClassLoader> TOOLING_LOADER = new AtomicReference<>()

  @Override
  void apply(Project project) {
    project.extensions.create("muzzle", MuzzleExtension, project.objects)

    // compileMuzzle compiles all projects required to run muzzle validation.
    // Not adding group and description to keep this task from showing in `gradle tasks`.
    def compileMuzzle = project.tasks.register('compileMuzzle') {
      dependsOn(':javaagent-bootstrap:classes')
      dependsOn(':javaagent-tooling:classes')
      dependsOn(':javaagent-extension-api:classes')
      dependsOn(project.tasks.classes)
    }

    def muzzle = project.tasks.register('muzzle') {
      group = 'Muzzle'
      description = "Run instrumentation muzzle on compile time dependencies"
      dependsOn(compileMuzzle)
    }

    project.tasks.register('printMuzzleReferences') {
      group = 'Muzzle'
      description = "Print references created by instrumentation muzzle"
      dependsOn(compileMuzzle)
      doLast {
        ClassLoader instrumentationCL = createInstrumentationClassloader(project)
        Method assertionMethod = instrumentationCL.loadClass('io.opentelemetry.javaagent.tooling.muzzle.matcher.MuzzleGradlePluginUtil')
          .getMethod('printMuzzleReferences', ClassLoader.class)
        assertionMethod.invoke(null, instrumentationCL)
      }
    }

    def hasRelevantTask = project.gradle.startParameter.taskNames.any { taskName ->
      // removing leading ':' if present
      taskName = taskName.replaceFirst('^:', '')
      String muzzleTaskPath = project.path.replaceFirst('^:', '')
      return 'muzzle' == taskName || "${muzzleTaskPath}:muzzle" == taskName
    }
    if (!hasRelevantTask) {
      // Adding muzzle dependencies has a large config overhead. Stop unless muzzle is explicitly run.
      return
    }

    RepositorySystem system = newRepositorySystem()
    RepositorySystemSession session = newRepositorySystemSession(system)

    project.afterEvaluate {
      // use runAfter to set up task finalizers in version order
      TaskProvider runAfter = muzzle

      for (MuzzleDirective muzzleDirective : project.muzzle.directives.get()) {
        project.getLogger().info("configured $muzzleDirective")

        if (muzzleDirective.coreJdk.get()) {
          runAfter = addMuzzleTask(muzzleDirective, null, project, runAfter)
        } else {
          muzzleDirectiveToArtifacts(project, muzzleDirective, system, session).collect() { Artifact singleVersion ->
            runAfter = addMuzzleTask(muzzleDirective, singleVersion, project, runAfter)
          }
          if (muzzleDirective.assertInverse.get()) {
            inverseOf(project, muzzleDirective, system, session).collect() { MuzzleDirective inverseDirective ->
              muzzleDirectiveToArtifacts(project, inverseDirective, system, session).collect() { Artifact singleVersion ->
                runAfter = addMuzzleTask(inverseDirective, singleVersion, project, runAfter)
              }
            }
          }
        }
      }
    }
  }

  private static ClassLoader getOrCreateToolingLoader(Project project) {
    synchronized (TOOLING_LOADER) {
      ClassLoader toolingLoader = TOOLING_LOADER.get()
      if (toolingLoader == null) {
        Set<URL> urls = new HashSet<>()
        project.getLogger().info('creating classpath for auto-tooling')
        for (File f : project.configurations.toolingRuntime.getFiles()) {
          project.getLogger().info('--' + f)
          urls.add(f.toURI().toURL())
        }
        def loader = new URLClassLoader(urls.toArray(new URL[0]), ClassLoader.platformClassLoader)
        assert TOOLING_LOADER.compareAndSet(null, loader)
        return TOOLING_LOADER.get()
      } else {
        return toolingLoader
      }
    }
  }

  /**
   * Create a classloader with core agent classes and project instrumentation on the classpath.
   */
  private static ClassLoader createInstrumentationClassloader(Project project) {
    project.getLogger().info("Creating instrumentation classpath for: " + project.getName())
    Set<URL> urls = new HashSet<>()
    for (File f : project.sourceSets.main.runtimeClasspath.getFiles()) {
      project.getLogger().info('--' + f)
      urls.add(f.toURI().toURL())
    }

    return new URLClassLoader(urls.toArray(new URL[0]), getOrCreateToolingLoader(project))
  }

  /**
   * Create a classloader with all compile-time dependencies on the classpath
   */
  private static ClassLoader createCompileDepsClassLoader(Project project) {
    List<URL> userUrls = new ArrayList<>()
    project.getLogger().info("Creating compile-time classpath for: " + project.getName())
    for (File f : project.configurations.compileClasspath.getFiles()) {
      project.getLogger().info('--' + f)
      userUrls.add(f.toURI().toURL())
    }
    for (File f : project.configurations.bootstrapRuntime.getFiles()) {
      project.getLogger().info('--' + f)
      userUrls.add(f.toURI().toURL())
    }
    return new URLClassLoader(userUrls.toArray(new URL[0]), ClassLoader.platformClassLoader)
  }

  /**
   * Create a classloader with dependencies for a single muzzle task.
   */
  private static ClassLoader createClassLoaderForTask(Project project, String muzzleTaskName) {
    List<URL> userUrls = new ArrayList<>()

    project.getLogger().info("Creating task classpath")
    project.configurations.getByName(muzzleTaskName).resolvedConfiguration.files.each { File jarFile ->
      project.getLogger().info("-- Added to instrumentation classpath: $jarFile")
      userUrls.add(jarFile.toURI().toURL())
    }

    for (File f : project.configurations.bootstrapRuntime.getFiles()) {
      project.getLogger().info("-- Added to instrumentation bootstrap classpath: $f")
      userUrls.add(f.toURI().toURL())
    }
    return new URLClassLoader(userUrls.toArray(new URL[0]), ClassLoader.platformClassLoader)
  }

  /**
   * Convert a muzzle directive to a list of artifacts
   */
  private static Set<Artifact> muzzleDirectiveToArtifacts(Project instrumentationProject, MuzzleDirective muzzleDirective, RepositorySystem system, RepositorySystemSession session) {
    Artifact directiveArtifact = new DefaultArtifact(muzzleDirective.group.get(), muzzleDirective.module.get(), "jar", muzzleDirective.versions.get())

    VersionRangeRequest rangeRequest = new VersionRangeRequest()
    rangeRequest.setRepositories(getProjectRepositories(instrumentationProject))
    rangeRequest.setArtifact(directiveArtifact)
    VersionRangeResult rangeResult = system.resolveVersionRange(session, rangeRequest)

    Set<Artifact> allVersionArtifacts = filterVersions(rangeResult, muzzleDirective.normalizedSkipVersions).collect { version ->
      new DefaultArtifact(muzzleDirective.group.get(), muzzleDirective.module.get(), "jar", version)
    }.toSet()

    if (allVersionArtifacts.isEmpty()) {
      throw new GradleException("No muzzle artifacts found for $muzzleDirective")
    }

    return allVersionArtifacts
  }

  private static List<RemoteRepository> getProjectRepositories(Project project) {
    project.repositories.collect {
      new RemoteRepository.Builder(it.name, "default", it.url.toString()).build()
    }
  }

  /**
   * Create a list of muzzle directives which assert the opposite of the given MuzzleDirective.
   */
  private static Set<MuzzleDirective> inverseOf(Project instrumentationProject, MuzzleDirective muzzleDirective, RepositorySystem system, RepositorySystemSession session) {
    Set<MuzzleDirective> inverseDirectives = new HashSet<>()

    Artifact allVersionsArtifact = new DefaultArtifact(muzzleDirective.group.get(), muzzleDirective.module.get(), "jar", "[,)")
    Artifact directiveArtifact = new DefaultArtifact(muzzleDirective.group.get(), muzzleDirective.module.get(), "jar", muzzleDirective.versions.get())

    List<RemoteRepository> repos = getProjectRepositories(instrumentationProject)
    VersionRangeRequest allRangeRequest = new VersionRangeRequest()
    allRangeRequest.setRepositories(repos)
    allRangeRequest.setArtifact(allVersionsArtifact)
    VersionRangeResult allRangeResult = system.resolveVersionRange(session, allRangeRequest)

    VersionRangeRequest rangeRequest = new VersionRangeRequest()
    rangeRequest.setRepositories(repos)
    rangeRequest.setArtifact(directiveArtifact)
    VersionRangeResult rangeResult = system.resolveVersionRange(session, rangeRequest)

    allRangeResult.getVersions().removeAll(rangeResult.getVersions())

    filterVersions(allRangeResult, muzzleDirective.normalizedSkipVersions).each { version ->
      MuzzleDirective inverseDirective = instrumentationProject.objects.newInstance(MuzzleDirective)
      inverseDirective.group = muzzleDirective.group
      inverseDirective.module = muzzleDirective.module
      inverseDirective.versions = version
      inverseDirective.assertPass = !muzzleDirective.assertPass
      inverseDirectives.add(inverseDirective)
    }

    return inverseDirectives
  }

  private static Set<String> filterVersions(VersionRangeResult range, Set<String> skipVersions) {
    Set<String> result = new HashSet<>()

    def predicate = new AcceptableVersions(range, skipVersions)
    if (predicate.test(range.lowestVersion)) {
      result.add(range.lowestVersion.toString())
    }
    if (predicate.test(range.highestVersion)) {
      result.add(range.highestVersion.toString())
    }

    List<Version> copy = new ArrayList<>(range.versions)
    Collections.shuffle(copy)
    while (result.size() < RANGE_COUNT_LIMIT && !copy.isEmpty()) {
      Version version = copy.pop()
      if (predicate.test(version)) {
        result.add(version.toString())
      }
    }

    return result
  }

  static class AcceptableVersions implements Predicate<Version> {
    private static final Pattern GIT_SHA_PATTERN = Pattern.compile('^.*-[0-9a-f]{7,}$')

    private final VersionRangeResult range
    private final Collection<String> skipVersions

    AcceptableVersions(VersionRangeResult range, Collection<String> skipVersions) {
      this.range = range
      this.skipVersions = skipVersions
    }

    @Override
    boolean test(Version version) {
      if (version == null) {
        return false
      }
      def versionString = version.toString().toLowerCase()
      if (skipVersions.contains(versionString)) {
        return false
      }

      def draftVersion = versionString.contains("rc") ||
        versionString.contains(".cr") ||
        versionString.contains("alpha") ||
        versionString.contains("beta") ||
        versionString.contains("-b") ||
        versionString.contains(".m") ||
        versionString.contains("-m") ||
        versionString.contains("-dev") ||
        versionString.contains("-ea") ||
        versionString.contains("-atlassian-") ||
        versionString.contains("public_draft") ||
        versionString.contains("snapshot") ||
        versionString.matches(GIT_SHA_PATTERN)

      return !draftVersion
    }
  }

  /**
   * Configure a muzzle task to pass or fail a given version.
   *
   * @param assertPass If true, assert that muzzle validation passes
   * @param versionArtifact version to assert against.
   * @param instrumentationProject instrumentation being asserted against.
   * @param runAfter Task which runs before the new muzzle task.
   *
   * @return The created muzzle task.
   */
  private static TaskProvider addMuzzleTask(MuzzleDirective muzzleDirective, Artifact versionArtifact, Project instrumentationProject, TaskProvider runAfter) {
    def taskName
    if (muzzleDirective.coreJdk.get()) {
      taskName = "muzzle-Assert$muzzleDirective"
    } else {
      taskName = "muzzle-Assert${muzzleDirective.assertPass ? "Pass" : "Fail"}-$versionArtifact.groupId-$versionArtifact.artifactId-$versionArtifact.version${!muzzleDirective.name.get().isEmpty() ? "-${muzzleDirective.getNameSlug()}" : ""}"
    }
    def config = instrumentationProject.configurations.create(taskName)

    if (!muzzleDirective.coreJdk.get()) {
      def dep = instrumentationProject.dependencies.create("$versionArtifact.groupId:$versionArtifact.artifactId:$versionArtifact.version") {
        transitive = true
      }
      // The following optional transitive dependencies are brought in by some legacy module such as log4j 1.x but are no
      // longer bundled with the JVM and have to be excluded for the muzzle tests to be able to run.
      dep.exclude group: 'com.sun.jdmk', module: 'jmxtools'
      dep.exclude group: 'com.sun.jmx', module: 'jmxri'

      config.dependencies.add(dep)
    }
    for (String additionalDependency : muzzleDirective.additionalDependencies.get()) {
      if (additionalDependency.count(":") < 2) {
        // Dependency definition without version, use the artifact's version.
        additionalDependency += ":${versionArtifact.version}"
      }
      config.dependencies.add(instrumentationProject.dependencies.create(additionalDependency) {
        transitive = true
      })
    }

    def muzzleTask = instrumentationProject.tasks.register(taskName) {
      dependsOn(instrumentationProject.configurations.named("runtimeClasspath"))
      doLast {
        ClassLoader instrumentationCL = createInstrumentationClassloader(instrumentationProject)
        def ccl = Thread.currentThread().contextClassLoader
        def bogusLoader = new SecureClassLoader() {
          @Override
          String toString() {
            return "bogus"
          }

        }
        Thread.currentThread().contextClassLoader = bogusLoader
        ClassLoader userCL = createClassLoaderForTask(instrumentationProject, taskName)
        try {
          // find all instrumenters, get muzzle, and assert
          Method assertionMethod = instrumentationCL.loadClass('io.opentelemetry.javaagent.tooling.muzzle.matcher.MuzzleGradlePluginUtil')
            .getMethod('assertInstrumentationMuzzled', ClassLoader.class, ClassLoader.class, boolean.class)
          assertionMethod.invoke(null, instrumentationCL, userCL, muzzleDirective.assertPass.get())
        } finally {
          Thread.currentThread().contextClassLoader = ccl
        }

        for (Thread thread : Thread.getThreads()) {
          if (thread.contextClassLoader == bogusLoader || thread.contextClassLoader == instrumentationCL || thread.contextClassLoader == userCL) {
            throw new GradleException("Task $taskName has spawned a thread: $thread with classloader $thread.contextClassLoader. This will prevent GC of dynamic muzzle classes. Aborting muzzle run.")
          }
        }
      }
    }
    runAfter.configure {
      finalizedBy(muzzleTask)
    }
    return muzzleTask
  }

  /**
   * Create muzzle's repository system
   */
  private static RepositorySystem newRepositorySystem() {
    DefaultServiceLocator locator = MavenRepositorySystemUtils.newServiceLocator()
    locator.addService(RepositoryConnectorFactory.class, BasicRepositoryConnectorFactory.class)
    locator.addService(TransporterFactory.class, HttpTransporterFactory.class)

    return locator.getService(RepositorySystem.class)
  }


  /**
   * Create muzzle's repository system session
   */
  private static RepositorySystemSession newRepositorySystemSession(RepositorySystem system) {
    DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession()

    def tempDir = File.createTempDir()
    tempDir.deleteOnExit()
    LocalRepository localRepo = new LocalRepository(tempDir)
    session.setLocalRepositoryManager(system.newLocalRepositoryManager(session, localRepo))

    return session
  }
}
