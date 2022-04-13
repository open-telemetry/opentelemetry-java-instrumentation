/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package test

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.common.util.concurrent.ListenableFuture
import com.twilio.Twilio
import com.twilio.exception.ApiException
import com.twilio.http.NetworkHttpClient
import com.twilio.http.Response
import com.twilio.http.TwilioRestClient
import com.twilio.rest.api.v2010.account.Call
import com.twilio.rest.api.v2010.account.Message
import com.twilio.type.PhoneNumber
import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import org.apache.http.HttpEntity
import org.apache.http.StatusLine
import org.apache.http.client.methods.CloseableHttpResponse
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClientBuilder

import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

import static io.opentelemetry.api.trace.SpanKind.CLIENT
import static io.opentelemetry.api.trace.StatusCode.ERROR

class TwilioClientTest extends AgentInstrumentationSpecification {
  final static String ACCOUNT_SID = "abc"
  final static String AUTH_TOKEN = "efg"

  final static String MESSAGE_RESPONSE_BODY = """
    {
      "account_sid": "AC14984e09e497506cf0d5eb59b1f6ace7",
      "api_version": "2010-04-01",
      "body": "Hello, World!",
      "date_created": "Thu, 30 Jul 2015 20:12:31 +0000",
      "date_sent": "Thu, 30 Jul 2015 20:12:33 +0000",
      "date_updated": "Thu, 30 Jul 2015 20:12:33 +0000",
      "direction": "outbound-api",
      "from": "+14155552345",
      "messaging_service_sid": "MGXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX",
      "num_media": "0",
      "num_segments": "1",
      "price": -0.00750,
      "price_unit": "USD",
      "sid": "MMXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX",
      "status": "sent",
      "subresource_uris": {
        "media": "/2010-04-01/Accounts/AC14984e09e497506cf0d5eb59b1f6ace7/Messages/SMXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX/Media.json"
      },
      "to": "+14155552345",
      "uri": "/2010-04-01/Accounts/AC14984e09e497506cf0d5eb59b1f6ace7/Messages/SMXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX.json"
    }
    """

  final static String CALL_RESPONSE_BODY = """
    {
      "account_sid": "ACXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX",
      "annotation": null,
      "answered_by": null,
      "api_version": "2010-04-01",
      "caller_name": null,
      "date_created": "Tue, 31 Aug 2010 20:36:28 +0000",
      "date_updated": "Tue, 31 Aug 2010 20:36:44 +0000",
      "direction": "inbound",
      "duration": "15",
      "end_time": "Tue, 31 Aug 2010 20:36:44 +0000",
      "forwarded_from": "+141586753093",
      "from": "+15017122661",
      "from_formatted": "(501) 712-2661",
      "group_sid": null,
      "parent_call_sid": null,
      "phone_number_sid": "PNXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX",
      "price": -0.03000,
      "price_unit": "USD",
      "sid": "CAXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX",
      "start_time": "Tue, 31 Aug 2010 20:36:29 +0000",
      "status": "completed",
      "subresource_uris": {
        "notifications": "/2010-04-01/Accounts/ACXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX/Calls/CAXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX/Notifications.json",
        "recordings": "/2010-04-01/Accounts/ACXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX/Calls/CAXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX/Recordings.json",
        "feedback": "/2010-04-01/Accounts/ACXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX/Calls/CAXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX/Feedback.json",
        "feedback_summaries": "/2010-04-01/Accounts/ACXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX/Calls/FeedbackSummary.json"
      },
      "to": "+15558675310",
      "to_formatted": "(555) 867-5310",
      "uri": "/2010-04-01/Accounts/ACXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX/Calls/CAXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX.json"
    }
    """

  final static String ERROR_RESPONSE_BODY = """
    {
      "code": 123,
      "message": "Testing Failure",
      "code": 567,
      "more_info": "Testing"
    }
    """

  TwilioRestClient twilioRestClient = Mock()

  def setupSpec() {
    Twilio.init(ACCOUNT_SID, AUTH_TOKEN)
  }

