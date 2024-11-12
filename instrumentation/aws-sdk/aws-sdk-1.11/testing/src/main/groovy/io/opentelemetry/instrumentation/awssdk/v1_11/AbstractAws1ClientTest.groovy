/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v1_11

import com.amazonaws.AmazonClientException
import com.amazonaws.ClientConfiguration
import com.amazonaws.SDKGlobalConfiguration
import com.amazonaws.SdkClientException
import com.amazonaws.auth.AWSCredentialsProviderChain
import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.AnonymousAWSCredentials
import com.amazonaws.auth.EnvironmentVariableCredentialsProvider
import com.amazonaws.auth.InstanceProfileCredentialsProvider
import com.amazonaws.auth.SystemPropertiesCredentialsProvider
import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.client.builder.AwsClientBuilder
import com.amazonaws.retry.PredefinedRetryPolicies
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder
import com.amazonaws.services.dynamodbv2.model.CreateTableRequest
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder
import com.amazonaws.services.kinesis.AmazonKinesisClientBuilder
import com.amazonaws.services.kinesis.model.DeleteStreamRequest
import com.amazonaws.services.rds.AmazonRDSClientBuilder
import com.amazonaws.services.rds.model.DeleteOptionGroupRequest
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import com.amazonaws.services.bedrockagent.AWSBedrockAgentClientBuilder
import com.amazonaws.services.bedrockagent.model.GetAgentRequest
import com.amazonaws.services.bedrockagent.model.GetKnowledgeBaseRequest
import com.amazonaws.services.bedrockagent.model.GetDataSourceRequest
import com.amazonaws.services.bedrock.AmazonBedrockClientBuilder
import com.amazonaws.services.bedrock.model.GetGuardrailRequest
import com.amazonaws.services.bedrockruntime.AmazonBedrockRuntimeClientBuilder
import com.amazonaws.services.bedrockruntime.model.InvokeModelRequest
import com.amazonaws.services.stepfunctions.AWSStepFunctionsClientBuilder
import com.amazonaws.services.stepfunctions.model.DescribeStateMachineRequest
import com.amazonaws.services.stepfunctions.model.DescribeActivityRequest
import com.amazonaws.services.sns.AmazonSNSClientBuilder
import com.amazonaws.services.sns.model.PublishRequest
import com.amazonaws.services.secretsmanager.AWSSecretsManagerClientBuilder
import com.amazonaws.services.secretsmanager.model.CreateSecretRequest
import com.amazonaws.services.lambda.AWSLambdaClientBuilder
import com.amazonaws.services.lambda.model.GetEventSourceMappingRequest
import com.amazonaws.services.lambda.model.GetFunctionRequest
import io.opentelemetry.api.trace.Span
import io.opentelemetry.instrumentation.test.InstrumentationSpecification
import io.opentelemetry.semconv.SemanticAttributes
import io.opentelemetry.testing.internal.armeria.common.HttpResponse
import io.opentelemetry.testing.internal.armeria.common.HttpStatus
import io.opentelemetry.testing.internal.armeria.common.MediaType
import io.opentelemetry.testing.internal.armeria.testing.junit5.server.mock.MockWebServerExtension
import spock.lang.Shared
import spock.lang.Unroll

import java.time.Duration
import java.nio.charset.StandardCharsets

import static io.opentelemetry.api.trace.SpanKind.CLIENT
import static io.opentelemetry.api.trace.SpanKind.PRODUCER
import static io.opentelemetry.api.trace.StatusCode.ERROR
import static io.opentelemetry.instrumentation.test.utils.PortUtils.UNUSABLE_PORT

abstract class AbstractAws1ClientTest extends InstrumentationSpecification {

  abstract <T> T configureClient(T client)

  static final CREDENTIALS_PROVIDER_CHAIN = new AWSCredentialsProviderChain(
    new EnvironmentVariableCredentialsProvider(),
    new SystemPropertiesCredentialsProvider(),
    new ProfileCredentialsProvider(),
    new InstanceProfileCredentialsProvider())

  @Shared
  def credentialsProvider = new AWSStaticCredentialsProvider(new AnonymousAWSCredentials())

  @Shared
  def server = new MockWebServerExtension()

  @Shared
  def endpoint

