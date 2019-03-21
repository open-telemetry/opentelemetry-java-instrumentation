package test

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.common.util.concurrent.ListenableFuture
import com.twilio.Twilio
import com.twilio.exception.ApiException
import com.twilio.http.Response
import com.twilio.http.TwilioRestClient
import com.twilio.rest.api.v2010.account.Message
import com.twilio.type.PhoneNumber
import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.api.DDSpanTypes
import io.opentracing.tag.Tags
import io.opentracing.util.GlobalTracer

import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

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

  def "synchronous call"() {
    setup:
    twilioRestClient.getObjectMapper() >> new ObjectMapper()

    1 * twilioRestClient.request(_) >> new Response(new ByteArrayInputStream(MESSAGE_RESPONSE_BODY.getBytes()), 200)

    GlobalTracer.get().buildSpan("test").startActive(true)

    Message message = Message.creator(
      new PhoneNumber("+1 555 720 5913"),  // To number
      new PhoneNumber("+1 555 555 5215"),  // From number
      "Hello world!"                    // SMS body
    ).create(twilioRestClient)

    Thread.sleep(1000);

    def scope = GlobalTracer.get().scopeManager().active()
    if (scope) {
      scope.close()
    }

    expect:

    message.body == "Hello, World!"

    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          serviceName "unnamed-java-app"
          operationName "test"
          resourceName "test"
          errored false
          parent()
        }
        span(1) {
          serviceName "twilio-sdk"
          operationName "twilio.sdk"
          resourceName "api.v2010.account.MessageCreator.create"
          spanType DDSpanTypes.HTTP_CLIENT
          errored false
          tags {
            "$Tags.COMPONENT.key" "twilio-sdk"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            "twilio.type" "com.twilio.rest.api.v2010.account.Message"
            "twilio.account" "AC14984e09e497506cf0d5eb59b1f6ace7"
            "twilio.sid" "MMXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX"
            "twilio.status" "sent"
            defaultTags()
          }
        }
      }
    }
  }

  def "Sync Failure"() {
    setup:

    twilioRestClient.getObjectMapper() >> new ObjectMapper()

    1 * twilioRestClient.request(_) >> new Response(new ByteArrayInputStream(ERROR_RESPONSE_BODY.getBytes()), 500)

    GlobalTracer.get().buildSpan("test").startActive(true)

    when:
    Message.creator(
      new PhoneNumber("+1 555 720 5913"),  // To number
      new PhoneNumber("+1 555 555 5215"),  // From number
      "Hello world!"                    // SMS body
    ).create(twilioRestClient)

    then:
    thrown(ApiException)

    def scope = GlobalTracer.get().scopeManager().active()
    if (scope) {
      scope.close()
    }

    expect:

    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          serviceName "unnamed-java-app"
          operationName "test"
          resourceName "test"
          errored false
          parent()
        }
        span(1) {
          serviceName "twilio-sdk"
          operationName "twilio.sdk"
          resourceName "api.v2010.account.MessageCreator.create"
          spanType DDSpanTypes.HTTP_CLIENT
          errored true
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
          serviceName "twilio-sdk"
          operationName "twilio.sdk"
          resourceName "api.v2010.account.MessageCreator.create"
          parent()
          spanType DDSpanTypes.HTTP_CLIENT
          errored false
          tags {
            "$Tags.COMPONENT.key" "twilio-sdk"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            "twilio.type" "com.twilio.rest.api.v2010.account.Message"
            "twilio.account" "AC14984e09e497506cf0d5eb59b1f6ace7"
            "twilio.sid" "MMXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX"
            "twilio.status" "sent"
            defaultTags()
          }
        }
      }
    }
  }

  def "asynchronous call"() {
    setup:
    twilioRestClient.getObjectMapper() >> new ObjectMapper()

    1 * twilioRestClient.request(_) >> new Response(new ByteArrayInputStream(MESSAGE_RESPONSE_BODY.getBytes()), 200)

    GlobalTracer.get().buildSpan("test").startActive(true)

    ListenableFuture<Message> future = Message.creator(
      new PhoneNumber("+1 555 720 5913"),  // To number
      new PhoneNumber("+1 555 555 5215"),  // From number
      "Hello world!"                    // SMS body
    ).createAsync(twilioRestClient)

    Message message = future.get(10, TimeUnit.SECONDS)

    def scope = GlobalTracer.get().scopeManager().active()
    if (scope) {
      scope.close()
    }

    expect:

    message != null
    message.body == "Hello, World!"

    assertTraces(1) {
      trace(0, 3) {
        span(0) {
          serviceName "unnamed-java-app"
          operationName "test"
          resourceName "test"
          errored false
          parent()
        }
        span(1) {
          serviceName "twilio-sdk"
          operationName "twilio.sdk"
          resourceName "api.v2010.account.MessageCreator.createAsync"
          spanType DDSpanTypes.HTTP_CLIENT
          errored false
          tags {
            "$Tags.COMPONENT.key" "twilio-sdk"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            "twilio.type" "com.twilio.rest.api.v2010.account.Message"
            "twilio.account" "AC14984e09e497506cf0d5eb59b1f6ace7"
            "twilio.sid" "MMXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX"
            "twilio.status" "sent"
            defaultTags()
          }
        }
        span(2) {
          serviceName "twilio-sdk"
          operationName "twilio.sdk"
          resourceName "api.v2010.account.MessageCreator.create"
          spanType DDSpanTypes.HTTP_CLIENT
          errored false
          tags {
            "$Tags.COMPONENT.key" "twilio-sdk"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            "twilio.type" "com.twilio.rest.api.v2010.account.Message"
            "twilio.account" "AC14984e09e497506cf0d5eb59b1f6ace7"
            "twilio.sid" "MMXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX"
            "twilio.status" "sent"
            defaultTags()
          }
        }
      }
    }
  }

  def "asynchronous error"() {
    setup:
    twilioRestClient.getObjectMapper() >> new ObjectMapper()

    1 * twilioRestClient.request(_) >> new Response(new ByteArrayInputStream(ERROR_RESPONSE_BODY.getBytes()), 500)

    GlobalTracer.get().buildSpan("test").startActive(true)

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
      def scope = GlobalTracer.get().scopeManager().active()
      if (scope) {
        scope.close()
      }
    }

    then:
    thrown(ExecutionException)

    expect:

    assertTraces(1) {
      trace(0, 3) {
        span(0) {
          serviceName "unnamed-java-app"
          operationName "test"
          resourceName "test"
          errored false
          parent()
        }
        span(1) {
          serviceName "twilio-sdk"
          operationName "twilio.sdk"
          resourceName "api.v2010.account.MessageCreator.createAsync"
          spanType DDSpanTypes.HTTP_CLIENT
          errored true
        }
        span(2) {
          serviceName "twilio-sdk"
          operationName "twilio.sdk"
          resourceName "api.v2010.account.MessageCreator.create"
          spanType DDSpanTypes.HTTP_CLIENT
          errored true
        }
      }
    }
  }

}
