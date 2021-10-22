/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import com.example.hello.HelloController;
import com.example.hello.TestFilter;
import java.io.File;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.EnterpriseArchive;
import org.jboss.shrinkwrap.api.spec.WebArchive;

// spring is inside ear/lib
public class LibsInEarOpenTelemetryHandlerMappingFilterTest
    extends AbstractOpenTelemetryHandlerMappingFilterTest {
  @Deployment
  static Archive<?> createDeployment() {
    WebArchive war =
        ShrinkWrap.create(WebArchive.class, "test.war")
            .addAsWebInfResource("web.xml")
            .addAsWebInfResource("dispatcher-servlet.xml")
            .addAsWebInfResource("applicationContext.xml")
            .addClass(HelloController.class)
            .addClass(TestFilter.class);

    EnterpriseArchive ear =
        ShrinkWrap.create(EnterpriseArchive.class)
            .setApplicationXML("application.xml")
            .addAsModule(war)
            .addAsLibraries(new File("build/app-libs").listFiles());

    return ear;
  }
}
