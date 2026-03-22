/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

// Includes work from:
/*
 * Copyright Quarkus Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.quarkus.gradle.tooling;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;

import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.ResolvedConfiguration;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.initialization.IncludedBuild;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskCollection;
import org.gradle.api.tasks.compile.AbstractCompile;
import org.gradle.api.tasks.testing.Test;
import org.gradle.internal.composite.IncludedBuildInternal;
import org.gradle.language.jvm.tasks.ProcessResources;
import org.gradle.tooling.provider.model.ParameterizedToolingModelBuilder;

import io.quarkus.bootstrap.BootstrapConstants;
import io.quarkus.bootstrap.model.ApplicationModel;
import io.quarkus.bootstrap.model.ApplicationModelBuilder;
import io.quarkus.bootstrap.model.CapabilityContract;
import io.quarkus.bootstrap.model.PlatformImports;
import io.quarkus.bootstrap.model.gradle.ModelParameter;
import io.quarkus.bootstrap.model.gradle.impl.ModelParameterImpl;
import io.quarkus.bootstrap.workspace.ArtifactSources;
import io.quarkus.bootstrap.workspace.DefaultArtifactSources;
import io.quarkus.bootstrap.workspace.DefaultSourceDir;
import io.quarkus.bootstrap.workspace.DefaultWorkspaceModule;
import io.quarkus.bootstrap.workspace.SourceDir;
import io.quarkus.bootstrap.workspace.WorkspaceModule;
import io.quarkus.fs.util.ZipUtils;
import io.quarkus.gradle.dependency.ApplicationDeploymentClasspathBuilder;
import io.quarkus.maven.dependency.ArtifactCoords;
import io.quarkus.maven.dependency.ArtifactDependency;
import io.quarkus.maven.dependency.ArtifactKey;
import io.quarkus.maven.dependency.DependencyFlags;
import io.quarkus.maven.dependency.GACT;
import io.quarkus.maven.dependency.GACTV;
import io.quarkus.maven.dependency.GAV;
import io.quarkus.maven.dependency.ResolvedDependency;
import io.quarkus.maven.dependency.ResolvedDependencyBuilder;
import io.quarkus.paths.PathCollection;
import io.quarkus.paths.PathList;
import io.quarkus.runtime.LaunchMode;

public class GradleApplicationModelBuilder implements ParameterizedToolingModelBuilder<ModelParameter> {

  private static final String MAIN_RESOURCES_OUTPUT = "build/resources/main";
  private static final String CLASSES_OUTPUT = "build/classes";

  /* @formatter:off */
  private static final byte COLLECT_TOP_EXTENSION_RUNTIME_NODES = 0b001;
  private static final byte COLLECT_DIRECT_DEPS =                 0b010;
  private static final byte COLLECT_RELOADABLE_MODULES =          0b100;
  /* @formatter:on */

  @Override
  public boolean canBuild(String modelName) {
    return modelName.equals(ApplicationModel.class.getName());
  }

  @Override
  public Class<ModelParameter> getParameterType() {
    return ModelParameter.class;
  }

  @Override
  public Object buildAll(String modelName, Project project) {
    final ModelParameterImpl modelParameter = new ModelParameterImpl();
    modelParameter.setMode(LaunchMode.DEVELOPMENT.toString());
    return buildAll(modelName, modelParameter, project);
  }

  @Override
  public Object buildAll(String modelName, ModelParameter parameter, Project project) {
    final LaunchMode mode = LaunchMode.valueOf(parameter.getMode());

    final ApplicationDeploymentClasspathBuilder classpathBuilder = new ApplicationDeploymentClasspathBuilder(project,
        mode);
    final Configuration classpathConfig = classpathBuilder.getRuntimeConfiguration();
    final Configuration deploymentConfig = classpathBuilder.getDeploymentConfiguration();
    final PlatformImports platformImports = classpathBuilder.getPlatformImports();

    boolean workspaceDiscovery = LaunchMode.DEVELOPMENT.equals(mode) || LaunchMode.TEST.equals(mode)
        || Boolean.parseBoolean(System.getProperty(BootstrapConstants.QUARKUS_BOOTSTRAP_WORKSPACE_DISCOVERY));
    if (!workspaceDiscovery) {
      Object property = project.getProperties().get(BootstrapConstants.QUARKUS_BOOTSTRAP_WORKSPACE_DISCOVERY);
      if (property != null) {
        workspaceDiscovery = Boolean.parseBoolean(property.toString());
      }
    }

    final ResolvedDependency appArtifact = getProjectArtifact(project, workspaceDiscovery);
    final ApplicationModelBuilder modelBuilder = new ApplicationModelBuilder()
        .setAppArtifact(appArtifact)
        .addReloadableWorkspaceModule(appArtifact.getKey())
        .setPlatformImports(platformImports);

    collectDependencies(classpathConfig.getResolvedConfiguration(), workspaceDiscovery,
        project, modelBuilder, appArtifact.getWorkspaceModule().mutable());
    collectExtensionDependencies(project, deploymentConfig, modelBuilder);
    addCompileOnly(project, classpathBuilder, modelBuilder);

    return modelBuilder.build();
  }

  private static void addCompileOnly(Project project, ApplicationDeploymentClasspathBuilder classpathBuilder,
      ApplicationModelBuilder modelBuilder) {
    Configuration compileOnlyConfig = classpathBuilder.getCompileOnly();
    final List<org.gradle.api.artifacts.ResolvedDependency> queue = new ArrayList<>(
        compileOnlyConfig.getResolvedConfiguration().getFirstLevelModuleDependencies());
    for (int index = 0; index < queue.size(); index++) {
      org.gradle.api.artifacts.ResolvedDependency resolvedDependency = queue.get(index);
      boolean skip = true;
      for (ResolvedArtifact artifact : resolvedDependency.getModuleArtifacts()) {
        if (!isDependency(artifact)) {
          continue;
        }
        var moduleId = artifact.getModuleVersion().getId();
        var key = ArtifactKey.of(moduleId.getGroup(), moduleId.getName(), artifact.getClassifier(), artifact.getType());
        var appDependency = modelBuilder.getDependency(key);
        if (appDependency == null) {
          addArtifactDependency(project, modelBuilder, artifact);
          appDependency = modelBuilder.getDependency(key);
          appDependency.clearFlag(DependencyFlags.DEPLOYMENT_CP);
        }
        if (!appDependency.isFlagSet(DependencyFlags.COMPILE_ONLY)) {
          skip = false;
          appDependency.setFlags(DependencyFlags.COMPILE_ONLY);
        }
      }
      if (!skip) {
        queue.addAll(resolvedDependency.getChildren());
      }
    }
  }

  public static ResolvedDependency getProjectArtifact(Project project, boolean workspaceDiscovery) {
    final ResolvedDependencyBuilder appArtifact = ResolvedDependencyBuilder.newInstance()
        .setGroupId(project.getGroup().toString())
        .setArtifactId(project.getName())
        .setVersion(project.getVersion().toString());

    final SourceSetContainer sourceSets = project.getExtensions().getByType(SourceSetContainer.class);
    final WorkspaceModule.Mutable mainModule = WorkspaceModule.builder()
        .setModuleId(new GAV(appArtifact.getGroupId(), appArtifact.getArtifactId(), appArtifact.getVersion()))
        .setModuleDir(project.getProjectDir().toPath())
        .setBuildDir(project.getBuildDir().toPath())
        .setBuildFile(project.getBuildFile().toPath());

    initProjectModule(project, mainModule, sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME), ArtifactSources.MAIN);
    if (workspaceDiscovery) {
      final TaskCollection<Test> testTasks = project.getTasks().withType(Test.class);
      if (!testTasks.isEmpty()) {
        final Map<File, SourceSet> sourceSetsByClassesDir = new HashMap<>();
        sourceSets.forEach(sourceSet -> {
          sourceSet.getOutput().getClassesDirs().forEach(classesDir -> {
            if (classesDir.exists()) {
              sourceSetsByClassesDir.put(classesDir, sourceSet);
            }
          });
        });
        testTasks.forEach(testTask -> {
          if (testTask.getEnabled()) {
            testTask.getTestClassesDirs().forEach(classesDir -> {
              if (classesDir.exists()) {
                final SourceSet sourceSet = sourceSetsByClassesDir.remove(classesDir);
                if (sourceSet != null) {
                  initProjectModule(project, mainModule, sourceSet,
                      sourceSet.getName().equals(SourceSet.TEST_SOURCE_SET_NAME)
                          ? ArtifactSources.TEST
                          : sourceSet.getName());
                }
              }
            });
          }
        });
      }
    }

    final PathList.Builder paths = PathList.builder();
    collectDestinationDirs(mainModule.getMainSources().getSourceDirs(), paths);
    collectDestinationDirs(mainModule.getMainSources().getResourceDirs(), paths);

    return appArtifact.setWorkspaceModule(mainModule).setResolvedPaths(paths.build()).build();
  }

  private static void collectDestinationDirs(Collection<SourceDir> sources, final PathList.Builder paths) {
    for (SourceDir source : sources) {
      final Path path = source.getOutputDir();
      if (paths.contains(path) || !Files.exists(path)) {
        continue;
      }
      paths.add(path);
    }
  }

  private void collectExtensionDependencies(Project project, Configuration deploymentConfiguration,
      ApplicationModelBuilder modelBuilder) {
    final ResolvedConfiguration resolvedConfiguration = deploymentConfiguration.getResolvedConfiguration();
    for (ResolvedArtifact artifact : resolvedConfiguration.getResolvedArtifacts()) {
      addArtifactDependency(project, modelBuilder, artifact);
    }
  }

  private static void addArtifactDependency(Project project, ApplicationModelBuilder modelBuilder, ResolvedArtifact artifact) {
    if (artifact.getId().getComponentIdentifier() instanceof ProjectComponentIdentifier projectComponentIdentifier) {
      var includedBuild = ToolingUtils.includedBuild(project, projectComponentIdentifier.getBuild().getBuildPath());
      final Project projectDependency;
      if (includedBuild != null) {
        projectDependency = ToolingUtils.includedBuildProject((IncludedBuildInternal) includedBuild,
            projectComponentIdentifier.getProjectPath());
      } else {
        projectDependency = project.getRootProject().findProject(projectComponentIdentifier.getProjectPath());
      }
      Objects.requireNonNull(projectDependency,
          () -> "project " + projectComponentIdentifier.getProjectPath() + " should exist");
      SourceSetContainer sourceSets = projectDependency.getExtensions().getByType(SourceSetContainer.class);

      SourceSet mainSourceSet = sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME);
      ResolvedDependencyBuilder dependency = modelBuilder.getDependency(
          toAppDependenciesKey(artifact.getModuleVersion().getId().getGroup(), artifact.getName(), artifact.getClassifier()));
      if (dependency == null) {
        dependency = toDependency(artifact, mainSourceSet);
        modelBuilder.addDependency(dependency);
      }
      dependency.setDeploymentCp();
      dependency.clearFlag(DependencyFlags.RELOADABLE);
    } else if (isDependency(artifact)) {
      ResolvedDependencyBuilder dependency = modelBuilder.getDependency(
          toAppDependenciesKey(artifact.getModuleVersion().getId().getGroup(), artifact.getName(), artifact.getClassifier()));
      if (dependency == null) {
        dependency = toDependency(artifact);
        modelBuilder.addDependency(dependency);
      }
      dependency.setDeploymentCp();
      dependency.clearFlag(DependencyFlags.RELOADABLE);
    }
  }

  private void collectDependencies(ResolvedConfiguration configuration,
      boolean workspaceDiscovery, Project project, ApplicationModelBuilder modelBuilder,
      WorkspaceModule.Mutable workspaceModule) {

    final Set<File> artifactFiles = null;

    configuration.getFirstLevelModuleDependencies().forEach(dependency ->
        collectDependencies(dependency, workspaceDiscovery, project, artifactFiles, new HashSet<>(),
            modelBuilder, workspaceModule,
            (byte) (COLLECT_TOP_EXTENSION_RUNTIME_NODES | COLLECT_DIRECT_DEPS | COLLECT_RELOADABLE_MODULES)));
  }

  private void collectDependencies(org.gradle.api.artifacts.ResolvedDependency resolvedDependency,
      boolean workspaceDiscovery, Project project, Set<File> artifactFiles, Set<ArtifactKey> processedModules,
      ApplicationModelBuilder modelBuilder, WorkspaceModule.Mutable parentModule, byte flags) {
    WorkspaceModule.Mutable projectModule = null;
    for (ResolvedArtifact artifact : resolvedDependency.getModuleArtifacts()) {
      final ArtifactKey artifactKey = toAppDependenciesKey(artifact.getModuleVersion().getId().getGroup(), artifact.getName(),
          artifact.getClassifier());
      if (!isDependency(artifact)) {
        continue;
      }
      var dependencyBuilder = modelBuilder.getDependency(artifactKey);
      if (dependencyBuilder != null) {
        if (isFlagOn(flags, COLLECT_DIRECT_DEPS)) {
          dependencyBuilder.setDirect(true);
        }
        continue;
      }
      final ArtifactCoords dependencyCoords = toArtifactCoords(artifact);
      dependencyBuilder = ResolvedDependencyBuilder.newInstance()
          .setCoords(dependencyCoords)
          .setRuntimeCp()
          .setDeploymentCp();
      if (isFlagOn(flags, COLLECT_DIRECT_DEPS)) {
        dependencyBuilder.setDirect(true);
        flags = clearFlag(flags, COLLECT_DIRECT_DEPS);
      }
      if (parentModule != null) {
        parentModule.addDependency(new ArtifactDependency(dependencyCoords));
      }

      PathCollection paths = null;
      if (workspaceDiscovery && artifact.getId().getComponentIdentifier() instanceof ProjectComponentIdentifier projectComponentIdentifier) {
        Project projectDependency = project.getRootProject().findProject(projectComponentIdentifier.getProjectPath());
        SourceSetContainer sourceSets = projectDependency == null ? null
            : projectDependency.getExtensions().findByType(SourceSetContainer.class);

        final String classifier = artifact.getClassifier();
        if (classifier == null || classifier.isEmpty()) {
          final IncludedBuild includedBuild = ToolingUtils.includedBuild(project.getRootProject(),
              projectComponentIdentifier.getBuild().getBuildPath());
          if (includedBuild != null) {
            final PathList.Builder pathBuilder = PathList.builder();

            if (includedBuild instanceof IncludedBuildInternal) {
              projectDependency = ToolingUtils.includedBuildProject((IncludedBuildInternal) includedBuild,
                  projectComponentIdentifier.getProjectPath());
            }
            if (projectDependency != null) {
              projectModule = initProjectModuleAndBuildPaths(projectDependency, artifact, modelBuilder, dependencyBuilder,
                  pathBuilder, SourceSet.MAIN_SOURCE_SET_NAME, false);
              addSubstitutedProject(pathBuilder, projectDependency.getProjectDir());
            } else {
              addSubstitutedProject(pathBuilder, includedBuild.getProjectDir());
            }
            paths = pathBuilder.build();
          } else if (sourceSets != null) {
            final PathList.Builder pathBuilder = PathList.builder();
            projectModule = initProjectModuleAndBuildPaths(projectDependency, artifact, modelBuilder, dependencyBuilder,
                pathBuilder, SourceSet.MAIN_SOURCE_SET_NAME, false);
            paths = pathBuilder.build();
          }
        } else if (sourceSets != null) {
          if (SourceSet.TEST_SOURCE_SET_NAME.equals(classifier)) {
            final PathList.Builder pathBuilder = PathList.builder();
            projectModule = initProjectModuleAndBuildPaths(projectDependency, artifact, modelBuilder, dependencyBuilder,
                pathBuilder, SourceSet.TEST_SOURCE_SET_NAME, true);
            paths = pathBuilder.build();
          } else if ("test-fixtures".equals(classifier)) {
            final PathList.Builder pathBuilder = PathList.builder();
            projectModule = initProjectModuleAndBuildPaths(projectDependency, artifact, modelBuilder, dependencyBuilder,
                pathBuilder, "testFixtures", true);
            paths = pathBuilder.build();
          }
        }
      }

      dependencyBuilder.setResolvedPaths(paths == null ? PathList.of(artifact.getFile().toPath()) : paths)
          .setWorkspaceModule(projectModule);
      if (processQuarkusDependency(dependencyBuilder, modelBuilder)) {
        if (isFlagOn(flags, COLLECT_TOP_EXTENSION_RUNTIME_NODES)) {
          dependencyBuilder.setFlags(DependencyFlags.TOP_LEVEL_RUNTIME_EXTENSION_ARTIFACT);
          flags = clearFlag(flags, COLLECT_TOP_EXTENSION_RUNTIME_NODES);
        }
        flags = clearFlag(flags, COLLECT_RELOADABLE_MODULES);
      }
      if (!isFlagOn(flags, COLLECT_RELOADABLE_MODULES)) {
        dependencyBuilder.clearFlag(DependencyFlags.RELOADABLE);
      }
      modelBuilder.addDependency(dependencyBuilder);

      if (artifactFiles != null) {
        artifactFiles.add(artifact.getFile());
      }
    }

    processedModules.add(ArtifactKey.ga(resolvedDependency.getModuleGroup(), resolvedDependency.getModuleName()));
    for (org.gradle.api.artifacts.ResolvedDependency child : resolvedDependency.getChildren()) {
      if (!processedModules.contains(new GACT(child.getModuleGroup(), child.getModuleName()))) {
        collectDependencies(child, workspaceDiscovery, project, artifactFiles, processedModules,
            modelBuilder, projectModule, flags);
      }
    }
  }

  private static String toNonNullClassifier(String resolvedClassifier) {
    return resolvedClassifier == null ? ArtifactCoords.DEFAULT_CLASSIFIER : resolvedClassifier;
  }

  private WorkspaceModule.Mutable initProjectModuleAndBuildPaths(final Project project,
      ResolvedArtifact resolvedArtifact, ApplicationModelBuilder appModel, final ResolvedDependencyBuilder appDependency,
      PathList.Builder buildPaths, String sourceName, boolean test) {

    appDependency.setWorkspaceModule().setReloadable();

    final WorkspaceModule.Mutable projectModule = appModel.getOrCreateProjectModule(
            new GAV(resolvedArtifact.getModuleVersion().getId().getGroup(), resolvedArtifact.getName(),
                resolvedArtifact.getModuleVersion().getId().getVersion()),
            project.getProjectDir(),
            project.getBuildDir())
        .setBuildFile(project.getBuildFile().toPath());

    final String classifier = toNonNullClassifier(resolvedArtifact.getClassifier());
    SourceSetContainer sourceSets = project.getExtensions().getByType(SourceSetContainer.class);
    initProjectModule(project, projectModule, sourceSets.findByName(sourceName), classifier);

    collectDestinationDirs(projectModule.getSources(classifier).getSourceDirs(), buildPaths);
    collectDestinationDirs(projectModule.getSources(classifier).getResourceDirs(), buildPaths);

    appModel.addReloadableWorkspaceModule(
        ArtifactKey.of(resolvedArtifact.getModuleVersion().getId().getGroup(), resolvedArtifact.getName(), classifier,
            ArtifactCoords.TYPE_JAR));
    return projectModule;
  }

  private boolean processQuarkusDependency(ResolvedDependencyBuilder artifactBuilder, ApplicationModelBuilder modelBuilder) {
    for (Path artifactPath : artifactBuilder.getResolvedPaths()) {
      if (!Files.exists(artifactPath) || !artifactBuilder.getType().equals(ArtifactCoords.TYPE_JAR)) {
        break;
      }
      if (Files.isDirectory(artifactPath)) {
        return processQuarkusDir(artifactBuilder, artifactPath.resolve(BootstrapConstants.META_INF), modelBuilder);
      } else {
        try (FileSystem artifactFs = ZipUtils.newFileSystem(artifactPath)) {
          return processQuarkusDir(artifactBuilder, artifactFs.getPath(BootstrapConstants.META_INF), modelBuilder);
        } catch (IOException exception) {
          throw new RuntimeException("Failed to process " + artifactPath, exception);
        }
      }
    }
    return false;
  }

  private static boolean processQuarkusDir(ResolvedDependencyBuilder artifactBuilder, Path quarkusDir,
      ApplicationModelBuilder modelBuilder) {
    if (!Files.exists(quarkusDir)) {
      return false;
    }
    final Path quarkusDescriptor = quarkusDir.resolve(BootstrapConstants.DESCRIPTOR_FILE_NAME);
    if (!Files.exists(quarkusDescriptor)) {
      return false;
    }
    final Properties extensionProperties = readDescriptor(quarkusDescriptor);
    if (extensionProperties == null) {
      return false;
    }
    artifactBuilder.setRuntimeExtensionArtifact();
    final String extensionCoords = artifactBuilder.toGACTVString();
    modelBuilder.handleExtensionProperties(extensionProperties, extensionCoords);

    final String providesCapabilities = extensionProperties.getProperty(BootstrapConstants.PROP_PROVIDES_CAPABILITIES);
    if (providesCapabilities != null) {
      modelBuilder.addExtensionCapabilities(CapabilityContract.of(extensionCoords, providesCapabilities, null));
    }
    return true;
  }

  private static Properties readDescriptor(final Path path) {
    if (!Files.exists(path)) {
      return null;
    }
    Properties properties = new Properties();
    try (BufferedReader reader = Files.newBufferedReader(path)) {
      properties.load(reader);
    } catch (IOException exception) {
      throw new UncheckedIOException("Failed to load extension description " + path, exception);
    }
    return properties;
  }

  private static void initProjectModule(Project project, WorkspaceModule.Mutable module, SourceSet sourceSet,
      String classifier) {
    if (sourceSet == null) {
      return;
    }

    final FileCollection allClassesDirs = sourceSet.getOutput().getClassesDirs();
    final List<SourceDir> sourceDirs = new ArrayList<>(1);
    project.getTasks().withType(AbstractCompile.class,
        task -> configureCompileTask(task.getSource(), task.getDestinationDirectory(), allClassesDirs, sourceDirs, task));

    final LinkedHashMap<File, Path> resourceDirs = new LinkedHashMap<>(1);
    final File resourcesOutputDir = sourceSet.getOutput().getResourcesDir();
    project.getTasks().withType(ProcessResources.class, task -> {
      if (!task.getEnabled()) {
        return;
      }
      final FileCollection source = task.getSource();
      if (source.isEmpty()) {
        return;
      }
      if (!task.getDestinationDir().equals(resourcesOutputDir)) {
        return;
      }
      final Path destinationDir = task.getDestinationDir().toPath();
      source.getAsFileTree().visit(visitDetails -> {
        if (visitDetails.getRelativePath().getSegments().length == 1) {
          final File sourceDir = visitDetails.getFile().getParentFile();
          resourceDirs.put(sourceDir, destinationDir);
        }
      });
    });
    if (resourcesOutputDir.exists() && resourceDirs.isEmpty()) {
      sourceSet.getResources().getSrcDirs().forEach(srcDir -> resourceDirs.put(srcDir, resourcesOutputDir.toPath()));
    }
    final List<SourceDir> resources = new ArrayList<>(resourceDirs.size());
    for (Map.Entry<File, Path> entry : resourceDirs.entrySet()) {
      resources.add(new DefaultSourceDir(entry.getKey().toPath(), entry.getValue(), null));
    }
    module.addArtifactSources(new DefaultArtifactSources(classifier, sourceDirs, resources));
  }

  private static void configureCompileTask(FileTree sources, DirectoryProperty destinationDirectory,
      FileCollection allClassesDirs, List<SourceDir> sourceDirs, Task task) {
    if (!task.getEnabled() || sources.isEmpty()) {
      return;
    }
    final File destinationDir = destinationDirectory.getAsFile().get();
    if (!allClassesDirs.contains(destinationDir)) {
      return;
    }
    sources.visit(visitDetails -> {
      if (visitDetails.getRelativePath().getSegments().length == 1) {
        final File sourceDir = visitDetails.getFile().getParentFile();
        sourceDirs.add(new DefaultSourceDir(sourceDir.toPath(), destinationDir.toPath(), null,
            Map.of("compiler", task.getName())));
      }
    });
  }

  private void addSubstitutedProject(PathList.Builder paths, File projectFile) {
    File mainResourceDirectory = new File(projectFile, MAIN_RESOURCES_OUTPUT);
    if (mainResourceDirectory.exists()) {
      paths.add(mainResourceDirectory.toPath());
    }
    File classesOutput = new File(projectFile, CLASSES_OUTPUT);
    File[] languageDirectories = classesOutput.listFiles();
    if (languageDirectories != null) {
      for (File languageDirectory : languageDirectories) {
        if (languageDirectory.isDirectory()) {
          for (File sourceSet : languageDirectory.listFiles()) {
            if (sourceSet.isDirectory() && sourceSet.getName().equals(SourceSet.MAIN_SOURCE_SET_NAME)) {
              paths.add(sourceSet.toPath());
            }
          }
        }
      }
    }
  }

  private static boolean isFlagOn(byte walkingFlags, byte flag) {
    return (walkingFlags & flag) > 0;
  }

  private static byte clearFlag(byte flags, byte flag) {
    if ((flags & flag) > 0) {
      flags ^= flag;
    }
    return flags;
  }

  private static boolean isDependency(ResolvedArtifact artifact) {
    return ArtifactCoords.TYPE_JAR.equalsIgnoreCase(artifact.getExtension()) || "exe".equalsIgnoreCase(artifact.getExtension())
        || artifact.getFile().isDirectory();
  }

  static ResolvedDependencyBuilder toDependency(ResolvedArtifact artifact, int... flags) {
    return toDependency(artifact, PathList.of(artifact.getFile().toPath()), null, flags);
  }

  static ResolvedDependencyBuilder toDependency(ResolvedArtifact artifact, SourceSet sourceSet) {
    PathList.Builder resolvedPathBuilder = PathList.builder();

    for (File classesDir : sourceSet.getOutput().getClassesDirs()) {
      if (classesDir.exists()) {
        resolvedPathBuilder.add(classesDir.toPath());
      }
    }
    File resourceDir = sourceSet.getOutput().getResourcesDir();
    if (resourceDir != null && resourceDir.exists()) {
      resolvedPathBuilder.add(resourceDir.toPath());
    }

    return ResolvedDependencyBuilder.newInstance()
        .setResolvedPaths(resolvedPathBuilder.build())
        .setCoords(toArtifactCoords(artifact));
  }

  static ResolvedDependencyBuilder toDependency(ResolvedArtifact artifact, PathCollection paths, DefaultWorkspaceModule module,
      int... flags) {
    int allFlags = 0;
    for (int flag : flags) {
      allFlags |= flag;
    }
    return ResolvedDependencyBuilder.newInstance()
        .setCoords(toArtifactCoords(artifact))
        .setResolvedPaths(paths)
        .setWorkspaceModule(module)
        .setFlags(allFlags);
  }

  private static ArtifactCoords toArtifactCoords(ResolvedArtifact artifact) {
    final String[] split = artifact.getModuleVersion().toString().split(":");
    return new GACTV(split[0], split[1], artifact.getClassifier(), artifact.getType(), split.length > 2 ? split[2] : null);
  }

  private static ArtifactKey toAppDependenciesKey(String groupId, String artifactId, String classifier) {
    return new GACT(groupId, artifactId, classifier, ArtifactCoords.TYPE_JAR);
  }
}
