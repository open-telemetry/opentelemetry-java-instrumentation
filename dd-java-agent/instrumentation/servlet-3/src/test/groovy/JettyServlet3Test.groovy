import datadog.trace.agent.test.utils.PortUtils
import org.eclipse.jetty.security.ConstraintMapping
import org.eclipse.jetty.security.ConstraintSecurityHandler
import org.eclipse.jetty.security.HashLoginService
import org.eclipse.jetty.security.LoginService
import org.eclipse.jetty.security.authentication.BasicAuthenticator
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.servlet.ServletContextHandler
import org.eclipse.jetty.util.security.Constraint
import spock.lang.Shared

import javax.servlet.Servlet

class JettyServlet3Test extends AbstractServlet3Test<ServletContextHandler> {

  @Shared
  private Server jettyServer

  def setupSpec() {
    port = PortUtils.randomOpenPort()
    jettyServer = new Server(port)

    ServletContextHandler servletContext = new ServletContextHandler(null, "/$context")
    setupAuthentication(jettyServer, servletContext)
    setupServlets(servletContext)
    jettyServer.setHandler(servletContext)

    jettyServer.start()

    System.out.println(
      "Jetty server: http://localhost:" + port + "/")
  }

  def cleanupSpec() {
    jettyServer.stop()
    jettyServer.destroy()
  }

  @Override
  String getContext() {
    return "jetty-context"
  }

  @Override
  void addServlet(ServletContextHandler servletContext, String url, Class<Servlet> servlet) {
    servletContext.addServlet(servlet, url)
  }

  static setupAuthentication(Server jettyServer, ServletContextHandler servletContext) {
    ConstraintSecurityHandler authConfig = new ConstraintSecurityHandler()

    Constraint constraint = new Constraint()
    constraint.setName("auth")
    constraint.setAuthenticate(true)
    constraint.setRoles("role")

    ConstraintMapping mapping = new ConstraintMapping()
    mapping.setPathSpec("/auth/*")
    mapping.setConstraint(constraint)

    authConfig.setConstraintMappings(mapping)
    authConfig.setAuthenticator(new BasicAuthenticator())

    LoginService loginService = new HashLoginService("TestRealm",
      "src/test/resources/realm.properties")
    authConfig.setLoginService(loginService)
    jettyServer.addBean(loginService)

    servletContext.setSecurityHandler(authConfig)
  }
}
