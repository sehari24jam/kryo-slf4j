Kryo-slf4j is a fork of the wonderful [kryo](https://github.com/EsotericSoftware/kryo) API, a fast and efficient object graph serialization framework for Java. The goals of the Kryo project are speed, efficiency, and an easy to use API. The project is useful any time objects need to be persisted, whether to a file, database, or over the network.

Kryo can also perform automatic deep and shallow copying/cloning. This is direct copying from object to object, not object->bytes->object.

Kryo-slf4j is a fork of Kryo that replaces the Minlog logging used by Kryo with slf4j providing users the choice to pick their desired logging implementation instead of having to deal with multiple logging implementations when using Kryo in projects using a different logging implementation.

Kryo-slf4j is built off v3.0.1 of Kryo and accordingly tracks the version. Please refer to the [Kryo](https://github.com/EsotericSoftware/kryo) project page for detailed documentation on the Kryo API and its usage.

You can obtain the release binaries from the releases page above or alternately if you use Maven you can get it directly from Maven Central using the following dependency configurations:
```xml
    <dependency>
      <groupId>net.lizalab</groupId>
      <artifactId>kryo-slf4j</artifactId>
      <version>3.0.1</version>
      <scope>compile</scope>
    </dependency>
```
