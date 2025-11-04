/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jfinal;

import com.jfinal.config.Constants;
import com.jfinal.config.Handlers;
import com.jfinal.config.Interceptors;
import com.jfinal.config.JFinalConfig;
import com.jfinal.config.Plugins;
import com.jfinal.config.Routes;
import com.jfinal.template.Engine;

public class TestConfig extends JFinalConfig {
  // 配置常量值
  @Override
  public void configConstant(Constants me) {
    me.setDevMode(true);
  }

  // 配置路由
  @Override
  public void configRoute(Routes me) {
    me.add("/", TestController.class);
  }

  @Override
  public void configEngine(Engine me) {}

  @Override
  public void configPlugin(Plugins me) {}

  @Override
  public void configInterceptor(Interceptors me) {}

  @Override
  public void configHandler(Handlers me) {}
}
