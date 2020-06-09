# OpenTelemetry Instrumentation: Spring and Spring Boot
<!-- ReadMe is in progress -->
<!-- TO DO: Add sections for starter guide -->


This package streamlines the manual instrumentation process of OpenTelemetry for [Spring](https://spring.io/projects/spring-framework) and [Spring Boot](https://spring.io/projects/spring-boot) applications. It will enable you to add traces to requests and database calls with minimal changes to application code. This package will not fully automate your OpenTelemetry instrumentation, instead, it will provide you with better tools to instrument your own code. 

The first section will walk you through span creation and propagation using the OpenTelemetry Java API and [Spring's RestTemplate Http Web Client](https://spring.io/guides/gs/consuming-rest/). This approach will use the "vanilla" OpenTelemetry API to make explicit tracing calls within an application's controller. 

The second section will build on the first. It will walk you through implementing spring-web handler and interceptor interfaces to create traces with minimal changes to existing application code. Using the OpenTelemetry API, this approach involves copy and pasting files and a significant amount of manual configurations. 

The third section will walk you through the annotations and configurations defined in the opentelemetry-contrib-spring package. This section will equip you with new tools to streamline the set up and instrumentation of OpenTelemetry on Spring and Spring Boot applications. With these tools you will be able to setup distributed tracing with little to no changes to existing configurations and easily customize traces with minor additions to application code.  
