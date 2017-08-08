import com.google.common.collect.Sets
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
import org.eclipse.aether.transport.file.FileTransporterFactory
import org.eclipse.aether.transport.http.HttpTransporterFactory
import org.eclipse.aether.version.Version
import org.gradle.api.Plugin
import org.gradle.api.Project

import java.util.concurrent.atomic.AtomicReference
import java.util.jar.JarFile

/**
 * Version syntax: https://maven.apache.org/enforcer/enforcer-rules/versionRanges.html
 */
class VersionScanPlugin implements Plugin<Project> {

  void apply(Project project) {
    RepositorySystem system = newRepositorySystem()
    RepositorySystemSession session = newRepositorySystemSession(system)

    project.extensions.create("versionScan", VersionScanExtension)
    project.task('scanVersions') {
      description = "Queries for all versions of configured modules and finds key classes"
    }

    if (!project.gradle.startParameter.taskNames.contains('scanVersions')) {
      return
    }

    Set<String> allInclude = Sets.newConcurrentHashSet()
    Set<String> allExclude = Sets.newConcurrentHashSet()
    AtomicReference<Set<String>> keyPresent = new AtomicReference(Collections.emptySet())
    AtomicReference<Set<String>> keyMissing = new AtomicReference(Collections.emptySet())
    def scanVersionsReport = project.task('scanVersionsReport') {
      description = "Prints the result of the scanVersions task"
      doLast {
        def inCommonPresent = keyPresent.get().size()
        def inCommonMissing = keyMissing.get().size()
        keyPresent.get().removeAll(allExclude)
        keyMissing.get().removeAll(allInclude)
        def exclusivePresent = keyPresent.get().size()
        def exclusiveMissing = keyMissing.get().size()

        if (project.hasProperty("showClasses")) {
          println "keyPresent: $inCommonPresent->$exclusivePresent - ${keyPresent.get()}"
          println "+++++++++++++++++++++"
          println "keyMissing: $inCommonMissing->$exclusiveMissing - ${keyMissing.get()}"
        } else {
          println "keyPresent: $inCommonPresent->$exclusivePresent"
          println "keyMissing: $inCommonMissing->$exclusiveMissing"
        }
      }
    }
    project.tasks.scanVersions.finalizedBy(scanVersionsReport)

    project.repositories {
      mavenCentral()
      jcenter()
    }

    project.afterEvaluate {
      String group = project.versionScan.group
      String module = project.versionScan.module
      String versions = project.versionScan.versions
      Artifact artifact = new DefaultArtifact(group, module, "jar", versions)
      Artifact allVersions = new DefaultArtifact(group, module, "jar", "(,)")

      VersionRangeRequest rangeRequest = new VersionRangeRequest()
      rangeRequest.setRepositories(newRepositories(system, session))

      rangeRequest.setArtifact(artifact)
      VersionRangeResult rangeResult = system.resolveVersionRange(session, rangeRequest)
      rangeRequest.setArtifact(allVersions)
      VersionRangeResult allResult = system.resolveVersionRange(session, rangeRequest)

      def includeVersionSet = Sets.newHashSet(filter(rangeResult.versions))
      def excludeVersionSet = Sets.newHashSet(filter(allResult.versions))
      excludeVersionSet.removeAll(includeVersionSet)

      if (excludeVersionSet.empty) {
        println "Found ${includeVersionSet.size()} versions, but none to exclude. Skipping..."
        scanVersionsReport.enabled = false
        return
      }

      println "Scanning ${includeVersionSet.size()} included and ${excludeVersionSet.size()} excluded versions.  Included: $includeVersionSet"

      includeVersionSet.each { version ->
        def name = "scanVersionInclude-$group-$module-$version"
        def config = project.configurations.create(name)
        config.dependencies.add(project.dependencies.create("$group:$module:$version"))

        def task = project.task(name) {
          doLast {
            project.configurations.getByName(name).resolvedConfiguration.files.each { jarFile ->
              def jar = new JarFile(jarFile)
              Set<String> contentSet = Sets.newConcurrentHashSet()
              for (jarEntry in jar.entries()) {
                contentSet.add(jarEntry.toString())
              }
              allInclude.addAll(contentSet)

              if (!keyPresent.compareAndSet(Collections.emptySet(), contentSet)) {
                def intersection = Sets.intersection(keyPresent.get(), contentSet)
                keyPresent.get().retainAll(intersection)
              }
            }
          }
        }
        project.tasks.scanVersions.finalizedBy(task)
        project.tasks.scanVersionsReport.mustRunAfter(task)
      }

      excludeVersionSet.each { version ->
        def name = "scanVersionExclude-$group-$module-$version"
        def config = project.configurations.create(name)
        config.dependencies.add(project.dependencies.create("$group:$module:$version"))

        def task = project.task(name) {
          doLast {
            project.configurations.getByName(name).resolvedConfiguration.files.each { jarFile ->
              def jar = new JarFile(jarFile)
              Set<String> contentSet = Sets.newConcurrentHashSet()
              for (jarEntry in jar.entries()) {
                contentSet.add(jarEntry.toString())
              }
              allExclude.addAll(contentSet)

              if (!keyMissing.compareAndSet(Collections.emptySet(), contentSet)) {
                def intersection = Sets.intersection(keyMissing.get(), contentSet)
                keyMissing.get().retainAll(intersection)
              }
            }
          }
        }
        project.tasks.scanVersions.finalizedBy(task)
        project.tasks.scanVersionsReport.mustRunAfter(task)
      }
    }
  }

  def filter(List<Version> list) {
    list.removeIf {
      def version = it.toString().toLowerCase()
      return version.contains("rc") || version.contains("alpha") || version.contains("beta")
    }
    return list
  }

  RepositorySystem newRepositorySystem() {
    DefaultServiceLocator locator = MavenRepositorySystemUtils.newServiceLocator()
    locator.addService(RepositoryConnectorFactory.class, BasicRepositoryConnectorFactory.class)
    locator.addService(TransporterFactory.class, FileTransporterFactory.class)
    locator.addService(TransporterFactory.class, HttpTransporterFactory.class)

    return locator.getService(RepositorySystem.class)
  }

  DefaultRepositorySystemSession newRepositorySystemSession(RepositorySystem system) {
    DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession()

    def tempDir = File.createTempDir()
    tempDir.deleteOnExit()
    LocalRepository localRepo = new LocalRepository(tempDir)
    session.setLocalRepositoryManager(system.newLocalRepositoryManager(session, localRepo))

    return session
  }

  static List<RemoteRepository> newRepositories(RepositorySystem system, RepositorySystemSession session) {
    return new ArrayList<RemoteRepository>(Arrays.asList(newCentralRepository()))
  }

  private static RemoteRepository newCentralRepository() {
    return new RemoteRepository.Builder("central", "default", "http://central.maven.org/maven2/").build()
  }
}
