/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.weblogic;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import javax.servlet.ServletContext;
import javax.servlet.ServletRequest;

/**
 * Provides wrappers around WebLogic internal class instances.
 */
public class WebLogicEntity {
  private static final MethodHandle REQUEST_GET_CONTEXT;
  private static final MethodHandle CONTEXT_GET_MBEAN;
  private static final MethodHandle CONTEXT_GET_SERVER;
  private static final MethodHandle SERVER_GET_MBEAN;
  private static final MethodHandle MBEAN_GET_PARENT;
  private static final MethodHandle MBEAN_GET_KEY;
  private static final Class<?> MBEAN_CLASS;
  private static final MethodHandle MBEAN_GET_ATTRIBUTE;

  static {
    MethodHandle requestGetContext;
    MethodHandle contextGetMBean;
    MethodHandle contextGetServer;
    MethodHandle serverGetMBean;
    MethodHandle mbeanGetParent;
    MethodHandle mbeanGetKey;
    MethodHandle mbeanGetAttribute;
    Class<?> mbeanClass;

    try {
      requestGetContext =
          MethodHandles.publicLookup()
              .findVirtual(
                  Class.forName("weblogic.servlet.internal.ServletRequestImpl"),
                  "getContext",
                  MethodType.methodType(
                      Class.forName("weblogic.servlet.internal.WebAppServletContext")));
    } catch (Exception e) {
      requestGetContext = null;
    }

    REQUEST_GET_CONTEXT = requestGetContext;

    try {
      contextGetMBean =
          MethodHandles.publicLookup()
              .findVirtual(
                  Class.forName("weblogic.servlet.internal.WebAppServletContext"),
                  "getMBean",
                  MethodType.methodType(
                      Class.forName("weblogic.management.configuration.WebAppComponentMBean")));
    } catch (Exception e) {
      contextGetMBean = null;
    }

    CONTEXT_GET_MBEAN = contextGetMBean;

    try {
      contextGetServer =
          MethodHandles.publicLookup()
              .findVirtual(
                  Class.forName("weblogic.servlet.internal.WebAppServletContext"),
                  "getServer",
                  MethodType.methodType(Class.forName("weblogic.servlet.internal.HttpServer")));
    } catch (Exception e) {
      contextGetServer = null;
    }

    CONTEXT_GET_SERVER = contextGetServer;

    try {
      serverGetMBean =
          MethodHandles.publicLookup()
              .findVirtual(
                  Class.forName("weblogic.servlet.internal.HttpServer"),
                  "getMBean",
                  MethodType.methodType(
                      Class.forName("weblogic.management.configuration.WebServerMBean")));
    } catch (Exception e) {
      serverGetMBean = null;
    }

    SERVER_GET_MBEAN = serverGetMBean;

    try {
      mbeanGetParent =
          MethodHandles.publicLookup()
              .findVirtual(
                  Class.forName("weblogic.descriptor.DescriptorBean"),
                  "getParentBean",
                  MethodType.methodType(Class.forName("weblogic.descriptor.DescriptorBean")));
    } catch (Exception e) {
      mbeanGetParent = null;
    }

    MBEAN_GET_PARENT = mbeanGetParent;

    try {
      mbeanGetKey =
          MethodHandles.publicLookup()
              .findVirtual(
                  Class.forName("weblogic.descriptor.internal.AbstractDescriptorBean"),
                  "_getKey",
                  MethodType.methodType(Object.class));
    } catch (Exception e) {
      mbeanGetKey = null;
    }

    MBEAN_GET_KEY = mbeanGetKey;

    try {
      mbeanGetAttribute =
          MethodHandles.publicLookup()
              .findVirtual(
                  Class.forName("javax.management.DynamicMBean"),
                  "getAttribute",
                  MethodType.methodType(Object.class, String.class));
    } catch (Exception e) {
      mbeanGetAttribute = null;
    }

    MBEAN_GET_ATTRIBUTE = mbeanGetAttribute;

    try {
      mbeanClass = Class.forName("weblogic.management.WebLogicMBean");
    } catch (Exception e) {
      mbeanClass = null;
    }

    MBEAN_CLASS = mbeanClass;
  }

  public static class Request {
    private static final Request NULL = new Request(null);

    public final ServletRequest instance;

    private Request(ServletRequest instance) {
      this.instance = instance;
    }

    public static Request wrap(ServletRequest instance) {
      return instance != null ? new Request(instance) : NULL;
    }

    public Context getContext() {
      try {
        return Context.wrap(
            REQUEST_GET_CONTEXT != null ? REQUEST_GET_CONTEXT.invoke(instance) : null);
      } catch (Throwable throwable) {
        return Context.NULL;
      }
    }
  }

  public static class Context {
    private static final Context NULL = new Context(null);

    public final ServletContext instance;

    private Context(ServletContext instance) {
      this.instance = instance;
    }

    public static Context wrap(Object instance) {
      return instance instanceof ServletContext ? new Context((ServletContext) instance) : NULL;
    }

    public Bean getBean() {
      try {
        return Bean.wrap(CONTEXT_GET_MBEAN != null ? CONTEXT_GET_MBEAN.invoke(instance) : null);
      } catch (Throwable throwable) {
        return Bean.NULL;
      }
    }

    public Server getServer() {
      try {
        return Server.wrap(CONTEXT_GET_SERVER != null ? CONTEXT_GET_SERVER.invoke(instance) : null);
      } catch (Throwable throwable) {
        return Server.NULL;
      }
    }
  }

  public static class Server {
    private static final Server NULL = new Server(null);

    public final Object instance;

    private Server(Object instance) {
      this.instance = instance;
    }

    public static Server wrap(Object instance) {
      return instance != null ? new Server(instance) : NULL;
    }

    public Bean getBean() {
      try {
        return Bean.wrap(SERVER_GET_MBEAN != null ? SERVER_GET_MBEAN.invoke(instance) : null);
      } catch (Throwable throwable) {
        return Bean.NULL;
      }
    }
  }

  public static class Bean {
    private static final Bean NULL = new Bean(null);

    public final Object instance;

    private Bean(Object instance) {
      this.instance = instance;
    }

    public static Bean wrap(Object instance) {
      return instance != null
              && MBEAN_CLASS != null
              && MBEAN_CLASS.isAssignableFrom(instance.getClass())
          ? new Bean(instance)
          : NULL;
    }

    public String getName() {
      try {
        Object key = MBEAN_GET_KEY != null ? MBEAN_GET_KEY.invoke(instance) : null;
        return key != null ? key.toString() : null;
      } catch (Throwable throwable) {
        return null;
      }
    }

    public Bean getParent() {
      try {
        return Bean.wrap(MBEAN_GET_PARENT != null ? MBEAN_GET_PARENT.invoke(instance) : null);
      } catch (Throwable throwable) {
        return Bean.NULL;
      }
    }

    public Object getAttribute(String name) {
      try {
        return MBEAN_GET_ATTRIBUTE != null ? MBEAN_GET_ATTRIBUTE.invoke(instance, name) : null;
      } catch (Throwable throwable) {
        return null;
      }
    }
  }
}
