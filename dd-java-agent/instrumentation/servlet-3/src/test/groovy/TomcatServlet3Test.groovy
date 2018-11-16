import com.google.common.io.Files
import org.apache.catalina.Context
import org.apache.catalina.LifecycleState
import org.apache.catalina.realm.MemoryRealm
import org.apache.catalina.realm.MessageDigestCredentialHandler
import org.apache.catalina.startup.Tomcat
import org.apache.tomcat.JarScanFilter
import org.apache.tomcat.JarScanType
import org.apache.tomcat.util.descriptor.web.LoginConfig
import org.apache.tomcat.util.descriptor.web.SecurityCollection
import org.apache.tomcat.util.descriptor.web.SecurityConstraint
import spock.lang.Shared

import javax.servlet.Servlet

class TomcatServlet3Test extends AbstractServlet3Test<Context> {

  @Shared
  Tomcat tomcatServer
  @Shared
  def baseDir = Files.createTempDir()

  def setupSpec() {
    tomcatServer = new Tomcat()
    tomcatServer.setPort(port)
    tomcatServer.getConnector()

    baseDir.deleteOnExit()
    tomcatServer.setBaseDir(baseDir.getAbsolutePath())

    final File applicationDir = new File(baseDir, "/webapps/ROOT")
    if (!applicationDir.exists()) {
      applicationDir.mkdirs()
      applicationDir.deleteOnExit()
    }
    Context servletContext = tomcatServer.addWebapp("/$context", applicationDir.getAbsolutePath())
    // Speed up startup by disabling jar scanning:
    servletContext.getJarScanner().setJarScanFilter(new JarScanFilter() {
      @Override
      boolean check(JarScanType jarScanType, String jarName) {
        return false
      }
    })

    setupAuthentication(tomcatServer, servletContext)
    setupServlets(servletContext)

    tomcatServer.start()
    System.out.println(
      "Tomcat server: http://" + tomcatServer.getHost().getName() + ":" + port + "/")
  }

  def cleanupSpec() {
    tomcatServer.stop()
    tomcatServer.destroy()
  }

  @Override
  String getContext() {
    return "tomcat-context"
  }

  @Override
  void addServlet(Context servletContext, String url, Class<Servlet> servlet) {
    String name = UUID.randomUUID()
    Tomcat.addServlet(servletContext, name, servlet.newInstance())
    servletContext.addServletMappingDecoded(url, name)
  }

  private setupAuthentication(Tomcat server, Context servletContext) {
    // Login Config
    LoginConfig authConfig = new LoginConfig()
    authConfig.setAuthMethod("BASIC")

    // adding constraint with role "test"
    SecurityConstraint constraint = new SecurityConstraint()
    constraint.addAuthRole("role")

    // add constraint to a collection with pattern /second
    SecurityCollection collection = new SecurityCollection()
    collection.addPattern("/auth/*")
    constraint.addCollection(collection)

    servletContext.setLoginConfig(authConfig)
    // does the context need a auth role too?
    servletContext.addSecurityRole("role")
    servletContext.addConstraint(constraint)

    // add tomcat users to realm
    MemoryRealm realm = new MemoryRealm() {
      protected void startInternal() {
        credentialHandler = new MessageDigestCredentialHandler()
        setState(LifecycleState.STARTING)
      }
    }
    realm.addUser(user, pass, "role")
    server.getEngine().setRealm(realm)

    servletContext.setLoginConfig(authConfig)
  }
}
