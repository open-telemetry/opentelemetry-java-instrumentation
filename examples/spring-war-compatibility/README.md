# Spring MVC WAR startup investigation

This standalone application investigates a `RequestPath` loader-constraint error while Spring creates the agent's `otelAutoDispatcherFilter` bean in `ContextLoaderListener`. It uses released Spring and agent artifacts, an external Tomcat, and real HTTP requests. It does not change instrumentation or provide a production deployment recipe.

The ordinary WAR works with and without the agents tested here. A two-WAR deployment with different Spring versions in the WAR and Tomcat's common loader reproduces the reported startup stack. See [the recorded results](RESULTS.md) for the conditions, controls, class origins, and limitations.

## Requirements and isolation

Use Docker with Linux containers and Python 3.9 or newer. No host Java or Maven installation is needed. Run the commands below from this directory. The Maven project is deliberately independent of the repository's Gradle build so the experiment compiles and runs on Java 8 without building an agent from source.

The images pin Maven 3.9.9, Tomcat 9.0.68, their base-image digests, Spring dependencies, and the released agents' SHA-256 checksums. These old framework and runtime versions are for local investigation only. Do not expose them to a network. The runner publishes a randomly allocated port on `127.0.0.1`, disables telemetry exporters, and removes each container in a `finally` block. Images and ignored `results/` directories remain for inspection.

## Ordinary WAR control

```shell
docker build --tag spring-war-probe:war .
python run.py --image spring-war-probe:war --output results/war
```

On Windows, use `--output results\war`. Each output directory must be new.

The Docker build runs these Maven commands in the build container:

```shell
mvn -B -ntp -Dspring.version=5.3.39 package
mvn -B -ntp -Dspring.version=5.2.25.RELEASE dependency:copy-dependencies -DincludeScope=runtime -DoutputDirectory=/common
```

The first command builds `target/probe.war`. The Dockerfile repackages its exploded contents for the selected dependency layout and deploys the resulting archive as `/usr/local/tomcat/webapps/probe.war`. It never installs an agent or Spring dependency on the host.

The default matrix runs the same image ID in fresh containers with no agent, agent 1.33.0, agent 2.10.0, agent 2.10.0 with `-Dotel.javaagent.experimental.indy=true`, and agent 2.33.0. Use `--agents none 2.10.0-indy` to select a subset.

Each deployment must return HTTP 200 with the exact body `hello from Spring MVC` followed by a newline from `/probe/hello`. After that request, `/probe/origins` reports Spring and agent helper code sources and defining classloader identities. The no-agent control must have no `otelAutoDispatcherFilter`; agent runs must have the bean and the expected agent version banner.

## Reproduce the startup failure

```shell
docker build --build-arg LAYOUT=duplicate --build-arg SPRING_VERSION=5.2.25.RELEASE --build-arg COMMON_SPRING_VERSION=5.3.39 --tag spring-war-probe:older-war .
python run.py --image spring-war-probe:older-war --shared-after --output results/shared-after
```

On Windows, use `--output results\shared-after`. A result of `requestpath-loader-constraint` is the observed reproduction, not a successful deployment. The runner exits 1 when any deployment is not healthy.

`--shared-after` performs the following sequence in each fresh Tomcat:

1. Deploy `/probe` with Spring 5.2.25.RELEASE in `WEB-INF/lib`, while Spring 5.3.39 is in Tomcat's `lib`.
2. Wait for `/probe/hello` to return HTTP 200 with the expected body.
3. Copy `/opt/shared.war` into Tomcat's `webapps/shared.war` using `docker exec`. Tomcat's ordinary auto-deployer starts it. This second WAR uses the same application classes, has no Spring JARs, and configures Spring 5.3 path-pattern matching from the common loader.
4. Check `/shared/hello` and capture its startup logs before requesting any class-origin diagnostics. Collect `/probe/origins` afterward so diagnostics cannot cause the startup failure.

The second WAR is another deployment of this investigation application, not an agent extension or a custom classloader. Its `path-patterns.xml` configuration needs Spring 5.3 in the common loader. The failure happens during root-context initialization, before the second WAR can handle an HTTP request.

