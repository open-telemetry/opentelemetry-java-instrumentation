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
import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.api.DDSpanTypes
import datadog.trace.api.DDTags
import datadog.trace.instrumentation.api.Tags
import org.apache.http.HttpEntity
import org.apache.http.HttpStatus
import org.apache.http.StatusLine
import org.apache.http.client.methods.CloseableHttpResponse
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClientBuilder

import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

import static datadog.trace.instrumentation.api.AgentTracer.activateSpan
import static datadog.trace.instrumentation.api.AgentTracer.startSpan

class TwilioClientTest extends AgentTestRunner {

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

  def "synchronous message"() {
    setup:
    twilioRestClient.getObjectMapper() >> new ObjectMapper()

    1 * twilioRestClient.request(_) >> new Response(new ByteArrayInputStream(MESSAGE_RESPONSE_BODY.getBytes()), 200)

    def scope = activateSpan(startSpan("test"), true)

    Message message = Message.creator(
      new PhoneNumber("+1 555 720 5913"),  // To number
      new PhoneNumber("+1 555 555 5215"),  // From number
      "Hello world!"                    // SMS body
    ).create(twilioRestClient)

    scope.close()

    expect:

    message.body == "Hello, World!"

    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          operationName "test"
          errored false
          parent()
          tags {
          }
        }
        span(1) {
          operationName "twilio.sdk"
          errored false
          tags {
            "$DDTags.SERVICE_NAME" "twilio-sdk"
            "$DDTags.RESOURCE_NAME" "api.v2010.account.MessageCreator.create"
            "$DDTags.SPAN_TYPE" DDSpanTypes.HTTP_CLIENT
            "$Tags.COMPONENT" "twilio-sdk"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
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

    def scope = activateSpan(startSpan("test"), true)

    Call call = Call.creator(
      new PhoneNumber("+15558881234"),  // To number
      new PhoneNumber("+15559994321"),  // From number

      // Read TwiML at this URL when a call connects (hold music)
      new URI("http://twimlets.com/holdmusic?Bucket=com.twilio.music.ambient")
    ).create(twilioRestClient)

    scope.close()

    expect:

    call.status == Call.Status.COMPLETED

    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          operationName "test"
          errored false
          parent()
          tags {
          }
        }
        span(1) {
          operationName "twilio.sdk"
          errored false
          tags {
            "$DDTags.SERVICE_NAME" "twilio-sdk"
            "$DDTags.RESOURCE_NAME" "api.v2010.account.CallCreator.create"
            "$DDTags.SPAN_TYPE" DDSpanTypes.HTTP_CLIENT
            "$Tags.COMPONENT" "twilio-sdk"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
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
    HttpClientBuilder clientBuilder = Mock()
    CloseableHttpClient httpClient = Mock()
    CloseableHttpResponse httpResponse = Mock()
    HttpEntity httpEntity = Mock()
    StatusLine statusLine = Mock()

    clientBuilder.build() >> httpClient

    httpClient.execute(_) >> httpResponse

    httpResponse.getEntity() >> httpEntity
    httpResponse.getStatusLine() >> statusLine

    httpEntity.getContent() >> { new ByteArrayInputStream(MESSAGE_RESPONSE_BODY.getBytes()) }
    httpEntity.isRepeatable() >> true
    httpEntity.getContentLength() >> MESSAGE_RESPONSE_BODY.length()

    statusLine.getStatusCode() >> HttpStatus.SC_OK

    NetworkHttpClient networkHttpClient = new NetworkHttpClient(clientBuilder)

    TwilioRestClient realTwilioRestClient =
      new TwilioRestClient.Builder("username", "password")
        .accountSid(ACCOUNT_SID)
        .httpClient(networkHttpClient)
        .build()

    def scope = activateSpan(startSpan("test"), true)

    Message message = Message.creator(
      new PhoneNumber("+1 555 720 5913"),  // To number
      new PhoneNumber("+1 555 555 5215"),  // From number
      "Hello world!"                    // SMS body
    ).create(realTwilioRestClient)

    scope.close()

    expect:

    message.body == "Hello, World!"

    assertTraces(1) {
      trace(0, 3) {
        span(0) {
          operationName "test"
          errored false
          parent()
          tags {
          }
        }
        span(1) {
          operationName "twilio.sdk"
          errored false
          tags {
            "$DDTags.SERVICE_NAME" "twilio-sdk"
            "$DDTags.RESOURCE_NAME" "api.v2010.account.MessageCreator.create"
            "$DDTags.SPAN_TYPE" DDSpanTypes.HTTP_CLIENT
            "$Tags.COMPONENT" "twilio-sdk"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
            "twilio.type" "com.twilio.rest.api.v2010.account.Message"
            "twilio.account" "AC14984e09e497506cf0d5eb59b1f6ace7"
            "twilio.sid" "MMXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX"
            "twilio.status" "sent"
          }
        }
        span(2) {
          operationName "http.request"
          errored false
          tags {
            "$DDTags.SPAN_TYPE" DDSpanTypes.HTTP_CLIENT
            "$Tags.COMPONENT" "apache-httpclient"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
            "$Tags.PEER_HOSTNAME" String
            "$Tags.HTTP_URL" String
            "$Tags.HTTP_METHOD" String
            "$Tags.HTTP_STATUS" Integer
          }
        }
      }
    }
  }

  def "http client retry"() {
    setup:
    HttpClientBuilder clientBuilder = Mock()
    CloseableHttpClient httpClient = Mock()
    CloseableHttpResponse httpResponse1 = Mock()
    CloseableHttpResponse httpResponse2 = Mock()
    HttpEntity httpEntity1 = Mock()
    HttpEntity httpEntity2 = Mock()
    StatusLine statusLine1 = Mock()
    StatusLine statusLine2 = Mock()

    clientBuilder.build() >> httpClient

    httpClient.execute(_) >>> [httpResponse1, httpResponse2]

    // First response is an HTTP/500 error, which should drive a retry
    httpResponse1.getEntity() >> httpEntity1
    httpResponse1.getStatusLine() >> statusLine1

    httpEntity1.getContent() >> { new ByteArrayInputStream(ERROR_RESPONSE_BODY.getBytes()) }

    httpEntity1.isRepeatable() >> true
    httpEntity1.getContentLength() >> ERROR_RESPONSE_BODY.length()

    statusLine1.getStatusCode() >> HttpStatus.SC_INTERNAL_SERVER_ERROR

    // Second response is HTTP/200 success
    httpResponse2.getEntity() >> httpEntity2
    httpResponse2.getStatusLine() >> statusLine2

    httpEntity2.getContent() >> {
      new ByteArrayInputStream(MESSAGE_RESPONSE_BODY.getBytes())
    }
    httpEntity2.isRepeatable() >> true
    httpEntity2.getContentLength() >> MESSAGE_RESPONSE_BODY.length()

    statusLine2.getStatusCode() >> HttpStatus.SC_OK

    NetworkHttpClient networkHttpClient = new NetworkHttpClient(clientBuilder)

    TwilioRestClient realTwilioRestClient =
      new TwilioRestClient.Builder("username", "password")
        .accountSid(ACCOUNT_SID)
        .httpClient(networkHttpClient)
        .build()

    def scope = activateSpan(startSpan("test"), true)

    Message message = Message.creator(
      new PhoneNumber("+1 555 720 5913"),  // To number
      new PhoneNumber("+1 555 555 5215"),  // From number
      "Hello world!"                    // SMS body
    ).create(realTwilioRestClient)

    scope.close()

    expect:

    message.body == "Hello, World!"

    assertTraces(1) {
      trace(0, 4) {
        span(0) {
          operationName "test"
          errored false
          parent()
          tags {
          }
        }
        span(1) {
          operationName "twilio.sdk"
          errored false
          tags {
            "$DDTags.SERVICE_NAME" "twilio-sdk"
            "$DDTags.RESOURCE_NAME" "api.v2010.account.MessageCreator.create"
            "$DDTags.SPAN_TYPE" DDSpanTypes.HTTP_CLIENT
            "$Tags.COMPONENT" "twilio-sdk"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
            "twilio.type" "com.twilio.rest.api.v2010.account.Message"
            "twilio.account" "AC14984e09e497506cf0d5eb59b1f6ace7"
            "twilio.sid" "MMXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX"
            "twilio.status" "sent"
          }
        }
        span(2) {
          operationName "http.request"
          errored false
          tags {
            "$DDTags.SPAN_TYPE" DDSpanTypes.HTTP_CLIENT
            "$Tags.COMPONENT" "apache-httpclient"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
            "$Tags.PEER_HOSTNAME" String
            "$Tags.HTTP_URL" String
            "$Tags.HTTP_METHOD" String
            "$Tags.HTTP_STATUS" Integer
          }
        }
        span(3) {
          operationName "http.request"
          errored true
          tags {
            "$DDTags.SPAN_TYPE" DDSpanTypes.HTTP_CLIENT
            "$Tags.COMPONENT" "apache-httpclient"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
            "$Tags.PEER_HOSTNAME" String
            "$Tags.HTTP_URL" String
            "$Tags.HTTP_METHOD" String
            "$Tags.HTTP_STATUS" Integer
          }
        }
      }
    }
  }

  def "http client retry async"() {
    setup:
    HttpClientBuilder clientBuilder = Mock()
    CloseableHttpClient httpClient = Mock()
    CloseableHttpResponse httpResponse1 = Mock()
    CloseableHttpResponse httpResponse2 = Mock()
    HttpEntity httpEntity1 = Mock()
    HttpEntity httpEntity2 = Mock()
    StatusLine statusLine1 = Mock()
    StatusLine statusLine2 = Mock()

    clientBuilder.build() >> httpClient

    httpClient.execute(_) >>> [httpResponse1, httpResponse2]

    // First response is an HTTP/500 error, which should drive a retry
    httpResponse1.getEntity() >> httpEntity1
    httpResponse1.getStatusLine() >> statusLine1

    httpEntity1.getContent() >> { new ByteArrayInputStream(ERROR_RESPONSE_BODY.getBytes()) }

    httpEntity1.isRepeatable() >> true
    httpEntity1.getContentLength() >> ERROR_RESPONSE_BODY.length()

    statusLine1.getStatusCode() >> HttpStatus.SC_INTERNAL_SERVER_ERROR

    // Second response is HTTP/200 success
    httpResponse2.getEntity() >> httpEntity2
    httpResponse2.getStatusLine() >> statusLine2

    httpEntity2.getContent() >> {
      new ByteArrayInputStream(MESSAGE_RESPONSE_BODY.getBytes())
    }
    httpEntity2.isRepeatable() >> true
    httpEntity2.getContentLength() >> MESSAGE_RESPONSE_BODY.length()

    statusLine2.getStatusCode() >> HttpStatus.SC_OK

    NetworkHttpClient networkHttpClient = new NetworkHttpClient(clientBuilder)

    TwilioRestClient realTwilioRestClient =
      new TwilioRestClient.Builder("username", "password")
        .accountSid(ACCOUNT_SID)
        .httpClient(networkHttpClient)
        .build()

    def scope = activateSpan(startSpan("test"), true)

    ListenableFuture<Message> future = Message.creator(
      new PhoneNumber("+1 555 720 5913"),  // To number
      new PhoneNumber("+1 555 555 5215"),  // From number
      "Hello world!"                    // SMS body
    ).createAsync(realTwilioRestClient)

    Message message
    try {
      message = future.get(10, TimeUnit.SECONDS)
    } finally {
      // Give the future callback a chance to run
      Thread.sleep(1000)
      scope.close()
    }

    expect:

    message.body == "Hello, World!"

    assertTraces(1) {
      trace(0, 5) {
        span(0) {
          operationName "test"
          errored false
          parent()
          tags {
          }
        }
        span(1) {
          operationName "twilio.sdk"
          errored false
          tags {
            "$DDTags.SERVICE_NAME" "twilio-sdk"
            "$DDTags.RESOURCE_NAME" "api.v2010.account.MessageCreator.createAsync"
            "$DDTags.SPAN_TYPE" DDSpanTypes.HTTP_CLIENT
            "$Tags.COMPONENT" "twilio-sdk"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
            "twilio.type" "com.twilio.rest.api.v2010.account.Message"
            "twilio.account" "AC14984e09e497506cf0d5eb59b1f6ace7"
            "twilio.sid" "MMXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX"
            "twilio.status" "sent"
          }
        }
        span(2) {
          operationName "twilio.sdk"
          errored false
          tags {
            "$DDTags.SERVICE_NAME" "twilio-sdk"
            "$DDTags.RESOURCE_NAME" "api.v2010.account.MessageCreator.create"
            "$DDTags.SPAN_TYPE" DDSpanTypes.HTTP_CLIENT
            "$Tags.COMPONENT" "twilio-sdk"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
            "twilio.type" "com.twilio.rest.api.v2010.account.Message"
            "twilio.account" "AC14984e09e497506cf0d5eb59b1f6ace7"
            "twilio.sid" "MMXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX"
            "twilio.status" "sent"
          }
        }
        // Spans are reported in reverse order of completion,
        // so the error span is last even though it happened first.
        span(3) {
          operationName "http.request"
          errored false
          tags {
            "$DDTags.SPAN_TYPE" DDSpanTypes.HTTP_CLIENT
            "$Tags.COMPONENT" "apache-httpclient"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
            "$Tags.PEER_HOSTNAME" String
            "$Tags.HTTP_URL" String
            "$Tags.HTTP_METHOD" String
            "$Tags.HTTP_STATUS" Integer
          }
        }
        span(4) {
          operationName "http.request"
          errored true
          tags {
            "$DDTags.SPAN_TYPE" DDSpanTypes.HTTP_CLIENT
            "$Tags.COMPONENT" "apache-httpclient"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
            "$Tags.PEER_HOSTNAME" String
            "$Tags.HTTP_URL" String
            "$Tags.HTTP_METHOD" String
            "$Tags.HTTP_STATUS" Integer
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

    def scope = activateSpan(startSpan("test"), true)

    when:
    Message.creator(
      new PhoneNumber("+1 555 720 5913"),  // To number
      new PhoneNumber("+1 555 555 5215"),  // From number
      "Hello world!"                    // SMS body
    ).create(twilioRestClient)

    then:
    thrown(ApiException)

    scope.close()

    expect:

    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          operationName "test"
          errored false
          parent()
          tags {
          }
        }
        span(1) {
          operationName "twilio.sdk"
          errored true
          tags {
            "$DDTags.SERVICE_NAME" "twilio-sdk"
            "$DDTags.RESOURCE_NAME" "api.v2010.account.MessageCreator.create"
            "$DDTags.SPAN_TYPE" DDSpanTypes.HTTP_CLIENT
            "$Tags.COMPONENT" "twilio-sdk"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
            errorTags(ApiException, "Testing Failure")
          }
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
          operationName "twilio.sdk"
          parent()
          errored false
          tags {
            "$DDTags.SERVICE_NAME" "twilio-sdk"
            "$DDTags.RESOURCE_NAME" "api.v2010.account.MessageCreator.create"
            "$DDTags.SPAN_TYPE" DDSpanTypes.HTTP_CLIENT
            "$Tags.COMPONENT" "twilio-sdk"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
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

    def scope = activateSpan(startSpan("test"), true)

    ListenableFuture<Message> future = Message.creator(
      new PhoneNumber("+1 555 720 5913"),  // To number
      new PhoneNumber("+1 555 555 5215"),  // From number
      "Hello world!"                    // SMS body
    ).createAsync(twilioRestClient)

    Message message
    try {
      message = future.get(10, TimeUnit.SECONDS)
    } finally {
      // Give the future callback a chance to run
      Thread.sleep(1000)
      scope.close()
    }

    then:

    message != null
    message.body == "Hello, World!"

    assertTraces(1) {
      trace(0, 3) {
        span(0) {
          operationName "test"
          errored false
          parent()
          tags {
          }
        }
        span(1) {
          operationName "twilio.sdk"
          errored false
          tags {
            "$DDTags.SERVICE_NAME" "twilio-sdk"
            "$DDTags.RESOURCE_NAME" "api.v2010.account.MessageCreator.createAsync"
            "$DDTags.SPAN_TYPE" DDSpanTypes.HTTP_CLIENT
            "$Tags.COMPONENT" "twilio-sdk"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
            "twilio.type" "com.twilio.rest.api.v2010.account.Message"
            "twilio.account" "AC14984e09e497506cf0d5eb59b1f6ace7"
            "twilio.sid" "MMXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX"
            "twilio.status" "sent"
          }
        }
        span(2) {
          operationName "twilio.sdk"
          errored false
          tags {
            "$DDTags.SERVICE_NAME" "twilio-sdk"
            "$DDTags.RESOURCE_NAME" "api.v2010.account.MessageCreator.create"
            "$DDTags.SPAN_TYPE" DDSpanTypes.HTTP_CLIENT
            "$Tags.COMPONENT" "twilio-sdk"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
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

    def scope = activateSpan(startSpan("test"), true)

    ListenableFuture<Message> future = Message.creator(
      new PhoneNumber("+1 555 720 5913"),  // To number
      new PhoneNumber("+1 555 555 5215"),  // From number
      "Hello world!"                    // SMS body
    ).createAsync(twilioRestClient)


    when:
    Message message
    try {
      message = future.get(10, TimeUnit.SECONDS)

    } finally {
      Thread.sleep(1000)
      scope.close()
    }

    then:
    thrown(ExecutionException)

    expect:

    assertTraces(1) {
      trace(0, 3) {
        span(0) {
          operationName "test"
          errored false
          parent()
          tags {
          }
        }
        span(1) {
          operationName "twilio.sdk"
          errored true
          tags {
            "$DDTags.SERVICE_NAME" "twilio-sdk"
            "$DDTags.RESOURCE_NAME" "api.v2010.account.MessageCreator.createAsync"
            "$DDTags.SPAN_TYPE" DDSpanTypes.HTTP_CLIENT
            "$Tags.COMPONENT" "twilio-sdk"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
            errorTags(ApiException, "Testing Failure")
          }
        }
        span(2) {
          operationName "twilio.sdk"
          errored true
          tags {
            "$DDTags.SERVICE_NAME" "twilio-sdk"
            "$DDTags.RESOURCE_NAME" "api.v2010.account.MessageCreator.create"
            "$DDTags.SPAN_TYPE" DDSpanTypes.HTTP_CLIENT
            "$Tags.COMPONENT" "twilio-sdk"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
            errorTags(ApiException, "Testing Failure")
          }
        }
      }
    }

    cleanup:
    Twilio.getExecutorService().shutdown()
    Twilio.setExecutorService(null)
    Twilio.setRestClient(null)
  }

}
