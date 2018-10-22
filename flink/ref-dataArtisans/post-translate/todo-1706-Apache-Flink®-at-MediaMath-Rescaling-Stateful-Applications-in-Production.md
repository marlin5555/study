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

由于事务数据是在私有数据中心产生的，而报告基础框架运行在AWS上。日志通过S3流入到亚马逊，因此Flink集群是运行在EC2的spot实例（instance）之上，它会随着spot价格的涨跌自动转移到可用的区域（zone）（这是一个独立的话题，将在Flink Forward SF上谈到这个话题）。

正如前面提到的，在2017年2月28日，北弗吉尼亚地区US-EAST-1发生了S3中断，在此期间，我们无法在Flink中处理新数据也无法创建检查点。当S3恢复完整功能时，Flink从其最后一个成功的检查点开始恢复执行，但现在有几个小时的数据一次性出现并需要处理。

This was more data than our AWS cluster was scaled to handle within a time frame that we and our customers would find acceptable. Luckily, though, we could simply take a savepoint of our Flink job, stop the job, then resume from the savepoint on a larger cluster, quickly processing the additional data.

Once we’d cleared the backlog and event time in the data had caught up with processing time, we again stopped the Flink job with a savepoint and scaled back down to an appropriately-sized cluster. Because Flink provides out-of-the-box rescalable state, this was a relatively easy crisis for us to manage.

这样的数据量超过了，在我们和客户可接受时间范围内，AWS集群进行扩展处理的能力。幸运的是，我们可以简单地获取Flink作业的保存点，停止作业，然后从较大集群上的保存点进行恢复，就可以快速处理额外的数据。

一旦我们清楚了积压的数据，并且数据中的业务（事件）时间（event time）赶上了处理时间，可以将Flink 作业（job）停止在一个保存点，并将集群规模压缩到适当的大小。由于Flink提供了开箱即用的可重扩展的状态，这对我们来说是一个相对容易的危机处理。

The incident inspired us to build a one-click™ rescaling system, which is an automation of the steps described above. In practice, there are three things that need to be highly available when managing Flink:
- The jar containing your job
- The most recent checkpoint
- The location of that checkpoint

Everything except for these three pieces can be treated as ephemeral. For us, S3 provides a checkpoint store, and a small PostgreSQL database provides the rest. We can easily push our job around to clusters of various sizes as circumstances change and we require more or fewer resources.

这一事件启示我们可以构建一键式的可伸缩系统（one-click™ rescaling system），这个系统可以将上述描述的步骤进行自动化处理。实际上，在进行Flink管理时有如下三个事情需要高可用：
- 包含在作业中的jar包
- 最近的检查点
- 该检查点的存储位置

除了上述三点之外的其他所有都可以被视为短暂的（ephemeral，注：也就是说可以被重建）。对我们来说，S3提供了检查点的存储，一个小的PostgreSQL数据库提供了剩下的。随着环境变化，我们可以轻松地将作业推送到不同规模的集群中，并可以按需请求更多或更少的资源。

In the future, we hope to implement automatic rescaling of jobs, but we need to find the correct set of metrics that signal a job is a) having an issue and b) that the issue could be solved by moving it to a larger cluster rather than something that requires human intervention.

Flink’s rescalable state makes it easy for us to respond to unforeseen resource-intensive scenarios, and more generally, to ensure that we’re always running our Flink programs on a properly-sized cluster–all while guaranteeing accurate results, which is mission-critical for us.

在未来，我们希望实现可自动伸缩（automatic rescale）的作业，当然我们需要找到一组正确的度量标准，它可以标识作业a)存在（rescale）问题，b)该问题可以通过将其转移到更大的集群进行解决，而且不需要对其进行人为干预。

Flink 可伸缩的状态使得我们更轻松地应对不可预见的资源密集型的场景，更一般地讲，这个特性保证我们始终在适当大小的集群上运行Flink程序，同时能够保证准确的结果，这对我们来说是至关重要的。

## State in Flink and Rescaling Stateful Streaming Jobs

