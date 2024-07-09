/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v1_11;

import static io.opentelemetry.instrumentation.awssdk.v1_11.AwsExperimentalAttributes.AWS_AGENT_ID;
import static io.opentelemetry.instrumentation.awssdk.v1_11.AwsExperimentalAttributes.AWS_DATASOURCE_ID;
import static io.opentelemetry.instrumentation.awssdk.v1_11.AwsExperimentalAttributes.AWS_KNOWLEDGEBASE_ID;

import io.opentelemetry.api.common.AttributeKey;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

enum AwsBedrockResourceType {
  AGENT_TYPE(AWS_AGENT_ID, RequestAccess::getAgentId),
  DATA_SOURCE_TYPE(AWS_DATASOURCE_ID, RequestAccess::getDataSourceId),
  KNOWLEDGE_BASE_TYPE(AWS_KNOWLEDGEBASE_ID, RequestAccess::getKnowledgeBaseId);

  @SuppressWarnings("ImmutableEnumChecker")
  private final AttributeKey<String> keyAttribute;

  @SuppressWarnings("ImmutableEnumChecker")
  private final Function<Object, String> attributeValueAccessor;

  AwsBedrockResourceType(
      AttributeKey<String> keyAttribute, Function<Object, String> attributeValueAccessor) {
    this.keyAttribute = keyAttribute;
    this.attributeValueAccessor = attributeValueAccessor;
  }

  public AttributeKey<String> getKeyAttribute() {
    return keyAttribute;
  }

  public Function<Object, String> getAttributeValueAccessor() {
    return attributeValueAccessor;
  }

  public static AwsBedrockResourceType getRequestType(String requestClass) {
    return AwsBedrockResourceTypeMap.BEDROCK_REQUEST_MAP.get(requestClass);
  }

  public static AwsBedrockResourceType getResponseType(String responseClass) {
    return AwsBedrockResourceTypeMap.BEDROCK_RESPONSE_MAP.get(responseClass);
  }

  private static class AwsBedrockResourceTypeMap {
    private static final Map<String, AwsBedrockResourceType> BEDROCK_REQUEST_MAP = new HashMap<>();
    private static final Map<String, AwsBedrockResourceType> BEDROCK_RESPONSE_MAP = new HashMap<>();

    // Bedrock request/response mapping
    // We only support operations that are related to the resource and where the context contains
    // the AgentID/DataSourceID/KnowledgeBaseID.
    // AgentID
    private static final List<String> agentRequestClasses =
        Arrays.asList(
            "CreateAgentActionGroupRequest",
            "CreateAgentAliasRequest",
            "DeleteAgentActionGroupRequest",
            "DeleteAgentAliasRequest",
            "DeleteAgentRequest",
            "DeleteAgentVersionRequest",
            "GetAgentActionGroupRequest",
            "GetAgentAliasRequest",
            "GetAgentRequest",
            "GetAgentVersionRequest",
            "ListAgentActionGroupsRequest",
            "ListAgentAliasesRequest",
            "ListAgentKnowledgeBasesRequest",
            "ListAgentVersionsRequest",
            "PrepareAgentRequest",
            "UpdateAgentActionGroupRequest",
            "UpdateAgentAliasRequest",
            "UpdateAgentRequest");
    private static final List<String> agentResponseClasses =
        Arrays.asList(
            "DeleteAgentAliasResult",
            "DeleteAgentResult",
            "DeleteAgentVersionResult",
            "PrepareAgentResult");
    // DataSourceID
    private static final List<String> dataSourceRequestClasses =
        Arrays.asList("DeleteDataSourceRequest", "GetDataSourceRequest", "UpdateDataSourceRequest");
    private static final List<String> dataSourceResponseClasses =
        Arrays.asList("DeleteDataSourceResult");
    // KnowledgeBaseID
    private static final List<String> knowledgeBaseRequestClasses =
        Arrays.asList(
            "AssociateAgentKnowledgeBaseRequest",
            "CreateDataSourceRequest",
            "DeleteKnowledgeBaseRequest",
            "DisassociateAgentKnowledgeBaseRequest",
            "GetAgentKnowledgeBaseRequest",
            "GetKnowledgeBaseRequest",
            "ListDataSourcesRequest",
            "UpdateAgentKnowledgeBaseRequest");
    private static final List<String> knowledgeBaseResponseClasses =
        Arrays.asList("DeleteKnowledgeBaseResult");

    private AwsBedrockResourceTypeMap() {}

    static {
      // Populate the BEDROCK_REQUEST_MAP
      for (String agentRequestClass : agentRequestClasses) {
        BEDROCK_REQUEST_MAP.put(agentRequestClass, AwsBedrockResourceType.AGENT_TYPE);
      }
      for (String dataSourceRequestClass : dataSourceRequestClasses) {
        BEDROCK_REQUEST_MAP.put(dataSourceRequestClass, AwsBedrockResourceType.DATA_SOURCE_TYPE);
      }
      for (String knowledgeBaseRequestClass : knowledgeBaseRequestClasses) {
        BEDROCK_REQUEST_MAP.put(
            knowledgeBaseRequestClass, AwsBedrockResourceType.KNOWLEDGE_BASE_TYPE);
      }

      // Populate the BEDROCK_RESPONSE_MAP
      for (String agentResponseClass : agentResponseClasses) {
        BEDROCK_REQUEST_MAP.put(agentResponseClass, AwsBedrockResourceType.AGENT_TYPE);
      }
      for (String dataSourceResponseClass : dataSourceResponseClasses) {
        BEDROCK_REQUEST_MAP.put(dataSourceResponseClass, AwsBedrockResourceType.DATA_SOURCE_TYPE);
      }
      for (String knowledgeBaseResponseClass : knowledgeBaseResponseClasses) {
        BEDROCK_REQUEST_MAP.put(
            knowledgeBaseResponseClass, AwsBedrockResourceType.KNOWLEDGE_BASE_TYPE);
      }
    }
  }
}
