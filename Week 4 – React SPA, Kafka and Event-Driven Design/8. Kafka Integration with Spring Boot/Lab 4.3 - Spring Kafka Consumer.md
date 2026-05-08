# Lab 4.3: Building a Spring Kafka Consumer

## Lab Overview

In this lab you will build a Spring Boot application that consumes the transaction events your Lab 4.2 producer is publishing to the `transactions` topic. The consumer simulates an analytics service that collects per-transaction statistics. For each transaction received, the consumer extracts the transaction type and amount and hands the resulting statistic to a printing service. In the next lab you will swap that printing service for one that writes to an Oracle database, but the listener and the rest of the application will not need to change.

This lab treats Lab 4.2 as a prerequisite. The producer you built in that lab will run alongside this consumer, and you will see end-to-end flow from the producer, through Kafka, to your consumer's transformation and output.

By the end of this lab you will:

- Build a Spring Boot Kafka consumer using `@KafkaListener`
- Configure consumer-side JSON deserialization for the `Transaction` record
- Use `auto-offset-reset: earliest` to replay historical messages
- Transform incoming events into a smaller statistic record
- Use an interface and an implementation to make the output destination swappable
- Observe the partition, key, and offset of each received message
- Run the producer and consumer simultaneously and watch the end-to-end flow

This lab keeps the focus on the **consumer** side of Spring Kafka. Lab 4.4 will replace the printing service with a database insert.

## Prerequisites

- Lab 4.2 completed successfully, with a working producer project
- Kafka 4.1.1 broker available at `C:\kafka` and ready to be started
- The `transactions` topic exists on the broker
- Java 21 installed and on your `PATH`
- IntelliJ IDEA Ultimate
- Internet access to use Spring Initializr (`start.spring.io`)

If your Kafka broker is not currently running, start it now in a Command Prompt window:

```
cd C:\kafka
bin\windows\kafka-server-start.bat config\server.properties
```

Wait for the line `[KafkaRaftServer nodeId=1] Kafka Server started` and leave the window open.

## A Note on the Two Warning Messages

The same two cosmetic warnings from previous labs will appear:

```
2026-04-25T01:39:03.731487Z main ERROR Reconfiguration failed: No configuration found for '2c7b84de' at 'null' in 'null'
```

```
DEPRECATED: A Log4j 1.x configuration file has been detected, which is no longer recommended.
```

Ignore both. They are known cosmetic issues with Kafka 4.1.x on Windows and do not affect functionality.

## Section 1: Building the Spring Boot Consumer

### 1.1 Generate the Project with Spring Initializr

Open a browser and go to https://start.spring.io.

Configure the project as follows:

| Setting | Value |
|---|---|
| Project | Maven |
| Language | Java |
| Spring Boot | **3.5.x** (the latest 3.5 patch release shown) |
| Group | `com.example` |
| Artifact | `transconsumer` |
| Name | `transconsumer` |
| Description | `Spring Kafka consumer for transaction events` |
| Package name | `com.example.transconsumer` |
| Packaging | Jar |
| Java | 21 |

The same notes from Lab 4.2 apply:

**Spring Boot version.** Make sure you explicitly select 3.5.x. Do not let Initializr default to 4.0.x.

**Artifact name.** Use `transconsumer` (no hyphens, no underscores) to avoid the underscore-in-package-name issue.

Add the following dependencies:

- **Spring for Apache Kafka**
- **Spring Boot Actuator**
- **Spring Web** (brings Jackson onto the classpath, which `JsonDeserializer` needs)

Click **Generate**, save the resulting ZIP file, extract it, and open the project in IntelliJ.

### 1.2 Verify the Spring Boot Version

Open `pom.xml` and confirm the parent version is 3.5.x:

```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.5.x</version>
    <relativePath/>
</parent>
```

If the version shows `4.0.x`, change it to a 3.5 release (for example `3.5.6`) and reload Maven by clicking the Maven reload icon in IntelliJ.

### 1.3 Configure the Application

Delete the default `application.properties` file and create a new file named `application.yml` in `src/main/resources`. Add the following:

```yaml
spring:
  main:
    web-application-type: none
  application:
    name: transconsumer

  kafka:
    bootstrap-servers: localhost:9092
    consumer:
      group-id: transconsumergroup
      auto-offset-reset: earliest
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      properties:
        spring.json.use.type.headers: false
        spring.json.value.default.type: com.example.transconsumer.model.Transaction
        spring.json.trusted.packages: "com.example.transconsumer.model"

logging:
  level:
    com.example.transconsumer: INFO
```

