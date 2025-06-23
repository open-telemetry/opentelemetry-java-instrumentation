# Jetty Metrics

Here is the list of metrics based on MBeans exposed by [Jetty](https://jetty.org/).

The metrics captured and their respective attributes depend on the Jetty version:
- [Jetty 12 and later](#jetty-12-and-later)
- [Jetty 9 to 11](#jetty-9-to-11)

## Jetty 12 and later

Those metrics require the following Jetty modules to be enabled : `jmx`, `http`, `statistics`, `sessions` and at least one of `ee8-deploy`, `ee9-deploy` or `ee10-deploy`.

| Metric Name             | Type          | Attributes    | Description                               |
|-------------------------|---------------|---------------|-------------------------------------------|
| jetty.thread.count      | UpDownCounter |               | The current number of threads             |
| jetty.thread.limit      | UpDownCounter |               | The maximum number of threads in the pool |
| jetty.thread.busy.count | UpDownCounter |               | The current number of busy threads        |
| jetty.thread.idle.count | UpDownCounter |               | The current number of idle threads        |
| jetty.thread.queue.size | UpDownCounter |               | The current job queue size                |
| jetty.io.select.count   | Counter       |               | The number of select calls                |
| jetty.session.count     | UpDownCounter | jetty.context | Current number of active sessions         |
| jetty.session.count.max | Gauge         | jetty.context | Maximum number of active sessions (*)     |

- `jetty.context` corresponds to the deployed application subfolder in `webapps` folder.
- `jetty.session.count.max` metric produces unpredictable values when more than one `org.eclipse.jetty.session:context=*,type=defaultsessioncache,id=*` MBean is present, the default Jetty deployment includes a single one.

## Jetty 9 to 11

Those metrics require the following Jetty modules to be enabled : `jmx`, `http` and `stats`.

| Metric Name                 | Type          | Attributes    | Description                               |
|-----------------------------|---------------|---------------|-------------------------------------------|
| jetty.thread.count          | UpDownCounter |               | The current number of threads             |
| jetty.thread.limit          | UpDownCounter |               | The maximum number of threads in the pool |
| jetty.thread.busy.count     | UpDownCounter |               | The current number of busy threads        |
| jetty.thread.idle.count     | UpDownCounter |               | The current number of idle threads        |
| jetty.thread.queue.size     | UpDownCounter |               | The current job queue size                |
| jetty.io.select.count       | Counter       |               | The number of select calls                |
| jetty.session.created.count | Counter       | jetty.context | The total number of created sessions      |
| jetty.session.duration.sum  | Gauge         | jetty.context | The cumulated session duration            |
| jetty.session.duration.max  | Gauge         | jetty.context | The maximum session duration              |
| jetty.session.duration.mean | Gauge         | jetty.context | The mean session duration                 |

- `jetty.context` corresponds to the deployed application subfolder in `webapps` folder.
- `jetty.session.duration.sum`, `jetty.session.duration.max`, `jetty.session.duration.mean` metrics will produce unpredictable results when more than one `org.eclipse.jetty.server.session:context=*,type=sessionhandler,id=*` MBean is present, the default Jetty deployment includes a single one.