  def cleanup() {
    Twilio.getExecutorService().shutdown()
    Twilio.setExecutorService(null)
    Twilio.setRestClient(null)
  }

  def "synchronous message"() {
    setup:
    twilioRestClient.getObjectMapper() >> new ObjectMapper()

    1 * twilioRestClient.request(_) >> new Response(new ByteArrayInputStream(MESSAGE_RESPONSE_BODY.getBytes()), 200)

    Message message = runWithSpan("test") {
      Message.creator(
        new PhoneNumber("+1 555 720 5913"),  // To number
        new PhoneNumber("+1 555 555 5215"),  // From number
        "Hello world!"                    // SMS body
      ).create(twilioRestClient)
    }

    expect:

    message.body == "Hello, World!"

    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          name "test"
          hasNoParent()
          attributes {
          }
        }
        span(1) {
          name "MessageCreator.create"
          kind CLIENT
          attributes {
            "twilio.type" "com.twilio.rest.api.v2010.account.Message"
            "twilio.account" "AC14984e09e497506cf0d5eb59b1f6ace7"
            "twilio.sid" "MMXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX"
            "twilio.status" "sent"
          }
        }
      }
    }
  }

  def "synchronous call"() {
    setup:
    twilioRestClient.getObjectMapper() >> new ObjectMapper()

    1 * twilioRestClient.request(_) >> new Response(new ByteArrayInputStream(CALL_RESPONSE_BODY.getBytes()), 200)

    Call call = runWithSpan("test") {
      Call.creator(
        new PhoneNumber("+15558881234"),  // To number
        new PhoneNumber("+15559994321"),  // From number

        // Read TwiML at this URL when a call connects (hold music)
        new URI("http://twimlets.com/holdmusic?Bucket=com.twilio.music.ambient")
      ).create(twilioRestClient)
    }

    expect:

    call.status == Call.Status.COMPLETED

    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          name "test"
          hasNoParent()
          attributes {
          }
        }
        span(1) {
          name "CallCreator.create"
          kind CLIENT
          attributes {
            "twilio.type" "com.twilio.rest.api.v2010.account.Call"
            "twilio.account" "ACXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX"
            "twilio.sid" "CAXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX"
            "twilio.status" "completed"
          }
        }
      }
    }
  }

  def "http client"() {
    setup:
    CloseableHttpClient httpClient = Mock()
    httpClient.execute(_) >> mockResponse(MESSAGE_RESPONSE_BODY, 200)

    HttpClientBuilder clientBuilder = Mock()
    clientBuilder.build() >> httpClient

    NetworkHttpClient networkHttpClient = new NetworkHttpClient(clientBuilder)

    TwilioRestClient realTwilioRestClient =
      new TwilioRestClient.Builder("username", "password")
        .accountSid(ACCOUNT_SID)
        .httpClient(networkHttpClient)
        .build()

    Message message = runWithSpan("test") {
      Message.creator(
        new PhoneNumber("+1 555 720 5913"),  // To number
        new PhoneNumber("+1 555 555 5215"),  // From number
        "Hello world!"                    // SMS body
      ).create(realTwilioRestClient)
    }

    expect:

    message.body == "Hello, World!"

    assertTraces(1) {
      trace(0, 3) {
        span(0) {
          name "test"
          hasNoParent()
          attributes {
          }
        }
        span(1) {
          name "MessageCreator.create"
          kind CLIENT
          childOf(span(0))
          attributes {
            "twilio.type" "com.twilio.rest.api.v2010.account.Message"
            "twilio.account" "AC14984e09e497506cf0d5eb59b1f6ace7"
            "twilio.sid" "MMXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX"
            "twilio.status" "sent"
          }
        }
        span(2) {
          name "HTTP POST"
          kind CLIENT
          childOf span(1)
          attributes {
            "$SemanticAttributes.NET_TRANSPORT.key" SemanticAttributes.NetTransportValues.IP_TCP
            "$SemanticAttributes.NET_PEER_NAME.key" "api.twilio.com"
            "$SemanticAttributes.NET_PEER_PORT.key" 443
            "$SemanticAttributes.HTTP_FLAVOR.key" SemanticAttributes.HttpFlavorValues.HTTP_1_1
            "$SemanticAttributes.HTTP_METHOD.key" "POST"
            "$SemanticAttributes.HTTP_URL.key" "https://api.twilio.com/2010-04-01/Accounts/abc/Messages.json"
            "$SemanticAttributes.HTTP_STATUS_CODE.key" 200
          }
        }
      }
    }
  }

  def "http client retry"() {
    setup:
    CloseableHttpClient httpClient = Mock()
    httpClient.execute(_) >>> [
      mockResponse(ERROR_RESPONSE_BODY, 500),
      mockResponse(MESSAGE_RESPONSE_BODY, 200)
    ]

    HttpClientBuilder clientBuilder = Mock()
    clientBuilder.build() >> httpClient

    NetworkHttpClient networkHttpClient = new NetworkHttpClient(clientBuilder)

    TwilioRestClient realTwilioRestClient =
      new TwilioRestClient.Builder("username", "password")
        .accountSid(ACCOUNT_SID)
        .httpClient(networkHttpClient)
        .build()

    Message message = runWithSpan("test") {
      Message.creator(
        new PhoneNumber("+1 555 720 5913"),  // To number
        new PhoneNumber("+1 555 555 5215"),  // From number
        "Hello world!"                    // SMS body
      ).create(realTwilioRestClient)
    }

    expect:
    message.body == "Hello, World!"

    assertTraces(1) {
      trace(0, 4) {
        span(0) {
          name "test"
          hasNoParent()
          attributes {
          }
        }
        span(1) {
          name "MessageCreator.create"
          kind CLIENT
          childOf(span(0))
          attributes {
            "twilio.type" "com.twilio.rest.api.v2010.account.Message"
            "twilio.account" "AC14984e09e497506cf0d5eb59b1f6ace7"
            "twilio.sid" "MMXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX"
            "twilio.status" "sent"
          }
        }
        span(2) {
          name "HTTP POST"
          kind CLIENT
          childOf span(1)
          status ERROR
          attributes {
            "$SemanticAttributes.NET_TRANSPORT.key" SemanticAttributes.NetTransportValues.IP_TCP
            "$SemanticAttributes.NET_PEER_NAME.key" "api.twilio.com"
            "$SemanticAttributes.NET_PEER_PORT.key" 443
            "$SemanticAttributes.HTTP_FLAVOR.key" SemanticAttributes.HttpFlavorValues.HTTP_1_1
            "$SemanticAttributes.HTTP_METHOD.key" "POST"
            "$SemanticAttributes.HTTP_URL.key" "https://api.twilio.com/2010-04-01/Accounts/abc/Messages.json"
            "$SemanticAttributes.HTTP_STATUS_CODE.key" 500
          }
        }
        span(3) {
          name "HTTP POST"
          kind CLIENT
          childOf span(1)
          attributes {
            "$SemanticAttributes.NET_TRANSPORT.key" SemanticAttributes.NetTransportValues.IP_TCP
            "$SemanticAttributes.NET_PEER_NAME.key" "api.twilio.com"
            "$SemanticAttributes.NET_PEER_PORT.key" 443
            "$SemanticAttributes.HTTP_FLAVOR.key" SemanticAttributes.HttpFlavorValues.HTTP_1_1
            "$SemanticAttributes.HTTP_METHOD.key" "POST"
            "$SemanticAttributes.HTTP_URL.key" "https://api.twilio.com/2010-04-01/Accounts/abc/Messages.json"
            "$SemanticAttributes.HTTP_STATUS_CODE.key" 200
          }
        }
      }
    }
  }

  def "http client retry async"() {
    setup:
    CloseableHttpClient httpClient = Mock()
    httpClient.execute(_) >>> [
      mockResponse(ERROR_RESPONSE_BODY, 500),
      mockResponse(MESSAGE_RESPONSE_BODY, 200)
    ]

    HttpClientBuilder clientBuilder = Mock()
    clientBuilder.build() >> httpClient

    NetworkHttpClient networkHttpClient = new NetworkHttpClient(clientBuilder)

    TwilioRestClient realTwilioRestClient =
      new TwilioRestClient.Builder("username", "password")
        .accountSid(ACCOUNT_SID)
        .httpClient(networkHttpClient)
        .build()

    Message message = runWithSpan("test") {
      ListenableFuture<Message> future = Message.creator(
        new PhoneNumber("+1 555 720 5913"),  // To number
        new PhoneNumber("+1 555 555 5215"),  // From number
        "Hello world!"                    // SMS body
      ).createAsync(realTwilioRestClient)

      try {
        return future.get(10, TimeUnit.SECONDS)
      } finally {
        // Give the future callback a chance to run
        Thread.sleep(1000)
      }
    }

    expect:
    message.body == "Hello, World!"

    assertTraces(1) {
      trace(0, 4) {
        span(0) {
          name "test"
          hasNoParent()
          attributes {
          }
        }
        span(1) {
          name "MessageCreator.createAsync"
          kind CLIENT
          childOf(span(0))
          attributes {
            "twilio.type" "com.twilio.rest.api.v2010.account.Message"
            "twilio.account" "AC14984e09e497506cf0d5eb59b1f6ace7"
            "twilio.sid" "MMXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX"
            "twilio.status" "sent"
          }
        }
        span(2) {
          name "HTTP POST"
          kind CLIENT
          childOf span(1)
          status ERROR
          attributes {
            "$SemanticAttributes.NET_TRANSPORT.key" SemanticAttributes.NetTransportValues.IP_TCP
            "$SemanticAttributes.NET_PEER_NAME.key" "api.twilio.com"
            "$SemanticAttributes.NET_PEER_PORT.key" 443
            "$SemanticAttributes.HTTP_FLAVOR.key" SemanticAttributes.HttpFlavorValues.HTTP_1_1
            "$SemanticAttributes.HTTP_METHOD.key" "POST"
            "$SemanticAttributes.HTTP_URL.key" "https://api.twilio.com/2010-04-01/Accounts/abc/Messages.json"
            "$SemanticAttributes.HTTP_STATUS_CODE.key" 500
          }
        }
        span(3) {
          name "HTTP POST"
          kind CLIENT
          childOf span(1)
          attributes {
            "$SemanticAttributes.NET_TRANSPORT.key" SemanticAttributes.NetTransportValues.IP_TCP
            "$SemanticAttributes.NET_PEER_NAME.key" "api.twilio.com"
            "$SemanticAttributes.NET_PEER_PORT.key" 443
            "$SemanticAttributes.HTTP_FLAVOR.key" SemanticAttributes.HttpFlavorValues.HTTP_1_1
            "$SemanticAttributes.HTTP_METHOD.key" "POST"
            "$SemanticAttributes.HTTP_URL.key" "https://api.twilio.com/2010-04-01/Accounts/abc/Messages.json"
            "$SemanticAttributes.HTTP_STATUS_CODE.key" 200
          }
        }
      }
    }

    cleanup:
    Twilio.getExecutorService().shutdown()
    Twilio.setExecutorService(null)
    Twilio.setRestClient(null)
  }

  def "Sync Failure"() {
    setup:

    twilioRestClient.getObjectMapper() >> new ObjectMapper()

    1 * twilioRestClient.request(_) >> new Response(new ByteArrayInputStream(ERROR_RESPONSE_BODY.getBytes()), 500)

    when:
    runWithSpan("test") {
      Message.creator(
        new PhoneNumber("+1 555 720 5913"),  // To number
        new PhoneNumber("+1 555 555 5215"),  // From number
        "Hello world!"                    // SMS body
      ).create(twilioRestClient)
    }

    then:
    thrown(ApiException)

    expect:
    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          name "test"
          status ERROR
          errorEvent(ApiException, "Testing Failure")
          hasNoParent()
        }
        span(1) {
          name "MessageCreator.create"
          kind CLIENT
          status ERROR
          errorEvent(ApiException, "Testing Failure")
        }
      }
    }
  }

  def "root span"() {
    setup:
    twilioRestClient.getObjectMapper() >> new ObjectMapper()

    1 * twilioRestClient.request(_) >> new Response(new ByteArrayInputStream(MESSAGE_RESPONSE_BODY.getBytes()), 200)

    Message message = Message.creator(
      new PhoneNumber("+1 555 720 5913"),  // To number
      new PhoneNumber("+1 555 555 5215"),  // From number
      "Hello world!"                    // SMS body
    ).create(twilioRestClient)

    expect:

    message.body == "Hello, World!"

    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "MessageCreator.create"
          kind CLIENT
          hasNoParent()
          attributes {
            "twilio.type" "com.twilio.rest.api.v2010.account.Message"
            "twilio.account" "AC14984e09e497506cf0d5eb59b1f6ace7"
            "twilio.sid" "MMXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX"
            "twilio.status" "sent"
          }
        }
      }
    }
  }

  def "asynchronous call"(a) {
    setup:
    twilioRestClient.getObjectMapper() >> new ObjectMapper()

    1 * twilioRestClient.request(_) >> new Response(new ByteArrayInputStream(MESSAGE_RESPONSE_BODY.getBytes()), 200)

    when:

    Message message = runWithSpan("test") {

      ListenableFuture<Message> future = Message.creator(
        new PhoneNumber("+1 555 720 5913"),  // To number
        new PhoneNumber("+1 555 555 5215"),  // From number
        "Hello world!"                    // SMS body
      ).createAsync(twilioRestClient)

      try {
        return future.get(10, TimeUnit.SECONDS)
      } finally {
        // Give the future callback a chance to run
        Thread.sleep(1000)
      }
    }

    then:

    message != null
    message.body == "Hello, World!"

    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          name "test"
          hasNoParent()
          attributes {
          }
        }
        span(1) {
          name "MessageCreator.createAsync"
          kind CLIENT
          attributes {
            "twilio.type" "com.twilio.rest.api.v2010.account.Message"
            "twilio.account" "AC14984e09e497506cf0d5eb59b1f6ace7"
            "twilio.sid" "MMXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX"
            "twilio.status" "sent"
          }
        }
      }
    }

    cleanup:
    Twilio.getExecutorService().shutdown()
    Twilio.setExecutorService(null)
    Twilio.setRestClient(null)

    where:
    a | _
    1 | _
    2 | _
  }

  def "asynchronous error"() {
    setup:
    twilioRestClient.getObjectMapper() >> new ObjectMapper()

    1 * twilioRestClient.request(_) >> new Response(new ByteArrayInputStream(ERROR_RESPONSE_BODY.getBytes()), 500)

    when:
    runWithSpan("test") {
      ListenableFuture<Message> future = Message.creator(
        new PhoneNumber("+1 555 720 5913"),  // To number
        new PhoneNumber("+1 555 555 5215"),  // From number
        "Hello world!"                    // SMS body
      ).createAsync(twilioRestClient)

      try {
        return future.get(10, TimeUnit.SECONDS)
      } finally {
        Thread.sleep(1000)
      }
    }

    then:
    thrown(ExecutionException)

    expect:

    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          name "test"
          status ERROR
          errorEvent(ApiException, "Testing Failure")
          hasNoParent()
        }
        span(1) {
          name "MessageCreator.createAsync"
          kind CLIENT
          status ERROR
          errorEvent(ApiException, "Testing Failure")
        }
      }
    }
  }

  private CloseableHttpResponse mockResponse(String body, int statusCode) {
    HttpEntity httpEntity = Mock()
    httpEntity.getContent() >> { new ByteArrayInputStream(body.getBytes()) }
    httpEntity.isRepeatable() >> true
    httpEntity.getContentLength() >> body.length()

    StatusLine statusLine = Mock()
    statusLine.getStatusCode() >> statusCode

    CloseableHttpResponse httpResponse = Mock()
    httpResponse.getEntity() >> httpEntity
    httpResponse.getStatusLine() >> statusLine

    return httpResponse
  }
}
