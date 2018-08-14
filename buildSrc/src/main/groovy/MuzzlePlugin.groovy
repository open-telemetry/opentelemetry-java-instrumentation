import org.gradle.api.Plugin
import org.gradle.api.Project

import java.lang.reflect.Method

/**
 * muzzle task plugin which runs muzzle validation against an instrumentation's compile-time dependencies.
 *
 * <p/>TODO: merge this with version scan
 */
class MuzzlePlugin implements Plugin<Project> {
  @Override
  void apply(Project project) {
    def bootstrapProject = project.rootProject.getChildProjects().get('dd-java-agent').getChildProjects().get('agent-bootstrap')
    def toolingProject = project.rootProject.getChildProjects().get('dd-java-agent').getChildProjects().get('agent-tooling')
    project.extensions.create("muzzle", MuzzleExtension)
    def compileMuzzle = project.task('compileMuzzle') {
      // not adding user and group to hide this from `gradle tasks`
    }
    def muzzle = project.task('muzzle') {
      group = 'Muzzle'
      description = "Run instrumentation muzzle on compile time dependencies"
      doLast {
        final ClassLoader userCL = createUserClassLoader(project, bootstrapProject)
        final ClassLoader agentCL = createDDClassloader(project, toolingProject)
        // find all instrumenters, get muzzle, and assert
        Method assertionMethod = agentCL.loadClass('datadog.trace.agent.tooling.muzzle.MuzzleVersionScanPlugin')
          .getMethod('assertInstrumentationNotMuzzled', ClassLoader.class)
        assertionMethod.invoke(null, userCL)
      }
    }
    def printReferences = project.task('printReferences') {
      group = 'Muzzle'
      description = "Print references created by instrumentation muzzle"
      doLast {
        final ClassLoader agentCL = createDDClassloader(project, toolingProject)
        Method assertionMethod = agentCL.loadClass('datadog.trace.agent.tooling.muzzle.MuzzleVersionScanPlugin')
          .getMethod('printMuzzleReferences')
        assertionMethod.invoke(null)
      }
    }
    project.tasks.compileMuzzle.dependsOn(bootstrapProject.tasks.compileJava)
    project.tasks.compileMuzzle.dependsOn(toolingProject.tasks.compileJava)
    project.afterEvaluate {
      project.tasks.compileMuzzle.dependsOn(project.tasks.compileJava)
      if (project.tasks.getNames().contains("compileScala")) {
        project.tasks.compileMuzzle.dependsOn(project.tasks.compileScala)
      }
    }

    project.tasks.muzzle.dependsOn(project.tasks.compileMuzzle)
    project.tasks.printReferences.dependsOn(project.tasks.compileMuzzle)
  }

  /**
   * Create a classloader with core agent classes and project instrumentation on the classpath.
   */
  private ClassLoader createDDClassloader(Project project, Project toolingProject) {
    project.getLogger().info("Creating dd classpath for: " + project.getName())
    Set<URL> ddUrls = new HashSet<>()
    for (File f : toolingProject.sourceSets.main.runtimeClasspath.getFiles()) {
      project.getLogger().info('--' + f)
      ddUrls.add(f.toURI().toURL())
    }
    for (File f : project.sourceSets.main.runtimeClasspath.getFiles()) {
      project.getLogger().info('--' + f)
      ddUrls.add(f.toURI().toURL())
    }

    return new URLClassLoader(ddUrls.toArray(new URL[0]), (ClassLoader) null)
  }

  /**
   * Create a classloader with user/library classes on the classpath.
   */
  private ClassLoader createUserClassLoader(Project project, Project bootstrapProject) {
    List<URL> userUrls = new ArrayList<>()
    project.getLogger().info("Creating user classpath for: " + project.getName())
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
}
