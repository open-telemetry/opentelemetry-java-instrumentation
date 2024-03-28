/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.aerospike.v7_0;

import com.aerospike.client.AerospikeException;
import com.aerospike.client.ResultCode;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import javax.annotation.Nullable;

final class AerospikeClientAttributeExtractor
    implements AttributesExtractor<AerospikeRequest, Void> {

  @Override
  public void onStart(
      AttributesBuilder attributes, Context parentContext, AerospikeRequest aerospikeRequest) {
    attributes.put(
        AerospikeSemanticAttributes.AEROSPIKE_NAMESPACE, aerospikeRequest.getNamespace());
    attributes.put(AerospikeSemanticAttributes.AEROSPIKE_SET_NAME, aerospikeRequest.getSet());
    attributes.put(AerospikeSemanticAttributes.AEROSPIKE_USER_KEY, aerospikeRequest.getUserKey());
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      AerospikeRequest aerospikeRequest,
      @Nullable Void unused,
      @Nullable Throwable error) {
    attributes.put(
        AerospikeSemanticAttributes.AEROSPIKE_STATUS, aerospikeRequest.getStatus().name());
    if (error != null) {
      if (error instanceof AerospikeException) {
        AerospikeException aerospikeException = (AerospikeException) error;
        attributes.put(
            AerospikeSemanticAttributes.AEROSPIKE_ERROR_CODE, aerospikeException.getResultCode());
      } else {
        attributes.put(AerospikeSemanticAttributes.AEROSPIKE_ERROR_CODE, ResultCode.CLIENT_ERROR);
      }
    } else {
      attributes.put(AerospikeSemanticAttributes.AEROSPIKE_ERROR_CODE, ResultCode.OK);
      if (aerospikeRequest.getSize() != null) {
        attributes.put(
            AerospikeSemanticAttributes.AEROSPIKE_TRANSFER_SIZE, aerospikeRequest.getSize());
      }
    }
  }
}
