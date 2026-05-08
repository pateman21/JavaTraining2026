# Lab 4.1: Introduction to Kafka at the Command Line

## Lab Overview

In this lab you will start a Kafka broker on your local machine, learn how its directory structure is organized, create a topic, and use Kafka's command-line producer and consumer tools to send and receive messages. The goal is to develop a working understanding of how Kafka organizes data and how producers and consumers interact with topics, before adding Spring Boot into the picture in the next lab.

By the end of this lab you will:

- Start and stop a Kafka broker running in KRaft mode
- Locate and interpret the most important settings in `server.properties`
- Create a topic and inspect its configuration
- Produce and consume messages using Kafka's command-line tools
- Understand what happens when a consumer reads from the beginning of a topic versus from the latest offset

This lab uses only the Kafka command-line tools. No Spring Boot, no Java code. The next lab will build on this foundation by connecting a Spring Boot application to the same broker.

## Prerequisites

- Kafka 4.1.1 installed at `C:\kafka` on your VM
- Java 21 installed and on your `PATH`

### Running Commands From the Kafka Directory

The Kafka batch scripts on this lab setup are not on your system `PATH`. This means you must run all Kafka commands from the `C:\kafka` directory using the full relative path to each script (for example `bin\windows\kafka-topics.bat` rather than just `kafka-topics.bat`).

For every command in this lab, the first step is to make sure you are in the right directory:

```
cd C:\kafka
```

You can verify your prerequisites by opening a Command Prompt and running:

```
java -version
cd C:\kafka
bin\windows\kafka-topics.bat --version
```

You should see Java 21 reported and Kafka version `4.1.1`.

## A Note on Two Warning Messages

Kafka on Windows produces two cosmetic warning messages that you will see throughout this lab. They are harmless and can be ignored.

The first appears on every Kafka command:

```
2026-04-25T01:39:03.731487Z main ERROR Reconfiguration failed: No configuration found for '2c7b84de' at 'null' in 'null'
```

This is a Log4j2 message about how its own configuration loaded. It does not indicate a problem with Kafka itself. The actual command output appears below the warning.

The second appears on certain commands and reads:

```
DEPRECATED: A Log4j 1.x configuration file has been detected, which is no longer recommended.
```

This is a known issue in the Kafka 4.1.x batch scripts on Windows. It does not affect functionality. Ignore it.

When the lab tells you to look for command output, look for the meaningful text, not these warnings.

## Section 1: Starting Kafka and Understanding the Installation

### 1.1 The Kafka Installation Directory

Open a Command Prompt and navigate to the Kafka installation:

```
cd C:\kafka
dir
```

You should see several subdirectories. The important ones are:

- **`bin\`** — Linux/Mac shell scripts. We will not use these on Windows.
- **`bin\windows\`** — Windows batch files. These are the commands you will run throughout the lab. Each Kafka tool has a `.bat` script here.
- **`config\`** — configuration files. The most important one is `server.properties`, which controls how the broker behaves.
- **`libs\`** — the Java JAR files that make up Kafka itself. Useful to know about but you will not edit anything here.
- **`data\`** — the directory where Kafka stores its data: messages, internal metadata, consumer offsets. This directory is created when the broker is first formatted.

Take a moment to list the contents of `bin\windows`:

```
dir bin\windows
```

You will see roughly 25 batch files. Note especially:

- `kafka-server-start.bat` — starts the broker
- `kafka-storage.bat` — manages the broker's storage (formatting, generating cluster IDs)
- `kafka-topics.bat` — creates, lists, describes, and modifies topics
- `kafka-console-producer.bat` — a CLI tool for publishing messages
- `kafka-console-consumer.bat` — a CLI tool for reading messages

These are the scripts you will use in this lab.

### 1.2 The Server Configuration File

Open the broker configuration file in a text editor:

```
notepad C:\kafka\config\server.properties
```

Scroll through the file briefly. It is heavily commented and is worth reading once for orientation. Find the section near the middle of the file labeled **Log Basics**. You should see something like:

```properties
############################# Log Basics #############################

