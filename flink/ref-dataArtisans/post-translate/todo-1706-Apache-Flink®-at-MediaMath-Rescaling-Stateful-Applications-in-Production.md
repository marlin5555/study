# Apache Flink® at MediaMath: Rescaling Stateful Applications in Production
June 12, 2017 - [Flink Features](https://data-artisans.com/blog/category/flink-features), [Use Cases](https://data-artisans.com/blog/category/use-cases) by [Stefan Richter](https://data-artisans.com/blog/author/stefan), [Seth Wiesman](https://data-artisans.com/blog/author/swiesman) and [Michael Winters](https://data-artisans.com/blog/author/mike)

This post also appeared on the [MediaMath Developer Blog](https://devblog.mediamath.com/apache-flink-at-mediamath-rescaling-stateful-applications-in-production).

Every once in awhile, Amazon Web Services experiences a service disruption, and millions of internet users around the globe panic as their favorite apps and websites cease to function. A short time later, the issue is resolved, and it’s back to business as usual. Most people move along with their day, eventually forgetting the micro-crisis altogether.

But it’s not so simple for the software engineers whose companies are built on top of AWS and who are responsible for recovering from the disruption. Such was the case for MediaMath, a programmatic marketing company that counts two thirds of the Fortune 500 in its customer base.

MediaMath’s reporting infrastructure, which is powered by Apache Flink®, runs inside AWS. An S3 service disruption earlier this year meant that incoming data wasn’t available to Flink for processing for a number of hours. When S3 came back online, there was a significant backlog of data waiting to be processed–more than their cluster was scaled to handle in a timely manner.

Their solution to the problem? Flink’s rescalable state.

Flink 1.2.0, released in February 2017, introduced the ability to rescale a stateful Flink program without losing existing application state. This means it was possible for MediaMath to add more resources to their cluster during recovery, allowing data that was delayed by the disruption to be restored in a timely manner–all with exactly-once semantics still intact and therefore with zero impact on the integrity of previously-processed results.

In this post, we’ll go into more detail about rescalable state and the different types of application state in Flink. Seth Wiesman, a data engineer at MediaMath, will share more on the S3 disruption and how his company takes advantage of rescalable state in Flink to respond to scenarios that are more resource intensive and to run their Flink application as efficiently as possible. And Stefan Richter, a software engineer at data Artisans, will give an in-depth overview of application state and how it’s managed in Flink.

Whether you’re new to stateful stream processing or you’d just like to learn more about the internals of one of Flink’s most powerful features, this post will provide a detailed overview of application state and its role in real-world stream processing.

每隔一段时间，亚马逊网络服务就会经历一次服务中断，由此引起应用程序和网站的不可访问，会使全球数百万互联网用户感到恐慌。不久之后，问题得到解决，并恢复正常。大多数人随波逐流，最终会完全忘记微观危机。

但，对于负责完成中断恢复的工程师，尤其是那些公司的应用是构建在AWS之上，这件事儿并不那么简单。MediaMath就属于这种情况，在(提供)编程市场（programmatic marketing）中，世界500强公司的三分之二都是它的客户。

MediaMath报告的基础架构由ApacheFlink®提供支持，并在AWS内部运行。今年早些时候，S3服务的中断意味着Flink无法再短时间内处理流入的数据。当S3重新连接时，有大量的数据等待被处理，这些数据超过了其集群的扩展性（scaled）和处理能力（processed）。

他们如何解决这个问题的？答案是Flink可缓存的状态。

Flink 1.2.0，在2017年2月发布，引入了可重扩展的能力（ability to rescale），这使得Flink程序不会丢失现有应用状态。这意味着在恢复期间，MediaMath可以增加更多资源到集群中，从而及时恢复那些由于中断而延迟的数据，所有这些数据都具备完整的一次且仅一次的语义，因此对前序处理结果的完整性影响是零。

在这篇文章中，将详细介绍Flink中的可重扩展状态（rescalable state），以及应用程序状态的不同类型。Seth Wiesman是MediaMath的数据工程师，他将分享在S3中断发生时的更多信息，以及他们公司如何充分利用Flink的可重扩展状态来应对资源密集型的场景，并使得Flink应用可以运行得更高效。Stefan Richter是data Artisans的软件开发工程师，将深入介绍应用程序状态，以及在Flink中如何管理状态。

无论你是有状态流处理的新手还是想了解Flink最强大功能之一的内部结构，本文将详细介绍应用程序状态以及其在实际流处理中的作用。

## One-click Rescaling with Flink at MediaMath

By [Seth Wiesman](https://www.linkedin.com/in/sethwiesman) of MediaMath

MediaMath is a global company running multiple data centers of machines and bidding on billions of online ad impressions each day. Whenever a bid on an ad is won, a log is generated. The log acts as a record of the financial transaction and also contains many interesting dimensions about where the ad was placed.

We use Apache Flink to power our real-time reporting infrastructure, which takes these raw logs and transforms them into detailed and meaningful reports for our clients. Our service level agreements with our customers require us to accept log records up to 7 days late and to reevaluate metrics based on changing metadata, both of which are easily implemented using Flink’s event time semantics and rich windowing APIs. And because our reports act as proof of purchase and are used for billing, it is important that we maintain highly accurate counts, even in the face of failure, which is possible due to Flink’s fault tolerance.

MediaMath 是一家全球性公司，运行着多个数据中心，并对每天数十亿在线广告展示进行出价。每当广告竞标达成，都将生成一条日志。该日志充当了金融交易的记录，还包含了许多关于广告位置信息的多维度信息。

我们使用Apache Flink来支持实时报告基础架构，它将接收这些原始日志，并为我们的客户转换成更详细且有意义的报告。我们与客户签订的服务级别协议要求我们接收最长7天内的日志记录，并可以依据元数据的更改重新计算指标，这两者都可以通过Flink基于事件时间语义和丰富的窗口API得以实现。由于我们的报告充当了购买证明并用于计费，即使面临失败，也应当维护高精度的计算，而这有赖于Flink的容错能力。

Although the transaction data are generated at private data centers, our reporting infrastructure is run within AWS. Logs flow into Amazon through S3, then our Flink cluster is run on EC2 spot instances, automatically failing over into different availability zones as spot prices rise and fall (a separate topic altogether and one that we spoke about at Flink Forward SF).

As mentioned earlier, on February 28th, 2017, there was an S3 outage for the Northern Virginia Region US-EAST-1, and during that time, we were unable to both process new data in Flink and create checkpoints. When S3 regained full functionality, Flink resumed execution from its last successful checkpoint, but there were now several hours’ worth of data that had appeared all at once needing to be processed.

This was more data than our AWS cluster was scaled to handle within a time frame that we and our customers would find acceptable. Luckily, though, we could simply take a savepoint of our Flink job, stop the job, then resume from the savepoint on a larger cluster, quickly processing the additional data.

Once we’d cleared the backlog and event time in the data had caught up with processing time, we again stopped the Flink job with a savepoint and scaled back down to an appropriately-sized cluster. Because Flink provides out-of-the-box rescalable state, this was a relatively easy crisis for us to manage.

The incident inspired us to build a one-click™ rescaling system, which is an automation of the steps described above. In practice, there are three things that need to be highly available when managing Flink:
- The jar containing your job
- The most recent checkpoint
- The location of that checkpoint

Everything except for these three pieces can be treated as ephemeral. For us, S3 provides a checkpoint store, and a small PostgreSQL database provides the rest. We can easily push our job around to clusters of various sizes as circumstances change and we require more or fewer resources.

In the future, we hope to implement automatic rescaling of jobs, but we need to find the correct set of metrics that signal a job is a) having an issue and b) that the issue could be solved by moving it to a larger cluster rather than something that requires human intervention.

Flink’s rescalable state makes it easy for us to respond to unforeseen resource-intensive scenarios, and more generally, to ensure that we’re always running our Flink programs on a properly-sized cluster–all while guaranteeing accurate results, which is mission-critical for us.

## State in Flink and Rescaling Stateful Streaming Jobs

By Stefan Richter ([@StefanRRichter](https://twitter.com/StefanRRichter)) of data Artisans

Now, it’s time for a deep-dive on application state in Flink. We’ll cover 5 topics in this section:

- An Intro to Stateful Stream Processing
- State in Apache Flink
- Requirements for rescaling stateful streaming jobs
- Reassigning operator state when rescaling
- Reassigning keyed state when rescaling

### An Intro to Stateful Stream Processing

At a high level, we can consider state in stream processing as memory in operators that remembers information about past input and can be used to influence the processing of future input.

In contrast, operators in stateless stream processing only consider their current inputs, without further context and knowledge about the past. A simple example to illustrate this difference: let us consider a source stream that emits events with schema e = {event_id:int, event_value:int}. Our goal is, for each event, to extract and output the event_value. We can easily achieve this with a simple source-map-sink pipeline, where the map function extracts the event_value from the event and emits it downstream to an outputting sink. This is an instance of stateless stream processing.

But what if we want to modify our job to output the event value only if it is larger than the value from the previous event? In this case, our map function obviously needs some way to remember the event_value from a past event — and so this is an instance of stateful stream processing.

This example should demonstrate that state is a fundamental, enabling concept in stream processing that is required for a majority of interesting use cases.

### State in Apache Flink

Apache Flink is a massively parallel distributed system that allows stateful stream processing at large scale. For scalability, a Flink job is logically decomposed into a graph of operators, and the execution of each operator is physically decomposed into multiple parallel operator instances. Conceptually, each parallel operator instance in Flink is an independent task that can be scheduled on its own machine in a network-connected cluster of shared-nothing machines.

For high throughput and low latency in this setting, network communications among tasks must be minimized. In Flink, network communication for stream processing only happens along the logical edges in the job’s operator graph (vertically), so that the stream data can be transferred from upstream to downstream operators.

However, there is no communication between the parallel instances of an operator (horizontally). To avoid such network communication, data locality is a key principle in Flink and strongly affects how state is stored and accessed.

For the sake of data locality, all state data in Flink is always bound to the task that runs the corresponding parallel operator instance and is co-located on the same machine that runs the task.

Through this design, all state data for a task is local, and no network communication between tasks is required for state access. Avoiding this kind of traffic is crucial for the scalability of a massively parallel distributed system like Flink.

For Flink’s stateful stream processing, we differentiate between two different types of state: operator state and keyed state. Operator state is scoped per parallel instance of an operator (sub-task), and keyed state can be thought of as “operator state that has been partitioned, or sharded, with exactly one state-partition per key.” We could have easily implemented our previous example as operator state: all events that are routed through the operator instance can influence its value.

### Rescaling Stateful Stream Processing Jobs

Changing the parallelism (that is, changing the number of parallel subtasks that perform work for an operator) in stateless streaming is very easy. It requires only starting or stopping parallel instances of stateless operators and dis-/connecting them to/from their upstream and downstream operators as shown in Figure 1A.

On the other hand, changing the parallelism of stateful operators is much more involved because we must also (i) redistribute the previous operator state in a (ii) consistent, (iii) meaningful way. Remember that in Flink’s shared-nothing architecture, all state is local to the task that runs the owning parallel operator instance, and there is no communication between parallel operator instances at job runtime.

However, there is already one mechanism in Flink that allows the exchange of operator state between tasks, in a consistent way, with exactly-once guarantees — Flink’s checkpointing!

You can see detail about Flink’s checkpoints in the documentation. In a nutshell, a checkpoint is triggered when a checkpoint coordinator injects a special event (a so-called checkpoint barrier) into a stream.

Checkpoint barriers flow downstream with the event stream from sources to sinks, and whenever an operator instance receives a barrier, the operator instance immediately snapshots its current state to a distributed storage system, e.g. HDFS.

On restore, the new tasks for the job (which potentially run on different machines now) can again pick up the state data from the distributed storage system.

![Stateless vs. stateful rescaling in stream processing](https://rawgit.com/marlin5555/study/master/flink/ref-dataArtisans/post-translate/pics/stateless-stateful-streaming.svg)
Figure 1: Stateless vs. Stateful Rescaling in Stream Processing



We can piggyback rescaling of stateful jobs on checkpointing, as shown in Figure 1B. First, a checkpoint is triggered and sent to a distributed storage system. Next, the job is restarted with a changed parallelism and can access a consistent snapshot of all previous state from the distributed storage. While this solves (i) redistribution of a (ii) consistent state across machines there is still one problem: without a clear 1:1 relationship between previous state and new parallel operator instances, how can we assign the state in a (iii) meaningful way?

We could again assign the state from previous map_1 and map_2 to the new map_1 and map_2. But this would leave map_3 with an empty state. Depending on the type of state and concrete semantics of the job, this naive approach could lead to anything from inefficiency to incorrect results.

In the following section, we’ll explain how we solved the problem of efficient, meaningful state reassignment in Flink. Each of Flink state’s two flavours, operator state and keyed state, requires a different approach to state assignment.

### Reassigning Operator State When Rescaling

First, we’ll discuss how state reassignment in rescaling works for operator state. A common real-world use-case of operator state in Flink is to maintain the current offsets for Kafka partitions in Kafka sources. Each Kafka source instance would maintain <PartitionID, Offset> pairs–one pair for each Kafka partition that the source is reading–as operator state. How would we redistribute this operator state in case of rescaling? Ideally, we would like to reassign all <PartitionID, Offset> pairs from the checkpoint in round robin across all parallel operator instances after the rescaling.

As a user, we are aware of the “meaning” of Kafka partition offsets, and we know that we can treat them as independent, redistributable units of state. The problem remains how we can we share this domain-specific knowledge with Flink.

Figure 2A illustrates the previous interface for checkpointing operator state in Flink. On snapshot, each operator instance returned an object that represented its complete state. In the case of a Kafka source, this object was a list of partition offsets.

This snapshot object was then written to the distributed store. On restore, the object was read from distributed storage and passed to the operator instance as a parameter to the restore function.

This approach was problematic for rescaling: how could Flink decompose the operator state into meaningful, redistributable partitions? Even though the Kafka source was actually always a list of partition offsets, the previously-returned state object was a black box to Flink and therefore could not be redistributed.

As a generalized approach to solve this black box problem, we slightly modified the checkpointing interface, called ListCheckpointed. Figure 2B shows the new checkpointing interface, which returns and receives a list of state partitions. Introducing a list instead of a single object makes the meaningful partitioning of state explicit: each item in the list still remains a black box to Flink, but is considered an atomic, independently re-distributable part of the operator state.

![Checkpointed vs. ListCheckpointed State in Apache Flink ](https://rawgit.com/marlin5555/study/master/flink/ref-dataArtisans/post-translate/pics/list-checkpointed.svg)
Figure 2: Checkpointed vs. ListCheckpointed Interface

Our approach provides a simple API with which implementing operators can encode domain-specific knowledge about how to partition and merge units of state. With our new checkpointing interface, the Kafka source makes individual partition offsets explicit, and state reassignment becomes as easy as splitting and merging lists.

```java
public class FlinkKafkaConsumer<T> extends RichParallelSourceFunction<T> implements CheckpointedFunction {

   // ...

   private transient ListState<Tuple2<KafkaTopicPartition, Long>> offsetsOperatorState;

   @Override
   public void initializeState(FunctionInitializationContext context) throws Exception {

      OperatorStateStore stateStore = context.getOperatorStateStore();
      // register the state with the backend
      this.offsetsOperatorState = stateStore.getSerializableListState("kafka-offsets");

      // if the job was restarted, we set the restored offsets
      if (context.isRestored()) {
         for (Tuple2<KafkaTopicPartition, Long> kafkaOffset : offsetsOperatorState.get()) {
            // ... restore logic
         }
      }
   }

   @Override
   public void snapshotState(FunctionSnapshotContext context) throws Exception {

      this.offsetsOperatorState.clear();

      // write the partition offsets to the list of operator states
      for (Map.Entry<KafkaTopicPartition, Long> partition : this.subscribedPartitionOffsets.entrySet()) {
         this.offsetsOperatorState.add(Tuple2.of(partition.getKey(), partition.getValue()));
      }
   }

   // ...

}
```
ListState Code Sample

### Reassigning Keyed State When Rescaling

The second flavour of state in Flink is keyed state. In contrast to operator state, keyed state is scoped by key, where the key is extracted from each stream event.

To illustrate how keyed state differs from operator state, let’s use the following example. Assume we have a stream of events, where each event has the schema {customer_id:int, value:int}. We have already learned that we can use operator state to compute and emit the running sum of values for all customers.

Now assume we want to slightly modify our goal and compute a running sum of values for each individual customer_id. This is a use case from keyed state, as one aggregated state must be maintained for each unique key in the stream.

Note that keyed state is only available for keyed streams, which are created through the keyBy() operation in Flink. The keyBy() operation (i) specifies how to extract a key from each event and (ii) ensures that all events with the same key are always processed by the same parallel operator instance. As a result, all keyed state is transitively also bound to one parallel operator instance, because for each key, exactly one operator instance is responsible. This mapping from key to operator is deterministically computed through hash partitioning on the key.

We can see that keyed state has one clear advantage over operator state when it comes to rescaling: we can easily figure out how to correctly split and redistribute the state across parallel operator instances. State reassignment simply follows the partitioning of the keyed stream. After rescaling, the state for each key must be assigned to the operator instance that is now responsible for that key, as determined by the hash partitioning of the keyed stream.

While this automatically solves the problem of logically remapping the state to sub-tasks after rescaling, there is one more practical problem left to solve: how can we efficiently transfer the state to the subtasks’ local backends?

When we’re not rescaling, each subtask can simply read the whole state as written to the checkpoint by a previous instance in one sequential read.

When rescaling, however, this is no longer possible–the state for each subtask is now potentially scattered across the files written by all subtasks (think about what happens if you change the parallelism in hash(key) mod parallelism). We have illustrated this problem in Figure 3A. In this example, we show how keys are shuffled when rescaling from parallelism 3 to 4 for a key space of 0, 20, using identity as hash function to keep it easy to follow.

A naive approach might be to read all the previous subtask state from the checkpoint in all sub-tasks and filter out the matching keys for each sub-task. While this approach can benefit from a sequential read pattern, each subtask potentially reads a large fraction of irrelevant state data, and the distributed file system receives a huge number of parallel read requests.

Another approach could be to build an index that tracks the location of the state for each key in the checkpoint. With this approach, all sub-tasks could locate and read the matching keys very selectively. This approach would avoid reading irrelevant data, but it has two major downsides. A materialized index for all keys, i.e. a key-to-read-offset mapping, can potentially grow very large. Furthermore, this approach can also introduce a huge amount of random I/O (when seeking to the data for individual keys, see Figure 3A, which typically entails very bad performance in distributed file systems.

Flink’s approach sits in between those two extremes by introducing key-groups as the atomic unit of state assignment. How does this work? The number of key-groups must be determined before the job is started and (currently) cannot be changed after the fact. As key-groups are the atomic unit of state assignment, this also means that the number of key-groups is the upper limit for parallelism. In a nutshell, key-groups give us a way to trade between flexibility in rescaling (by setting an upper limit for parallelism) and the maximum overhead involved in indexing and restoring the state.

We assign key-groups to subtasks as ranges. This makes the reads on restore not only sequential within each key-group, but often also across multiple key-groups. An additional benefit: this also keeps the metadata of key-group-to-subtask assignments very small. We do not maintain explicit lists of key-groups because it is sufficient to track the range boundaries.

We have illustrated rescaling from parallelism 3 to 4 with 10 key-groups in Figure 3B. As we can see, introducing key-groups and assigning them as ranges greatly improves the access pattern over the naive approach. Equation 2 and 3 in Figure 3B also details how we compute key-groups and the range assignment.

![Using key-groups in Apache Flink to change parallelism](https://rawgit.com/marlin5555/study/master/flink/ref-dataArtisans/post-translate/pics/key-groups-3.svg)
Figure 3: Changing Parallelism With and Without Key-Groups

## Wrapping Up
Thanks for staying with us, and we hope you now have a clear idea of how rescalable state works in Apache Flink and how to make use of rescaling in real-world scenarios.

Flink 1.3.0, which was released earlier this month, adds more tooling for state management and fault tolerance in Flink, including incremental checkpoints. And the community is exploring features such as…
- State replication
- State that isn’t bound to the lifecycle of a Flink job
- Automatic rescaling (with no savepoints required)
…for Flink 1.4.0 and beyond.

If you’d like to learn more, we recommend starting with the Apache Flink documentation. You can also check out Seth’s Flink Forward San Francisco talk “From Zero to Streaming” or Stefan’s talk “Improvements for Large State and Recovery in Flink”.
