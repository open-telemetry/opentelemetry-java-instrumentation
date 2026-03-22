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

package io.quarkus.gradle.dependency;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ResolvedArtifact;

import io.quarkus.gradle.tooling.dependency.DependencyUtils;
import io.quarkus.gradle.tooling.dependency.ExtensionDependency;
import io.quarkus.maven.dependency.ArtifactCoords;
import io.quarkus.maven.dependency.ArtifactKey;
import io.quarkus.maven.dependency.GACT;
import io.quarkus.runtime.LaunchMode;

public class ConditionalDependenciesEnabler {

  private final Map<GACT, Set<ExtensionDependency<?>>> featureVariants = new HashMap<>();
  private final Map<ModuleVersionIdentifier, ExtensionDependency<?>> allExtensions = new HashMap<>();
  private final Project project;
  private final Configuration enforcedPlatforms;
  private final Set<ArtifactKey> existingArtifacts = new HashSet<>();
  private final List<Dependency> unsatisfiedConditionalDeps = new ArrayList<>();

  public ConditionalDependenciesEnabler(Project project, LaunchMode mode, Configuration platforms) {
    this.project = project;
    this.enforcedPlatforms = platforms;

    Configuration baseRuntimeConfig = project.getConfigurations()
        .getByName(ApplicationDeploymentClasspathBuilder.getBaseRuntimeConfigName(mode));

    if (!baseRuntimeConfig.getIncoming().getDependencies().isEmpty()) {
      collectConditionalDependencies(baseRuntimeConfig.getResolvedConfiguration().getResolvedArtifacts());
      while (!unsatisfiedConditionalDeps.isEmpty()) {
        boolean satisfiedConditionalDeps = false;
        final int originalUnsatisfiedCount = unsatisfiedConditionalDeps.size();
        int index = 0;
        while (index < unsatisfiedConditionalDeps.size()) {
          final Dependency conditionalDep = unsatisfiedConditionalDeps.get(index);
          if (resolveConditionalDependency(conditionalDep)) {
            satisfiedConditionalDeps = true;
            unsatisfiedConditionalDeps.remove(index);
          } else {
            ++index;
          }
        }
        if (!satisfiedConditionalDeps && unsatisfiedConditionalDeps.size() == originalUnsatisfiedCount) {
          break;
        }
      }
      reset();
    }
  }

  public Collection<ExtensionDependency<?>> getAllExtensions() {
    return allExtensions.values();
  }

  private void reset() {
    featureVariants.clear();
    existingArtifacts.clear();
    unsatisfiedConditionalDeps.clear();
  }

  private void collectConditionalDependencies(Set<ResolvedArtifact> runtimeArtifacts) {
    for (ResolvedArtifact artifact : runtimeArtifacts) {
      existingArtifacts.add(getKey(artifact));
      ExtensionDependency<?> extension = DependencyUtils.getExtensionInfoOrNull(project, artifact);
      if (extension != null) {
        allExtensions.put(extension.getExtensionId(), extension);
        for (Dependency conditionalDep : extension.getConditionalDependencies()) {
          if (!exists(conditionalDep)) {
            queueConditionalDependency(extension, conditionalDep);
          }
        }
      }
    }
  }

  private boolean resolveConditionalDependency(Dependency conditionalDep) {
    final Configuration conditionalDeps = createConditionalDependenciesConfiguration(project, conditionalDep);
    Set<ResolvedArtifact> resolvedArtifacts = conditionalDeps.getResolvedConfiguration().getResolvedArtifacts();

    boolean satisfied = false;
    for (ResolvedArtifact artifact : resolvedArtifacts) {
      if (conditionalDep.getName().equals(artifact.getName())
          && conditionalDep.getVersion().equals(artifact.getModuleVersion().getId().getVersion())
          && artifact.getModuleVersion().getId().getGroup().equals(conditionalDep.getGroup())) {
        final ExtensionDependency<?> extensionDependency = DependencyUtils.getExtensionInfoOrNull(project, artifact);
        if (extensionDependency != null && (extensionDependency.getDependencyConditions().isEmpty()
            || exist(extensionDependency.getDependencyConditions()))) {
          satisfied = true;
          enableConditionalDependency(extensionDependency.getExtensionId());
          break;
        }
      }
    }

    if (!satisfied) {
      return false;
    }

    for (ResolvedArtifact artifact : resolvedArtifacts) {
      existingArtifacts.add(getKey(artifact));
      ExtensionDependency<?> extensionDependency = DependencyUtils.getExtensionInfoOrNull(project, artifact);
      if (extensionDependency == null) {
        continue;
      }
      extensionDependency.setConditional(true);
      allExtensions.put(extensionDependency.getExtensionId(), extensionDependency);
      for (Dependency dependency : extensionDependency.getConditionalDependencies()) {
        if (!exists(dependency)) {
          queueConditionalDependency(extensionDependency, dependency);
        }
      }
    }
    return satisfied;
  }

  private void queueConditionalDependency(ExtensionDependency<?> extension, Dependency conditionalDep) {
    featureVariants.computeIfAbsent(getFeatureKey(conditionalDep), key -> {
      unsatisfiedConditionalDeps.add(conditionalDep);
      return new HashSet<>();
    }).add(extension);
  }

  private Configuration createConditionalDependenciesConfiguration(Project project, Dependency conditionalDep) {
    Configuration conditionalDepConfiguration = project.getConfigurations().detachedConfiguration();
    enforcedPlatforms.getExcludeRules().forEach(rule -> {
      conditionalDepConfiguration.exclude(Map.of(
          "group", rule.getGroup(),
          "module", rule.getModule()));
    });
    for (Dependency platformDependency : enforcedPlatforms.getAllDependencies()) {
      conditionalDepConfiguration.getDependencies().add(platformDependency);
    }
    conditionalDepConfiguration.getDependencies().add(conditionalDep);
    return conditionalDepConfiguration;
  }

  private void enableConditionalDependency(ModuleVersionIdentifier dependency) {
    final Set<ExtensionDependency<?>> extensions = featureVariants.remove(getFeatureKey(dependency));
    if (extensions == null) {
      return;
    }
    extensions.forEach(extension -> extension.importConditionalDependency(project.getDependencies(), dependency));
  }

  private boolean exist(List<ArtifactKey> dependencies) {
    return existingArtifacts.containsAll(dependencies);
  }

  private boolean exists(Dependency dependency) {
    return existingArtifacts.contains(ArtifactKey.of(dependency.getGroup(), dependency.getName(), null, ArtifactCoords.TYPE_JAR));
  }

  public boolean exists(ExtensionDependency dependency) {
    return existingArtifacts.contains(ArtifactKey.of(dependency.getGroup(), dependency.getName(), null, ArtifactCoords.TYPE_JAR));
  }

  private static GACT getFeatureKey(ModuleVersionIdentifier version) {
    return new GACT(version.getGroup(), version.getName());
  }

  private static GACT getFeatureKey(Dependency version) {
    return new GACT(version.getGroup(), version.getName());
  }

  private static ArtifactKey getKey(ResolvedArtifact artifact) {
    return ArtifactKey.of(artifact.getModuleVersion().getId().getGroup(), artifact.getName(), artifact.getClassifier(),
        artifact.getType());
  }
}
