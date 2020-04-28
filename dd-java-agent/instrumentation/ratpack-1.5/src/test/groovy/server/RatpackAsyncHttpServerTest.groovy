package server

import ratpack.exec.Promise
import ratpack.groovy.test.embed.GroovyEmbeddedApp
import ratpack.test.embed.EmbeddedApp

import static datadog.trace.agent.test.base.HttpServerTest.ServerEndpoint.ERROR
import static datadog.trace.agent.test.base.HttpServerTest.ServerEndpoint.EXCEPTION
import static datadog.trace.agent.test.base.HttpServerTest.ServerEndpoint.QUERY_PARAM
import static datadog.trace.agent.test.base.HttpServerTest.ServerEndpoint.REDIRECT
import static datadog.trace.agent.test.base.HttpServerTest.ServerEndpoint.SUCCESS

class RatpackAsyncHttpServerTest extends RatpackHttpServerTest {

  @Override
  EmbeddedApp startServer(int bindPort) {
    def ratpack = GroovyEmbeddedApp.ratpack {
      serverConfig {
        port bindPort
      }
      bindings {
        bind TestErrorHandler
      }
      handlers {
        prefix(SUCCESS.rawPath()) {
          all {
            Promise.sync {
              SUCCESS
            } then { ServerEndpoint endpoint ->
              controller(endpoint) {
                context.response.status(endpoint.status).send(endpoint.body)
              }
            }
          }
        }
        prefix(QUERY_PARAM.rawPath()) {
          all {
            Promise.sync {
              QUERY_PARAM
            } then { ServerEndpoint endpoint ->
              controller(endpoint) {
                context.response.status(endpoint.status).send(request.query)
              }
            }
          }
        }
        prefix(REDIRECT.rawPath()) {
          all {
            Promise.sync {
              REDIRECT
            } then { ServerEndpoint endpoint ->
              controller(endpoint) {
                context.redirect(endpoint.body)
              }
            }
          }
        }
        prefix(ERROR.rawPath()) {
          all {
            Promise.sync {
              ERROR
            } then { ServerEndpoint endpoint ->
              controller(endpoint) {
                context.response.status(endpoint.status).send(endpoint.body)
              }
            }
          }
        }
        prefix(EXCEPTION.rawPath()) {
          all {
            Promise.sync {
              EXCEPTION
            } then { ServerEndpoint endpoint ->
              controller(endpoint) {
                throw new Exception(endpoint.body)
              }
            }
          }
        }
      }
    }
    ratpack.server.start()

    assert ratpack.address.port == bindPort
    return ratpack
  }
}
