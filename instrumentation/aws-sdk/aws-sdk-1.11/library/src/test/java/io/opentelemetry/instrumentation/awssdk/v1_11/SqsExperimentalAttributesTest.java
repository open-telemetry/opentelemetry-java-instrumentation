/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v1_11;

import com.amazonaws.DefaultRequest;
import com.amazonaws.Request;
import com.amazonaws.Response;
import com.amazonaws.services.sqs.model.SendMessageRequest;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import org.junit.Test;
import java.lang.reflect.Constructor;
import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

/*
  We cannot use a real looking SQS URL with a local endpoint for these tests because
  SQS throws an exception stating that the URL does not match the local endpoint.

  This test synthetically creates a sendMessage request and asserts that the correct
  experimental attributes are added by the attribute extractor.

  The private get methods are used to get classes that we do not want to expose to
  users, but that we want to use to circumvent the issue above.
*/
public class SqsExperimentalAttributesTest {
  @Test
  public void basic() throws Exception {
    sqsRegionTest(
        "https://sqs.us-west-2.amazonaws.com/123456789012/MyQueue",
        "https://sqs.us-west-2.amazonaws.com",
        "us-west-2",
        "123456789012",
        "aws");

    sqsRegionTest(
        "https://sqs.eu-north-1.amazonaws.com/112233445566/MyQueue",
        "https://sqs.eu-north-1.amazonaws.com",
        "eu-north-1",
        "112233445566",
        "aws");

    sqsRegionTest(
        "https://sqs.us-west-1.amazonaws.com/111122223333/MyQueue",
        "https://sqs.us-west-1.amazonaws.com",
        "us-west-1",
        "111122223333",
        "aws");
  }


  @Test
  public void usGovRegions() throws Exception {
    sqsRegionTest(
        "https://sqs.us-gov-east-1.amazonaws.com/123456789012/MyQueue",
        "https://sqs.us-gov-east-1.amazonaws.com",
        "us-gov-east-1",
        "123456789012",
        "aws-us-gov");

    sqsRegionTest(
        "https://sqs.us-gov-west-1.amazonaws.com/112233445567/MyQueue",
        "https://sqs.us-gov-west-1.amazonaws.com",
        "us-gov-west-1",
        "112233445567",
        "aws-us-gov");
  }

  @Test
  public void legacyFormat() throws Exception {
    sqsRegionTest(
        "https://cn-northwest-1.queue.amazonaws.com/123456789012/MyQueue",
        "https://cn-northwest-1.queue.amazonaws.com",
        "cn-northwest-1",
        "123456789012",
        "aws-cn");

    sqsRegionTest(
        "https://ap-south-1.queue.amazonaws.com/123412341234/MyQueue",
        "https://ap-south-1.queue.amazonaws.com",
        "ap-south-1",
        "123412341234",
        "aws");
  }

  @Test
  public void specialCaseNorthVirginia() throws Exception {
    sqsRegionTest(
        "https://queue.amazonaws.com/123412341234/MyQueue",
        "https://queue.amazonaws.com",
        "us-east-1",
        "123412341234",
        "aws");
  }

  @Test
  public void localEndpoint() throws Exception {
    sqsRegionTest(
        "http://127.0.0.1:1212/123412341234/MyQueue",
        "http://127.0.0.1:1212",
        null,
        "123412341234",
        null);
  }

  @Test
  public void negativeTests() throws Exception {
    sqsRegionTest(
        "https://amazonaws.com",
        "https://amazonaws.com",
        null,
        null,
        null);

    sqsRegionTest(
        "test",
        "test",
        null,
        null,
        null);
  }

  private static void sqsRegionTest(
      String url,
      String endpoint,
      String region,
      String accountId,
      String partition) throws Exception {
    SendMessageRequest request = new SendMessageRequest()
        .withQueueUrl(url)
        .withMessageBody("Hello World!");

    DefaultRequest<SqsProcessRequest> defaultRequest = new DefaultRequest<>(request, "SQS");
    defaultRequest.setEndpoint(new URI(endpoint));

    AttributesExtractor<Request<?>, Response<?>> extractor = getExtractor();
    AttributesBuilder attributesBuilder = getAttributesBuilder();
    extractor.onStart(attributesBuilder, Context.current(), defaultRequest);
    Attributes attributes = attributesBuilder.build();

    assertThat(attributes.get(AttributeKey.stringKey("aws.region"))).isEqualTo(region);
    assertThat(attributes.get(AttributeKey.stringKey("aws.accountId"))).isEqualTo(accountId);
    assertThat(attributes.get(AttributeKey.stringKey("aws.partition"))).isEqualTo(partition);
  }

  @SuppressWarnings ("unchecked")
  private static AttributesExtractor<Request<?>, Response<?>> getExtractor() throws Exception {
    Constructor<?> constructor = Class.forName("io.opentelemetry.instrumentation.awssdk.v1_11.AwsSdkExperimentalAttributesExtractor")
        .getDeclaredConstructors()[0];

    constructor.setAccessible(true);

    return (AttributesExtractor<Request<?>, Response<?>>)constructor.newInstance();
  }

  private static AttributesBuilder getAttributesBuilder() throws Exception {
    Constructor<?> constructor = Class.forName("io.opentelemetry.api.common.ArrayBackedAttributesBuilder")
        .getDeclaredConstructors()[0];

    constructor.setAccessible(true);

    return(AttributesBuilder) constructor.newInstance();
  }
}
