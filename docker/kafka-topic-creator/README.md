# Creates kafka topics automatically.

## Parameters

`BOOTSTRAP_VALUE` - kafka host,  I used value `"kafka:19092"` to run it locally.

`KAFKA_TOPICS` - space separated list of kafka topics. Example, `topic_1, topic_2, topic_3`.

Note, this container should run only **after** your original kafka broker and zookeeper are running.
After this container creates topics, it is not needed anymore.
