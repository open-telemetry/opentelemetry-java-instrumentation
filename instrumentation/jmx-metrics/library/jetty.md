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

- `jetty.context` corresponds to the deployed application subfolder in `webapps` folder.

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
| jetty.session.duration.sum  | Counter       | jetty.context | The cumulated session duration            |

- `jetty.context` corresponds to the deployed application subfolder in `webapps` folder.
