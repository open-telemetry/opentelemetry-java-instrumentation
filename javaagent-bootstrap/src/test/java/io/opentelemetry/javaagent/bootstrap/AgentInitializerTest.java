/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class AgentInitializerTest {
  @AfterEach
  void tearDown() {
    System.clearProperty("foo");
    System.clearProperty("bar");
  }

  @Test
  void agentArgsSystemProperties() {
    AgentInitializer.setSystemProperties("foo=bar=baz;bar=b,c");

    assertThat(System.getProperty("foo")).isEqualTo("bar=baz");
    assertThat(System.getProperty("bar")).isEqualTo("b,c");
  }

  // This list of values have been compiled by invoking every binary in ${JAVA_HOME}/bin
  @ParameterizedTest
  @ValueSource(
      strings = {
        // sample from JDK 25+
        "com.sun.tools.javac.Main Foo.java",
        "jdk.compiler/com.sun.tools.javac.Main Foo.java",
        "jdk.jartool/sun.tools.jar.Main --version",
        "jdk.jshell/jdk.internal.jshell.tool.JShellToolProvider --version",
        "jdk.jlink/jdk.tools.jlink.internal.Main --version",
        "java.base/sun.security.tools.keytool.Main -help",
        "jdk.javadoc/jdk.javadoc.internal.tool.Main --version",
        "jdk.jdeps/com.sun.tools.jdeps.Main --version",
        "jdk.jdeps/com.sun.tools.javap.Main --version",
        "jdk.jartool/sun.security.tools.jarsigner.Main",
        "jdk.jcmd/sun.tools.jcmd.JCmd --version",
        "jdk.jcmd/sun.tools.jps.Jps --version",
        // JDK 11+
        "java.base/com.sun.java.util.jar.pack.Driver",
        "java.base/sun.security.tools.keytool.Main",
        "java.rmi/sun.rmi.registry.RegistryImpl",
        "java.rmi/sun.rmi.server.Activation",
        "jdk.aot/jdk.tools.jaotc.Main",
        "jdk.hotspot.agent/sun.jvm.hotspot.SALauncher",
        "jdk.jartool/sun.security.tools.jarsigner.Main",
        "jdk.javadoc/jdk.javadoc.internal.tool.Main",
        "jdk.jcmd/sun.tools.jcmd.JCmd",
        "jdk.jcmd/sun.tools.jps.Jps",
        "jdk.jfr/jdk.jfr.internal.tool.Main",
        "jdk.jlink/jdk.tools.jimage.Main",
        "jdk.jlink/jdk.tools.jlink.internal.Main",
        "jdk.jlink/jdk.tools.jmod.Main",
        "jdk.jshell/jdk.internal.jshell.tool.JShellToolProvider",
        "jdk.rmic/sun.rmi.rmic.Main",
        // JDK 8 (before JPMS)
        "com.sun.corba.se.impl.activation.ORBD",
        "com.sun.corba.se.impl.activation.ServerTool",
        "com.sun.corba.se.impl.naming.cosnaming.TransientNameServer",
        "com.sun.javafx.tools.packager.Main",
        "com.sun.java.util.jar.pack.Driver",
        "com.sun.tools.corba.se.idl.toJavaPortable.Compile",
        "com.sun.tools.example.debug.tty.TTY",
        "com.sun.tools.extcheck.Main",
        "com.sun.tools.hat.Main",
        "com.sun.tools.internal.jxc.SchemaGenerator",
        "com.sun.tools.internal.ws.WsGen",
        "com.sun.tools.internal.ws.WsImport",
        "com.sun.tools.internal.xjc.Driver",
        "com.sun.tools.javac.Main",
        "com.sun.tools.javadoc.Main",
        "com.sun.tools.javah.Main",
        "com.sun.tools.javap.Main",
        "com.sun.tools.jdeps.Main",
        "com.sun.tools.script.shell.Main",
        "jdk.jfr.internal.tool.Main",
        "jdk.nashorn.tools.Shell",
        "sun.applet.Main",
        "sun.jvm.hotspot.CLHSDB",
        "sun.jvm.hotspot.HSDB",
        "sun.jvm.hotspot.jdi.SADebugServer",
        "sun.rmi.registry.RegistryImpl",
        "sun.rmi.rmic.Main",
        "sun.rmi.server.Activation",
        "sun.rmi.transport.proxy.CGIHandler",
        "sun.security.tools.jarsigner.Main",
        "sun.security.tools.keytool.Main",
        "sun.security.tools.policytool.PolicyTool",
        "sun.tools.jar.Main",
        "sun.tools.jcmd.JCmd",
        "sun.tools.jconsole.JConsole",
        "sun.tools.jinfo.JInfo",
        "sun.tools.jmap.JMap",
        "sun.tools.jps.Jps",
        "sun.tools.jstack.JStack",
        "sun.tools.jstatd.Jstatd",
        "sun.tools.jstat.Jstat",
        "sun.tools.native2ascii.Main",
        "sun.tools.serialver.SerialVer",
      })
  void isJdkToolMainClass_true(String sunJavaCommand) {
    assertThat(AgentInitializer.isJdkToolMainClass(sunJavaCommand)).isTrue();
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(
      strings = {
        "com.example.Main arg1 arg2",
        "myapp/com.example.Main arg1 arg2",
        "/path/to/application.jar arg1",
      })
  void isJdkToolMainClass_false(String sunJavaCommand) {
    assertThat(AgentInitializer.isJdkToolMainClass(sunJavaCommand)).isFalse();
  }
}
