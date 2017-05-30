package com.datadoghq.trace.instrument;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * @author renaudboutet
 */
public class JettyServletInstrumentationTest {

	private Server jettyServer;
	private ServletContextHandler servletContext;

	@Before
	public void beforeTest() throws Exception {
		servletContext = new ServletContextHandler();
		servletContext.setContextPath("/");
		//		servletContext.addServlet(TestServlet.class, "/hello");

		jettyServer = new Server(0);
		jettyServer.setHandler(servletContext);
		jettyServer.start();
	}
	
	 @Test
	 public void testIsTracingFilterPresent() throws IOException {
		 assertThat( servletContext.getServletContext().getFilterRegistration("tracingFilter")).isNotNull();
	 }

	@After
	public void afterTest() throws Exception {
		jettyServer.stop();
		jettyServer.join();
	}

}