# A comma separated list of directories under which to store log files
log.dirs=C:/kafka/data
```

The setting `log.dirs` tells Kafka where to store its data. This includes all topic message data, the cluster's internal metadata, and consumer group offsets. Despite the name, this is **not** the directory for application log files (those go in `C:\kafka\logs`). The "log" in `log.dirs` refers to Kafka's append-only event log, which is the data structure underlying topics.

Confirm that your `log.dirs` is set to `C:/kafka/data`. Use forward slashes, even on Windows. If your file shows a different path, change it to match and save the file.

If the path you find is `/tmp/kraft-combined-logs` (Kafka's default), the storage was not configured for this lab and you will need to complete the optional Section 1.3 before continuing. Otherwise, skip Section 1.3 and proceed to Section 1.4.

Close the file when you are done.

### 1.3 (Optional) Formatting the Kafka Storage

This section is only needed if your `log.dirs` setting was not configured, or if you need to recover from corrupted storage later in the course.

Kafka in KRaft mode (the architecture introduced in Kafka 4.x, which replaces the older ZooKeeper architecture) requires that its storage be formatted before the broker can start for the first time. Formatting writes a small `meta.properties` file that identifies the broker and the cluster it belongs to. Once formatted, you do not need to format again unless the storage becomes corrupted.

#### 1.3.1 Generate a Cluster ID

A KRaft cluster is identified by a UUID. In production with multiple brokers, every broker in the cluster shares the same UUID; for a single-broker classroom setup you simply generate one and use it.

From `C:\kafka`:

```
bin\windows\kafka-storage.bat random-uuid
```

You will see the harmless Log4j warning followed by a UUID like:

```
KY_qlo8EQEK98t6e_j8kWA
```

Copy the UUID. You will need it in the next step. If you generate a new UUID at any point, that becomes the new cluster identity and any previously formatted storage with a different UUID will need to be wiped first.

#### 1.3.2 Format the Storage

Run the format command from `C:\kafka`, replacing `<UUID>` with the value you just generated:

```
cd C:\kafka
bin\windows\kafka-storage.bat format -t <UUID> -c config\server.properties --standalone
```

The `--standalone` flag tells Kafka that this broker is also acting as its own controller. In a multi-node production cluster you would use different flags to declare multi-controller setups, but for our single-node lab `--standalone` is correct.

You should see output like:

```
Formatting dynamic metadata voter directory C:/kafka/data with metadata.version 4.1-IV1.
```

Verify the directory was created:

```
dir C:\kafka\data
```

You should see at least `meta.properties` and `bootstrap.checkpoint`. If those files are present, the storage is formatted and ready.

#### 1.3.3 Recovering from Corrupted Storage

If at some point Kafka refuses to start, or you see errors about an inconsistent metadata log, you can wipe and reformat the storage. **This deletes all topics and messages**, so only do this if you are willing to lose everything in the broker.

```
cd C:\kafka
rmdir /s /q data
bin\windows\kafka-storage.bat random-uuid
bin\windows\kafka-storage.bat format -t <NEW_UUID> -c config\server.properties --standalone
```

Use a fresh UUID each time you reformat. After the reformat you can start the broker again as described in the next section.

### 1.4 Important: Do Not Delete Topics

Throughout this course, **do not run any topic deletion commands**. Even though Kafka has a setting to enable topic deletion, and even though the documented command works in many environments, on this Windows lab setup deleting a topic frequently corrupts the metadata log and forces a full storage reformat (which loses all topics and messages).

If you accidentally create a topic with the wrong name, just leave it alone and create a new one with the correct name. Stale topics do no harm.

### 1.5 Starting the Broker

Now start the Kafka broker. From the `C:\kafka` directory:

```
cd C:\kafka
bin\windows\kafka-server-start.bat config\server.properties
```

Kafka will produce a substantial amount of startup output, scrolling for 10 to 20 seconds. Watch for the line that confirms successful startup:

```
[KafkaRaftServer nodeId=1] Kafka Server started
```

Once you see that, the broker is running and listening for client connections on port 9092.

**Leave this Command Prompt window open.** Closing it stops the broker. From here on, every other Kafka command in this lab will be run in **a separate Command Prompt window**, while this one continues to run the broker. You can think of it as the broker's console.

#### What Just Happened

When you ran `kafka-server-start.bat`, the broker did several things:

- Read its configuration from `config\server.properties`
- Loaded its existing metadata from `C:\kafka\data` (or began with a fresh formatted state)
- Bound to network port 9092 to accept producer and consumer connections
- Bound to network port 9093 for KRaft controller communication
- Began running its internal poll loop, ready to accept requests

The broker is now a long-running server process. Producers and consumers will connect to it over the network. It will keep running until you close the window or press Ctrl+C in it.

## Section 2: Creating and Inspecting a Topic

Open a **new** Command Prompt window. Leave the broker window running. All commands in this section use the new window.

In the new window, change to the Kafka directory:

```
cd C:\kafka
```

You will need to do this in every new Command Prompt window you open in this lab.

### 2.1 Creating a Topic

Create a topic called `labone` with three partitions:

```
bin\windows\kafka-topics.bat --create --topic labone --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1
```

You should see:

```
Created topic labone.
```

Let's break down what each part of that command means:

- `--create` is the action.
- `--topic labone` is the topic name.
- `--bootstrap-server localhost:9092` tells the command-line tool how to find the broker. Every Kafka client (producer, consumer, or admin tool) connects to a "bootstrap server" first, fetches cluster metadata, and then talks to the appropriate brokers from there. On a single-broker setup, the bootstrap server and the only broker are the same.
- `--partitions 3` says the topic should be divided into three partitions. Partitions are how Kafka achieves parallelism within a topic. Each partition is an independent ordered log.
- `--replication-factor 1` says there should be one copy of each partition. In production this would typically be 3 for fault tolerance, but with only one broker we can only have one copy.

#### Why Three Partitions

We chose three partitions deliberately, even though one would work for this small lab. Most of what you will see in this lab and the next is more interesting with multiple partitions:

- Messages with different keys will land on different partitions, demonstrating Kafka's partitioning behavior
- Multiple consumers in a group can work in parallel across partitions
- The output of `describe` will show you what the partition layout actually looks like

If you only had one partition, all of these concepts would be hidden.

### 2.2 Listing Topics

List all topics that exist on the broker:

```
bin\windows\kafka-topics.bat --list --bootstrap-server localhost:9092
```

You should see `labone` in the output. You may also see internal topics whose names start with double underscores (`__consumer_offsets`, `__cluster_metadata`). These are Kafka's own internal bookkeeping topics. You can ignore them, but it is useful to know they exist:

- `__consumer_offsets` is where Kafka stores the committed positions of every consumer group across every partition. When a consumer crashes and restarts, it reads its offsets from here.
- `__cluster_metadata` is where the KRaft controller stores cluster-wide metadata: which topics exist, which broker leads which partition, which configurations are set. This is the topic that replaced ZooKeeper.

You may also see another topic called `test-topic` if the install team used it to verify the setup. That topic is not used in this course; ignore it.

### 2.3 Describing a Topic

Get a detailed view of `labone`:

```
bin\windows\kafka-topics.bat --describe --topic labone --bootstrap-server localhost:9092
```

The output looks something like this:

```
Topic: labone   TopicId: abc123...   PartitionCount: 3   ReplicationFactor: 1   Configs:
        Topic: labone   Partition: 0    Leader: 1   Replicas: 1   Isr: 1   Elr:    LastKnownElr:
        Topic: labone   Partition: 1    Leader: 1   Replicas: 1   Isr: 1   Elr:    LastKnownElr:
        Topic: labone   Partition: 2    Leader: 1   Replicas: 1   Isr: 1   Elr:    LastKnownElr:
