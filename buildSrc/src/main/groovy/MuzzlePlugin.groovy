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
    def muzzle = project.task('muzzle') {
      group = 'Muzzle'
      description = "Run instrumentation muzzle on compile time dependencies"
      doLast {
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
        final ClassLoader userCL = new URLClassLoader(userUrls.toArray(new URL[0]), (ClassLoader) null)

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

        final ClassLoader agentCL = new URLClassLoader(ddUrls.toArray(new URL[0]), (ClassLoader) null)
        // find all instrumenters, get muzzle, and assert
        Method assertionMethod = agentCL.loadClass('datadog.trace.agent.tooling.muzzle.MuzzleVersionScanPlugin')
          .getMethod('assertInstrumentationNotMuzzled', ClassLoader.class)
        assertionMethod.invoke(null, userCL)
      }
    }
    // project.tasks.muzzle.dependsOn(bootstrapProject.tasks.shadowJar)
    project.tasks.muzzle.dependsOn(bootstrapProject.tasks.compileJava)
    project.tasks.muzzle.dependsOn(toolingProject.tasks.compileJava)
    project.afterEvaluate {
      project.tasks.muzzle.dependsOn(project.tasks.compileJava)
      if (project.tasks.getNames().contains("compileScala")) {
        project.tasks.muzzle.dependsOn(project.tasks.compileScala)
      }
    }
  }
}
