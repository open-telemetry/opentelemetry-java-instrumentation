/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

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
import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.model.ObjectFactory

/**
 * muzzle task plugin which runs muzzle validation against a range of dependencies.
 */
class MuzzlePlugin implements Plugin<Project> {
  /**
   * Select a random set of versions to test
   */
  private static final int RANGE_COUNT_LIMIT = 10
  /**
   * Remote repositories used to query version ranges and fetch dependencies
   */
  private static final List<RemoteRepository> MUZZLE_REPOS
  private static final AtomicReference<ClassLoader> TOOLING_LOADER = new AtomicReference<>()

  static {
    RemoteRepository central = new RemoteRepository.Builder("central", "default", "https://repo1.maven.org/maven2/").build()
    RemoteRepository sonatype = new RemoteRepository.Builder("sonatype", "default", "https://oss.sonatype.org/content/repositories/releases/").build()
    RemoteRepository jcenter = new RemoteRepository.Builder("jcenter", "default", "https://jcenter.bintray.com/").build()
    RemoteRepository spring = new RemoteRepository.Builder("spring", "default", "https://repo.spring.io/libs-release/").build()
    RemoteRepository jboss = new RemoteRepository.Builder("jboss", "default", "https://repository.jboss.org/nexus/content/repositories/releases/").build()
    RemoteRepository typesafe = new RemoteRepository.Builder("typesafe", "default", "https://repo.typesafe.com/typesafe/releases").build()
    RemoteRepository akka = new RemoteRepository.Builder("akka", "default", "https://dl.bintray.com/akka/maven/").build()
    RemoteRepository atlassian = new RemoteRepository.Builder("atlassian", "default", "https://maven.atlassian.com/content/repositories/atlassian-public/").build()
//    MUZZLE_REPOS = Arrays.asList(central, sonatype, jcenter, spring, jboss, typesafe, akka, atlassian)
    MUZZLE_REPOS = Arrays.asList(central, jcenter, typesafe)
  }

