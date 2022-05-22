/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambdaevents.v2_2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.KinesisEvent;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.amazonaws.services.lambda.runtime.events.SNSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.ScheduledEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.opentelemetry.instrumentation.awslambdacore.v1_0.internal.WrappedLambda;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TracingRequestWrapperStandardEventsTest {
  private static final String SUCCESS = "success";
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final Map<Class<?>, String> EVENTS_JSON = buildEventExamples();

  private final OpenTelemetrySdk sdk = OpenTelemetrySdk.builder().build();
  private final Context context = mock(Context.class);
  private TracingRequestWrapper wrapper;

  private static Map<Class<?>, String> buildEventExamples() {
    Map<Class<?>, String> events = new HashMap<>();
    events.put(
        ScheduledEventRequestHandler.class,
        "{\n"
            + "    \"version\": \"0\",\n"
            + "    \"id\": \"53dc4d37-cffa-4f76-80c9-8b7d4a4d2eaa\",\n"
            + "    \"detail-type\": \"Scheduled Event\",\n"
            + "    \"source\": \"aws.events\",\n"
            + "    \"account\": \"123456789012\",\n"
            + "    \"time\": \"2015-10-08T16:53:06Z\",\n"
            + "    \"region\": \"us-east-1\",\n"
            + "    \"resources\": [\n"
            + "        \"arn:aws:events:us-east-1:123456789012:rule/my-scheduled-rule\"\n"
            + "    ],\n"
            + "    \"detail\": {}\n"
            + "}");
    events.put(
        KinesisEventRequestHandler.class,
        "{\n"
            + "    \"Records\": [\n"
            + "        {\n"
            + "            \"kinesis\": {\n"
            + "                \"kinesisSchemaVersion\": \"1.0\",\n"
            + "                \"partitionKey\": \"1\",\n"
            + "                \"sequenceNumber\": \"49590338271490256608559692538361571095921575989136588898\",\n"
            + "                \"data\": \"SGVsbG8sIHRoaXMgaXMgYSB0ZXN0Lg==\",\n"
            + "                \"approximateArrivalTimestamp\": 1545084650.987\n"
            + "            },\n"
            + "            \"eventSource\": \"aws:kinesis\",\n"
            + "            \"eventVersion\": \"1.0\",\n"
            + "            \"eventID\": \"shardId-000000000006:49590338271490256608559692538361571095921575989136588898\",\n"
            + "            \"eventName\": \"aws:kinesis:record\",\n"
            + "            \"invokeIdentityArn\": \"arn:aws:iam::123456789012:role/lambda-role\",\n"
            + "            \"awsRegion\": \"us-east-2\",\n"
            + "            \"eventSourceARN\": \"arn:aws:kinesis:us-east-2:123456789012:stream/lambda-stream\"\n"
            + "        },\n"
            + "        {\n"
            + "            \"kinesis\": {\n"
            + "                \"kinesisSchemaVersion\": \"1.0\",\n"
            + "                \"partitionKey\": \"1\",\n"
            + "                \"sequenceNumber\": \"49590338271490256608559692540925702759324208523137515618\",\n"
            + "                \"data\": \"VGhpcyBpcyBvbmx5IGEgdGVzdC4=\",\n"
            + "                \"approximateArrivalTimestamp\": 1545084711.166\n"
            + "            },\n"
            + "            \"eventSource\": \"aws:kinesis\",\n"
            + "            \"eventVersion\": \"1.0\",\n"
            + "            \"eventID\": \"shardId-000000000006:49590338271490256608559692540925702759324208523137515618\",\n"
            + "            \"eventName\": \"aws:kinesis:record\",\n"
            + "            \"invokeIdentityArn\": \"arn:aws:iam::123456789012:role/lambda-role\",\n"
            + "            \"awsRegion\": \"us-east-2\",\n"
            + "            \"eventSourceARN\": \"arn:aws:kinesis:us-east-2:123456789012:stream/lambda-stream\"\n"
            + "        }\n"
            + "    ]\n"
            + "}");
    events.put(
        SqsEventRequestHandler.class,
        "{\n"
            + "    \"Records\": [\n"
            + "        {\n"
            + "            \"messageId\": \"059f36b4-87a3-44ab-83d2-661975830a7d\",\n"
            + "            \"receiptHandle\": \"AQEBwJnKyrHigUMZj6rYigCgxlaS3SLy0a...\",\n"
            + "            \"body\": \"Test message.\",\n"
            + "            \"attributes\": {\n"
            + "                \"ApproximateReceiveCount\": \"1\",\n"
            + "                \"SentTimestamp\": \"1545082649183\",\n"
            + "                \"SenderId\": \"AIDAIENQZJOLO23YVJ4VO\",\n"
            + "                \"ApproximateFirstReceiveTimestamp\": \"1545082649185\"\n"
            + "            },\n"
            + "            \"messageAttributes\": {},\n"
            + "            \"md5OfBody\": \"e4e68fb7bd0e697a0ae8f1bb342846b3\",\n"
            + "            \"eventSource\": \"aws:sqs\",\n"
            + "            \"eventSourceARN\": \"arn:aws:sqs:us-east-2:123456789012:my-queue\",\n"
            + "            \"awsRegion\": \"us-east-2\"\n"
            + "        },\n"
            + "        {\n"
            + "            \"messageId\": \"2e1424d4-f796-459a-8184-9c92662be6da\",\n"
            + "            \"receiptHandle\": \"AQEBzWwaftRI0KuVm4tP+/7q1rGgNqicHq...\",\n"
            + "            \"body\": \"Test message.\",\n"
            + "            \"attributes\": {\n"
            + "                \"ApproximateReceiveCount\": \"1\",\n"
            + "                \"SentTimestamp\": \"1545082650636\",\n"
            + "                \"SenderId\": \"AIDAIENQZJOLO23YVJ4VO\",\n"
            + "                \"ApproximateFirstReceiveTimestamp\": \"1545082650649\"\n"
            + "            },\n"
            + "            \"messageAttributes\": {},\n"
            + "            \"md5OfBody\": \"e4e68fb7bd0e697a0ae8f1bb342846b3\",\n"
            + "            \"eventSource\": \"aws:sqs\",\n"
            + "            \"eventSourceARN\": \"arn:aws:sqs:us-east-2:123456789012:my-queue\",\n"
            + "            \"awsRegion\": \"us-east-2\"\n"
            + "        }\n"
            + "    ]\n"
            + "}");
    events.put(
        S3EventRequestHandler.class,
        "{\n"
            + "  \"Records\": [\n"
            + "    {\n"
            + "      \"eventVersion\": \"2.1\",\n"
            + "      \"eventSource\": \"aws:s3\",\n"
            + "      \"awsRegion\": \"us-east-2\",\n"
            + "      \"eventTime\": \"2019-09-03T19:37:27.192Z\",\n"
            + "      \"eventName\": \"ObjectCreated:Put\",\n"
            + "      \"userIdentity\": {\n"
            + "        \"principalId\": \"AWS:AIDAINPONIXQXHT3IKHL2\"\n"
            + "      },\n"
            + "      \"requestParameters\": {\n"
            + "        \"sourceIPAddress\": \"205.255.255.255\"\n"
            + "      },\n"
            + "      \"responseElements\": {\n"
            + "        \"x-amz-request-id\": \"D82B88E5F771F645\",\n"
            + "        \"x-amz-id-2\": \"vlR7PnpV2Ce81l0PRw6jlUpck7Jo5ZsQjryTjKlc5aLWGVHPZLj5NeC6qMa0emYBDXOo6QBU0Wo=\"\n"
            + "      },\n"
            + "      \"s3\": {\n"
            + "        \"s3SchemaVersion\": \"1.0\",\n"
            + "        \"configurationId\": \"828aa6fc-f7b5-4305-8584-487c791949c1\",\n"
            + "        \"bucket\": {\n"
            + "          \"name\": \"DOC-EXAMPLE-BUCKET\",\n"
            + "          \"ownerIdentity\": {\n"
            + "            \"principalId\": \"A3I5XTEXAMAI3E\"\n"
            + "          },\n"
            + "          \"arn\": \"arn:aws:s3:::lambda-artifacts-deafc19498e3f2df\"\n"
            + "        },\n"
            + "        \"object\": {\n"
            + "          \"key\": \"b21b84d653bb07b05b1e6b33684dc11b\",\n"
            + "          \"size\": 1305107,\n"
            + "          \"eTag\": \"b21b84d653bb07b05b1e6b33684dc11b\",\n"
            + "          \"sequencer\": \"0C0F6F405D6ED209E1\"\n"
            + "        }\n"
            + "      }\n"
            + "    }\n"
            + "  ]\n"
            + "}");
    events.put(
        SnsEventRequestHandler.class,
        "{\n"
            + "  \"Records\": [\n"
            + "    {\n"
            + "      \"EventVersion\": \"1.0\",\n"
            + "      \"EventSubscriptionArn\": \"arn:aws:sns:us-east-2:123456789012:sns-lambda:21be56ed-a058-49f5-8c98-aedd2564c486\",\n"
            + "      \"EventSource\": \"aws:sns\",\n"
            + "      \"Sns\": {\n"
            + "        \"SignatureVersion\": \"1\",\n"
            + "        \"Timestamp\": \"2019-01-02T12:45:07.000Z\",\n"
            + "        \"Signature\": \"tcc6faL2yUC6dgZdmrwh1Y4cGa/ebXEkAi6RibDsvpi+tE/1+82j...65r==\",\n"
            + "        \"SigningCertUrl\": \"https://sns.us-east-2.amazonaws.com/SimpleNotificationService-ac565b8b1a6c5d002d285f9598aa1d9b.pem\",\n"
            + "        \"MessageId\": \"95df01b4-ee98-5cb9-9903-4c221d41eb5e\",\n"
            + "        \"Message\": \"Hello from SNS!\",\n"
            + "        \"MessageAttributes\": {\n"
            + "          \"Test\": {\n"
            + "            \"Type\": \"String\",\n"
            + "            \"Value\": \"TestString\"\n"
            + "          },\n"
            + "          \"TestBinary\": {\n"
            + "            \"Type\": \"Binary\",\n"
            + "            \"Value\": \"TestBinary\"\n"
            + "          }\n"
            + "        },\n"
            + "        \"Type\": \"Notification\",\n"
            + "        \"UnsubscribeUrl\": \"https://sns.us-east-2.amazonaws.com/?Action=Unsubscribe&amp;SubscriptionArn=arn:aws:sns:us-east-2:123456789012:test-lambda:21be56ed-a058-49f5-8c98-aedd2564c486\",\n"
            + "        \"TopicArn\":\"arn:aws:sns:us-east-2:123456789012:sns-lambda\",\n"
            + "        \"Subject\": \"TestInvoke\"\n"
            + "      }\n"
            + "    }\n"
            + "  ]\n"
            + "}");

    return events;
  }

  private TracingRequestWrapper buildWrapper(Class<?> targetClass) {
    WrappedLambda wrappedLambda = new WrappedLambda(targetClass, "handleRequest");

    return new TracingRequestWrapper(sdk, wrappedLambda, TracingRequestWrapper::map);
  }

  @ParameterizedTest
  @ValueSource(
      classes = {
        ScheduledEventRequestHandler.class,
        KinesisEventRequestHandler.class,
        SqsEventRequestHandler.class,
        S3EventRequestHandler.class,
        SnsEventRequestHandler.class
      })
  void handleScheduledEvent(Class<?> targetClass) throws JsonProcessingException {
    wrapper = buildWrapper(targetClass);
    Object parsedScheduledEvent =
        OBJECT_MAPPER.readValue(EVENTS_JSON.get(targetClass), Object.class);
    assertEquals(SUCCESS, wrapper.doHandleRequest(parsedScheduledEvent, context));
  }

  public static class ScheduledEventRequestHandler
      implements RequestHandler<ScheduledEvent, String> {
    @Override
    public String handleRequest(ScheduledEvent i, Context cntxt) {
      return SUCCESS;
    }
  }

  public static class KinesisEventRequestHandler implements RequestHandler<KinesisEvent, String> {
    @Override
    public String handleRequest(KinesisEvent i, Context cntxt) {
      return SUCCESS;
    }
  }

  public static class SqsEventRequestHandler implements RequestHandler<SQSEvent, String> {
    @Override
    public String handleRequest(SQSEvent i, Context cntxt) {
      return SUCCESS;
    }
  }

  public static class S3EventRequestHandler implements RequestHandler<S3Event, String> {
    @Override
    public String handleRequest(S3Event i, Context cntxt) {
      return SUCCESS;
    }
  }

  public static class SnsEventRequestHandler implements RequestHandler<SNSEvent, String> {
    @Override
    public String handleRequest(SNSEvent i, Context cntxt) {
      return SUCCESS;
    }
  }
}
