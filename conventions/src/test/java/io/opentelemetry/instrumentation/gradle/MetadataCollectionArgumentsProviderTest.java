/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.gradle;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Collections;
import org.gradle.api.Project;
import org.gradle.api.tasks.testing.Test;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MetadataCollectionArgumentsProviderTest {
  @ParameterizedTest
  @CsvSource({
    ":module:test, true",
    ":module:testExperimental, true",
    ":module:version37Test, true",
    ":module:latestDepTest, true",
    ":other:test, false",
    ":module:testDisabled, false"
  })
  void selectsOnlyRequestedTasks(String taskPath, boolean expected) {
    MetadataCollectionArgumentsProvider provider =
        new MetadataCollectionArgumentsProvider(
            true,
            taskPath,
            Arrays.asList(
                ":module:test",
                ":module:testExperimental",
                ":module:version37Test",
                ":module:latestDepTest"));

    assertThat(provider.asArguments()).containsExactly("-DcollectMetadata=" + expected);
  }

  @ParameterizedTest
  @CsvSource({
    "check",
    ":module:check",
    ":instrumentation:test",
    "test",
    "module:test",
    ":module:te",
    ":mod:test"
  })
  void doesNotCollectForAggregateOrInexactSelectors(String requestedTask) {
    MetadataCollectionArgumentsProvider provider =
        new MetadataCollectionArgumentsProvider(
            true, ":module:test", Collections.singletonList(requestedTask));

    assertThat(provider.asArguments()).containsExactly("-DcollectMetadata=false");
  }

  @ParameterizedTest
  @CsvSource({":module:testExperimental", ":module:latestDepTest", ":dependency:test"})
  void doesNotCollectDependencyTasks(String taskPath) {
    MetadataCollectionArgumentsProvider provider =
        new MetadataCollectionArgumentsProvider(
            true, taskPath, Collections.singletonList(":module:test"));

    assertThat(provider.asArguments()).containsExactly("-DcollectMetadata=false");
  }

  @org.junit.jupiter.api.Test
  void collectionMustBeEnabled() {
    MetadataCollectionArgumentsProvider provider =
        new MetadataCollectionArgumentsProvider(
            false, ":module:test", Collections.singletonList(":module:test"));

    assertThat(provider.asArguments()).containsExactly("-DcollectMetadata=false");
  }

  @org.junit.jupiter.api.Test
  void copiedSystemPropertiesDoNotCopyCollectionSelection() {
    Project project = ProjectBuilder.builder().build();
    Test sourceTask = project.getTasks().register("test", Test.class).get();
    Test variantTask = project.getTasks().register("testExperimental", Test.class).get();
    sourceTask
        .getJvmArgumentProviders()
        .add(
            new MetadataCollectionArgumentsProvider(
                true, sourceTask.getPath(), Collections.singletonList(":test")));
    variantTask
        .getJvmArgumentProviders()
        .add(
            new MetadataCollectionArgumentsProvider(
                true, variantTask.getPath(), Collections.singletonList(":test")));
    sourceTask.systemProperty("metadataConfig", "otel.semconv-stability.preview=rpc");

    variantTask.setJvmArgs(sourceTask.getJvmArgs());
    variantTask.setSystemProperties(sourceTask.getSystemProperties());

    assertThat(sourceTask.getAllJvmArgs()).contains("-DcollectMetadata=true");
    assertThat(variantTask.getAllJvmArgs())
        .contains("-DcollectMetadata=false", "-DmetadataConfig=otel.semconv-stability.preview=rpc")
        .doesNotContain("-DcollectMetadata=true");
  }
}
