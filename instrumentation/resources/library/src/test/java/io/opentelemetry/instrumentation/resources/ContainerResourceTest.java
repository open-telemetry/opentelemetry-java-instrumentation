/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.resources;

import static io.opentelemetry.semconv.incubating.ContainerIncubatingAttributes.CONTAINER_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.opentelemetry.sdk.resources.Resource;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ContainerResourceTest {

  public static final String TEST_CONTAINER_ID = "abcdef123123deadbeef";
  @Mock CgroupV1ContainerIdExtractor v1;
  @Mock CgroupV2ContainerIdExtractor v2;

  @Test
  void v1Success() {
    when(v1.extractContainerId()).thenReturn(Optional.of(TEST_CONTAINER_ID));
    ContainerResource containerResource = new ContainerResource(v1, v2);
    Resource resource = containerResource.buildResource();
    assertThat(resource.getAttribute(CONTAINER_ID)).isEqualTo(TEST_CONTAINER_ID);
    verifyNoInteractions(v2);
  }

  @Test
  void v2Success() {
    when(v1.extractContainerId()).thenReturn(Optional.empty());
    when(v2.extractContainerId()).thenReturn(Optional.of(TEST_CONTAINER_ID));
    ContainerResource containerResource = new ContainerResource(v1, v2);
    Resource resource = containerResource.buildResource();
    assertThat(resource.getAttribute(CONTAINER_ID)).isEqualTo(TEST_CONTAINER_ID);
  }

  @Test
  void bothVersionsFail() {
    when(v1.extractContainerId()).thenReturn(Optional.empty());
    when(v2.extractContainerId()).thenReturn(Optional.empty());
    ContainerResource containerResource = new ContainerResource(v1, v2);
    Resource resource = containerResource.buildResource();
    assertThat(resource).isSameAs(Resource.empty());
  }
}