  def setupSpec() {
    System.setProperty(SDKGlobalConfiguration.ACCESS_KEY_SYSTEM_PROPERTY, "my-access-key")
    System.setProperty(SDKGlobalConfiguration.SECRET_KEY_SYSTEM_PROPERTY, "my-secret-key")
    server.start()
    endpoint = new AwsClientBuilder.EndpointConfiguration("${server.httpUri()}", "us-west-2")
  }

  def cleanupSpec() {
    System.clearProperty(SDKGlobalConfiguration.ACCESS_KEY_SYSTEM_PROPERTY)
    System.clearProperty(SDKGlobalConfiguration.SECRET_KEY_SYSTEM_PROPERTY)
    server.stop()
  }

  def setup() {
    server.beforeTestExecution(null)
  }

  @Unroll
  def "send #operation request with mocked response"() {
    setup:
    server.enqueue(HttpResponse.of(HttpStatus.OK, MediaType.PLAIN_TEXT_UTF_8, body))

    when:
    def client = configureClient(clientBuilder).withEndpointConfiguration(endpoint).withCredentials(credentialsProvider).build()
    def response = call.call(client)

    then:
    response != null

    client.requestHandler2s != null
    client.requestHandler2s.find { it.getClass().getSimpleName() == "TracingRequestHandler" } != null

    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "$service.$operation"
          kind operation == "SendMessage" ? PRODUCER : CLIENT
          hasNoParent()
          attributes {
            "$SemanticAttributes.HTTP_URL" "${server.httpUri()}"
            "$SemanticAttributes.HTTP_METHOD" "$method"
            "$SemanticAttributes.HTTP_STATUS_CODE" 200
            "$SemanticAttributes.HTTP_REQUEST_CONTENT_LENGTH" { it == null || it instanceof Long }
            "$SemanticAttributes.HTTP_RESPONSE_CONTENT_LENGTH" Long
            "$SemanticAttributes.NET_PROTOCOL_NAME" "http"
            "$SemanticAttributes.NET_PROTOCOL_VERSION" "1.1"
            "$SemanticAttributes.NET_PEER_PORT" server.httpPort()
            "$SemanticAttributes.NET_PEER_NAME" "127.0.0.1"
            "$SemanticAttributes.RPC_SYSTEM" "aws-api"
            "$SemanticAttributes.RPC_SERVICE" { it.contains(service) }
            "$SemanticAttributes.RPC_METHOD" "${operation}"
            "aws.endpoint" "${server.httpUri()}"
            "aws.agent" "java-aws-sdk"
            for (def addedTag : additionalAttributes) {
              "$addedTag.key" "$addedTag.value"
            }
          }
        }
      }
    }

    def request = server.takeRequest()
    request.request().headers().get("X-Amzn-Trace-Id") != null
    request.request().headers().get("traceparent") == null

    where:
    service      | operation           | method | path                  | clientBuilder                                                     | call                                                                            | additionalAttributes              | body
    "S3"         | "CreateBucket"      | "PUT"  | "/testbucket/"        | AmazonS3ClientBuilder.standard().withPathStyleAccessEnabled(true) | { c -> c.createBucket("testbucket") }                                           | ["aws.bucket.name": "testbucket"] | ""
    "S3"         | "GetObject"         | "GET"  | "/someBucket/someKey" | AmazonS3ClientBuilder.standard().withPathStyleAccessEnabled(true) | { c -> c.getObject("someBucket", "someKey") }                                   | ["aws.bucket.name": "someBucket"] | ""
    "DynamoDBv2" | "CreateTable"       | "POST" | "/"                   | AmazonDynamoDBClientBuilder.standard()                            | { c -> c.createTable(new CreateTableRequest("sometable", null)) }               | ["aws.table.name": "sometable"]   | ""
    "Kinesis"    | "DeleteStream"      | "POST" | "/"                   | AmazonKinesisClientBuilder.standard()                             | { c -> c.deleteStream(new DeleteStreamRequest().withStreamName("somestream")) } | ["aws.stream.name": "somestream"] | ""
    // Some users may implicitly subclass the request object to mimic a fluent style
    "Kinesis"    | "DeleteStream"      | "POST" | "/"                   | AmazonKinesisClientBuilder.standard()                             | { c ->
      c.deleteStream(new DeleteStreamRequest() {
        {
          withStreamName("somestream")
        }
      })
    }                                                                                                                                                                                                                         | ["aws.stream.name": "somestream"] | ""
    "EC2"        | "AllocateAddress"   | "POST" | "/"                   | AmazonEC2ClientBuilder.standard()                                 | { c -> c.allocateAddress() }                                                    | [:]                               | """
        <AllocateAddressResponse xmlns="http://ec2.amazonaws.com/doc/2016-11-15/">
           <requestId>59dbff89-35bd-4eac-99ed-be587EXAMPLE</requestId>
           <publicIp>192.0.2.1</publicIp>
           <domain>standard</domain>
        </AllocateAddressResponse>
      """
    "RDS"        | "DeleteOptionGroup" | "POST" | "/"                   | AmazonRDSClientBuilder.standard()                                 | { c -> c.deleteOptionGroup(new DeleteOptionGroupRequest()) }                    | [:]                               | """
        <DeleteOptionGroupResponse xmlns="http://rds.amazonaws.com/doc/2014-09-01/">
          <ResponseMetadata>
            <RequestId>0ac9cda2-bbf4-11d3-f92b-31fa5e8dbc99</RequestId>
          </ResponseMetadata>
        </DeleteOptionGroupResponse>
      """
    "Bedrock"    | "GetGuardrail"      | "GET" | "/"                   |
    AmazonBedrockClientBuilder.standard()                             | { c -> c.getGuardrail(new GetGuardrailRequest().withGuardrailIdentifier("guardrailId")) } | ["aws.bedrock.guardrail.id":"guardrailId", "aws.bedrock.guardrail.arn": "guardrailArn"] | """
        {
           "blockedInputMessaging": "string",
           "blockedOutputsMessaging": "string",
           "contentPolicy": {},
           "createdAt": "2024-06-12T18:31:45Z",
           "description": "string",
           "guardrailArn": "guardrailArn",
           "guardrailId": "guardrailId",
           "kmsKeyArn": "string",
           "name": "string",
           "sensitiveInformationPolicy": {},
           "status": "READY",
           "topicPolicy": {
              "topics": [
                 {
                    "definition": "string",
                    "examples": [ "string" ],
                    "name": "string",
                    "type": "string"
                 }
              ]
           },
           "updatedAt": "2024-06-12T18:31:48Z",
           "version": "DRAFT",
           "wordPolicy": {}
        }
      """
    "AWSBedrockAgent"    | "GetAgent"      | "GET" | "/"                   | AWSBedrockAgentClientBuilder.standard()                             | { c -> c.getAgent(new GetAgentRequest().withAgentId("agentId")) } | ["aws.bedrock.agent.id": "agentId"] | ""
    "AWSBedrockAgent"    | "GetKnowledgeBase"      | "GET" | "/"                   | AWSBedrockAgentClientBuilder.standard()                             | { c -> c.getKnowledgeBase(new GetKnowledgeBaseRequest().withKnowledgeBaseId("knowledgeBaseId")) } | ["aws.bedrock.knowledge_base.id": "knowledgeBaseId"] | ""
    "AWSBedrockAgent"    | "GetDataSource"      | "GET" | "/"                   | AWSBedrockAgentClientBuilder.standard()                             | { c -> c.getDataSource(new GetDataSourceRequest().withDataSourceId("datasourceId").withKnowledgeBaseId("knowledgeBaseId")) } | ["aws.bedrock.data_source.id": "datasourceId"] | ""
    "BedrockRuntime" | "InvokeModel" | "POST" | "/" |
        AmazonBedrockRuntimeClientBuilder.standard() |
        { c ->
          c.invokeModel(
              new InvokeModelRequest()
                  .withModelId("ai21.jamba-1-5-mini-v1:0")
                  .withBody(StandardCharsets.UTF_8.encode('''
            {
              "messages": [{
                "role": "user",
                "message": "Which LLM are you?"
              }],
              "max_tokens": 1000,
              "top_p": 0.8,
              "temperature": 0.7
            }
          '''))
          )
        } |
        [
            "gen_ai.request.model": "ai21.jamba-1-5-mini-v1:0",
            "gen_ai.system": "aws_bedrock",
            "gen_ai.request.max_tokens": "1000",
            "gen_ai.request.temperature": "0.7",
            "gen_ai.request.top_p": "0.8",
            "gen_ai.response.finish_reasons": "[stop]",
            "gen_ai.usage.input_tokens": "5",
            "gen_ai.usage.output_tokens": "42"
        ] |
        '''
    {
      "choices": [{
        "finish_reason": "stop"
      }],
      "usage": {
        "prompt_tokens": 5,
        "completion_tokens": 42
      }
    }
    '''
    "BedrockRuntime" | "InvokeModel" | "POST" | "/" |
        AmazonBedrockRuntimeClientBuilder.standard() |
        { c ->
          c.invokeModel(
              new InvokeModelRequest()
                  .withModelId("amazon.titan-text-premier-v1:0")
                  .withBody(StandardCharsets.UTF_8.encode('''
            {
              "inputText": "Hello, world!",
              "textGenerationConfig": {
                "temperature": 0.7,
                "topP": 0.9,
                "maxTokenCount": 100,
                "stopSequences": ["END"]
              }
            }
          '''))
          )
        } |
        [
            "gen_ai.request.model": "amazon.titan-text-premier-v1:0",
            "gen_ai.system": "aws_bedrock",
            "gen_ai.request.max_tokens": "100",
            "gen_ai.request.temperature": "0.7",
            "gen_ai.request.top_p": "0.9",
            "gen_ai.response.finish_reasons": "[stop]",
            "gen_ai.usage.input_tokens": "5",
            "gen_ai.usage.output_tokens": "42"
        ] |
        '''
    {
      "inputTextTokenCount": 5,
      "results": [
        {
          "tokenCount": 42,
          "outputText": "Hi! I'm Titan, an AI assistant. How can I help you today?",
          "completionReason": "stop"
        }
      ]
    }
    '''
    "BedrockRuntime" | "InvokeModel" | "POST" | "/" |
        AmazonBedrockRuntimeClientBuilder.standard() |
        { c ->
          c.invokeModel(
              new InvokeModelRequest()
                  .withModelId("anthropic.claude-3-5-sonnet-20241022-v2:0")
                  .withBody(StandardCharsets.UTF_8.encode('''
            {
              "anthropic_version": "bedrock-2023-05-31",
              "messages": [{
                "role": "user",
                "content": "Hello, world"
              }],
              "max_tokens": 100,
              "temperature": 0.7,
              "top_p": 0.9
            }
          '''))
          )
        } |
        [
            "gen_ai.request.model": "anthropic.claude-3-5-sonnet-20241022-v2:0",
            "gen_ai.system": "aws_bedrock",
            "gen_ai.request.max_tokens": "100",
            "gen_ai.request.temperature": "0.7",
            "gen_ai.request.top_p": "0.9",
            "gen_ai.response.finish_reasons": "[end_turn]",
            "gen_ai.usage.input_tokens": "2095",
            "gen_ai.usage.output_tokens": "503"
        ] |
        '''
    {
      "stop_reason": "end_turn",
      "usage": {
        "input_tokens": 2095,
        "output_tokens": 503
      }
    }
    '''
    "BedrockRuntime" | "InvokeModel" | "POST" | "/" |
        AmazonBedrockRuntimeClientBuilder.standard() |
        { c ->
          c.invokeModel(
              new InvokeModelRequest()
                  .withModelId("meta.llama3-70b-instruct-v1:0")
                  .withBody(StandardCharsets.UTF_8.encode('''
            {
              "prompt": "<|begin_of_text|><|start_header_id|>user<|end_header_id|>\\\\nDescribe the purpose of a 'hello world' program in one line. <|eot_id|>\\\\n<|start_header_id|>assistant<|end_header_id|>\\\\n",
              "max_gen_len": 128,
              "temperature": 0.1,
              "top_p": 0.9
            }
          '''))
          )
        } |
        [
            "gen_ai.request.model": "meta.llama3-70b-instruct-v1:0",
            "gen_ai.system": "aws_bedrock",
            "gen_ai.request.max_tokens": "128",
            "gen_ai.request.temperature": "0.1",
            "gen_ai.request.top_p": "0.9",
            "gen_ai.response.finish_reasons": "[stop]",
            "gen_ai.usage.input_tokens": "2095",
            "gen_ai.usage.output_tokens": "503"
        ] |
        '''
    {
      "prompt_token_count": 2095,
      "generation_token_count": 503,
      "stop_reason": "stop"
    }
    '''
    "BedrockRuntime" | "InvokeModel" | "POST" | "/" |
        AmazonBedrockRuntimeClientBuilder.standard() |
        { c ->
          c.invokeModel(
              new InvokeModelRequest()
                  .withModelId("cohere.command-r-v1:0")
                  .withBody(StandardCharsets.UTF_8.encode('''
            {
              "message": "Convince me to write a LISP interpreter in one line.",
              "temperature": 0.8,
              "max_tokens": 4096,
              "p": 0.45 
            }
          '''))
          )
        } |
        [
            "gen_ai.request.model": "cohere.command-r-v1:0",
            "gen_ai.system": "aws_bedrock",
            "gen_ai.request.max_tokens": "4096",
            "gen_ai.request.temperature": "0.8",
            "gen_ai.request.top_p": "0.45",
            "gen_ai.response.finish_reasons": "[COMPLETE]",
            "gen_ai.usage.input_tokens": "9",
            "gen_ai.usage.output_tokens": "2"
        ] |
        '''
    {
      "text": "test-output",
      "finish_reason": "COMPLETE"
    }
    '''
    "BedrockRuntime" | "InvokeModel" | "POST" | "/" |
        AmazonBedrockRuntimeClientBuilder.standard() |
        { c ->
          c.invokeModel(
              new InvokeModelRequest()
                  .withModelId("mistral.mistral-large-2402-v1:0")
                  .withBody(StandardCharsets.UTF_8.encode('''
        {
            "prompt": "<s>[INST] Describe the difference between a compiler and interpreter in one line. [/INST]\\\\n",
            "max_tokens": 4096,
            "temperature": 0.75,
            "top_p": 0.25
        }
    '''))
          )
        } |
        [
            "gen_ai.request.model": "mistral.mistral-large-2402-v1:0",
            "gen_ai.system": "aws_bedrock",
            "gen_ai.request.max_tokens": "4096",
            "gen_ai.request.temperature": "0.75",
            "gen_ai.request.top_p": "0.25",
            "gen_ai.response.finish_reasons": "[stop]",
            "gen_ai.usage.input_tokens": "16",
            "gen_ai.usage.output_tokens": "2"
        ] |
        '''
        {
          "outputs": [{
            "text": "test-output",
            "stop_reason": "stop"
          }]
        }
    '''
    "AWSStepFunctions" | "DescribeStateMachine" | "POST" | "/" | AWSStepFunctionsClientBuilder.standard()
    | { c -> c.describeStateMachine(new DescribeStateMachineRequest().withStateMachineArn("stateMachineArn")) }
    | ["aws.stepfunctions.state_machine.arn": "stateMachineArn"]
    | ""
    "AWSStepFunctions" | "DescribeActivity" | "POST" | "/" | AWSStepFunctionsClientBuilder.standard()
    | { c -> c.describeActivity(new DescribeActivityRequest().withActivityArn("activityArn")) }
    | ["aws.stepfunctions.activity.arn": "activityArn"]
    | ""
    "SNS" | "Publish" | "POST" | "/" | AmazonSNSClientBuilder.standard()
    | { c -> c.publish(new PublishRequest().withMessage("message").withTopicArn("topicArn")) }
    | ["aws.sns.topic.arn": "topicArn"]
    | """
      <PublishResponse xmlns="https://sns.amazonaws.com/doc/2010-03-31/">
          <PublishResult>
              <MessageId>567910cd-659e-55d4-8ccb-5aaf14679dc0</MessageId>
          </PublishResult>
          <ResponseMetadata>
              <RequestId>d74b8436-ae13-5ab4-a9ff-ce54dfea72a0</RequestId>
          </ResponseMetadata>
      </PublishResponse>
      """
    "AWSSecretsManager" | "CreateSecret" | "POST" | "/" | AWSSecretsManagerClientBuilder.standard()
    | { c -> c.createSecret(new CreateSecretRequest().withName("secretName").withSecretString("secretValue")) }
    | ["aws.secretsmanager.secret.arn": "arn:aws:secretsmanager:us-west-2:123456789012:secret:MyTestDatabaseSecret-a1b2c3"]
    | """
      {
        "ARN": "arn:aws:secretsmanager:us-west-2:123456789012:secret:MyTestDatabaseSecret-a1b2c3",
        "Name":"MyTestDatabaseSecret",
        "VersionId": "EXAMPLE1-90ab-cdef-fedc-ba987SECRET1"
      }
    """
    "AWSLambda" | "GetEventSourceMapping" | "GET" | "/" | AWSLambdaClientBuilder.standard()
    | { c -> c.getEventSourceMapping(new GetEventSourceMappingRequest().withUUID("uuid")) }
    | ["aws.lambda.resource_mapping.id": "uuid"]
    | ""
    "AWSLambda" | "GetFunction" | "GET" | "/" | AWSLambdaClientBuilder.standard()
    | { c-> c.getFunction(new GetFunctionRequest().withFunctionName("functionName")) }
    | ["aws.lambda.function.name": "functionName"]
    | ""
  }

  def "send #operation request to closed port"() {
    setup:
    server.enqueue(HttpResponse.of(HttpStatus.OK, MediaType.PLAIN_TEXT_UTF_8, body))

    when:
    def client = configureClient(clientBuilder)
      .withCredentials(CREDENTIALS_PROVIDER_CHAIN)
      .withClientConfiguration(new ClientConfiguration().withRetryPolicy(PredefinedRetryPolicies.getDefaultRetryPolicyWithCustomMaxRetries(0)))
      .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration("http://127.0.0.1:${UNUSABLE_PORT}", "us-east-1"))
      .build()
    call.call(client)

    then:
    thrown SdkClientException

    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "$service.$operation"
          kind CLIENT
          status ERROR
          errorEvent SdkClientException, ~/Unable to execute HTTP request/
          hasNoParent()
          attributes {
            "$SemanticAttributes.HTTP_URL" "http://127.0.0.1:${UNUSABLE_PORT}"
            "$SemanticAttributes.HTTP_METHOD" "$method"
            "$SemanticAttributes.NET_PEER_NAME" "127.0.0.1"
            "$SemanticAttributes.NET_PEER_PORT" 61
            "$SemanticAttributes.RPC_SYSTEM" "aws-api"
            "$SemanticAttributes.RPC_SERVICE" { it.contains(service) }
            "$SemanticAttributes.RPC_METHOD" "${operation}"
            "aws.endpoint" "http://127.0.0.1:${UNUSABLE_PORT}"
            "aws.agent" "java-aws-sdk"
            for (def addedTag : additionalAttributes) {
              "$addedTag.key" "$addedTag.value"
            }
          }
        }
      }
    }

    where:
    service | operation   | method | url                  | call                                          | additionalAttributes              | body | clientBuilder
    "S3"    | "GetObject" | "GET"  | "someBucket/someKey" | { c -> c.getObject("someBucket", "someKey") } | ["aws.bucket.name": "someBucket"] | ""   | AmazonS3ClientBuilder.standard()
  }

  // TODO(anuraaga): Add events for retries.
  def "timeout and retry errors not captured"() {
    setup:
    // One retry so two requests.
    server.enqueue(HttpResponse.delayed(HttpResponse.of(HttpStatus.OK), Duration.ofMillis(5000)))
    server.enqueue(HttpResponse.delayed(HttpResponse.of(HttpStatus.OK), Duration.ofMillis(5000)))
    AmazonS3Client client = configureClient(AmazonS3ClientBuilder.standard())
      .withClientConfiguration(new ClientConfiguration()
        .withRequestTimeout(50 /* ms */)
        .withRetryPolicy(PredefinedRetryPolicies.getDefaultRetryPolicyWithCustomMaxRetries(1)))
      .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration("${server.httpUri()}", "us-east-1"))
      .build()

    when:
    client.getObject("someBucket", "someKey")

    then:
    !Span.current().getSpanContext().isValid()
    thrown AmazonClientException

    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "S3.GetObject"
          kind CLIENT
          status ERROR
          try {
            errorEvent AmazonClientException, ~/Unable to execute HTTP request/
          } catch (AssertionError e) {
            errorEvent SdkClientException, "Unable to execute HTTP request: Request did not complete before the request timeout configuration."
          }
          hasNoParent()
          attributes {
            "$SemanticAttributes.HTTP_URL" "${server.httpUri()}"
            "$SemanticAttributes.HTTP_METHOD" "GET"
            "$SemanticAttributes.NET_PEER_PORT" server.httpPort()
            "$SemanticAttributes.NET_PEER_NAME" "127.0.0.1"
            "$SemanticAttributes.RPC_SYSTEM" "aws-api"
            "$SemanticAttributes.RPC_SERVICE" "Amazon S3"
            "$SemanticAttributes.RPC_METHOD" "GetObject"
            "aws.endpoint" "${server.httpUri()}"
            "aws.agent" "java-aws-sdk"
            "aws.bucket.name" "someBucket"
          }
        }
      }
    }
  }
}
