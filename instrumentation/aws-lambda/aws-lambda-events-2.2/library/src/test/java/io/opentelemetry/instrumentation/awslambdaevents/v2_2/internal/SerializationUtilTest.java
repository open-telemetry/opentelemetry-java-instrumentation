/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambdaevents.v2_2.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.amazonaws.services.lambda.runtime.events.KinesisEvent;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.amazonaws.services.lambda.runtime.events.SNSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.ScheduledEvent;
import com.amazonaws.services.lambda.runtime.events.models.s3.S3EventNotification;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.joda.time.DateTime;
import org.junit.jupiter.api.Test;

public class SerializationUtilTest {

  private static final Map<Class<?>, String> events = buildEventExamples();

  private static Map<Class<?>, String> buildEventExamples() {
    Map<Class<?>, String> events = new HashMap<>();
    events.put(
        ScheduledEvent.class,
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
        KinesisEvent.class,
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
        SQSEvent.class,
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
        S3Event.class,
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
        SNSEvent.class,
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

  private static void assertScheduledEvent(ScheduledEvent event) {
    assertThat(event).isNotNull();
    assertThat(event.getSource()).isEqualTo("aws.events");
    assertThat(event.getDetailType()).isEqualTo("Scheduled Event");
    assertThat(event.getAccount()).isEqualTo("123456789012");
    assertThat(event.getId()).isEqualTo("53dc4d37-cffa-4f76-80c9-8b7d4a4d2eaa");
    assertThat(event.getRegion()).isEqualTo("us-east-1");
    assertThat(event.getTime().getMillis())
        .isEqualTo(new DateTime("2015-10-08T16:53:06Z").getMillis());
  }

  private static void assertKinesisEvent(KinesisEvent event) {
    assertThat(event).isNotNull();
    assertThat(event.getRecords()).isNotNull();
    assertThat(event.getRecords().size()).isEqualTo(2);
    KinesisEvent.KinesisEventRecord record = event.getRecords().get(0);
    assertThat(record.getEventSource()).isEqualTo("aws:kinesis");
    assertThat(record.getEventSourceARN())
        .isEqualTo("arn:aws:kinesis:us-east-2:123456789012:stream/lambda-stream");
    assertThat(record.getEventName()).isEqualTo("aws:kinesis:record");
    assertThat(record.getEventID())
        .isEqualTo("shardId-000000000006:49590338271490256608559692538361571095921575989136588898");
    assertThat(record.getKinesis().getPartitionKey()).isEqualTo("1");
    assertThat(record.getKinesis().getSequenceNumber())
        .isEqualTo("49590338271490256608559692538361571095921575989136588898");
    assertThat(new String(record.getKinesis().getData().array(), StandardCharsets.UTF_8))
        .isEqualTo("Hello, this is a test.");
  }

  private static void assertSqsEvent(SQSEvent event) {
    assertThat(event).isNotNull();
    assertThat(event.getRecords()).isNotNull();
    assertThat(event.getRecords().size()).isEqualTo(2);
    SQSEvent.SQSMessage record = event.getRecords().get(0);
    assertThat(record.getEventSource()).isEqualTo("aws:sqs");
    assertThat(record.getEventSourceArn()).isEqualTo("arn:aws:sqs:us-east-2:123456789012:my-queue");
    assertThat(record.getMessageId()).isEqualTo("059f36b4-87a3-44ab-83d2-661975830a7d");
    assertThat(record.getBody()).isEqualTo("Test message.");
    assertThat(record.getMd5OfBody()).isEqualTo("e4e68fb7bd0e697a0ae8f1bb342846b3");
    assertThat(record.getReceiptHandle()).isEqualTo("AQEBwJnKyrHigUMZj6rYigCgxlaS3SLy0a...");
  }

  private static void assertS3Event(S3Event event) {
    assertThat(event).isNotNull();
    assertThat(event.getRecords()).isNotNull();
    assertThat(event.getRecords().size()).isEqualTo(1);
    S3EventNotification.S3EventNotificationRecord record = event.getRecords().get(0);
    assertThat(record.getEventSource()).isEqualTo("aws:s3");
    assertThat(record.getEventName()).isEqualTo("ObjectCreated:Put");
    assertThat(record.getS3().getBucket().getName()).isEqualTo("DOC-EXAMPLE-BUCKET");
    assertThat(record.getS3().getBucket().getArn())
        .isEqualTo("arn:aws:s3:::lambda-artifacts-deafc19498e3f2df");
    assertThat(record.getS3().getObject().getKey()).isEqualTo("b21b84d653bb07b05b1e6b33684dc11b");
  }

  private static void assertSnsEvent(SNSEvent event) {
    assertThat(event).isNotNull();
    assertThat(event.getRecords()).isNotNull();
    assertThat(event.getRecords().size()).isEqualTo(1);
    SNSEvent.SNSRecord record = event.getRecords().get(0);
    assertThat(record.getEventSource()).isEqualTo("aws:sns");
    assertThat(record.getEventSubscriptionArn())
        .isEqualTo(
            "arn:aws:sns:us-east-2:123456789012:sns-lambda:21be56ed-a058-49f5-8c98-aedd2564c486");
    assertThat(record.getSNS().getMessageId()).isEqualTo("95df01b4-ee98-5cb9-9903-4c221d41eb5e");
    assertThat(record.getSNS().getMessage()).isEqualTo("Hello from SNS!");
    assertThat(record.getSNS().getType()).isEqualTo("Notification");
    assertThat(record.getSNS().getTopicArn())
        .isEqualTo("arn:aws:sns:us-east-2:123456789012:sns-lambda");
    assertThat(record.getSNS().getSubject()).isEqualTo("TestInvoke");
  }

  @Test
  void scheduledEventDeserializedFromStringJson() {
    String eventBody = events.get(ScheduledEvent.class);
    ScheduledEvent event = SerializationUtil.fromJson(eventBody, ScheduledEvent.class);

    assertScheduledEvent(event);
  }

  @Test
  void scheduledEventDeserializedFromInputStreamJson() {
    String eventBody = events.get(ScheduledEvent.class);
    ScheduledEvent event =
        SerializationUtil.fromJson(
            new ByteArrayInputStream(eventBody.getBytes(StandardCharsets.UTF_8)),
            ScheduledEvent.class);

    assertScheduledEvent(event);
  }

  @Test
  void kinesisEventDeserializedFromStringJson() {
    String eventBody = events.get(KinesisEvent.class);
    KinesisEvent event = SerializationUtil.fromJson(eventBody, KinesisEvent.class);

    assertKinesisEvent(event);
  }

  @Test
  void kinesisEventDeserializedFromInputStreamJson() {
    String eventBody = events.get(KinesisEvent.class);
    KinesisEvent event =
        SerializationUtil.fromJson(
            new ByteArrayInputStream(eventBody.getBytes(StandardCharsets.UTF_8)),
            KinesisEvent.class);

    assertKinesisEvent(event);
  }

  @Test
  void sqsEventDeserializedFromStringJson() {
    String eventBody = events.get(SQSEvent.class);
    SQSEvent event = SerializationUtil.fromJson(eventBody, SQSEvent.class);

    assertSqsEvent(event);
  }

  @Test
  void sqsEventDeserializedFromInputStreamJson() {
    String eventBody = events.get(SQSEvent.class);
    SQSEvent event =
        SerializationUtil.fromJson(
            new ByteArrayInputStream(eventBody.getBytes(StandardCharsets.UTF_8)), SQSEvent.class);

    assertSqsEvent(event);
  }

  @Test
  void s3EventDeserializedFromStringJson() {
    String eventBody = events.get(S3Event.class);
    S3Event event = SerializationUtil.fromJson(eventBody, S3Event.class);

    assertS3Event(event);
  }

  @Test
  void s3EventDeserializedFromInputStreamJson() {
    String eventBody = events.get(S3Event.class);
    S3Event event =
        SerializationUtil.fromJson(
            new ByteArrayInputStream(eventBody.getBytes(StandardCharsets.UTF_8)), S3Event.class);

    assertS3Event(event);
  }

  @Test
  void snsEventDeserializedFromStringJson() {
    String eventBody = events.get(SNSEvent.class);
    SNSEvent event = SerializationUtil.fromJson(eventBody, SNSEvent.class);

    assertSnsEvent(event);
  }

  @Test
  void snsEventDeserializedFromInputStreamJson() {
    String eventBody = events.get(SNSEvent.class);
    SNSEvent event =
        SerializationUtil.fromJson(
            new ByteArrayInputStream(eventBody.getBytes(StandardCharsets.UTF_8)), SNSEvent.class);

    assertSnsEvent(event);
  }
}