A walkthrough of what each setting does:

**`spring.main.web-application-type: none`** tells Spring Boot not to start an embedded web server. The Spring Web dependency we included brings in Tomcat, but we do not need a web server for a consumer service: it has no HTTP endpoints, accepts no web requests, and serves no user traffic. Disabling the web server avoids port conflicts when running multiple Spring Boot Kafka applications on the same machine (such as the producer and consumer in this lab), starts the application faster, and produces cleaner startup logs.

This pattern is common in real systems: many Spring Boot applications are not web applications, but rather background workers that process events, run scheduled jobs, or consume from queues. The Spring Web dependency is still useful for these applications because it brings in shared infrastructure like Jackson, but the embedded server itself is overhead. `web-application-type: none` is the standard way to declare "I am a service worker, not a web app."

**`bootstrap-servers: localhost:9092`** is the broker address, identical to the producer's setting.

**`group-id: transconsumergroup`** is the consumer group identity. All consumers that share this group ID cooperate to read the topic, with Kafka assigning each partition to exactly one group member. We will use this when running multiple consumer instances in the optional exploration.

**`auto-offset-reset: earliest`** controls what happens the first time this consumer group connects to a topic, before it has any committed offsets. With `earliest`, the consumer starts at offset 0 and reads everything in the topic. With `latest`, it would start at the head and only see new messages. For this lab, `earliest` is the right choice because it lets you immediately see the transactions your producer has been writing.

**`key-deserializer`** mirrors the producer's `StringSerializer`. The keys are account IDs, encoded as strings.

**`value-deserializer`** is `JsonDeserializer`, the consumer-side counterpart to the producer's `JsonSerializer`. It uses Jackson to parse JSON byte arrays back into Java objects.

The three nested `properties:` settings under `value-deserializer` deserve particular attention:

- **`spring.json.use.type.headers: false`** tells the deserializer to ignore the `__TypeId__` header that Spring's `JsonSerializer` adds to outgoing messages. By default, Spring would try to instantiate the exact class named in that header, which would be `com.example.transproducer.model.Transaction` — a class that does not exist in this consumer project. Disabling type headers is the cleanest way to handle this.

- **`spring.json.value.default.type`** tells the deserializer what type to instantiate when no type header is consulted. It points to the consumer's own `Transaction` class.

- **`spring.json.trusted.packages`** is a security feature: the deserializer will only deserialize classes from packages in this list, preventing certain classes of attacks. We list our model package.

This configuration pattern (disabling the type header, providing a default type) is the recommended approach when producer and consumer are separate applications with their own model classes.

### 1.4 Define the Transaction Record

The consumer needs its own copy of the `Transaction` record to deserialize incoming events. Even though the structure matches what the producer sends, this is a deliberate design choice: the consumer owns its view of the event contract, and the two services do not share code.

Create a new package `com.example.transconsumer.model` and add two files.

`TransactionType.java`:

```java
package com.example.transconsumer.model;

public enum TransactionType {
    DEPOSIT,
    WITHDRAWAL,
    PURCHASE,
    SERVICE_CHARGE,
    PAYMENT
}
```

`Transaction.java`:

```java
package com.example.transconsumer.model;

import java.math.BigDecimal;
import java.time.Instant;

public record Transaction(
    String id,
    String accountId,
    BigDecimal amount,
    TransactionType type,
    Instant timestamp
) {}
```

This is the same shape as the producer's `Transaction`. The fields and types must match for JSON deserialization to work. In a larger system, this kind of duplication is sometimes managed through a shared schema or a code-generation step; for this lab the two records are simply hand-written copies.

### 1.5 Define the Statistic Record

Create a `TransactionStatistic` record in the same `model` package. This is the smaller, transformed shape that the consumer will hand to its output service:

```java
package com.example.transconsumer.model;

import java.math.BigDecimal;

public record TransactionStatistic(
    TransactionType type,
    BigDecimal amount
) {}
```

The statistic captures only what the analytics use case cares about: which type of transaction, and how much. The other fields on `Transaction` (id, accountId, timestamp) are dropped. In a real analytics pipeline you might keep more fields; for this lab the minimal pair makes the transformation step explicit.

### 1.6 Define the StatisticsCollector Interface

Now the swappable output destination. Create a new package `com.example.transconsumer.service` and add `StatisticsCollector.java`:

