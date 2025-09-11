/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.resources;

import com.google.auto.service.AutoService;
import io.opentelemetry.sdk.extension.incubator.fileconfig.DeclarativeConfigurationCustomizer;
import io.opentelemetry.sdk.extension.incubator.fileconfig.DeclarativeConfigurationCustomizerProvider;
import io.opentelemetry.sdk.extension.incubator.fileconfig.internal.model.ExperimentalResourceDetectionModel;
import io.opentelemetry.sdk.extension.incubator.fileconfig.internal.model.ExperimentalResourceDetectorModel;
import io.opentelemetry.sdk.extension.incubator.fileconfig.internal.model.ResourceModel;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Adds essential resource detectors to the resource model in declarative configuration, if they are
 * not already present.
 */
@AutoService(DeclarativeConfigurationCustomizerProvider.class)
public class ResourceCustomizerProvider implements DeclarativeConfigurationCustomizerProvider {

  // opentelemetry-javaagent-distribution: adds "distro.name" and "distro.version" attributes
  // (DistroComponentProvider in this package)
  private static final List<String> REQUIRED_DETECTORS =
      Collections.singletonList("opentelemetry-javaagent-distribution");

  @Override
  public void customize(DeclarativeConfigurationCustomizer customizer) {
    customizer.addModelCustomizer(
        model -> {
          ResourceModel resource = model.getResource();
          if (resource == null) {
            resource = new ResourceModel();
            model.withResource(resource);
          }
          ExperimentalResourceDetectionModel detectionModel = resource.getDetectionDevelopment();
          if (detectionModel == null) {
            detectionModel = new ExperimentalResourceDetectionModel();
            resource.withDetectionDevelopment(detectionModel);
          }
          List<ExperimentalResourceDetectorModel> detectors =
              Objects.requireNonNull(detectionModel.getDetectors());
          Set<String> names =
              detectors.stream()
                  .flatMap(detector -> detector.getAdditionalProperties().keySet().stream())
                  .collect(Collectors.toSet());

          for (String name : REQUIRED_DETECTORS) {
            if (!names.contains(name)) {
              ExperimentalResourceDetectorModel detector = new ExperimentalResourceDetectorModel();
              detector.getAdditionalProperties().put(name, null);
              // add first (the least precedence)
              // so that the user can add a differently named detector that takes precedence
              detectors.add(0, detector);
            }
          }
          return model;
        });
  }
}
