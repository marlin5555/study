# Managing Large State in Apache Flink®: An Intro to Incremental Checkpointing
January 23, 2018 - [Flink Features](https://data-artisans.com/blog/category/flink-features), [Resources](https://data-artisans.com/blog/category/resources) by [Stefan Richter](https://data-artisans.com/blog/author/stefan) and [Chris Ward](https://data-artisans.com/blog/author/chris)

Apache Flink was purpose-built for stateful stream processing. Let’s quickly review: what is state in a stream processing application? I defined state and stateful stream processing in a [previous blog post](https://data-artisans.com/blog/apache-flink-at-mediamath-rescaling-stateful-applications#stateful-streaming), and in case you need a refresher, *state is defined as memory in an application’s operators that stores information about previously-seen events that you can use to influence the processing of future events*.

Apache Flink 为有状态的流处理进行了专门的设计。快速回顾一下：在流处理应用中，什么是状态？我在[之前的博客](https://data-artisans.com/blog/apache-flink-at-mediamath-rescaling-stateful-applications#stateful-streaming)中，曾定义过状态和有状态的流处理，把定义摘要放在这里，简单复习下：*状态是应用程序算子中的专用内存，它存储的信息由那些已经发生过的事件转换而成，而且该信息对未来事件的处理有所帮助。*

State is a fundamental, enabling concept in stream processing required for a majority of interesting use cases. Some examples highlighted in the [Flink documentation](https://ci.apache.org/projects/flink/flink-docs-release-1.3/dev/stream/state.html):
- When an application searches for certain event patterns, the state stores the sequence of events encountered so far.
- When aggregating events per minute, the state holds the pending aggregates.
- When training a machine learning model over a stream of data points, the state holds the current version of the model parameters.

状态在流处理中是一个基础概念，在大多数用例中都会被用到。[Flink文档](https://ci.apache.org/projects/flink/flink-docs-release-1.3/dev/stream/state.html)中有一些值得注意的示例：
- 当应用程序搜索某个特定事件模式时，状态存储了到目前为止的事件序列。
- 当每分钟聚合事件时，状态保存着挂起的聚合。
- 当在数据点流（data points）上训练机器学习模型，状态保存着模型参数的当前版本。

However, stateful stream processing is only useful in production environments if the state is fault tolerant. “Fault tolerance” means that even if there’s a software or machine failure, the computed end-result is accurate, with no data loss or double-counting of events.

但是，只有状态具有容错性，状态流处理在生产环境中才是有用的。“容错”意味着即使存在软件或机器故障，计算的最终结果也是准确的，不会发生数据丢失或事件的重复计算。

Flink’s fault tolerance has always been a powerful and popular attribute of the framework, minimizing the impact of software or machine failure on your business and making it possible to guarantee exactly-once results from a Flink application.

Flink的容错一直是这个框架最强大且受欢迎的特性，它可以最小化软件或机器故障给业务带来的影响，使Flink应用总能确保一次且仅一次（Exactly-once）的结果。

Core to this is checkpointing, which is the mechanism Flink uses to make application state fault tolerant. A checkpoint in Flink is a global, asynchronous snapshot of application state and position in the input stream that’s taken on a regular interval and sent to durable storage (usually a distributed file system). In the event of a failure, Flink restarts an application using the most recently-completed checkpoint as a starting point.

容错的核心是检查点（checkpointing），它是Flink使应用程序状态可容错的机制。Flink中的检查点是应用程序状态的全局异步快照，通过输入流周期性获取 *快照位置*（*这里参见barrier与检查点机制*），并发送到持久化存储中（通常使用的是分布式文件系统）。当发生故障时，Flink将重启应用程序，并使用最新完成的检查点作为起点（start point）。

Some Apache Flink users run applications with gigabytes or even terabytes of application state. These users have reported that with such large state, creating a checkpoint was often a slow and resource intensive operation, which is why in Flink 1.3 we introduced a new feature called ‘incremental checkpointing.’

一些Apache Flink用户运行应用程序时使用了GB甚至TB级的状态。这些用户提到如此大的状态，创建检查点成为了一个缓慢且资源密集型的操作，这就是为什么Flink 1.3引入了一个新特性：增量检查点。

Before incremental checkpointing, every single Flink checkpoint consisted of the full state of an application. We created the incremental checkpointing feature after we observed that writing the full state for every checkpoint was often unnecessary, as the state changes from one checkpoint to the next were rarely that large. Incremental checkpointing instead maintains the differences (or ‘delta’) between each checkpoint and stores only the differences between the last completed checkpoint and the current application state.

在增量检查点之前，每个Flink检查点都是由应用程序的全量状态组成。我们注意到对每个检查点进行全量状态的写操作并不是必要的，由此创建了增量检查点特性，这个想法是基于当前检查点到下一个检查点很少有太大的变化（*这就是说，我们只需要记录增量的改变，而不需要每次都记录全量的状态*）。增量检查点维护的是检查点之间的差异(difference)（或叫增量(delta)），并且仅存储前一个完成的检查点与当前应用程序状态之间的差异。

Incremental checkpoints can provide a significant performance improvement for jobs with a very large state. Implementation of the feature by a production user with terabytes of state shows a drop in checkpoint time from more than 3 minutes per checkpoint down to 30 seconds per checkpoint after implementing incremental checkpoints. This improvement is a result of not needing to transfer the full state to durable storage on each checkpoint.

增量检查点可以为大状态的作业（job）带来显著的性能提升。具有TB级状态的生产用户在实现这一特性后，使原本3分钟完成的检查点创建时间下降到了30秒。这种性能提升主要来自于不再需要为每个检查点将将全量状态转移到持久化存储中。

## How to Start

## 如何启用？

Currently, you can only use incremental checkpointing with a [RocksDB state backend](https://ci.apache.org/projects/flink/flink-docs-release-1.4/ops/state/state_backends.html), and Flink uses RocksDB’s internal backup mechanism to consolidate checkpoint data over time. As a result, the incremental checkpoint history in Flink does not grow indefinitely, and Flink eventually consumes and prunes old checkpoints automatically.

当前，你只能在[RocksDB](https://ci.apache.org/projects/flink/flink-docs-release-1.4/ops/state/state_backends.html)的持久化存储上使用增量检查点特性，Flink使用了RocksDB内部备份机制来整合检查点数据。因此，增量检查点历史记录在Flink中不会无限增长，Flink最终会自动消费并修建旧的检查点。

To enable incremental checkpointing in your application, I recommend you read [the Apache Flink documentation on checkpointing](https://ci.apache.org/projects/flink/flink-docs-release-1.4/ops/state/large_state_tuning.html#tuning-rocksdb) for full details, but in summary, you enable checkpointing as normal and also enable incremental checkpointing in the constructor by setting the second parameter to true.

为在你的应用程序中使用增量检查点，推荐你阅读[Apache Flink有关检查点的文档](https://ci.apache.org/projects/flink/flink-docs-release-1.4/ops/state/large_state_tuning.html#tuning-rocksdb)来了解全部细节。总的来说，你可以照常启用检查点，并通过将第二个参数设置为true来在构造函数中启用增量检查点。

### Java Example

```java
StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
env.setStateBackend(new RocksDBStateBackend(filebackend, true));
```

### Scala Example

```scala
val env = StreamExecutionEnvironment.getExecutionEnvironment()
env.setStateBackend(new RocksDBStateBackend(filebackend, true))
```

By default, Flink retains 1 completed checkpoint, so if you need a higher number, you can configure it with the following flag:

`state.checkpoints.num-retained`

默认情况下，Flink保留1个完成的检查点，当你需要更多存储下的检查点时，可以在配置文档中对如下标识进行设置：

`state.checkpoints.num-retained`

## How it Works

## 如何工作？

Flink’s incremental checkpointing uses RocksDB checkpoints as a foundation. RocksDB is a key-value store based on ‘log-structured-merge’ (LSM) trees that collects all changes in a mutable (changeable) in-memory buffer called a ‘memtable’. Any updates to the same key in the memtable replace previous values, and once the memtable is full, RocksDB writes it to disk with all entries sorted by their key and with light compression applied. Once RocksDB writes the memtable to disk it is immutable (unchangeable) and is now called a ‘sorted-string-table’ (sstable).

Flink的增量检查点以基于RocksDB的检查点为基础。RocksDB是基于LSM（log-structured-merge）树的键值型存储，它将所有改变（change）收集在被称为“memtable”的可变（mutable|changeable）内存缓冲区。对memtable中相同键的任何修改都会替换其前值，一旦memtable满了，RocksDB将所有记录按照其键排序并写到磁盘上，（*写入磁盘序列化时*）会应用轻型压缩。一旦RocksDB将memtable写到磁盘，它将是不可变的（immutable|unchangeable），此时被称作sstable（sorted-string-table）。

A ‘compaction’ background task merges sstables to consolidate potential duplicates for each key, and over time RocksDB deletes the original sstables, with the merged sstable containing all information from across all the other sstables.

后台压缩任务会对sstable进行合并以消解每个key上潜在的重复，随着时间推移，合并后（merged）的sstable包含来自所有其他sstable的信息，RocksDB将删除原始的sstable。

On top of this, Flink tracks which sstable files RocksDB has created and deleted since the previous checkpoint, and as the sstables are immutable, Flink uses this to figure out the state changes. To do this, Flink triggers a flush in RocksDB, forcing all memtables into sstables on disk, and hard-linked in a local temporary directory. This process is synchronous to the processing pipeline, and Flink performs all further steps asynchronously and does not block processing.

除此之外，Flink会跟踪RocksDB自上一个检查点以来，创建和删除的sstable文件，由于sstable是不可变的，因此Flink使用它来计算状态的改变。为此，Flink在RocksDB中触发刷新，强制所有memtable写入磁盘成为sstable，并在本地临时目录中产生hard-linked。这个过程相对于管道处理来说是同步操作，Flink以异步方式执行所有后续步骤，不会造成阻塞。

Then Flink copies all new sstables to stable storage (e.g., HDFS, S3) to reference in the new checkpoint. Flink doesn’t copy all sstables that already existed in the previous checkpoint to stable storage but re-references them. Any new checkpoints will no longer reference deleted files because deleted sstables in RocksDB are always the result of compaction, and it eventually replaces old tables with an sstable that is the result of a merge. This how in Flink’s incremental checkpoints can prune the checkpoint history.

接下来，Flink把所有新的sstable拷贝到稳定存储（stable storage，如：HDFS、S3）中，新的检查点对其进行引用。Flink并不会对之前检查点上已经存在的sstable进行拷贝，而是对它们进行重新引用。由于RocksDB中sstable被删除是由压缩导致的，并且最终通过合并后的sstable对旧的sstable进行替换，因此任何新的检查点都不会引用删除了的文件。这就是Flink增量检查点中如何修剪检查点的历史记录。

For tracking changes between checkpoints, the uploading of consolidated tables is redundant work. Flink performs the process incrementally, and typically adds only a small overhead, so we consider this worthwhile because it allows Flink to keep a shorter history of checkpoints to consider in a recovery.

为了跟踪检查点间的改变，合并表的上传操作看起来是多余的工作。Flink以增量方式执行这个过程（*合并表上传*），并且通常只增加很少的开销，由于它使得Flink为检查点保存了更短的历史记录，（*更短的历史记录*）方便恢复操作的执行（recovery），因此我们认为做这些是值得的。

(Click on the image below to open a full-size version in a new tab)

![Apache Flink incremental checkpointing implementation example](https://rawgit.com/marlin5555/study/master/flink/ref-dataArtisans/post-translate/pics/incremental_cp_impl_example.svg)

Take an example with a subtask of one operator that has a keyed state, and the number of retained checkpoints set at 2. The columns in the figure above show the state of the local RocksDB instance for each checkpoint, the files it references, and the counts in the shared state registry after the checkpoint completes.

（*上图是*）以算子（operator）的子任务（subtask）为例，它存储了状态，保留检查点的数量设置为2。上图中列分别代表了1.显示每个检查点在本地RocksDB实例中的状态信息，2.（*检查点*）引用的文件，3.在检查点完成后共享状态注册引用数。

For checkpoint ‘CP 1’, the local RocksDB directory contains two sstable files, and it considers these new and uploads them to stable storage using directory names that match the checkpoint name. When the checkpoint completes, Flink creates the two entries in the shared state registry and sets their counts to ‘1’. The key in the shared state registry is a composite of an operator, subtask, and the original sstable file name. The registry also keeps a mapping from the key to the file path in stable storage.

对于检查点“CP 1”，本地RocksDB目录中包含了两个sstable文件，它将这些文件视为新文件，并将其上传到持久化存储中，（持久化存储中的）目录名与检查点的名称相匹配。当检查点完成后，Flink会在共享状态注册表中创建两条记录，并将其引用数字设为“1”。共享状态注册表中的Key是算子、子任务、原始sstable文件名的混合体。注册表还保存了从键（key）到持久化存储中文件目录的映射关系（mapping）。

For checkpoint ‘CP 2’, RocksDB has created two new sstable files, and the two older ones still exist. For checkpoint ‘CP 2’, Flink adds the two new files to stable storage and can reference the previous two files. When the checkpoint completes, Flink increases the counts for all referenced files by 1.

对于检查点“CP 2”，RocksDB创建了两个新的sstable文件（*分别是sstable-(3)、sstable-(4)*），两个老文件（*分别是sstable-(1)、sstable-(2)*）还存在。对检查点“CP 2”来说，Flink添加了两个新文件到持久化存储中，并引用了前面两个文件。当检查点结束时，Flink将所有文件引用次数都增加1。

For checkpoint ‘CP 3’, RocksDB’s compaction has merged sstable-(1), sstable-(2), and sstable-(3) into sstable-(1,2,3) and deleted the original files. This merged file contains the same information as the source files, with all duplicate entries eliminated. In addition to this merged file, sstable-(4) still exists and there is now a new sstable-(5) file. Flink adds the new sstable-(1,2,3) and sstable-(5) files to stable storage, sstable-(4) is re-referenced from checkpoint ‘CP 2’ and increases the counts for referenced files by 1. The older ‘CP 1’ checkpoint is now deleted as the number of retained checkpoints (2) has been reached. As part of this deletion, Flink decreases the counts for all files referenced ‘CP 1’, (sstable-(1) and sstable-(2)), by 1.

对于检查点“CP 3”，RocksDB将对sstable进行压缩，使sstable-(1)、sstable-(2)、sstable-(3)融合成sstable-(1,2,3)，并删除原始文件。融合后的文件包含了源文件中相同的信息，并移除了所有重复条目。除了增加合并文件外，sstable-(4)仍存在，并新增了sstable-(5)文件。Flink将新增加的sstable-(1,2,3)和sstable-(5)文件保存到持久化存储中，sstable-(4)被检查点“CP 2”重新引用，并将所有引用文件数增加1。由于保留检查点数量设置为2，因此触发了老的检查点“CP 1”被删除。作为删除结果，Flink将所有“CP 1”引用文件（sstable-(1)、sstable-(2)）的注册表数量减小1。

For checkpoint ‘CP-4’, RocksDB has merged sstable-(4), sstable-(5), and a new sstable-(6) into sstable-(4,5,6). Flink adds this new table to stable storage and references it together with sstable-(1,2,3), it increases the counts for sstable-(1,2,3) and sstable-(4,5,6) by 1 and then deletes ‘CP-2’ as the number of retained checkpoints has been reached. As the counts for sstable-(1), sstable-(2), and sstable-(3) have now dropped to 0, and Flink deletes them from stable storage.

对于检查点“CP 4”，RocksDB融合了sstable-(4)、sstable-(5)以及新增加的sstable-(6)，形成了sstable-(4,5,6)。Flink增加了新的table到持久化存储中，并对sstable-(1,2,3)和新增加的sstable-(4,5,6)进行了引用，这增加了注册表中sstable-(1,2,3)和sstable-(4,5,6)的引用数，同时由于保留检查点数量原因删除了“CP 2”。由于sstable-(1)、sstable-(2)、sstable-(3)在注册表中的引用数量降到了0，Flink将从持久化存储中对其进行删除。

## Race Conditions and Concurrent Checkpoints

## 竞争条件和并发检查点

As Flink can execute multiple checkpoints in parallel, sometimes new checkpoints start before confirming previous checkpoints as completed. Because of this, Flink must consider which of the previous checkpoints to use as a basis for a new incremental checkpoint. Flink only references state from a checkpoint confirmed by the checkpoint coordinator so that it doesn’t unintentionally reference a deleted shared file.

由于Flink可以并行执行多个检查点，有时新的检查点会在确认前一个检查点完成前就启动了。因此，Flink必须考虑使用哪个以前的检查点作为增量检查点的基础。通过检查点协调器，Flink仅从确认后的检查点中进行状态引用，因此它不会无意识地引用已删除的共享文件。

## Restoring Checkpoints and Performance Considerations

## 从检查点恢复和性能注意事项

If you enable incremental checkpointing, there are no further configuration steps needed to recover your state in case of failure. If a failure occurs, Flink’s JobManager tells all tasks to restore from the last completed checkpoint, be it a full or incremental checkpoint. Each TaskManager then downloads their share of the state from the checkpoint on the distributed file system.

如果启用了增量检查点，则在发生故障时不需要对恢复状态（recover state）进行额外配置。如果故障发生了，Flink的JobManager会告知所有的task从最近一次完成的检查点进行状态恢复，无论这个检查点是全量检查点还是增量检查点。每个TaskManager都会从分布式文件系统中下载这个检查点被其（*Task*）引用的状态。

Though the feature can lead to a substantial improvement in checkpoint time for users with a large state, there are trade-offs to consider with incremental checkpointing. Overall, the process reduces the checkpointing time during normal operations but can lead to a longer recovery time depending on the size of your state. If the cluster failure is particularly severe and the Flink TaskManagers have to read from multiple checkpoints, recovery can be a slower operation than when using non-incremental checkpointing. You’ll need to plan for larger distributed storage to maintain the checkpoints and the network overhead to read from it.

虽然这个特性可以给使用大状态（large state）的用户在检查点生成时间上带来显著地改善，但仍需权衡使用增量检查点。总的来说，这个过程减少了正常操作时用于检查点的时间（*即状态进行持久化的时间*），但这会导致恢复需要更长的时间，恢复用时则与依赖的状态数量相关。如果集群故障特别严重，Flink TaskManager不得不从多个检查点进行读取，与非增量检查点相比，增量检查点的恢复将是更慢的操作。需要规划更大的分布式存储来维护检查点，更大的网络带宽来从存储中读取检查点。

There are some strategies for improving the convenience/performance trade-off, and I recommend you read [the Flink documentation](https://ci.apache.org/projects/flink/flink-docs-release-1.4/ops/state/large_state_tuning.html) for more details.

有一些策略有助于改善便利性、性能之间的权衡（trade-off），推荐阅读[Flink的文档](https://ci.apache.org/projects/flink/flink-docs-release-1.4/ops/state/large_state_tuning.html)获取更多细节信息。

Interested in learning more about checkpoints in Flink? Check out Stefan Richter’s [Flink Forward Berlin 2017 talk “A Look at Flink’s Internal Data Structures and Algorithms for Efficient Checkpointing”](https://berlin-2017.flink-forward.org/kb_sessions/a-look-at-flinks-internal-data-structures-and-algorithms-for-efficient-checkpointing/).

有兴趣了解更多关于Flink检查点的相关信息？推荐Stefan Richter在Flink Forward 柏林 2017 的演讲[“A Look at Flink’s Internal Data Structures and Algorithms for Efficient Checkpointing”](https://berlin-2017.flink-forward.org/kb_sessions/a-look-at-flinks-internal-data-structures-and-algorithms-for-efficient-checkpointing/)。

And you might also enjoy our CTO Stephan Ewen’s [Flink Forward San Francisco 2017 talk “Experiences Running Flink at Very Large Scale”](https://sf-2017.flink-forward.org/kb_sessions/experiences-running-flink-at-very-large-scale/).

当然你也会喜欢CTO Stephan Ewen在Flink Forward 旧金山 2017 的演讲 [“Experiences Running Flink at Very Large Scale”](https://sf-2017.flink-forward.org/kb_sessions/experiences-running-flink-at-very-large-scale/)。