```java
package com.example.transconsumer.service;

import com.example.transconsumer.model.TransactionStatistic;

public interface StatisticsCollector {
    void collect(TransactionStatistic statistic);
}
```

This interface is the seam between the listener (which knows about Kafka and transformation) and the output destination (which knows about how the statistic is recorded). The listener will depend on this interface, not on any specific implementation. In the next lab, you will replace the implementation with one that writes to a database, and the listener will not need to change.

This pattern (an interface with one or more implementations) is sometimes called "dependency inversion" or simply "programming to an interface." It is a standard practice in well-structured Spring applications.

### 1.7 Implement the Console Collector

In the same `service` package, add `ConsoleStatisticsCollector.java`:

```java
package com.example.transconsumer.service;

import com.example.transconsumer.model.TransactionStatistic;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ConsoleStatisticsCollector implements StatisticsCollector {

    private static final Logger log = LoggerFactory.getLogger(ConsoleStatisticsCollector.class);

    @Override
    public void collect(TransactionStatistic statistic) {
        log.info("Statistic -> {} {}",
            String.format("%-15s", statistic.type()),
            statistic.amount());
    }
}
```

This is the only implementation of `StatisticsCollector` in this lab. It logs the statistic to the console using a fixed-width format that aligns the type column for readable output.

The `@Service` annotation registers this class as a Spring bean. Because it is the only `StatisticsCollector` implementation, Spring will inject it wherever a `StatisticsCollector` is required.

### 1.8 Implement the Listener

The listener is the entry point for events arriving from Kafka. In the `service` package, add `TransactionListener.java`:

```java
package com.example.transconsumer.service;

import com.example.transconsumer.model.Transaction;
import com.example.transconsumer.model.TransactionStatistic;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class TransactionListener {

    private static final Logger log = LoggerFactory.getLogger(TransactionListener.class);

    private final StatisticsCollector collector;

    public TransactionListener(StatisticsCollector collector) {
        this.collector = collector;
    }

    @KafkaListener(topics = "transactions")
    public void handle(ConsumerRecord<String, Transaction> record) {
        Transaction transaction = record.value();

        log.info("Received key={} partition={} offset={}",
            record.key(), record.partition(), record.offset());

        TransactionStatistic statistic = new TransactionStatistic(
            transaction.type(),
            transaction.amount()
        );

        collector.collect(statistic);
    }
}
```

This is the most important class in the lab. Walk through it carefully.

**The `@Service` annotation** registers the class as a Spring bean.

**The constructor takes `StatisticsCollector`** (the interface, not the concrete class). Spring's dependency injection finds the `ConsoleStatisticsCollector` bean and injects it. If you later replace `ConsoleStatisticsCollector` with a database collector, this constructor parameter does not change.

**The `@KafkaListener(topics = "transactions")` annotation** registers this method with Spring Kafka's listener container. The container runs a poll loop in the background and calls this method once per received record. From the application's point of view, this method is the handler that reacts to incoming events. From Kafka's point of view, this consumer is a member of the `transconsumergroup` consumer group, and it is reading whichever partitions the group coordinator has assigned to it.

**The method takes `ConsumerRecord<String, Transaction>`** rather than just the `Transaction` value. The full `ConsumerRecord` gives access to the key, partition, offset, timestamp, and headers. Using it here lets us log the key and partition (matching what the producer logged in Lab 4.2) without configuring multiple `@Header` parameters.

**The method body does three things:**

1. Logs the key, partition, and offset from the incoming record. You should be able to cross-reference these against the producer's logs.
2. Constructs a `TransactionStatistic` from the transaction's `type` and `amount`. This is the transformation step.
3. Hands the statistic to the collector for output. The listener does not know whether the collector prints to the console, writes to a file, or inserts into a database. That is the collector's concern.

This separation between "what the consumer does with each event" (the listener) and "where the result goes" (the collector) is what makes the application easy to extend in Lab 4.4.

### 1.9 The Application Class

The default `TransconsumerApplication.java` generated by Initializr does not need any changes. It looks like this:

```java
package com.example.transconsumer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class TransconsumerApplication {

    public static void main(String[] args) {
        SpringApplication.run(TransconsumerApplication.class, args);
    }
}
```

Unlike the producer, no `@EnableScheduling` is needed because the consumer is event-driven rather than schedule-driven. The Kafka container does its own polling on its own thread, and the application class only needs to bring the Spring context up.

## Section 2: Running the End-to-End Flow