```

Let's interpret this. The first line summarizes the topic itself: a unique TopicId, three partitions, replication factor of 1.

The next three lines describe each partition individually:

- **`Leader: 1`** — Broker 1 (the only broker in the cluster) is the leader for this partition. All reads and writes for this partition go through the leader.
- **`Replicas: 1`** — There is one replica of this partition, on broker 1.
- **`Isr: 1`** — The "in-sync replica set" contains broker 1. In a single-broker setup the leader is the only replica, so it is trivially in-sync with itself. In a multi-broker production setup you would see more entries here.
- **`Elr` and `LastKnownElr`** — These relate to "eligible leader replicas," a more recent KRaft feature. For this lab they are not relevant and will be empty.

#### What This Tells You

Even though you created one topic called `labone`, behind the scenes Kafka created three independent storage areas — one per partition. When you produce messages to `labone`, each message gets routed to one of these three partitions based on its key (or round-robin if there is no key). Consumers will read from these partitions independently.

If you look in `C:\kafka\data` you can see this structure on disk:

```
dir C:\kafka\data
```

You will find subdirectories named `labone-0`, `labone-1`, and `labone-2` — one per partition. Inside each, Kafka stores the actual segment files containing the messages. This physical layout is what we discussed conceptually in the lecture material.

## Section 3: Producing and Consuming Messages

Now you will see Kafka in action, sending messages from a producer to a consumer through the topic.

### 3.1 Starting a Console Producer

In your second Command Prompt (the one not running the broker), start a console producer for the `labone` topic. You should still be in `C:\kafka` from the earlier section.

```
bin\windows\kafka-console-producer.bat --topic labone --bootstrap-server localhost:9092
```

Nothing visible happens immediately. The producer is waiting for input. It looks like the command hung, but it is actually waiting for you to type messages. Each line you type and press Enter on will be sent as a single message to the topic.

**Do not type anything yet.** Leave the producer waiting. You will start the consumer next.

### 3.2 Starting a Console Consumer

Open a **third** Command Prompt window. You should now have:

- Window 1: the running broker
- Window 2: the producer waiting for input
- Window 3: a fresh Command Prompt for the consumer

In the third window, change to the Kafka directory and start a consumer for the `labone` topic:

```
cd C:\kafka
bin\windows\kafka-console-consumer.bat --topic labone --bootstrap-server localhost:9092
```

Like the producer, it will appear to hang. This is correct — the consumer is now polling Kafka for new messages, but no messages have been produced yet, so it sits silently.

#### What the Consumer Is Doing

By default, a console consumer started this way reads only **new** messages — messages that arrive after it starts. If messages were already in the topic (from a previous run, for instance), the consumer would not see them. This is the `auto.offset.reset=latest` behavior we will discuss in the lecture material on consumer offsets.

This is one of the most common surprises for developers new to Kafka: starting a consumer does not automatically replay history.

### 3.3 Sending Messages

Switch back to Window 2 (the producer). Type a message and press Enter:

```
Hello Kafka
```

Switch to Window 3 (the consumer). You should see the message appear:

```
Hello Kafka
```

The message has flowed from your typing in the producer, through the broker, to the consumer. Type a few more messages in the producer:

```
This is message two
And another one
The third message in the lab
```

Each one should appear in the consumer window, in the order you sent them.

#### What Just Happened

When you typed a message in the producer:

1. The producer process serialized your text into bytes.
2. Because there is no key on these messages, the producer chose a partition using a sticky-partitioning strategy (it picks one partition and keeps using it until the batch fills up, then switches).
3. The producer sent the message to the broker, which appended it to the chosen partition's log on disk in `C:\kafka\data\labone-N\`.
4. The broker assigned the message an offset within that partition.
5. Your consumer was polling the broker for new messages on all three partitions, found this one available, fetched it, and printed it to the console.

The whole round trip happens in milliseconds. The fact that you see messages appear in the consumer in the same order you typed them is partly a coincidence of the sticky-partitioning behavior — if the producer happened to switch partitions mid-stream, you might see slight reordering. Within a single partition, order is always preserved; across partitions, it is not.

### 3.4 Observing Asynchrony

Stop the consumer in Window 3 by pressing Ctrl+C. Notice that the producer in Window 2 is unaffected — it does not know or care that there is no consumer. Kafka does not have a concept of "the consumer is gone."

Now type a few more messages in the producer:

```
The consumer is gone
But I can still send
These are accumulating in the topic
```

These messages are flowing into the broker and being stored, even though no consumer is reading them. This is one of Kafka's defining properties: producers and consumers are decoupled in time. The producer publishes; messages persist; consumers read on their own schedule.

### 3.5 Reading from the Beginning

In Window 3, restart the consumer with an additional flag:

```
bin\windows\kafka-console-consumer.bat --topic labone --bootstrap-server localhost:9092 --from-beginning
```

This time the `--from-beginning` flag tells the consumer to start reading from the earliest available offset on each partition, rather than from the latest. You should see all the messages you have produced so far, including the ones produced while the consumer was stopped.

#### Why This Matters

This demonstrates two important Kafka properties:

**Messages are durable.** They were stored on disk by the broker as soon as they were produced. The consumer being absent did not cause any loss of data.

**Consumers control their own position.** The consumer decided where in the log to start reading. With `--from-beginning` it started at offset 0; without it, it would start at the latest offset. In real applications, consumer groups remember where they left off and resume from there automatically. Console consumers without a group don't track position between runs, which is why this flag exists.

You may notice that the messages are not necessarily in the same order you typed them. They are grouped by partition: within each partition the order is preserved, but the consumer reads partitions independently and interleaves their output. This is the per-partition ordering guarantee discussed in the lecture material.

### 3.6 Producing Messages with Keys

Stop the producer in Window 2 with Ctrl+C, then restart it with a new option that lets you specify keys:

```
bin\windows\kafka-console-producer.bat --topic labone --bootstrap-server localhost:9092 --property "parse.key=true" --property "key.separator=:"
```

Now when you produce a message, you can prefix it with a key followed by a colon. Try these:

```
user-1:placed an order
user-2:logged in
user-1:updated profile
user-3:created account
user-1:checked out
user-2:logged out
```

Switch to Window 3 to see them appear (the consumer should still be running with `--from-beginning`).

#### What Keys Do

When a producer includes a key, Kafka hashes the key and uses the hash to deterministically choose a partition. This means **every message with the same key always goes to the same partition.** All three `user-1` messages above went to the same partition. All `user-2` messages went to the same partition (possibly a different one).

This is the mechanism for per-entity ordering. If you want all events for a particular customer, account, or order to be processed in order, you give them the same key. Kafka guarantees order within a partition, so events with the same key are guaranteed to be processed in the order they were produced.

This is a foundational concept for the next lab. When you build a Spring Boot producer, you will choose a key for each message based on whatever entity the events are about, and that choice will determine ordering for downstream consumers.

#### Verify with Describe

Stop the consumer (Ctrl+C in Window 3). Restart it with an option that shows which partition each message came from:

```
bin\windows\kafka-console-consumer.bat --topic labone --bootstrap-server localhost:9092 --from-beginning --property "print.partition=true" --property "print.key=true" --property "key.separator= | "
```

Look at the output. You should see entries like:

```
Partition:0   user-2 |  logged in
Partition:0   user-2 |  logged out
Partition:1   user-1 |  placed an order
Partition:1   user-1 |  updated profile
Partition:1   user-1 |  checked out
Partition:2   user-3 |  created account
```

(Your specific partition assignments may vary, but the principle holds.) Notice that all three `user-1` messages are on the same partition, all `user-2` messages are on another, and `user-3` is on a third. The ordering within each partition matches the order you produced them in.

This is per-key ordering in action. It is one of the most important properties Kafka offers and one of the most common patterns you will use in real applications.

## Section 4: Wrapping Up

### 4.1 What to Leave Running

For the next lab you will need:

- The Kafka broker (Window 1) — leave it running, or be prepared to restart it
- The `labone` topic — it will persist as long as the broker's storage is intact

You can close the producer and consumer windows. They are short-lived clients and easy to restart.

### 4.2 Stopping the Broker

When you are done with the lab and ready to shut down, switch to Window 1 and press Ctrl+C. Kafka will shut down cleanly over a few seconds. If you simply close the window without Ctrl+C, the broker is killed abruptly, which is usually fine but occasionally requires a longer recovery on the next startup.

After Ctrl+C, wait for the prompt to return before closing the window.

### 4.3 What You Have Learned

In this lab you have:

- Started a single-broker Kafka cluster running in KRaft mode
- Examined the broker's storage directory and configuration file
- Created a multi-partition topic
- Sent messages with a console producer
- Received messages with a console consumer, both from the latest offset and from the beginning
- Observed how message keys determine partition assignment and per-entity ordering

These concepts (broker, topic, partition, producer, consumer, offset, key, ordering) are exactly what Spring for Apache Kafka wraps in its programming model. In the next lab you will use a Spring Boot application to produce and consume messages on this same broker, and you will see how the abstractions you have just touched at the command line map onto the Spring annotations and configuration.

### 4.4 Quick Reference

All commands below should be run from the `C:\kafka` directory (use `cd C:\kafka` first in any new Command Prompt window):

| Command | Purpose |
|---|---|
| `bin\windows\kafka-server-start.bat config\server.properties` | Start the broker |
| `bin\windows\kafka-storage.bat random-uuid` | Generate a cluster ID |
| `bin\windows\kafka-storage.bat format -t <UUID> -c config\server.properties --standalone` | Format the broker's storage |
| `bin\windows\kafka-topics.bat --create --topic <name> --partitions <N> --replication-factor 1 --bootstrap-server localhost:9092` | Create a topic |
| `bin\windows\kafka-topics.bat --list --bootstrap-server localhost:9092` | List all topics |
| `bin\windows\kafka-topics.bat --describe --topic <name> --bootstrap-server localhost:9092` | Describe a topic |
| `bin\windows\kafka-console-producer.bat --topic <name> --bootstrap-server localhost:9092` | Start a console producer |
| `bin\windows\kafka-console-consumer.bat --topic <name> --bootstrap-server localhost:9092 [--from-beginning]` | Start a console consumer |

### 4.5 Self-Check

Before moving on to Lab 4.2, you should be able to answer these questions:

1. What is the purpose of the `log.dirs` setting in `server.properties`, and why is it called "log" instead of "data"?
2. When you create a topic with three partitions, how many separate directories does Kafka create in its storage area?
3. If a producer sends a message with key `customer-42`, which partition does it go to?
4. What is the difference between starting a console consumer with and without `--from-beginning`?
5. If two messages have the same key, can they end up on different partitions?

If any of these are unclear, review the relevant section of the lab before continuing.

## End of Lab 4.1