To perform that sequence manually with agent 2.10.0:

```shell
docker run --detach --name spring-war-manual --publish 127.0.0.1:18080:8080 --env "CATALINA_OPTS=-verbose:class -javaagent:/opt/agents/2.10.0.jar" spring-war-probe:older-war
curl --fail http://127.0.0.1:18080/probe/hello
docker exec spring-war-manual cp /opt/shared.war /usr/local/tomcat/webapps/shared.war
docker logs spring-war-manual
curl --include http://127.0.0.1:18080/shared/hello
docker rm --force spring-war-manual
```

Wait for the first endpoint before copying the second WAR. Tomcat's auto-deployment scan can take roughly ten seconds. Use `curl.exe` in Windows PowerShell. For the no-agent control, omit `-javaagent:/opt/agents/2.10.0.jar`; for the historical indy run, append `-Dotel.javaagent.experimental.indy=true` to `CATALINA_OPTS`.

## Other dependency layouts

```shell
docker build --build-arg LAYOUT=common --tag spring-war-probe:common .
python run.py --image spring-war-probe:common
docker build --build-arg LAYOUT=duplicate --tag spring-war-probe:duplicate .
python run.py --image spring-war-probe:duplicate
python run.py --image spring-war-probe:older-war
docker build --build-arg LAYOUT=parent-first --build-arg SPRING_VERSION=5.2.25.RELEASE --build-arg COMMON_SPRING_VERSION=5.3.39 --tag spring-war-probe:older-parent-first .
python run.py --image spring-war-probe:older-parent-first --shared-after
```

| Layout | WAR Spring dependencies | Tomcat common-loader dependencies | Delegation |
| --- | --- | --- | --- |
| `war` | `SPRING_VERSION`, default 5.3.39 | None | Tomcat default, webapp first |
| `common` | None | `SPRING_VERSION` | Tomcat default |
| `duplicate` | `SPRING_VERSION` | `COMMON_SPRING_VERSION`, default 5.2.25.RELEASE | Tomcat default |
| `parent-first` | `SPRING_VERSION` | `COMMON_SPRING_VERSION` | `<Loader delegate="true"/>` for `/probe` |

These use Tomcat's documented [common loader and delegation settings](https://tomcat.apache.org/tomcat-9.0-doc/class-loader-howto.html), not custom classloading code. Tomcat advises against normally placing application libraries in the common loader. `parent-first` is a comparison, not a general fix recommendation. It changes which framework version the application actually uses.

## Evidence and classification

`run.py` resolves the image tag to an immutable local image ID before starting the matrix. Each case saves the exact Docker launch arguments in `command.json`, container metadata in `inspect.json`, HTTP bodies, class-origin diagnostics, `startup.log`, `tomcat.log`, and a machine-readable `result.json`. Two-WAR cases also save the first endpoint's response and origins. JVM `-verbose:class` output records actual class definitions even when context initialization fails.

`requestpath-loader-constraint` requires a `LinkageError` whose message contains both `loader constraint violation` and `org/springframework/http/server/RequestPath`, plus stack markers for `OpenTelemetryHandlerMappingFilter.parseAndCacheMh`, its `<clinit>`, `otelAutoDispatcherFilter`, and `ContextLoaderListener.contextInitialized`. It checks the logs captured before diagnostic requests. A different site gets `requestpath-loader-constraint-other-site`. Other startup or HTTP failures and missing instrumentation receive separate classifications.

A healthy result checks both the exact HTTP response and active instrumentation. A 404, an unrelated exception, a missing agent bean, or a diagnostic endpoint failure is not a reproduction. Transport errors appear in the saved HTTP body rather than counting as a working endpoint.

Run the checker's regression tests inside a disposable container:

```powershell
$source = (Get-Location).Path
docker run --rm --mount "type=bind,source=$source,target=/work,readonly" --workdir /work python:3.12-slim python -B -m unittest -v test_run
```

In a POSIX shell, set `source="$(pwd)"` instead.
