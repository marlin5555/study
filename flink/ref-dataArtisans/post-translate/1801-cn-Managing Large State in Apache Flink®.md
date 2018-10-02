# Managing Large State in Apache Flink®: An Intro to Incremental Checkpointing
January 23, 2018 - [Flink Features](https://data-artisans.com/blog/category/flink-features), [Resources](https://data-artisans.com/blog/category/resources) by [Stefan Richter](https://data-artisans.com/blog/author/stefan) and [Chris Ward](https://data-artisans.com/blog/author/chris)

Apache Flink 为有状态的流处理进行了专门的设计。快速回顾一下：在流处理应用中，什么是状态？我在[之前的博客](https://data-artisans.com/blog/apache-flink-at-mediamath-rescaling-stateful-applications#stateful-streaming)中，曾定义过状态和有状态的流处理，把定义摘要放在这里，简单复习下：*状态是应用程序算子中的专用内存，它存储的信息由那些已经发生过的事件转换而成，而且该信息对未来事件的处理有所帮助。*

状态在流处理中是一个基础概念，在大多数用例中都会被用到。[Flink文档](https://ci.apache.org/projects/flink/flink-docs-release-1.3/dev/stream/state.html)中有一些值得注意的示例：
- 当应用程序搜索某个特定事件模式时，状态存储了到目前为止的事件序列。
- 当每分钟聚合事件时，状态保存着挂起的聚合。
- 当在数据点流（data points）上训练机器学习模型，状态保存着模型参数的当前版本。

但是，只有状态具有容错性，状态流处理在生产环境中才是有用的。“容错”意味着即使存在软件或机器故障，计算的最终结果也是准确的，不会发生数据丢失或事件的重复计算。

Flink的容错一直是这个框架最强大且受欢迎的特性，它可以最小化软件或机器故障给业务带来的影响，使Flink应用总能确保一次且仅一次（Exactly-once）的结果。

容错的核心是检查点（checkpointing），它是Flink使应用程序状态可容错的机制。Flink中的检查点是应用程序状态的全局异步快照，通过输入流周期性获取 *快照位置*（*这里参见barrier与检查点机制*），并发送到持久化存储中（通常使用的是分布式文件系统）。当发生故障时，Flink将重启应用程序，并使用最新完成的检查点作为起点（start point）。

一些Apache Flink用户运行应用程序时使用了GB甚至TB级的状态。这些用户提到如此大的状态，创建检查点成为了一个缓慢且资源密集型的操作，这就是为什么Flink 1.3引入了一个新特性：增量检查点。

在增量检查点之前，每个Flink检查点都是由应用程序的全量状态组成。我们注意到对每个检查点进行全量状态的写操作并不是必要的，由此创建了增量检查点特性，这个想法是基于当前检查点到下一个检查点很少有太大的变化（*这就是说，我们只需要记录增量的改变，而不需要每次都记录全量的状态*）。增量检查点维护的是检查点之间的差异(difference)（或叫增量(delta)），并且仅存储前一个完成的检查点与当前应用程序状态之间的差异。

增量检查点可以为大状态的作业（job）带来显著的性能提升。具有TB级状态的生产用户在实现这一特性后，使原本3分钟完成的检查点创建时间下降到了30秒。这种性能提升主要来自于不再需要为每个检查点将将全量状态转移到持久化存储中。

## 如何启用？

当前，你只能在[RocksDB](https://ci.apache.org/projects/flink/flink-docs-release-1.4/ops/state/state_backends.html)的持久化存储上使用增量检查点特性，Flink使用了RocksDB内部备份机制来整合检查点数据。因此，增量检查点历史记录在Flink中不会无限增长，Flink最终会自动消费并修建旧的检查点。

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

默认情况下，Flink保留1个完成的检查点，当你需要更多存储下的检查点时，可以在配置文档中对如下标识进行设置：

`state.checkpoints.num-retained`

## 如何工作？

Flink的增量检查点以基于RocksDB的检查点为基础。RocksDB是基于LSM（log-structured-merge）树的键值型存储，它将所有改变（change）收集在被称为“memtable”的可变（mutable|changeable）内存缓冲区。对memtable中相同键的任何修改都会替换其前值，一旦memtable满了，RocksDB将所有记录按照其键排序并写到磁盘上，（*写入磁盘序列化时*）会应用轻型压缩。一旦RocksDB将memtable写到磁盘，它将是不可变的（immutable|unchangeable），此时被称作sstable（sorted-string-table）。

后台压缩任务会对sstable进行合并以消解每个key上潜在的重复，随着时间推移，合并后（merged）的sstable包含来自所有其他sstable的信息，RocksDB将删除原始的sstable。

除此之外，Flink会跟踪RocksDB自上一个检查点以来，创建和删除的sstable文件，由于sstable是不可变的，因此Flink使用它来计算状态的改变。为此，Flink在RocksDB中触发刷新，强制所有memtable写入磁盘成为sstable，并在本地临时目录中产生hard-linked。这个过程相对于管道处理来说是同步操作，Flink以异步方式执行所有后续步骤，不会造成阻塞。

接下来，Flink把所有新的sstable拷贝到稳定存储（stable storage，如：HDFS、S3）中，新的检查点对其进行引用。Flink并不会对之前检查点上已经存在的sstable进行拷贝，而是对它们进行重新引用。由于RocksDB中sstable被删除是由压缩导致的，并且最终通过合并后的sstable对旧的sstable进行替换，因此任何新的检查点都不会引用删除了的文件。这就是Flink增量检查点中如何修剪检查点的历史记录。

为了跟踪检查点间的改变，合并表的上传操作看起来是多余的工作。Flink以增量方式执行这个过程（*合并表上传*），并且通常只增加很少的开销，由于它使得Flink为检查点保存了更短的历史记录，（*更短的历史记录*）方便恢复操作的执行（recovery），因此我们认为做这些是值得的。

(Click on the image below to open a full-size version in a new tab)

![Apache Flink incremental checkpointing implementation example](https://rawgit.com/marlin5555/study/master/flink/ref-dataArtisans/post-translate/pics/incremental_cp_impl_example.svg)

（*上图是*）以算子（operator）的子任务（subtask）为例，它存储了状态，保留检查点的数量设置为2。上图中列分别代表了1.显示每个检查点在本地RocksDB实例中的状态信息，2.（*检查点*）引用的文件，3.在检查点完成后共享状态注册引用数。

对于检查点“CP 1”，本地RocksDB目录中包含了两个sstable文件，它将这些文件视为新文件，并将其上传到持久化存储中，（持久化存储中的）目录名与检查点的名称相匹配。当检查点完成后，Flink会在共享状态注册表中创建两条记录，并将其引用数字设为“1”。共享状态注册表中的Key是算子、子任务、原始sstable文件名的混合体。注册表还保存了从键（key）到持久化存储中文件目录的映射关系（mapping）。

对于检查点“CP 2”，RocksDB创建了两个新的sstable文件（*分别是sstable-(3)、sstable-(4)*），两个老文件（*分别是sstable-(1)、sstable-(2)*）还存在。对检查点“CP 2”来说，Flink添加了两个新文件到持久化存储中，并引用了前面两个文件。当检查点结束时，Flink将所有文件引用次数都增加1。

对于检查点“CP 3”，RocksDB将对sstable进行压缩，使sstable-(1)、sstable-(2)、sstable-(3)融合成sstable-(1,2,3)，并删除原始文件。融合后的文件包含了源文件中相同的信息，并移除了所有重复条目。除了增加合并文件外，sstable-(4)仍存在，并新增了sstable-(5)文件。Flink将新增加的sstable-(1,2,3)和sstable-(5)文件保存到持久化存储中，sstable-(4)被检查点“CP 2”重新引用，并将所有引用文件数增加1。由于保留检查点数量设置为2，因此触发了老的检查点“CP 1”被删除。作为删除结果，Flink将所有“CP 1”引用文件（sstable-(1)、sstable-(2)）的注册表数量减小1。

对于检查点“CP 4”，RocksDB融合了sstable-(4)、sstable-(5)以及新增加的sstable-(6)，形成了sstable-(4,5,6)。Flink增加了新的table到持久化存储中，并对sstable-(1,2,3)和新增加的sstable-(4,5,6)进行了引用，这增加了注册表中sstable-(1,2,3)和sstable-(4,5,6)的引用数，同时由于保留检查点数量原因删除了“CP 2”。由于sstable-(1)、sstable-(2)、sstable-(3)在注册表中的引用数量降到了0，Flink将从持久化存储中对其进行删除。

## 竞争条件和并发检查点

由于Flink可以并行执行多个检查点，有时新的检查点会在确认前一个检查点完成前就启动了。因此，Flink必须考虑使用哪个以前的检查点作为增量检查点的基础。通过检查点协调器，Flink仅从确认后的检查点中进行状态引用，因此它不会无意识地引用已删除的共享文件。

## 从检查点恢复和性能注意事项

如果启用了增量检查点，则在发生故障时不需要对恢复状态（recover state）进行额外配置。如果故障发生了，Flink的JobManager会告知所有的task从最近一次完成的检查点进行状态恢复，无论这个检查点是全量检查点还是增量检查点。每个TaskManager都会从分布式文件系统中下载这个检查点被其（*Task*）引用的状态。

虽然这个特性可以给使用大状态（large state）的用户在检查点生成时间上带来显著地改善，但仍需权衡使用增量检查点。总的来说，这个过程减少了正常操作时用于检查点的时间（*即状态进行持久化的时间*），但这会导致恢复需要更长的时间，恢复用时则与依赖的状态数量相关。如果集群故障特别严重，Flink TaskManager不得不从多个检查点进行读取，与非增量检查点相比，增量检查点的恢复将是更慢的操作。需要规划更大的分布式存储来维护检查点，更大的网络带宽来从存储中读取检查点。

有一些策略有助于改善便利性、性能之间的权衡（trade-off），推荐阅读[Flink的文档](https://ci.apache.org/projects/flink/flink-docs-release-1.4/ops/state/large_state_tuning.html)获取更多细节信息。

有兴趣了解更多关于Flink检查点的相关信息？推荐Stefan Richter在Flink Forward 柏林 2017 的演讲[“A Look at Flink’s Internal Data Structures and Algorithms for Efficient Checkpointing”](https://berlin-2017.flink-forward.org/kb_sessions/a-look-at-flinks-internal-data-structures-and-algorithms-for-efficient-checkpointing/)。

当然你也会喜欢CTO Stephan Ewen在Flink Forward 旧金山 2017 的演讲 [“Experiences Running Flink at Very Large Scale”](https://sf-2017.flink-forward.org/kb_sessions/experiences-running-flink-at-very-large-scale/)。
