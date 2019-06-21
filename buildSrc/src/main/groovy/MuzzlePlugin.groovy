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

import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicReference

/**
 * muzzle task plugin which runs muzzle validation against a range of dependencies.
 */
class MuzzlePlugin implements Plugin<Project> {
  /**
   * Remote repositories used to query version ranges and fetch dependencies
   */
  private static final List<RemoteRepository> MUZZLE_REPOS
  private static final AtomicReference<ClassLoader> TOOLING_LOADER = new AtomicReference<>()
  static {
    RemoteRepository central = new RemoteRepository.Builder("central", "default", "http://central.maven.org/maven2/").build()
    MUZZLE_REPOS = new ArrayList<RemoteRepository>(Arrays.asList(central))
  }

  @Override
  void apply(Project project) {
    def bootstrapProject = project.rootProject.getChildProjects().get('dd-java-agent').getChildProjects().get('agent-bootstrap')
    def toolingProject = project.rootProject.getChildProjects().get('dd-java-agent').getChildProjects().get('agent-tooling')
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
          final ClassLoader userCL = createCompileDepsClassLoader(project, bootstrapProject)
          final ClassLoader instrumentationCL = createInstrumentationClassloader(project, toolingProject)
          Method assertionMethod = instrumentationCL.loadClass('datadog.trace.agent.tooling.muzzle.MuzzleVersionScanPlugin')
            .getMethod('assertInstrumentationMuzzled', ClassLoader.class, ClassLoader.class, boolean.class)
          assertionMethod.invoke(null, instrumentationCL, userCL, true)
        }
        println "Muzzle executing for $project"
      }
    }
    def printReferences = project.task('printReferences') {
      group = 'Muzzle'
      description = "Print references created by instrumentation muzzle"
      doLast {
        final ClassLoader instrumentationCL = createInstrumentationClassloader(project, toolingProject)
        Method assertionMethod = instrumentationCL.loadClass('datadog.trace.agent.tooling.muzzle.MuzzleVersionScanPlugin')
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
    project.tasks.printReferences.dependsOn(project.tasks.compileMuzzle)

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

    if (!project.rootProject.tasks.getNames().contains('muzzleCleanup')) {
      def muzzleCleanup = project.rootProject.task('muzzleCleanup') {
        group = 'Muzzle'
        doLast {
          project.rootProject.getLogger().info("Cleaning up global tooling loader")
          TOOLING_LOADER.set(null)
        }
      }
      muzzleCleanup.outputs.upToDateWhen { false }
    }
    project.tasks.muzzle.finalizedBy(project.rootProject.tasks.muzzleCleanup)

    final RepositorySystem system = newRepositorySystem()
    final RepositorySystemSession session = newRepositorySystemSession(system)

    project.afterEvaluate {
      // use runAfter to set up task finalizers in version order
      Task runAfter = project.tasks.muzzle

      for (MuzzleDirective muzzleDirective : project.muzzle.directives) {
        project.getLogger().info("configured ${muzzleDirective.assertPass ? 'pass' : 'fail'} directive: ${muzzleDirective.group}:${muzzleDirective.module}:${muzzleDirective.versions}")

        muzzleDirectiveToArtifacts(muzzleDirective, system, session).collect() { Artifact singleVersion ->
          runAfter = addMuzzleTask(muzzleDirective, singleVersion, project, runAfter, bootstrapProject, toolingProject)
        }
        if (muzzleDirective.assertInverse) {
          inverseOf(muzzleDirective, system, session).collect() { MuzzleDirective inverseDirective ->
            muzzleDirectiveToArtifacts(inverseDirective, system, session).collect() { Artifact singleVersion ->
              runAfter = addMuzzleTask(inverseDirective, singleVersion, project, runAfter, bootstrapProject, toolingProject)
            }
          }
        }
      }
    }
  }

  private static ClassLoader getOrCreateToolingLoader(Project toolingProject) {
    final ClassLoader toolingLoader = TOOLING_LOADER.get()
    if (toolingLoader == null) {
      Set<URL> ddUrls = new HashSet<>()
      toolingProject.getLogger().info('creating classpath for agent-tooling')
      for (File f : toolingProject.sourceSets.main.runtimeClasspath.getFiles()) {
        toolingProject.getLogger().info('--' + f)
        ddUrls.add(f.toURI().toURL())
      }
      TOOLING_LOADER.compareAndSet(null, new URLClassLoader(ddUrls.toArray(new URL[0]), (ClassLoader) null))
      return TOOLING_LOADER.get()
    } else {
      return toolingLoader
    }
  }

  /**
   * Create a classloader with core agent classes and project instrumentation on the classpath.
   */
  private static ClassLoader createInstrumentationClassloader(Project project, Project toolingProject) {
    project.getLogger().info("Creating instrumentation classpath for: " + project.getName())
    Set<URL> ddUrls = new HashSet<>()
    for (File f : project.sourceSets.main.runtimeClasspath.getFiles()) {
      project.getLogger().info('--' + f)
      ddUrls.add(f.toURI().toURL())
    }

    return new URLClassLoader(ddUrls.toArray(new URL[0]), getOrCreateToolingLoader(toolingProject))
  }

  /**
   * Create a classloader with all compile-time dependencies on the classpath
   */
  private static ClassLoader createCompileDepsClassLoader(Project project, Project bootstrapProject) {
    List<URL> userUrls = new ArrayList<>()
    project.getLogger().info("Creating compile-time classpath for: " + project.getName())
    for (File f : project.configurations.compileOnly.getFiles()) {
      project.getLogger().info('--' + f)
      userUrls.add(f.toURI().toURL())
    }
    for (File f : bootstrapProject.sourceSets.main.runtimeClasspath.getFiles()) {
      project.getLogger().info('--' + f)
      userUrls.add(f.toURI().toURL())
    }
    return new URLClassLoader(userUrls.toArray(new URL[0]), (ClassLoader) null)
  }

  /**
   * Create a classloader with dependencies for a single muzzle task.
   */
  private static ClassLoader createClassLoaderForTask(Project project, Project bootstrapProject, String muzzleTaskName) {
    final List<URL> userUrls = new ArrayList<>()

    project.getLogger().info("Creating task classpath")
    project.configurations.getByName(muzzleTaskName).resolvedConfiguration.files.each { File jarFile ->
      project.getLogger().info("-- Added to instrumentation classpath: $jarFile")
      userUrls.add(jarFile.toURI().toURL())
    }

    for (File f : bootstrapProject.sourceSets.main.runtimeClasspath.getFiles()) {
      project.getLogger().info("-- Added to instrumentation bootstrap classpath: $f")
      userUrls.add(f.toURI().toURL())
    }
    return new URLClassLoader(userUrls.toArray(new URL[0]), (ClassLoader) null)
  }

  /**
   * Convert a muzzle directive to a list of artifacts
   */
  private static List<Artifact> muzzleDirectiveToArtifacts(MuzzleDirective muzzleDirective, RepositorySystem system, RepositorySystemSession session) {
    final Artifact directiveArtifact = new DefaultArtifact(muzzleDirective.group, muzzleDirective.module, "jar", muzzleDirective.versions)

    final VersionRangeRequest rangeRequest = new VersionRangeRequest()
    rangeRequest.setRepositories(MUZZLE_REPOS)
    rangeRequest.setArtifact(directiveArtifact)
    final VersionRangeResult rangeResult = system.resolveVersionRange(session, rangeRequest)

    final List<Artifact> allVersionArtifacts = filterVersion(rangeResult.versions).collect { version ->
      new DefaultArtifact(muzzleDirective.group, muzzleDirective.module, "jar", version.toString())
    }

    return allVersionArtifacts
  }

  /**
   * Create a list of muzzle directives which assert the opposite of the given MuzzleDirective.
   */
  private static List<MuzzleDirective> inverseOf(MuzzleDirective muzzleDirective, RepositorySystem system, RepositorySystemSession session) {
    List<MuzzleDirective> inverseDirectives = new ArrayList<>()

    final Artifact allVerisonsArtifact = new DefaultArtifact(muzzleDirective.group, muzzleDirective.module, "jar", "[,)")
    final Artifact directiveArtifact = new DefaultArtifact(muzzleDirective.group, muzzleDirective.module, "jar", muzzleDirective.versions)


    final VersionRangeRequest allRangeRequest = new VersionRangeRequest()
    allRangeRequest.setRepositories(MUZZLE_REPOS)
    allRangeRequest.setArtifact(allVerisonsArtifact)
    final VersionRangeResult allRangeResult = system.resolveVersionRange(session, allRangeRequest)

    final VersionRangeRequest rangeRequest = new VersionRangeRequest()
    rangeRequest.setRepositories(MUZZLE_REPOS)
    rangeRequest.setArtifact(directiveArtifact)
    final VersionRangeResult rangeResult = system.resolveVersionRange(session, rangeRequest)

    filterVersion(allRangeResult.versions).collect { version ->
      if (!rangeResult.versions.contains(version)) {
        final MuzzleDirective inverseDirective = new MuzzleDirective()
        inverseDirective.group = muzzleDirective.group
        inverseDirective.module = muzzleDirective.module
        inverseDirective.versions = "$version"
        inverseDirective.assertPass = !muzzleDirective.assertPass
        inverseDirectives.add(inverseDirective)
      }
    }

    return inverseDirectives
  }

  /**
   * Configure a muzzle task to pass or fail a given version.
   *
   * @param assertPass If true, assert that muzzle validation passes
   * @param versionArtifact version to assert against.
   * @param instrumentationProject instrumentation being asserted against.
   * @param runAfter Task which runs before the new muzzle task.
   * @param bootstrapProject Agent bootstrap project.
   * @param toolingProject Agent tooling project.
   *
   * @return The created muzzle task.
   */
  private static Task addMuzzleTask(MuzzleDirective muzzleDirective, Artifact versionArtifact, Project instrumentationProject, Task runAfter, Project bootstrapProject, Project toolingProject) {
    def taskName = "muzzle-Assert${muzzleDirective.assertPass ? "Pass" : "Fail"}-$versionArtifact.groupId-$versionArtifact.artifactId-$versionArtifact.version${muzzleDirective.name ? "-${muzzleDirective.getNameSlug()}" : ""}"
    def config = instrumentationProject.configurations.create(taskName)
    def dep =  instrumentationProject.dependencies.create("$versionArtifact.groupId:$versionArtifact.artifactId:$versionArtifact.version") {
      transitive = true
    }
    // The following optional transitive dependencies are brought in by some legacy module such as log4j 1.x but are no
    // longer bundled with the JVM and have to be excluded for the muzzle tests to be able to run.
    dep.exclude group: 'com.sun.jdmk', module: 'jmxtools'
    dep.exclude group: 'com.sun.jmx', module: 'jmxri'

    config.dependencies.add(dep)
    for (String additionalDependency : muzzleDirective.additionalDependencies) {
      config.dependencies.add(instrumentationProject.dependencies.create(additionalDependency) {
        transitive = true
      })
    }

    def muzzleTask = instrumentationProject.task(taskName) {
      doLast {
        final ClassLoader userCL = createClassLoaderForTask(instrumentationProject, bootstrapProject, taskName)
        final ClassLoader instrumentationCL = createInstrumentationClassloader(instrumentationProject, toolingProject)
        // find all instrumenters, get muzzle, and assert
        Method assertionMethod = instrumentationCL.loadClass('datadog.trace.agent.tooling.muzzle.MuzzleVersionScanPlugin')
          .getMethod('assertInstrumentationMuzzled', ClassLoader.class, ClassLoader.class, boolean.class)
        assertionMethod.invoke(null, instrumentationCL, userCL, muzzleDirective.assertPass)

        for (Thread thread : Thread.getThreads()) {
          if (thread.getName().startsWith("dd-")) {
            throw new GradleException("Task $taskName has spawned a thread: $thread. This will prevent GC of dynamic muzzle classes. Aborting muzzle run.")
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

  /**
   * Filter out snapshot-type builds from versions list.
   */
  private static filterVersion(List<Version> list) {
    list.removeIf {
      def version = it.toString().toLowerCase()
      return version.contains("rc") ||
        version.contains(".cr") ||
        version.contains("alpha") ||
        version.contains("beta") ||
        version.contains("-b") ||
        version.contains(".m") ||
        version.contains("-dev") ||
        version.contains("public_draft")
    }
    return list
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
  List<String> additionalDependencies = new ArrayList<>()
  boolean assertPass
  boolean assertInverse = false

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
    final MuzzleDirective pass = objectFactory.newInstance(MuzzleDirective)
    action.execute(pass)
    pass.assertPass = true
    directives.add(pass)
  }

  void fail(Action<? super MuzzleDirective> action) {
    final MuzzleDirective fail = objectFactory.newInstance(MuzzleDirective)
    action.execute(fail)
    fail.assertPass = false
    directives.add(fail)
  }
}
