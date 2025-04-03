# Custom Filters

This folder will be mounted into the GRPC Docker Compose federation server container and set in the classpath.
It allows any jars and therefore classes to be picked up reflectively.
It is designed to be used to pickup custom `MessageFilter` classes set using the `filter_classname` property for a given client.
This has been set to be `uk.gov.dbt.ndtp.filter.TestFilter` and this is the class held in the example jar.
This filter will allow through every 3 messages from a topic.

See [Filter Configuration](../../docs/server-configuration.md#configuring-a-custom-filter) for more information on how to configure a custom filter.
