/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hbase.client.v2_0;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import java.lang.reflect.Method;
import org.apache.hadoop.hbase.shaded.protobuf.generated.ClientProtos;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class HbaseBatchMetadataTest {

  private static final String METADATA_CLASS_NAME =
      "io.opentelemetry.javaagent.instrumentation.hbase.client.v2_0.HbaseBatchMetadata";

  @RegisterExtension
  static final AgentInstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Test
  void derivesMetadataFromEachRequestWhenBatchIsSplitAcrossServers()
      throws ReflectiveOperationException {
    Object getMetadata = createMetadata(request(region(getAction())));
    Object mutationMetadata = createMetadata(request(region(mutationAction(), mutationAction())));

    assertThat(getOperation(getMetadata)).isEqualTo("Get");
    assertThat(getOperationBatchSize(getMetadata)).isNull();
    assertThat(getOperation(mutationMetadata)).isEqualTo("BATCH Mutate");
    assertThat(getOperationBatchSize(mutationMetadata)).isEqualTo(2L);
  }

  @Test
  void derivesMetadataAcrossRegionsInOneRequest() throws ReflectiveOperationException {
    Object metadata = createMetadata(request(region(getAction()), region(mutationAction())));

    assertThat(getOperation(metadata)).isEqualTo("BATCH");
    assertThat(getOperationBatchSize(metadata)).isEqualTo(2L);
  }

  private static Object createMetadata(ClientProtos.MultiRequest request)
      throws ReflectiveOperationException {
    Class.forName("org.apache.hadoop.hbase.ipc.AbstractRpcClient");
    Class<?> metadataClass = Class.forName(METADATA_CLASS_NAME);
    Method create = metadataClass.getMethod("create", ClientProtos.MultiRequest.class);
    return create.invoke(null, request);
  }

  private static String getOperation(Object metadata) throws ReflectiveOperationException {
    return (String) metadata.getClass().getMethod("getOperation").invoke(metadata);
  }

  private static Long getOperationBatchSize(Object metadata) throws ReflectiveOperationException {
    return (Long) metadata.getClass().getMethod("getOperationBatchSize").invoke(metadata);
  }

  private static ClientProtos.MultiRequest request(ClientProtos.RegionAction... regionActions) {
    ClientProtos.MultiRequest.Builder request = ClientProtos.MultiRequest.newBuilder();
    for (ClientProtos.RegionAction regionAction : regionActions) {
      request.addRegionAction(regionAction);
    }
    return request.buildPartial();
  }

  private static ClientProtos.RegionAction region(ClientProtos.Action... actions) {
    ClientProtos.RegionAction.Builder regionAction = ClientProtos.RegionAction.newBuilder();
    for (ClientProtos.Action action : actions) {
      regionAction.addAction(action);
    }
    return regionAction.buildPartial();
  }

  private static ClientProtos.Action getAction() {
    return ClientProtos.Action.newBuilder()
        .setGet(ClientProtos.Get.getDefaultInstance())
        .buildPartial();
  }

  private static ClientProtos.Action mutationAction() {
    return ClientProtos.Action.newBuilder()
        .setMutation(ClientProtos.MutationProto.getDefaultInstance())
        .buildPartial();
  }
}
