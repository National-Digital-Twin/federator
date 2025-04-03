# Logging configuration

**Repository:** `federator`  
**Description:** `how to change the logging configuration`

<!-- SPDX-License-Identifier: OGL-UK-3.0 -->

---

## Overview

This describes how to change the logging level of the application.

## Changing the level of detail in the logging configuration

The logging configuration is defined in a properties file [`logback.xml`](../src/main/resources/logback.xml) which is located in the `src/main/resources` directory.

The logging configuration is defined in the `<root level="Logging-Level">` element of the file.
This element defines the root logger and is the parent of all other loggers in the configuration.

For example to set the level of logging to debug, the root element configuration would look like this:

```xml
<root level="DEBUG">
    <appender-ref ref="ASYNC_CONSOLE"/>
</root>
```

The level attribute can be set to one of the following values: `TRACE`, `DEBUG`, `INFO`, `WARN`, `ERROR`, `OFF`

Rebuild the application jar and docker images to apply the changes.

Once completed you can confirm the changes by running the application and checking the logs (unless you have set them to `OFF`):

```text
11:35:25,960 |-INFO in ch.qos.logback.classic.model.processor.RootLoggerModelHandler - Setting level of ROOT logger to ERROR
```

For more information please refer to the [logback documentation](http://logback.qos.ch/manual/configuration.html)

---

**Maintained by the National Digital Twin Programme (NDTP).**

© Crown Copyright 2025. This work has been developed by the National Digital Twin Programme and is legally attributed to the Department for Business and Trade (UK) as the
governing entity.  
Licensed under the Open Government Licence v3.0.  
For full licensing terms, see [OGL_LICENSE.md](../OGL_LICENSE.md).
