/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.gradle.muzzle;

import java.io.File;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.SecureClassLoader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.VersionRangeRequest;
import org.eclipse.aether.resolution.VersionRangeResolutionException;
import org.eclipse.aether.resolution.VersionRangeResult;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transport.http.HttpTransporterFactory;
import org.eclipse.aether.version.Version;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.artifacts.repositories.ArtifactRepository;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskProvider;

public class MuzzlePlugin implements Plugin<Project> {
  /** Select a random set of versions to test */
  private static final int RANGE_COUNT_LIMIT = 10;

  private static volatile ClassLoader TOOLING_LOADER;

  @Override
  public void apply(Project project) {
    MuzzleExtension muzzleConfig =
        project.getExtensions().create("muzzle", MuzzleExtension.class, project.getObjects());

    // compileMuzzle compiles all projects required to run muzzle validation.
    // Not adding group and description to keep this task from showing in `gradle tasks`.
    TaskProvider<?> compileMuzzle =
        project
            .getTasks()
            .register(
                "compileMuzzle",
                task -> {
                  task.dependsOn(":javaagent-bootstrap:classes");
                  task.dependsOn(":javaagent-tooling:classes");
                  task.dependsOn(":javaagent-extension-api:classes");
                  task.dependsOn(project.getTasks().named(JavaPlugin.CLASSES_TASK_NAME));
                });

    TaskProvider<?> muzzle =
        project
            .getTasks()
            .register(
                "muzzle",
                task -> {
                  task.setGroup("Muzzle");
                  task.setDescription("Run instrumentation muzzle on compile time dependencies");
                  task.dependsOn(compileMuzzle);
                });

    project
        .getTasks()
        .register(
            "printMuzzleReferences",
            task -> {
              task.setGroup("Muzzle");
              task.setDescription("Print references created by instrumentation muzzle");
              task.dependsOn(compileMuzzle);
              task.doLast(
                  unused -> {
                    ClassLoader instrumentationCL = createInstrumentationClassloader(project);
                    try {
                      Method assertionMethod =
                          instrumentationCL
                              .loadClass(
                                  "io.opentelemetry.javaagent.tooling.muzzle.matcher.MuzzleGradlePluginUtil")
                              .getMethod("printMuzzleReferences", ClassLoader.class);
                      assertionMethod.invoke(null, instrumentationCL);
                    } catch (Exception e) {
                      throw new IllegalStateException(e);
                    }
                  });
            });

    boolean hasRelevantTask =
        project.getGradle().getStartParameter().getTaskNames().stream()
            .anyMatch(
                taskName -> {
                  // removing leading ':' if present
                  if (taskName.startsWith(":")) {
                    taskName = taskName.substring(1);
                  }
                  String projectPath = project.getPath().substring(1);
                  // Either the specific muzzle task in this project or the top level, full-project
                  // muzzle task.
                  return taskName.equals(projectPath + ":muzzle") || taskName.equals("muzzle");
                });
    if (!hasRelevantTask) {
      // Adding muzzle dependencies has a large config overhead. Stop unless muzzle is explicitly
      // run.
      return;
    }

    RepositorySystem system = newRepositorySystem();
    RepositorySystemSession session = newRepositorySystemSession(system, project);

    project.afterEvaluate(
        unused -> {
          // use runAfter to set up task finalizers in version order
          TaskProvider<?> runAfter = muzzle;

          for (MuzzleDirective muzzleDirective : muzzleConfig.getDirectives().get()) {
            project.getLogger().info("configured " + muzzleDirective);

            if (muzzleDirective.getCoreJdk().get()) {
              runAfter = addMuzzleTask(muzzleDirective, null, project, runAfter);
            } else {
              for (Artifact singleVersion :
                  muzzleDirectiveToArtifacts(project, muzzleDirective, system, session)) {
                runAfter = addMuzzleTask(muzzleDirective, singleVersion, project, runAfter);
              }
              if (muzzleDirective.getAssertInverse().get()) {
                for (MuzzleDirective inverseDirective :
                    inverseOf(project, muzzleDirective, system, session)) {
                  for (Artifact singleVersion :
                      muzzleDirectiveToArtifacts(project, inverseDirective, system, session)) {
                    runAfter = addMuzzleTask(inverseDirective, singleVersion, project, runAfter);
                  }
                }
              }
            }
          }
        });
  }

