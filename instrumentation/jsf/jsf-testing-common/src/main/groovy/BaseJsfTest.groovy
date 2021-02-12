/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import static io.opentelemetry.api.trace.SpanKind.INTERNAL
import static io.opentelemetry.instrumentation.test.utils.TraceUtils.basicSpan

import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import io.opentelemetry.instrumentation.test.asserts.TraceAssert
import io.opentelemetry.instrumentation.test.base.HttpServerTestTrait
import io.opentelemetry.sdk.trace.data.SpanData
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import org.eclipse.jetty.annotations.AnnotationConfiguration
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.util.resource.Resource
import org.eclipse.jetty.webapp.WebAppContext
import org.jsoup.Jsoup
import spock.lang.Unroll

abstract class BaseJsfTest extends AgentInstrumentationSpecification implements HttpServerTestTrait<Server> {

  @Override
  Server startServer(int port) {
    String jsfVersion = getJsfVersion()

    List<String> configurationClasses = new ArrayList<>()
    Collections.addAll(configurationClasses, WebAppContext.getDefaultConfigurationClasses())
    configurationClasses.add(AnnotationConfiguration.getName())

    WebAppContext webAppContext = new WebAppContext()
    webAppContext.setContextPath(getContextPath())
    webAppContext.setConfigurationClasses(configurationClasses)
    // set up test application
    webAppContext.setBaseResource(Resource.newSystemResource("test-app-" + jsfVersion))
    // add additional resources for test app
    Resource extraResource = Resource.newSystemResource("test-app-" + jsfVersion + "-extra")
    if (extraResource != null) {
      webAppContext.getMetaData().addWebInfJar(extraResource)
    }
    webAppContext.getMetaData().getWebInfClassesDirs().add(Resource.newClassPathResource("/"))

    def jettyServer = new Server(port)
    jettyServer.connectors.each {
      it.setHost('localhost')
    }

    jettyServer.setHandler(webAppContext)
    jettyServer.start()

    return jettyServer
  }

  abstract String getJsfVersion();

  @Override
  void stopServer(Server server) {
    server.stop()
    server.destroy()
  }

  @Override
  String getContextPath() {
    return "/jetty-context"
  }

  @Unroll
  def "test #path"() {
    setup:
    def url = HttpUrl.get(address.resolve("hello.jsf")).newBuilder().build()
    def request = request(url, "GET", null).build()
    Response response = client.newCall(request).execute()

    expect:
    response.code() == 200
    response.body().string().trim() == "Hello"

    and:
    assertTraces(1) {
      trace(0, 1) {
        basicSpan(it, 0, getContextPath() + "/hello.xhtml", null)
      }
    }

    where:
    path << ['hello.jsf', 'faces/hello.xhtml']
  }

  def "test greeting"() {
    // we need to display the page first before posting data to it
    setup:
    def url = HttpUrl.get(address.resolve("greeting.jsf")).newBuilder().build()
    def request = request(url, "GET", null).build()
    Response response = client.newCall(request).execute()
    def doc = Jsoup.parse(response.body().string())

    expect:
    response.code() == 200
    doc.selectFirst("title").text() == "Hello, World!"

    and:
    assertTraces(1) {
      trace(0, 1) {
        basicSpan(it, 0, getContextPath() + "/greeting.xhtml", null)
      }
    }
    clearExportedData()

    when:
    // extract parameters needed to post back form
    def viewState = doc.selectFirst("[name=javax.faces.ViewState]")?.val()
    def formAction = doc.selectFirst("#app-form").attr("action")
    def jsessionid = formAction.substring(formAction.indexOf("jsessionid=") + "jsessionid=".length())

    then:
    viewState != null
    jsessionid != null

    when:
    // use the session created for first request
    def url2 = HttpUrl.get(address.resolve("greeting.jsf;jsessionid=" + jsessionid)).newBuilder().build()
    // set up form parameter for post
    RequestBody formBody = new FormBody.Builder()
      .add("app-form", "app-form")
      // value used for name is returned in app-form:output-message element
      .add("app-form:name", "test")
      .add("app-form:submit", "Say hello")
      .add("app-form_SUBMIT", "1") // MyFaces
      .add("javax.faces.ViewState", viewState)
      .build()
    def request2 = this.request(url2, "POST", formBody).build()
    Response response2 = client.newCall(request2).execute()
    def responseContent = response2.body().string()
    def doc2 = Jsoup.parse(responseContent)

    then:
    response2.code() == 200
    doc2.getElementById("app-form:output-message").text() == "Hello test"

    and:
    assertTraces(1) {
      trace(0, 2) {
        basicSpan(it, 0, getContextPath() + "/greeting.xhtml", null)
        handlerSpan(it, 1, span(0), "#{greetingForm.submit()}")
      }
    }
  }

  def "test exception"() {
    // we need to display the page first before posting data to it
    setup:
    def url = HttpUrl.get(address.resolve("greeting.jsf")).newBuilder().build()
    def request = request(url, "GET", null).build()
    Response response = client.newCall(request).execute()
    def doc = Jsoup.parse(response.body().string())

    expect:
    response.code() == 200
    doc.selectFirst("title").text() == "Hello, World!"

    and:
    assertTraces(1) {
      trace(0, 1) {
        basicSpan(it, 0, getContextPath() + "/greeting.xhtml", null)
      }
    }
    clearExportedData()

    when:
    // extract parameters needed to post back form
    def viewState = doc.selectFirst("[name=javax.faces.ViewState]").val()
    def formAction = doc.selectFirst("#app-form").attr("action")
    def jsessionid = formAction.substring(formAction.indexOf("jsessionid=") + "jsessionid=".length())

    then:
    viewState != null
    jsessionid != null

    when:
    // use the session created for first request
    def url2 = HttpUrl.get(address.resolve("greeting.jsf;jsessionid=" + jsessionid)).newBuilder().build()
    // set up form parameter for post
    RequestBody formBody = new FormBody.Builder()
      .add("app-form", "app-form")
      // setting name parameter to "exception" triggers throwing exception in GreetingForm
      .add("app-form:name", "exception")
      .add("app-form:submit", "Say hello")
      .add("app-form_SUBMIT", "1") // MyFaces
      .add("javax.faces.ViewState", viewState)
      .build()
    def request2 = this.request(url2, "POST", formBody).build()
    Response response2 = client.newCall(request2).execute()

    then:
    response2.code() == 500

    and:
    assertTraces(1) {
      trace(0, 2) {
        basicSpan(it, 0, getContextPath() + "/greeting.xhtml", null, new Exception("submit exception"))
        handlerSpan(it, 1, span(0), "#{greetingForm.submit()}", new Exception("submit exception"))
      }
    }
  }

  Request.Builder request(HttpUrl url, String method, RequestBody body) {
    return new Request.Builder()
      .url(url)
      .method(method, body)
      .header("User-Agent", TEST_USER_AGENT)
      .header("X-Forwarded-For", TEST_CLIENT_IP)
  }

  void handlerSpan(TraceAssert trace, int index, Object parent, String spanName, Exception expectedException = null) {
    trace.span(index) {
      name spanName
      kind INTERNAL
      errored expectedException != null
      if (expectedException != null) {
        errorEvent(expectedException.getClass(), expectedException.getMessage())
      }
      childOf((SpanData) parent)
    }
  }
}