  @Override
  void apply(Project project) {
    def bootstrapProject = project.rootProject.getChildProjects().get('javaagent-bootstrap')
    def toolingProject = project.rootProject.getChildProjects().get('javaagent-tooling')
    project.extensions.create("muzzle", MuzzleExtension, project.objects)

    // compileMuzzle compiles all projects required to run muzzle validation.
    // Not adding group and description to keep this task from showing in `gradle tasks`.
    def compileMuzzle = project.task('compileMuzzle')
    def muzzle = project.task('muzzle') {
      group = 'Muzzle'
      description = "Run instrumentation muzzle on compile time dependencies"
      doLast {
        if (!project.muzzle.directives.any { it.assertPass }) {
          project.getLogger().info('No muzzle pass directives configured. Asserting pass against instrumentation compile-time dependencies')
          ClassLoader userCL = createCompileDepsClassLoader(project)
          ClassLoader instrumentationCL = createInstrumentationClassloader(project)
          Method assertionMethod = instrumentationCL.loadClass('io.opentelemetry.javaagent.tooling.muzzle.matcher.MuzzleGradlePluginUtil')
            .getMethod('assertInstrumentationMuzzled', ClassLoader.class, ClassLoader.class, boolean.class)
          assertionMethod.invoke(null, instrumentationCL, userCL, true)
        }
        println "Muzzle executing for $project"
      }
    }
    def printReferences = project.task('printMuzzleReferences') {
      group = 'Muzzle'
      description = "Print references created by instrumentation muzzle"
      doLast {
        ClassLoader instrumentationCL = createInstrumentationClassloader(project)
        Method assertionMethod = instrumentationCL.loadClass('io.opentelemetry.javaagent.tooling.muzzle.matcher.MuzzleGradlePluginUtil')
          .getMethod('printMuzzleReferences', ClassLoader.class)
        assertionMethod.invoke(null, instrumentationCL)
      }
    }
    project.tasks.compileMuzzle.dependsOn(bootstrapProject.tasks.compileJava)
    project.tasks.compileMuzzle.dependsOn(toolingProject.tasks.compileJava)
    project.afterEvaluate {
      project.tasks.compileMuzzle.dependsOn(project.tasks.compileJava)
      if (project.tasks.getNames().contains('compileScala')) {
        project.tasks.compileMuzzle.dependsOn(project.tasks.compileScala)
      }
    }
    project.tasks.muzzle.dependsOn(project.tasks.compileMuzzle)
    project.tasks.printMuzzleReferences.dependsOn(project.tasks.compileMuzzle)

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
      Task runAfter = project.tasks.muzzle

      for (MuzzleDirective muzzleDirective : project.muzzle.directives) {
        project.getLogger().info("configured $muzzleDirective")

        if (muzzleDirective.coreJdk) {
          runAfter = addMuzzleTask(muzzleDirective, null, project, runAfter)
        } else {
          muzzleDirectiveToArtifacts(muzzleDirective, system, session).collect() { Artifact singleVersion ->
            runAfter = addMuzzleTask(muzzleDirective, singleVersion, project, runAfter)
          }
          if (muzzleDirective.assertInverse) {
            inverseOf(muzzleDirective, system, session).collect() { MuzzleDirective inverseDirective ->
              muzzleDirectiveToArtifacts(inverseDirective, system, session).collect() { Artifact singleVersion ->
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
  private static Set<Artifact> muzzleDirectiveToArtifacts(MuzzleDirective muzzleDirective, RepositorySystem system, RepositorySystemSession session) {
    Artifact directiveArtifact = new DefaultArtifact(muzzleDirective.group, muzzleDirective.module, "jar", muzzleDirective.versions)

    VersionRangeRequest rangeRequest = new VersionRangeRequest()
    rangeRequest.setRepositories(MUZZLE_REPOS)
    rangeRequest.setArtifact(directiveArtifact)
    VersionRangeResult rangeResult = system.resolveVersionRange(session, rangeRequest)

    Set<Artifact> allVersionArtifacts = filterVersions(rangeResult, muzzleDirective.skipVersions).collect { version ->
      new DefaultArtifact(muzzleDirective.group, muzzleDirective.module, "jar", version)
    }.toSet()

    if (allVersionArtifacts.isEmpty()) {
      throw new GradleException("No muzzle artifacts found for $muzzleDirective.group:$muzzleDirective.module $muzzleDirective.versions")
    }

    return allVersionArtifacts
  }

  /**
   * Create a list of muzzle directives which assert the opposite of the given MuzzleDirective.
   */
  private static Set<MuzzleDirective> inverseOf(MuzzleDirective muzzleDirective, RepositorySystem system, RepositorySystemSession session) {
    Set<MuzzleDirective> inverseDirectives = new HashSet<>()

    Artifact allVersionsArtifact = new DefaultArtifact(muzzleDirective.group, muzzleDirective.module, "jar", "[,)")
    Artifact directiveArtifact = new DefaultArtifact(muzzleDirective.group, muzzleDirective.module, "jar", muzzleDirective.versions)

    VersionRangeRequest allRangeRequest = new VersionRangeRequest()
    allRangeRequest.setRepositories(MUZZLE_REPOS)
    allRangeRequest.setArtifact(allVersionsArtifact)
    VersionRangeResult allRangeResult = system.resolveVersionRange(session, allRangeRequest)

    VersionRangeRequest rangeRequest = new VersionRangeRequest()
    rangeRequest.setRepositories(MUZZLE_REPOS)
    rangeRequest.setArtifact(directiveArtifact)
    VersionRangeResult rangeResult = system.resolveVersionRange(session, rangeRequest)

    allRangeResult.getVersions().removeAll(rangeResult.getVersions())

    filterVersions(allRangeResult, muzzleDirective.skipVersions).each { version ->
      MuzzleDirective inverseDirective = new MuzzleDirective()
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
  private static Task addMuzzleTask(MuzzleDirective muzzleDirective, Artifact versionArtifact, Project instrumentationProject, Task runAfter) {
    def taskName
    if (muzzleDirective.coreJdk) {
      taskName = "muzzle-Assert$muzzleDirective"
    } else {
      taskName = "muzzle-Assert${muzzleDirective.assertPass ? "Pass" : "Fail"}-$versionArtifact.groupId-$versionArtifact.artifactId-$versionArtifact.version${muzzleDirective.name ? "-${muzzleDirective.getNameSlug()}" : ""}"
    }
    def config = instrumentationProject.configurations.create(taskName)

    if (!muzzleDirective.coreJdk) {
      def dep = instrumentationProject.dependencies.create("$versionArtifact.groupId:$versionArtifact.artifactId:$versionArtifact.version") {
        transitive = true
      }
      // The following optional transitive dependencies are brought in by some legacy module such as log4j 1.x but are no
      // longer bundled with the JVM and have to be excluded for the muzzle tests to be able to run.
      dep.exclude group: 'com.sun.jdmk', module: 'jmxtools'
      dep.exclude group: 'com.sun.jmx', module: 'jmxri'

      config.dependencies.add(dep)
    }
    for (String additionalDependency : muzzleDirective.additionalDependencies) {
      config.dependencies.add(instrumentationProject.dependencies.create(additionalDependency) {
        transitive = true
      })
    }

    def muzzleTask = instrumentationProject.task(taskName) {
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
          assertionMethod.invoke(null, instrumentationCL, userCL, muzzleDirective.assertPass)
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
    runAfter.finalizedBy(muzzleTask)
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

// plugin extension classes

/**
 * A pass or fail directive for a single dependency.
 */
class MuzzleDirective {

  /**
   * Name is optional and is used to further define the scope of a directive. The motivation for this is that this
   * plugin creates a config for each of the dependencies under test with name '...-<group_id>-<artifact_id>-<version>'.
   * The problem is that if we want to test multiple times the same configuration under different conditions, e.g.
   * with different extra dependencies, the plugin would throw an error as it would try to create several times the
   * same config. This property can be used to differentiate those config names for different directives.
   */
  String name

  String group
  String module
  String versions
  Set<String> skipVersions = new HashSet<>()
  List<String> additionalDependencies = new ArrayList<>()
  boolean assertPass
  boolean assertInverse = false
  boolean coreJdk = false

  void coreJdk() {
    coreJdk = true
  }

  /**
   * Adds extra dependencies to the current muzzle test.
   *
   * @param compileString An extra dependency in the gradle canonical form: '<group_id>:<artifact_id>:<version_id>'.
   */
  void extraDependency(String compileString) {
    additionalDependencies.add(compileString)
  }

  /**
   * Slug of directive name.
   *
   * @return A slug of the name or an empty string if name is empty. E.g. 'My Directive' --> 'My-Directive'
   */
  String getNameSlug() {
    if (null == name) {
      return ""
    }

    return name.trim().replaceAll("[^a-zA-Z0-9]+", "-")
  }

  String toString() {
    if (coreJdk) {
      return "${assertPass ? 'Pass' : 'Fail'}-core-jdk"
    } else {
      return "${assertPass ? 'pass' : 'fail'} $group:$module:$versions"
    }
  }
}

/**
 * Muzzle extension containing all pass and fail directives.
 */
class MuzzleExtension {
  final List<MuzzleDirective> directives = new ArrayList<>()
  private final ObjectFactory objectFactory

  @javax.inject.Inject
  MuzzleExtension(final ObjectFactory objectFactory) {
    this.objectFactory = objectFactory
  }

  void pass(Action<? super MuzzleDirective> action) {
    MuzzleDirective pass = objectFactory.newInstance(MuzzleDirective)
    action.execute(pass)
    postConstruct(pass)
    pass.assertPass = true
    directives.add(pass)
  }

  void fail(Action<? super MuzzleDirective> action) {
    MuzzleDirective fail = objectFactory.newInstance(MuzzleDirective)
    action.execute(fail)
    postConstruct(fail)
    fail.assertPass = false
    directives.add(fail)
  }

  private postConstruct(MuzzleDirective directive) {
    // Make skipVersions case insensitive.
    directive.skipVersions = directive.skipVersions.collect {
      it.toLowerCase()
    }
  }
}