### 2.1 Confirm the Producer Is Available

For this section you need both the producer (from Lab 4.2) and the consumer (from this lab) running simultaneously.

Open the Lab 4.2 producer project (`transproducer`) in a separate IntelliJ window. If you closed it after the previous lab, simply reopen it now.

The Kafka broker should already be running. If not, start it as described at the top of this lab.

### 2.2 Start the Producer

In the `transproducer` IntelliJ window, run `TransproducerApplication`. Confirm that you see the producer logging transaction sends:

```
Sent ... A-007 PURCHASE 247.32 -> partition 1, offset N
Sent ... A-002 DEPOSIT 89.15 -> partition 0, offset M
```

(Where N and M are wherever the offsets currently are; they will not be 0 because of all the events you produced in Lab 4.2.)

Leave the producer running.

### 2.3 Start the Consumer

Now switch to the `transconsumer` IntelliJ window and run `TransconsumerApplication`.

Within a few seconds of startup, you should see a flood of consumer log lines:

```
Received key=A-002 partition=0 offset=0
Statistic -> DEPOSIT          89.15
Received key=A-002 partition=0 offset=1
Statistic -> SERVICE_CHARGE   12.00
Received key=A-007 partition=1 offset=0
Statistic -> PURCHASE         247.32
Received key=A-007 partition=1 offset=1
Statistic -> WITHDRAWAL       412.88
...
```

This is the consumer reading the entire history of the `transactions` topic, starting at offset 0 on each partition (because of `auto-offset-reset: earliest`). For each record, the listener logs the key, partition, and offset, then hands a transformed statistic to the collector, which logs it.

After the consumer catches up to the head of the log, the burst of historical output stops and you will see the consumer steady-state at one statistic per second, matching the producer's pace.

### 2.4 What to Observe

While both applications are running, take a few minutes to study the output:

**Cross-reference partitions.** Pick a recent transaction in the producer's log. Note its account ID, partition, and offset. Find the same transaction in the consumer's log (by partition and offset). The data should match: same account, same partition, same offset.

**The consumer is "behind" the producer in real time.** When the producer logs `Sent ... A-007 ... -> partition 1, offset N`, you should see the consumer log `Received key=A-007 partition=1 offset=N` shortly afterward (typically within milliseconds).

**The transformation step is visible.** Each `Received` line is followed by a `Statistic` line. The two lines together represent one event being received and processed.

**Per-partition ordering is preserved.** All `partition=1` lines should be in increasing offset order. Same for partitions 0 and 2. The interleaving across partitions can be in any order, but within each partition, ordering is strict.

### 2.5 Restart the Consumer

While the producer continues to run, stop the consumer in IntelliJ. Wait a few seconds. Then start the consumer again.

