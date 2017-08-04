package com.datadoghq.example.dropwizard;

import com.datadoghq.example.dropwizard.resources.SimpleCrudResource;
import io.dropwizard.Application;
import io.dropwizard.Configuration;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;

public class BookApplication extends Application<Configuration> {
  public static void main(String[] args) throws Exception {
    new BookApplication().run(args);
  }

  @Override
  public String getName() {
    return "hello-world";
  }

  @Override
  public void initialize(Bootstrap<Configuration> bootstrap) {
    // nothing to do yet
  }

  @Override
  public void run(Configuration configuration, Environment environment) {

    environment.jersey().register(new SimpleCrudResource());
  }
}