By Stefan Richter ([@StefanRRichter](https://twitter.com/StefanRRichter)) of data Artisans

Now, it’s time for a deep-dive on application state in Flink. We’ll cover 5 topics in this section:

- An Intro to Stateful Stream Processing
- State in Apache Flink
- Requirements for rescaling stateful streaming jobs
- Reassigning operator state when rescaling
- Reassigning keyed state when rescaling

接下来，是时候深入了解Flink中的应用程序状态了。将在这一节中介绍如下5个方面：
- 有状态流处理的简要介绍
- Apache Flink中的状态
- 可伸缩有状态流作业的需求
- 伸缩时对算子状态进行重分配
- 伸缩时对键状态进行重分配

### An Intro to Stateful Stream Processing

### 有状态流处理的简要介绍

At a high level, we can consider state in stream processing as memory in operators that remembers information about past input and can be used to influence the processing of future input.

In contrast, operators in stateless stream processing only consider their current inputs, without further context and knowledge about the past. A simple example to illustrate this difference: let us consider a source stream that emits events with schema `e = {event_id:int, event_value:int}`. Our goal is, for each event, to extract and output the `event_value`. We can easily achieve this with a simple source-map-sink pipeline, where the map function extracts the event_value from the event and emits it downstream to an outputting sink. This is an instance of stateless stream processing.

But what if we want to modify our job to output the event value only if it is larger than the value from the previous event? In this case, our map function obviously needs some way to remember the event_value from a past event — and so this is an instance of stateful stream processing.

This example should demonstrate that state is a fundamental, enabling concept in stream processing that is required for a majority of interesting use cases.

在高抽象层次，我们将流处理中的状态视为算子的存储器，它可以记忆有关过去输入的信息，并可以用来影响针对未来输入的处理。

相比之下，无状态流处理中的算子仅仅考虑当前输入，并没有关于过去的背景知识。用一个简单的例子来展示其中的不同：设想流的数据源，它可以发射如下格式的事件：`e = {event_id:int, event_value:int}`。我们的目标是，对于任一事件，需要抽取并输出`event_value`，通过一个简单的source-map-sink的流水线就可以实现，其中map函数对于每个事件event抽取event_value值，并将结果发送到sink中。这是一个无状态流处理的实例。

但如果对作业进行修改使其满足：只有当当前值大于前序事件的值时，才将事件值输出，需要怎么做呢？在这个场景中，map函数显然需要通过某种方式‘记住’过去事件中的event_value。这就是有状态流处理的实例。

这个例子说明了状态是流处理中的基础概念，通过启用状态概念，可以使得流处理满足更多的用例。

### State in Apache Flink

### Apache Flink中的状态

Apache Flink is a massively parallel distributed system that allows stateful stream processing at large scale. For scalability, a Flink job is logically decomposed into a graph of operators, and the execution of each operator is physically decomposed into multiple parallel operator instances. Conceptually, each parallel operator instance in Flink is an independent task that can be scheduled on its own machine in a network-connected cluster of shared-nothing machines.

For high throughput and low latency in this setting, network communications among tasks must be minimized. In Flink, network communication for stream processing only happens along the logical edges in the job’s operator graph (vertically), so that the stream data can be transferred from upstream to downstream operators.

Apache Flink是大规模并行分布式系统，它支持大规模的有状态流处理。从扩展性来讲，Flink作业在逻辑上被分解成算子图，每个算子的执行物理上都被分解到多个并行的算子实例中。概念上来说，Flink中每个并行算子实例都是独立的任务（task），可以在无共享（shared-nothing）机器的网络连接集群（network-connected cluster）中，在其所在机器上进行调度（schedule）。

为达到高吞吐低延时的设置，任务间的网络计算需要最小化。在Flink中，流计算中的网络计算仅仅发生在作业中算子图的逻辑边上（垂直），基于此流上的数据可以从上游算子流转到下游算子。

However, there is no communication between the parallel instances of an operator (horizontally). To avoid such network communication, data locality is a key principle in Flink and strongly affects how state is stored and accessed.

For the sake of data locality, all state data in Flink is always bound to the task that runs the corresponding parallel operator instance and is co-located on the same machine that runs the task.

同时，在算子的并行实例之间并没有计算（水平）。为避免此类的网络计算，数据局部性是Flink中的关键原子，并严重影响状态的存储和访问方式。

为了达到数据局部性，Flink中的所有数据状态总是绑定在任务（task）上，运行着相应的并行算子实例，并共同位于运行任务的相同机器上。

Through this design, all state data for a task is local, and no network communication between tasks is required for state access. Avoiding this kind of traffic is crucial for the scalability of a massively parallel distributed system like Flink.

For Flink’s stateful stream processing, we differentiate between two different types of state: operator state and keyed state. Operator state is scoped per parallel instance of an operator (sub-task), and keyed state can be thought of as “operator state that has been partitioned, or sharded, with exactly one state-partition per key.” We could have easily implemented our previous example as operator state: all events that are routed through the operator instance can influence its value.

通过这个设计，同一任务的所有状态数据都是本地的（local），对状态访问需求并不会引起任务之间的网络计算。在大规模并行分布式系统上，例如Flink，实现可伸缩性的关键就是避免引起此类流量（traffic）。

对于Flink的有状态流处理，我们需要区分两类不同的状态：算子的状态和键状态。算子状态的范围是每个算子的并行实例（子任务），键状态可以被看做“算子状态被分区或分片，每个key有一份状态分片”。通过算子状态，可以很容易实现前面的例子：通过算子实例的所有事件都会影响它的值。

### Rescaling Stateful Stream Processing Jobs

### 可伸缩的有状态流处理作业

Changing the parallelism (that is, changing the number of parallel subtasks that perform work for an operator) in stateless streaming is very easy. It requires only starting or stopping parallel instances of stateless operators and dis-/connecting them to/from their upstream and downstream operators as shown in Figure 1A.

On the other hand, changing the parallelism of stateful operators is much more involved because we must also (i) redistribute the previous operator state in a (ii) consistent, (iii) meaningful way. Remember that in Flink’s shared-nothing architecture, all state is local to the task that runs the owning parallel operator instance, and there is no communication between parallel operator instances at job runtime.

改变并行度（即，改变同一算子执行时并行的子任务数量）在无状态流上是很容易的。它仅仅需要启动或停止无状态算子的并行实例，并断开/连接其到上游和下游算子，如图1A所示。

另一方面，改变有状态算子的并行度将牵扯更多内容，由于(i)将前序算子状态进行重分布使其满足(ii)一致性，(iii)有意义。回忆Flink的无共享体系架构，对任务来说所有状态都是本地的（local），并运行着并行算子实例，在作业执行期间并行算子实例之间并不发生计算。

However, there is already one mechanism in Flink that allows the exchange of operator state between tasks, in a consistent way, with exactly-once guarantees — Flink’s checkpointing!

You can see detail about Flink’s checkpoints in [the documentation](https://ci.apache.org/projects/flink/flink-docs-release-1.3/internals/stream_checkpointing.html). In a nutshell, a checkpoint is triggered when a checkpoint coordinator injects a special event (a so-called checkpoint barrier) into a stream.

Flink中已经有机制，满足了任务间以一致的方式交换算子状态，并获得了一次且仅一次的保证--这就是Flink的检查点机制！

可以在这个[文档](https://ci.apache.org/projects/flink/flink-docs-release-1.3/internals/stream_checkpointing.html)中查看有关Flink检查点的更详细信息。简而言之，当检查点协调器将特殊事件（即所谓的检查点栅格barrier）注入到流中，检查点将会被其（barrier）触发。

Checkpoint barriers flow downstream with the event stream from sources to sinks, and whenever an operator instance receives a barrier, the operator instance immediately snapshots its current state to a distributed storage system, e.g. HDFS.

On restore, the new tasks for the job (which potentially run on different machines now) can again pick up the state data from the distributed storage system.

检查点栅格随着事件流从source到sink流向下游，一旦算子实例接收到栅格，该算子实例立即执行快照操作将其当前状态保存到分布式存储系统中，如HDFS。

在还原时，作业中的新任务（现在可能运行在不同的机器上）可以再次从分布式系统中获取状态数据。

![Stateless vs. stateful rescaling in stream processing](https://rawgit.com/marlin5555/study/master/flink/ref-dataArtisans/post-translate/pics/stateless-stateful-streaming.svg)
Figure 1: Stateless vs. Stateful Rescaling in Stream Processing

We can piggyback rescaling of stateful jobs on checkpointing, as shown in Figure 1B. First, a checkpoint is triggered and sent to a distributed storage system. Next, the job is restarted with a changed parallelism and can access a consistent snapshot of all previous state from the distributed storage. While this solves (i) redistribution of a (ii) consistent state across machines there is still one problem: without a clear 1:1 relationship between previous state and new parallel operator instances, how can we assign the state in a (iii) meaningful way?

We could again assign the state from previous map_1 and map_2 to the new map_1 and map_2. But this would leave map_3 with an empty state. Depending on the type of state and concrete semantics of the job, this naive approach could lead to anything from inefficiency to incorrect results.

In the following section, we’ll explain how we solved the problem of efficient, meaningful state reassignment in Flink. Each of Flink state’s two flavours, operator state and keyed state, requires a different approach to state assignment.

我们可以在检查点之上整合入有状态作业的可伸缩特性，如图1B所示。首先，检查点被触发并被保存到分布式存储系统中。接下来，作业通过修改后的并行度进行重启，可以从分布式存储中获取到所有前序状态的一致性快照。这虽然解决了跨机器的(ii)一致性状态的(i)重分布，但仍然存在一个问题：在前序状态和新的并行算子实例之间没有清晰的1:1关系，我们如何通过(iii)有意义的方式进行状态分配？

我们可以再次将前序的map_1和map_2的状态分配给新的map_1和map_2。但这会使得map_3拥有空状态。基于状态的类型和作业的具体语义，这种天真的处理方式将导致低效率到错误结果的任何事情。

在接下来的部分中，将展示我们是如何高效、有意义地解决了Flink中状态重分配的问题的。Flink状态的两种类型，算子状态和键状态，需要不同的状态重分配方式。

### Reassigning Operator State When Rescaling

### 在伸缩时对算子状态重分配

First, we’ll discuss how state reassignment in rescaling works for operator state. A common real-world use-case of operator state in Flink is to maintain the current offsets for Kafka partitions in Kafka sources. Each Kafka source instance would maintain <PartitionID, Offset> pairs–one pair for each Kafka partition that the source is reading–as operator state. How would we redistribute this operator state in case of rescaling? Ideally, we would like to reassign all <PartitionID, Offset> pairs from the checkpoint in round robin across all parallel operator instances after the rescaling.

As a user, we are aware of the “meaning” of Kafka partition offsets, and we know that we can treat them as independent, redistributable units of state. The problem remains how we can we share this domain-specific knowledge with Flink.

首先，我们需要讨论：算子状态在发生伸缩时，状态如何进行重分配。在Flink中，常用的真实用例的算子状态当属Kafka source中对Kafka分区的当前偏移量（offset）的维护。每个Kafka source实例都将维护<PartitionID, Offset>记录对作为其算子状态，在每个Kafka分区都对应一个记录对用于source进行数据读取。在发生伸缩时，如何对算子状态进行重分配？理想情况下，我们希望重新分配从检查点中来的所有<PartitionID, Offset>记录对，通过round robin（循环）方式将其缩放给所有并行算子实例。

作为用户，我们很清楚知道Kafka分区偏移量的内涵，我们可以将其视作独立的，在重分配时可以作为状态的基本单元来处理。接下来的问题就是我们如何才能将这一特定领域的知识应用到Flink上。

Figure 2A illustrates the previous interface for checkpointing operator state in Flink. On snapshot, each operator instance returned an object that represented its complete state. In the case of a Kafka source, this object was a list of partition offsets.

This snapshot object was then written to the distributed store. On restore, the object was read from distributed storage and passed to the operator instance as a parameter to the restore function.

图2A展示了Flink中算子状态在检查点前的接口。在进行快照时，每个算子实例将返回与其全部状态相对应的对象。以Kafka source为例，这个对象就是分区偏移量列表。

这个快照对象接下来写入到分布式存储中。在恢复时，这个对象从分布式存储中被读取，并作为恢复函数的参数被传送给算子实例。

This approach was problematic for rescaling: how could Flink decompose the operator state into meaningful, redistributable partitions? Even though the Kafka source was actually always a list of partition offsets, the previously-returned state object was a black box to Flink and therefore could not be redistributed.

As a generalized approach to solve this black box problem, we slightly modified the checkpointing interface, called ListCheckpointed. Figure 2B shows the new checkpointing interface, which returns and receives a list of state partitions. Introducing a list instead of a single object makes the meaningful partitioning of state explicit: each item in the list still remains a black box to Flink, but is considered an atomic, independently re-distributable part of the operator state.

这种方法在缩放时是存在问题的：Flink如何将算子状态按照有意义的方式进行分解，并且还适用于重新划分分区？虽然Kafka source事实上总是分区偏移量的列表，但对Flink来说前序返回的状态对象是一个黑盒，这是不能被重新分配的。

作为解决黑盒问题的通用方法，我们稍微改造了检查点的接口，被称作ListCheckpointed。图2B展示了新的检查点接口，它将返回和接收状态分区的列表。通过引入列表取代单一的大对象使得状态分区更有意义也更清晰：列表中的每个项对Flink来说仍是黑盒，但它在算子状态重分布时，被看成是原子的、相互之间是独立的。

![Checkpointed vs. ListCheckpointed State in Apache Flink ](https://rawgit.com/marlin5555/study/master/flink/ref-dataArtisans/post-translate/pics/list-checkpointed.svg)
Figure 2: Checkpointed vs. ListCheckpointed Interface

Our approach provides a simple API with which implementing operators can encode domain-specific knowledge about how to partition and merge units of state. With our new checkpointing interface, the Kafka source makes individual partition offsets explicit, and state reassignment becomes as easy as splitting and merging lists.

我们提供了一个简单的API，实现算子可以使用该API编码有关如何分区和合并状态单元的特定领域的知识。使用新的检查点接口，Kafka source可以清晰地指定独立分区的偏移量，并且状态重新分配变得像拆分和合并列表一样简单。

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

### 伸缩时对键状态重新分配

The second flavour of state in Flink is keyed state. In contrast to operator state, keyed state is scoped by key, where the key is extracted from each stream event.

To illustrate how keyed state differs from operator state, let’s use the following example. Assume we have a stream of events, where each event has the schema `{customer_id:int, value:int}`. We have already learned that we can use operator state to compute and emit the running sum of values for all customers.

Flink中的第二种状态是键状态。与算子状态不一样，键状态依据键限定范围，其中键是从每条流事件中抽取出来的。

为展示键状态与算子状态的不同，使用接下来的例子。假设一个事件流，每个事件的模式是`{customer_id:int, value:int}`。已经知道我们可以使用算子状态计算并发射所有用户值的运行时总和。

Now assume we want to slightly modify our goal and compute a running sum of values for each individual customer_id. This is a use case from keyed state, as one aggregated state must be maintained for each unique key in the stream.

Note that keyed state is only available for keyed streams, which are created through the keyBy() operation in Flink. The keyBy() operation (i) specifies how to extract a key from each event and (ii) ensures that all events with the same key are always processed by the same parallel operator instance. As a result, all keyed state is transitively also bound to one parallel operator instance, because for each key, exactly one operator instance is responsible. This mapping from key to operator is deterministically computed through hash partitioning on the key.

现假设我们稍稍修改下目标，为每个独立的customer_id计算值的运行时总和。这是键状态的一个用例，因为必须为流中的每个唯一键来维护一个聚合状态。

注意：键状态仅针对键流（keyed stream）是可用的，其中键流在Flink中可以通过keyBy()操作创建。keyBy()操作(i)指定如何从单一事件中抽取key，(ii)确保所有具有相同key的事件被同一个并行算子实例进行处理。因此，所有键状态绑定到一个并行算子实例，对于每个key，都有一个确定的算子实例为之负责。这种从key到算子的映射是通过key上的哈希散列确定性地计算得出的。

We can see that keyed state has one clear advantage over operator state when it comes to rescaling: we can easily figure out how to correctly split and redistribute the state across parallel operator instances. State reassignment simply follows the partitioning of the keyed stream. After rescaling, the state for each key must be assigned to the operator instance that is now responsible for that key, as determined by the hash partitioning of the keyed stream.

While this automatically solves the problem of logically remapping the state to sub-tasks after rescaling, there is one more practical problem left to solve: how can we efficiently transfer the state to the subtasks’ local backends?

可以看到，在进行重新缩放时，键状态比算子状态有一个明显优势：我们可以很容易弄清楚如何在并行算子实例间正确地分割和重新分配状态。在keyed流分区之后，状态重新分配将十分简单。在缩放之后，每个key的状态必须分配给为该key负责的相应算子实例，而这已经通过keyed stream的哈希分区确定好了。

虽然这会自动解决缩放后状态到子任务之间逻辑上的重映射问题，但仍有一个实践过程中的问题需要解决：我们如何高效地将状态转移到子任务的本地后端？

When we’re not rescaling, each subtask can simply read the whole state as written to the checkpoint by a previous instance in one sequential read.

When rescaling, however, this is no longer possible–the state for each subtask is now potentially scattered across the files written by all subtasks (think about what happens if you change the parallelism in hash(key) mod parallelism). We have illustrated this problem in Figure 3A. In this example, we show how keys are shuffled when rescaling from parallelism 3 to 4 for a key space of 0, 20, using identity as hash function to keep it easy to follow.

如果没有重缩放，每个子任务都可以简单地顺序读取由前序实例写入检查点的整个状态。

当发生重缩放，这种做法不再可行-每个子任务的状态现在可能散列在多个文件中，这些文件可能是由所有子任务写成的（想象下，改变了并行度，而hash(key)对并行度取模）。通过图3A，我们阐述了这个问题。在这个例子中，当把并发度从3缩放到4时，对于key的取值空间0到20，我们展示了key是如何shuffle的，为便于跟踪，我们使用其自身的值作为哈希函数的结果。

A naive approach might be to read all the previous subtask state from the checkpoint in all sub-tasks and filter out the matching keys for each sub-task. While this approach can benefit from a sequential read pattern, each subtask potentially reads a large fraction of irrelevant state data, and the distributed file system receives a huge number of parallel read requests.

Another approach could be to build an index that tracks the location of the state for each key in the checkpoint. With this approach, all sub-tasks could locate and read the matching keys very selectively. This approach would avoid reading irrelevant data, but it has two major downsides. A materialized index for all keys, i.e. a key-to-read-offset mapping, can potentially grow very large. Furthermore, this approach can also introduce a huge amount of random I/O (when seeking to the data for individual keys, see Figure 3A, which typically entails very bad performance in distributed file systems.

简单的做法可能是从所有前序子任务读取检查点获取子任务状态，并过滤每个子任务所匹配的key。虽然这种方法可以发挥顺序读取模式的优势，但每个子任务可能都要读取很大部分不相关的状态数据，并且分布式文件系统将收到大量的分布式读取请求。

另一个方法可以是构建一个索引，该索引将索引检查点中每个key的状态位置。使用这种方法，所有子任务可以非常有选择地定位并读取匹配key的内容。这个方法可以避免读取不相关数据，但是它有两个主要缺陷。所有key的物化索引，即key-to-read-offset的映射，这可能会变得非常大。此外，这个方法可能会引起大量的随机I/O（在为独立key获取数据时，参见图3A，在分布式文件系统中这通常会带来非常差的性能。）

Flink’s approach sits in between those two extremes by introducing key-groups as the atomic unit of state assignment. How does this work? The number of key-groups must be determined before the job is started and (currently) cannot be changed after the fact. As key-groups are the atomic unit of state assignment, this also means that the number of key-groups is the upper limit for parallelism. In a nutshell, key-groups give us a way to trade between flexibility in rescaling (by setting an upper limit for parallelism) and the maximum overhead involved in indexing and restoring the state.

We assign key-groups to subtasks as ranges. This makes the reads on restore not only sequential within each key-group, but often also across multiple key-groups. An additional benefit: this also keeps the metadata of key-group-to-subtask assignments very small. We do not maintain explicit lists of key-groups because it is sufficient to track the range boundaries.

Flink的做法介于上述两个极端中间，它引入键组（key-group）作为状态分配的基本单元。这是如何工作的？key-group的数量在作业启动前必须是确定的，并且（当前）在事后不能修改。由于key-group是状态重分配的基本单元，这意味着key-group的数量是并行度的上限。简而言之，key-group使得我们有了折中的手段：在重缩放时的灵活性（通过设置并发度的上限）和索引与恢复状态时涉及的最大开销之间取得平衡。

我们将key-group按照范围（range）分配给子任务。这使得在恢复时的读取操作的连续读取特性不仅可以在每个key-group内，还可以跨越多个key-group。一个额外的收益是：这将保持key-group-to-subtask的分配映射这一元数据始终很小。我们不维护key-group的明确列表，因为仅仅跟踪范围边界就足够了。

We have illustrated rescaling from parallelism 3 to 4 with 10 key-groups in Figure 3B. As we can see, introducing key-groups and assigning them as ranges greatly improves the access pattern over the naive approach. Equation 2 and 3 in Figure 3B also details how we compute key-groups and the range assignment.

在图3B中我们展示了使用10个key-group将并行度从3缩放到4的场景。可以看出，引入key-group并将其分配相应的范围，与简单的处理方式相比，将大大改善访问模式。图3B中的等式2和3更详细地说明了我们计算key-group和范围分配是如何进行的。

![Using key-groups in Apache Flink to change parallelism](https://rawgit.com/marlin5555/study/master/flink/ref-dataArtisans/post-translate/pics/key-groups-3.svg)
Figure 3: Changing Parallelism With and Without Key-Groups

## Wrapping Up

## 总结一下

Thanks for staying with us, and we hope you now have a clear idea of how rescalable state works in Apache Flink and how to make use of rescaling in real-world scenarios.

[Flink 1.3.0](https://data-artisans.com/blog/apache-flink-1-3-0-evolution-stream-processing), which was released earlier this month, adds more tooling for state management and fault tolerance in Flink, including incremental checkpoints. And the community is exploring features such as…
- State replication
- State that isn’t bound to the lifecycle of a Flink job
- Automatic rescaling (with no savepoints required)
…for Flink 1.4.0 and beyond.

If you’d like to learn more, we recommend starting with the [Apache Flink documentation](https://ci.apache.org/projects/flink/flink-docs-release-1.3/dev/stream/state.html). You can also check out Seth’s Flink Forward San Francisco talk [“From Zero to Streaming”](https://www.youtube.com/watch?v=mSLesPzWplA&t=893s&index=13&list=PLDX4T_cnKjD2UC6wJr_wRbIvtlMtkc-n2) or Stefan’s talk [“Improvements for Large State and Recovery in Flink”](https://www.youtube.com/watch?v=Tn_uk5EDiv8&index=22&list=PLDX4T_cnKjD2UC6wJr_wRbIvtlMtkc-n2).

感谢与我们保持联系，希望你已经对Apache Flink中可伸缩的状态如何工作有了一个清晰地认识，并能够在实际场景中使用可伸缩的特性。

本月早些时候发布的[Flink 1.3.0](https://data-artisans.com/blog/apache-flink-1-3-0-evolution-stream-processing)中，为Flink添加了更多的工具用于状态管理和容错，包括增量检查点。社区正在探索如下特性...
- 状态复制
- 不受Flink作业生命周期约束的状态信息
- 自动伸缩（无需保存点）
...期待Flink1.4.0及更高版本

如果你有兴趣了解更多，推荐从[Apache Flink文档](https://ci.apache.org/projects/flink/flink-docs-release-1.3/dev/stream/state.html)开始。也可以把Seth在Flink Forward San Francisco上的演讲[“From Zero to Streaming”](https://www.youtube.com/watch?v=mSLesPzWplA&t=893s&index=13&list=PLDX4T_cnKjD2UC6wJr_wRbIvtlMtkc-n2) 或Stefan的演讲[“Improvement for Large State and Recovery in Flink”](https://www.youtube.com/watch?v=Tn_uk5EDiv8&index=22&list=PLDX4T_cnKjD2UC6wJr_wRbIvtlMtkc-n2)下载下来观看。