This time you will **not** see the historical replay. The consumer remembers where it left off (its committed offsets are stored in the broker's `__consumer_offsets` topic) and resumes from there. You will see only the transactions produced during the gap and any new ones since.

This demonstrates the core consumer-group behavior: the group ID is the consumer's identity, and its progress is durable across restarts. If you wanted to replay history, you would need to either reset the group's offsets explicitly or use a different group ID.

### 2.6 Stop the Producer

In the producer's IntelliJ window, click the red stop button. The producer shuts down. The consumer continues running but stops receiving new transactions because none are being produced.

You can leave the consumer running; it will sit quietly waiting for new events. This is normal Kafka consumer behavior.

When you are done observing, stop the consumer with the red stop button as well.

## Section 3: Optional Exploration

These exercises are not required, but they reinforce concepts from the course material.

### 3.1 Try a Different Consumer Group ID

Stop the consumer if it is running. Open `application.yml` and change the group ID:

```yaml
spring:
  kafka:
    consumer:
      group-id: anothergroup
```

Run the consumer again. Notice that it processes the **entire history** of the topic from offset 0, even though your previous consumer (with `transconsumergroup`) had already processed it.

Why? Because `anothergroup` is a brand new consumer group. It has no committed offsets. With `auto-offset-reset: earliest`, it starts at offset 0 and reads everything.

This demonstrates that consumer groups are independent. Each group has its own offsets, and adding a new group does not affect any existing group. This is the fan-out property of Kafka: you can have any number of independent groups consuming the same topic.

When you are done experimenting, change the group ID back to `transconsumergroup`. Once you do, restart the consumer. It will only see new transactions, because `transconsumergroup` already processed everything earlier.

### 3.2 Examine the Internal Offsets Topic

While your consumer is running (or after it has run), use the CLI tool to look at where Kafka stores consumer group offsets:

```
cd C:\kafka
bin\windows\kafka-consumer-groups.bat --bootstrap-server localhost:9092 --describe --group transconsumergroup
```

You will see output similar to this:

```
GROUP                   TOPIC          PARTITION  CURRENT-OFFSET  LOG-END-OFFSET  LAG  CONSUMER-ID                                     HOST            CLIENT-ID
transconsumergroup      transactions   0          247             247             0    consumer-transconsumergroup-1-...               /127.0.0.1      consumer-transconsumergroup-1
transconsumergroup      transactions   1          189             189             0    consumer-transconsumergroup-1-...               /127.0.0.1      consumer-transconsumergroup-1
transconsumergroup      transactions   2          312             312             0    consumer-transconsumergroup-1-...               /127.0.0.1      consumer-transconsumergroup-1
```

This is the actual state of your consumer group as Kafka tracks it. For each partition you see:

- **CURRENT-OFFSET** is where your consumer has committed up to
- **LOG-END-OFFSET** is the head of the partition (the next offset that will be assigned to a new message)
- **LAG** is the difference: how many messages are waiting to be processed

When the consumer is keeping up, lag is 0 or close to it. When the consumer is falling behind, lag grows.

If you stop the consumer and let the producer keep running, you can re-run this command and watch the lag grow. Then start the consumer again and watch the lag shrink as the consumer catches up.

This is the primary operational metric for monitoring Kafka consumers in production.

### 3.3 Trace an Event End to End

With both applications running, pick a single transaction and trace it through the system:

1. In the producer's log, find a recent line, for example:
   ```
   Sent 6f8a3c4d-... A-007 PURCHASE 247.32 -> partition 1, offset 1234
   ```
2. In the consumer's log, find the matching `Received` line:
   ```
   Received key=A-007 partition=1 offset=1234
   ```
3. The next line in the consumer should be the resulting statistic:
   ```
   Statistic -> PURCHASE         247.32
   ```

You have now traced the lifecycle of one event: produced by the application, hashed to a partition by key, written to disk by the broker, fetched by the consumer, deserialized into a `Transaction`, transformed into a `TransactionStatistic`, and printed by the collector.

## Section 4: Wrapping Up

### 4.1 What to Leave Running

For Lab 4.4, you will need:

- The Kafka broker still running (or be prepared to restart it)
- The `transactions` topic — it persists as long as the broker's storage is intact
- Both your producer and consumer projects available to run

You can stop the producer and consumer applications. They will be reused (or the consumer modified) in the next lab.

### 4.2 What You Have Learned

In this lab you have:

- Built a Spring Boot Kafka consumer using `@KafkaListener`
- Configured the application to run as a service worker rather than a web application
- Configured JSON deserialization to handle messages from a producer with a different model package
- Used `auto-offset-reset: earliest` to replay history on first run
- Transformed an incoming event into a smaller domain-specific record
- Separated the listener from the output destination using an interface
- Observed the partition, key, and offset of each record
- Verified end-to-end producer-to-consumer flow

The conceptual foundation for the next lab is now in place. The `StatisticsCollector` interface is the seam where Lab 4.4 will plug in a database-backed implementation. The listener, the deserialization configuration, the consumer group setup, and the transformation logic will all stay the same. Only the collector changes, and the rest of the application does not know or care.

### 4.3 Self-Check

Before moving on to Lab 4.4, you should be able to answer these questions:

1. What is the role of the `group-id` setting, and what would happen if you ran two consumers with the same group ID at the same time?
2. Why did `auto-offset-reset: earliest` cause the consumer to process all historical transactions on first run, but not on subsequent restarts?
3. Why do we configure `spring.json.use.type.headers: false` and `spring.json.value.default.type` in the consumer?
4. What is the role of the `StatisticsCollector` interface? What makes this design easier to extend than calling `System.out.println` directly from the listener?
5. The listener method takes a `ConsumerRecord<String, Transaction>`. What information does that give you that you would not have if the method just took a `Transaction`?
6. How does Kafka know where each consumer group has read up to? Where is that information stored?
7. If you stopped this consumer for an hour while the producer kept running, what would happen when you restarted the consumer?
8. What does `spring.main.web-application-type: none` do, and why is it appropriate for both the producer and consumer in this course?

If any of these are unclear, review the relevant section of the lab before continuing.

## End of Lab 4.3
