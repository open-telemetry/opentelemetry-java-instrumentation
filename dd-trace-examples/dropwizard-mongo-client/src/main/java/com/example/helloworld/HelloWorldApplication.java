package com.example.helloworld;

import com.example.helloworld.resources.SimpleCrudResource;
import io.dropwizard.Application;
import io.dropwizard.Configuration;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;

public class HelloWorldApplication extends Application<Configuration> {
	public static void main(String[] args) throws Exception {
		new HelloWorldApplication().run(args);
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