  /** Create a classloader with core agent classes and project instrumentation on the classpath. */
  private static ClassLoader createInstrumentationClassloader(Project project) {
    project.getLogger().info("Creating instrumentation classpath for: " + project.getName());
    SourceSetContainer sourceSets = project.getExtensions().getByType(SourceSetContainer.class);
    FileCollection runtimeClasspath =
        sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME).getRuntimeClasspath();

    return classpathLoader(runtimeClasspath, getOrCreateToolingLoader(project), project);
  }

  private static synchronized ClassLoader getOrCreateToolingLoader(Project project) {
    if (TOOLING_LOADER == null) {
      project.getLogger().info("creating classpath for auto-tooling");
      FileCollection toolingRuntime = project.getConfigurations().getByName("toolingRuntime");
      TOOLING_LOADER =
          classpathLoader(toolingRuntime, ClassLoader.getPlatformClassLoader(), project);
    }
    return TOOLING_LOADER;
  }

  private static ClassLoader classpathLoader(
      FileCollection classpath, ClassLoader parent, Project project) {
    URL[] urls =
        StreamSupport.stream(classpath.spliterator(), false)
            .map(
                file -> {
                  project.getLogger().info("--" + file);
                  try {
                    return file.toURI().toURL();
                  } catch (MalformedURLException e) {
                    throw new IllegalStateException(e);
                  }
                })
            .toArray(URL[]::new);
    return new URLClassLoader(urls, parent);
  }

  /**
   * Configure a muzzle task to pass or fail a given version.
   *
   * @param versionArtifact version to assert against.
   * @param instrumentationProject instrumentation being asserted against.
   * @param runAfter Task which runs before the new muzzle task.
   * @return The created muzzle task.
   */
  private static TaskProvider addMuzzleTask(
      MuzzleDirective muzzleDirective,
      Artifact versionArtifact,
      Project instrumentationProject,
      TaskProvider<?> runAfter) {
    final String taskName;
    if (muzzleDirective.getCoreJdk().get()) {
      taskName = "muzzle-Assert" + muzzleDirective;
    } else {
      StringBuilder sb = new StringBuilder("muzzle-Assert");
      if (muzzleDirective.getAssertPass().isPresent()) {
        sb.append("Pass");
      } else {
        sb.append("Fail");
      }
      sb.append('-')
          .append(versionArtifact.getGroupId())
          .append('-')
          .append(versionArtifact.getArtifactId())
          .append('-')
          .append(versionArtifact.getVersion());
      if (!muzzleDirective.getName().get().isEmpty()) {
        sb.append(muzzleDirective.getNameSlug());
      }
      taskName = sb.toString();
    }
    Configuration config = instrumentationProject.getConfigurations().create(taskName);

    if (!muzzleDirective.getCoreJdk().get()) {
      ModuleDependency dep =
          (ModuleDependency)
              instrumentationProject
                  .getDependencies()
                  .create(
                      versionArtifact.getGroupId()
                          + ':'
                          + versionArtifact.getArtifactId()
                          + ':'
                          + versionArtifact.getVersion());
      dep.setTransitive(true);
      // The following optional transitive dependencies are brought in by some legacy module such as
      // log4j 1.x but are no
      // longer bundled with the JVM and have to be excluded for the muzzle tests to be able to run.
      exclude(dep, "com.sun.jdmk", "jmxtools");
      exclude(dep, "com.sun.jmx", "jmxri");
      for (String excluded : muzzleDirective.getExcludedDependencies().get()) {
        String[] parts = excluded.split(":");
        exclude(dep, parts[0], parts[1]);
      }

      config.getDependencies().add(dep);
    }
    for (String additionalDependency : muzzleDirective.getAdditionalDependencies().get()) {
      if (countColons(additionalDependency) < 2) {
        // Dependency definition without version, use the artifact's version.
        additionalDependency = additionalDependency + ':' + versionArtifact.getVersion();
      }
      ModuleDependency dep =
          (ModuleDependency) instrumentationProject.getDependencies().create(additionalDependency);
      dep.setTransitive(true);
      config.getDependencies().add(dep);
    }

    TaskProvider<?> muzzleTask =
        instrumentationProject
            .getTasks()
            .register(
                taskName,
                task -> {
                  task.dependsOn(
                      instrumentationProject.getConfigurations().named("runtimeClasspath"));
                  task.doLast(
                      unused -> {
                        ClassLoader instrumentationCL =
                            createInstrumentationClassloader(instrumentationProject);
                        ClassLoader ccl = Thread.currentThread().getContextClassLoader();
                        ClassLoader bogusLoader =
                            new SecureClassLoader() {
                              @Override
                              public String toString() {
                                return "bogus";
                              }
                            };
                        Thread.currentThread().setContextClassLoader(bogusLoader);
                        ClassLoader userCL =
                            createClassLoaderForTask(instrumentationProject, taskName);
                        try {
                          // find all instrumenters, get muzzle, and assert
                          Method assertionMethod =
                              instrumentationCL
                                  .loadClass(
                                      "io.opentelemetry.javaagent.tooling.muzzle.matcher.MuzzleGradlePluginUtil")
                                  .getMethod(
                                      "assertInstrumentationMuzzled",
                                      ClassLoader.class,
                                      ClassLoader.class,
                                      boolean.class);
                          assertionMethod.invoke(
                              null,
                              instrumentationCL,
                              userCL,
                              muzzleDirective.getAssertPass().get());
                        } catch (Exception e) {
                          throw new IllegalStateException(e);
                        } finally {
                          Thread.currentThread().setContextClassLoader(ccl);
                        }

                        for (Thread thread : Thread.getAllStackTraces().keySet()) {
                          if (thread.getContextClassLoader() == bogusLoader
                              || thread.getContextClassLoader() == instrumentationCL
                              || thread.getContextClassLoader() == userCL) {
                            throw new GradleException(
                                "Task "
                                    + taskName
                                    + " has spawned a thread: "
                                    + thread
                                    + " with classloader "
                                    + thread.getContextClassLoader()
                                    + ". This will prevent GC of dynamic muzzle classes. Aborting muzzle run.");
                          }
                        }
                      });
                });
    runAfter.configure(task -> task.finalizedBy(muzzleTask));
    return muzzleTask;
  }

  /** Create a classloader with dependencies for a single muzzle task. */
  private static ClassLoader createClassLoaderForTask(Project project, String muzzleTaskName) {
    ConfigurableFileCollection userUrls = project.getObjects().fileCollection();
    project.getLogger().info("Creating task classpath");
    userUrls.from(
        project
            .getConfigurations()
            .getByName(muzzleTaskName)
            .getResolvedConfiguration()
            .getFiles());
    return classpathLoader(
        userUrls.plus(project.getConfigurations().getByName("bootstrapRuntime")),
        ClassLoader.getPlatformClassLoader(),
        project);
  }

  /** Convert a muzzle directive to a list of artifacts */
  private static Set<Artifact> muzzleDirectiveToArtifacts(
      Project instrumentationProject,
      MuzzleDirective muzzleDirective,
      RepositorySystem system,
      RepositorySystemSession session) {
    Artifact directiveArtifact =
        new DefaultArtifact(
            muzzleDirective.getGroup().get(),
            muzzleDirective.getModule().get(),
            "jar",
            muzzleDirective.getVersions().get());

    VersionRangeRequest rangeRequest = new VersionRangeRequest();
    rangeRequest.setRepositories(getProjectRepositories(instrumentationProject));
    rangeRequest.setArtifact(directiveArtifact);
    final VersionRangeResult rangeResult;
    try {
      rangeResult = system.resolveVersionRange(session, rangeRequest);
    } catch (VersionRangeResolutionException e) {
      throw new IllegalStateException(e);
    }

    Set<Artifact> allVersionArtifacts =
        filterVersions(rangeResult, muzzleDirective.getNormalizedSkipVersions()).stream()
            .map(
                version ->
                    new DefaultArtifact(
                        muzzleDirective.getGroup().get(),
                        muzzleDirective.getModule().get(),
                        "jar",
                        version))
            .collect(Collectors.toSet());

    if (allVersionArtifacts.isEmpty()) {
      throw new GradleException("No muzzle artifacts found for " + muzzleDirective);
    }

    return allVersionArtifacts;
  }

  private static List<RemoteRepository> getProjectRepositories(Project project) {
    List<RemoteRepository> repositories = new ArrayList<>();
    // Manually add mavenCentral until https://github.com/gradle/gradle/issues/17295
    // Adding mavenLocal is much more complicated but hopefully isn't required for normal usage of
    // Muzzle.
    repositories.add(
        new RemoteRepository.Builder(
                "MavenCentral", "default", "https://repo.maven.apache.org/maven2/")
            .build());
    for (ArtifactRepository repository : project.getRepositories()) {
      if (repository instanceof MavenArtifactRepository) {
        repositories.add(
            new RemoteRepository.Builder(
                    repository.getName(),
                    "default",
                    ((MavenArtifactRepository) repository).getUrl().toString())
                .build());
      }
    }
    return repositories;
  }

  /** Create a list of muzzle directives which assert the opposite of the given MuzzleDirective. */
  private static Set<MuzzleDirective> inverseOf(
      Project instrumentationProject,
      MuzzleDirective muzzleDirective,
      RepositorySystem system,
      RepositorySystemSession session) {
    Set<MuzzleDirective> inverseDirectives = new HashSet<>();

    Artifact allVersionsArtifact =
        new DefaultArtifact(
            muzzleDirective.getGroup().get(), muzzleDirective.getModule().get(), "jar", "[,)");
    Artifact directiveArtifact =
        new DefaultArtifact(
            muzzleDirective.getGroup().get(),
            muzzleDirective.getModule().get(),
            "jar",
            muzzleDirective.getVersions().get());

    List<RemoteRepository> repos = getProjectRepositories(instrumentationProject);
    VersionRangeRequest allRangeRequest = new VersionRangeRequest();
    allRangeRequest.setRepositories(repos);
    allRangeRequest.setArtifact(allVersionsArtifact);
    final VersionRangeResult allRangeResult;
    try {
      allRangeResult = system.resolveVersionRange(session, allRangeRequest);
    } catch (VersionRangeResolutionException e) {
      throw new IllegalStateException(e);
    }

    VersionRangeRequest rangeRequest = new VersionRangeRequest();
    rangeRequest.setRepositories(repos);
    rangeRequest.setArtifact(directiveArtifact);
    final VersionRangeResult rangeResult;
    try {
      rangeResult = system.resolveVersionRange(session, rangeRequest);
    } catch (VersionRangeResolutionException e) {
      throw new IllegalStateException(e);
    }

    allRangeResult.getVersions().removeAll(rangeResult.getVersions());

    for (String version :
        filterVersions(allRangeResult, muzzleDirective.getNormalizedSkipVersions())) {
      MuzzleDirective inverseDirective =
          instrumentationProject.getObjects().newInstance(MuzzleDirective.class);
      inverseDirective.getGroup().set(muzzleDirective.getGroup());
      inverseDirective.getModule().set(muzzleDirective.getModule());
      inverseDirective.getVersions().set(version);
      inverseDirective.getAssertPass().set(!muzzleDirective.getAssertPass().get());
      inverseDirective.getExcludedDependencies().set(muzzleDirective.getExcludedDependencies());
      inverseDirectives.add(inverseDirective);
    }

    return inverseDirectives;
  }

  private static Set<String> filterVersions(VersionRangeResult range, Set<String> skipVersions) {
    Set<String> result = new HashSet<>();

    AcceptableVersions predicate = new AcceptableVersions(skipVersions);
    if (predicate.test(range.getLowestVersion())) {
      result.add(range.getLowestVersion().toString());
    }
    if (predicate.test(range.getHighestVersion())) {
      result.add(range.getHighestVersion().toString());
    }

    List<Version> copy = new ArrayList<>(range.getVersions());
    Collections.shuffle(copy);
    for (Version version : copy) {
      if (result.size() >= RANGE_COUNT_LIMIT) {
        break;
      }
      if (predicate.test(version)) {
        result.add(version.toString());
      }
    }

    return result;
  }

  /** Create muzzle's repository system */
  private static RepositorySystem newRepositorySystem() {
    DefaultServiceLocator locator = MavenRepositorySystemUtils.newServiceLocator();
    locator.addService(RepositoryConnectorFactory.class, BasicRepositoryConnectorFactory.class);
    locator.addService(TransporterFactory.class, HttpTransporterFactory.class);

    return locator.getService(RepositorySystem.class);
  }

  /** Create muzzle's repository system session */
  private static RepositorySystemSession newRepositorySystemSession(
      RepositorySystem system, Project project) {
    DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();
    File muzzleRepo = project.file("build/muzzleRepo");
    LocalRepository localRepo = new LocalRepository(muzzleRepo);
    session.setLocalRepositoryManager(system.newLocalRepositoryManager(session, localRepo));
    return session;
  }

  private static int countColons(String s) {
    int count = 0;
    for (int i = 0; i < s.length(); i++) {
      if (s.charAt(i) == ':') {
        count++;
      }
    }
    return count;
  }

  private static void exclude(ModuleDependency dependency, String group, String module) {
    Map<String, String> exclusions = new HashMap<>();
    exclusions.put("group", group);
    exclusions.put("module", module);
    dependency.exclude(exclusions);
  }
}
