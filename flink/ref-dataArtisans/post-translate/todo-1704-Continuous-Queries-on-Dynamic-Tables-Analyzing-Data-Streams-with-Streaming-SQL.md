# Continuous Queries on Dynamic Tables: Analyzing Data Streams with Streaming SQL
April 11, 2017 - [Flink Features](https://data-artisans.com/blog/category/flink-features), [Table API and SQL](https://data-artisans.com/blog/category/table-api-and-sql) by [Fabian Hueske](https://data-artisans.com/blog/author/fabian), [Xiaowei Jiang](https://data-artisans.com/blog/author/xiaowei) and [Shaoxuan Wang](https://data-artisans.com/blog/author/shaoxuan)

> Xiaowei Jiang is a Senior Director in Alibaba’s Search Infrastructure division, and Shaoxuan Wang is a Senior Manager in Alibaba’s Search Infrastructure division. Fabian Hueske ([@fhueske](https://twitter.com/fhueske?lang=en)) is a co-founder and software engineer at data Artisans and an Apache Flink® committer and PMC member.
>
> This post originally appeared on the [Apache Flink blog](http://flink.apache.org/news/2017/04/04/dynamic-tables.html). It was reproduced here under the [Apache License, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0.html).

> Xiaowei Jiang 是阿里巴巴搜索基础架构团队的高级总监，Shaoxuan Wang是阿里巴巴搜索基础架构团队的高级经理。Fabian Hueske ([@fhueske](https://twitter.com/fhueske?lang=en))是data Artisans的联合创始人和软件开发工程师，是Apache Flink® 的commiter和项目管理委员会（PMC）成员。
>
>这篇博文最早发于[Apache Flink blog](http://flink.apache.org/news/2017/04/04/dynamic-tables.html)。是在[Apache License, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0.html)版权许可下被复制的。

More and more companies are adopting stream processing and are migrating existing batch applications to streaming or implementing streaming solutions for new use cases. Many of those applications focus on analyzing streaming data. The data streams that are analyzed come from a wide variety of sources such as database transactions, clicks, sensor measurements, or IoT devices.

越来越多的公司在采用流处理引擎，正在将现存的批处理应用迁移到流处理上，或为新用例实现一套流处理解决方案。这些应用大多聚焦在流数据的分析场景上。被用于分析的数据流来源非常广泛，如：数据库事务、点击、传感器测量数据、物联网（IoT）设备等。

![streaming data](./pics/dynamic-tables-1.png)

Apache Flink is very well suited to power streaming analytics applications because it provides support for event-time semantics, stateful exactly-once processing, and achieves high throughput and low latency at the same time. Due to these features, Flink is able to compute exact and deterministic results from high-volume input streams in near real-time while providing exactly-once semantics in case of failures.

Apache Flink非常适合流处理分析应用，这是由于它提供了基于事件时间语义的支持、有状态的一次且仅一次（exactly-once）处理机制、并同时支持了高吞吐和低延时。由于上述特征，Flink完全在高容量输入流上，近乎实时地计算出精确（exact）和确定（deterministic）结果，同时在出现故障时还能提供exactly-once语义支持。

Flink’s core API for stream processing, the [DataStream API](https://ci.apache.org/projects/flink/flink-docs-release-1.2/dev/datastream_api.html), is very expressive and provides primitives for many common operations. Among other features, it offers highly customizable windowing logic, different state primitives with varying performance characteristics, hooks to register and react on timers, and tooling for efficient asynchronous requests to external systems. On the other hand, many stream analytics applications follow similar patterns and do not require the level of expressiveness as provided by the DataStream API. They could be expressed in a more natural and concise way using a domain specific language. As we all know, SQL is the de-facto standard for data analytics. For streaming analytics, SQL would enable a larger pool of people to specify applications on data streams in less time. However, no open source stream processor offers decent SQL support yet.

Flink用于进行流处理核心API，[DataStream API](https://ci.apache.org/projects/flink/flink-docs-release-1.2/dev/datastream_api.html)，具有强大的表现力，并为很多通用操作提供了原语（primitive）支持。除了其他的特性，它还提供了1.高度定制化的窗口逻辑，2.具有不同性能特点的不同的状态原语，3.可以在定时器上注册和响应的钩子（hook），4.用于对外部系统提供的高效异步请求工具。另一方面，很多流分析应用遵循类似的模式，并不需要DataStream API提供的高级别的表现力。使用特定域的语言（意指功能受限），他们希望通过更自然和简洁的方式进行表达。众所周知，SQL是数据分析领域的事实标准。对流处理分析，SQL将使更多的人能够在更短时间内完成数据流上的特定应用。但到目前为止，并没有开源流处理引擎提供像样的SQL支持。

## Why is SQL on Streams a Big Deal?

## 为什么流上的SQL分析很重要？

SQL is the most widely used language for data analytics for many good reasons:
- SQL is declarative: You specify what you want but not how to compute it.
- SQL can be effectively optimized: An optimizer figures out an efficient plan to compute your result.
- SQL can be efficiently evaluated: The processing engine knows exactly what to compute and how to do so efficiently.
- And finally, everybody knows and many tools speak SQL.

SQL在数据分析领域是最广泛使用的语言，这是有很多原因的：
- SQL 是声明式的：只需指定你要什么而不用告诉怎样计算。
- SQL可以被高效地优化：优化器将为计算提供一个高效的执行计划。
- SQL可以被有效评估：处理引擎确切地知道要计算什么，以及如何有效计算出结果。
- 最后，每个人都懂得、很多工具都支持SQL

So being able to process and analyze data streams with SQL makes stream processing technology available to many more users. Moreover, it significantly reduces the time and effort to define efficient stream analytics applications due to the SQL’s declarative nature and potential to be automatically optimized.

However, SQL (and the relational data model and algebra) were not designed with streaming data in mind. Relations are (multi-)sets and not infinite sequences of tuples. When executing a SQL query, conventional database systems and query engines read and process a data set, which is completely available, and produce a fixed sized result. In contrast, data streams continuously provide new records such that data arrives over time. Hence, streaming queries have to continuously process the arriving data and never “complete”.

That being said, processing streams with SQL is not impossible. Some relational database systems feature eager maintenance of materialized views, which is similar to evaluating SQL queries on streams of data. A materialized view is defined as a SQL query just like a regular (virtual) view. However, the result of the query is actually stored (or materialized) in memory or on disk such that the view does not need to be computed on-the-fly when it is queried. In order to prevent that a materialized view becomes stale, the database system needs to update the view whenever its base relations (the tables referenced in its definition query) are modified. If we consider the changes on the view’s base relations as a stream of modifications (or as a changelog stream) it becomes obvious that materialized view maintenance and SQL on streams are somehow related.

因此，能够使用SQL处理和分析数据，将使得流处理技术可为更多用户使用。此外，支持SQL将有效减少用户用于定义高效流分析应用所花费的时间和精力，这主要得益于SQL更自然的声明式特征和自动优化的潜能。

但是，SQL（以及关系数据模型和代数）在设计之初并未考虑到流数据的特性。关系是定义在（多重）集之上的，而非定义在无限元组序列上的。当执行一个SQL查询，传统数据库系统和查询引擎将读取并处理一个数据集（data set），这个数据集是完全可用的，并会产生固定大小的结果。相反，数据流随着数据随时间到来，将源源不断地提供新纪录。因此，流查询必须持续不断地处理到达的数据并永不会“完成”。

话虽如此，使用SQL在流上进行处理也不是不可能。一些关系数据库提供了对物化视图（materialized view）频繁维护的特性，这与在数据流上应用SQL查询十分相似。物化视图由SQL查询定义，就像常规（虚拟）视图一样。但是，查询结果实际上是存储（物化）在内存或磁盘中的，以便在查询时不需要动态计算。为了防止物化视图变得陈旧（stale），无论何时其基本关系（物化视图定义时引用的表）发生了修改，数据库系统就需对视图进行更新。如果我们将视图基本关系上的修改看作是修改（数据）流（或者称之为 changelog 流），则显然物化视图的维护就和流上SQL的执行在某种程度上是相似的。

## Flink’s Relational APIs: Table API and SQL

## Flink 关系API：Table API和SQL

Since version 1.1.0 (released in August 2016), Flink features two semantically equivalent relational APIs, the language-embedded Table API (for Java and Scala) and standard SQL. Both APIs are designed as unified APIs for online streaming and historic batch data. This means that

**a query produces exactly the same result regardless whether its input is static batch data or streaming data.**

Unified APIs for stream and batch processing are important for several reasons. First of all, users only need to learn a single API to process static and streaming data. Moreover, the same query can be used to analyze batch and streaming data, which allows to jointly analyze historic and live data in the same query. At the current state we haven’t achieved complete unification of batch and streaming semantics yet, but the community is making very good progress towards this goal.

The following code snippet shows two equivalent Table API and SQL queries that compute a simple windowed aggregate on a stream of temperature sensor measurements. The syntax of the SQL query is based on [Apache Calcite’s](https://calcite.apache.org/) syntax for [grouped window functions](https://calcite.apache.org/docs/reference.html#grouped-window-functions) and will be supported in version 1.3.0 of Flink.

自从1.1.0版本（发布于2016年8月），Flink具有了两个语义上等价的关系API，嵌入式语言的Table API（对java和scala编程来说）和标准SQL。两个API在处理在线流数据和历史批量数据上都被设计成统一的API（*意即两个API都可以同时处理流和批*）。这意味着：

**无论输入是静态批量数据还是流数据，查询总是能产生完全相同的结果。**

出于多种原因考虑，将流和批处理的API进行统一都是十分重要的。首先，对用户来说，处理静态数据和流数据，只需要学习一套API。进一步，相同的查询可以同时用在分析批量数据和流数据上，这允许在用同一个查询联合分析历史数据和在线数据。在当前状态下，我们尚未完成批和流在语义上的完全统一，但社区一直在朝着这个目标前进并取得了很好的进展。

下面的代码片段展示了Table API和SQL两个等效API查询，这些查询在温度传感器测量的数据流上完成简单的窗口聚合操作。SQL查询的语法是基于[Apache Calcite](https://calcite.apache.org/)的分组窗口函数（[grouped window functions](https://calcite.apache.org/docs/reference.html#grouped-window-functions)）语法，这将在Flink 1.3.0版本中得到支持。

```scala
val env = StreamExecutionEnvironment.getExecutionEnvironment
 env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime)

val tEnv = TableEnvironment.getTableEnvironment(env)

// define a table source to read sensor data (sensorId, time, room, temp)
 val sensorTable = ??? // can be a CSV file, Kafka topic, database, or ...
 // register the table source
 tEnv.registerTableSource("sensors", sensorTable)

// Table API
 val tapiResult: Table = tEnv.scan("sensors") // scan sensors table
 .window(Tumble over 1.hour on 'rowtime as 'w) // define 1-hour window
 .groupBy('w, 'room) // group by window and room
 .select('room, 'w.end, 'temp.avg as 'avgTemp) // compute average temperature

// SQL
 val sqlResult: Table = tEnv.sql("""
 |SELECT room, TUMBLE_END(rowtime, INTERVAL '1' HOUR), AVG(temp) AS avgTemp
 |FROM sensors
 |GROUP BY TUMBLE(rowtime, INTERVAL '1' HOUR), room
 |""".stripMargin)
```

As you can see, both APIs are tightly integrated with each other and Flink’s primary [DataStream](https://ci.apache.org/projects/flink/flink-docs-release-1.2/dev/datastream_api.html) and [DataSet](https://ci.apache.org/projects/flink/flink-docs-release-1.2/dev/batch/index.html) APIs. A `Table` can be generated from and converted to a `DataSet` or `DataStream`. Hence, it is easily possible to scan an external table source such as a database or [Parquet](https://parquet.apache.org/) file, do some preprocessing with a `Table` API query, convert the result into a `DataSet` and run a [Gelly](https://ci.apache.org/projects/flink/flink-docs-release-1.2/dev/libs/gelly/index.html) graph algorithm on it. The queries defined in the example above can also be used to process batch data by changing the execution environment.

如您所见，这两个API相互间深度集成，并与Flink基础的DataStream和DataSet API也有深度集成。一个`Table`可以生成自/转化成一个`DataSet`或`DataStream`。因此，可以很容易扫描外部数据源如数据库或[Parquet](https://parquet.apache.org/)文件，并用`Table`API语句进行一些预处理操作，并将结果写到`DataSet`，在之后可以使用[Gelly](https://ci.apache.org/projects/flink/flink-docs-release-1.2/dev/libs/gelly/index.html)图算法在其上进行分析。示例中，同样的查询语句只需要修改下运行时环境，就可以用来处理批数据。

Internally, both APIs are translated into the same logical representation, optimized by Apache Calcite, and compiled into DataStream or DataSet programs. In fact, the optimization and translation process does not know whether a query was defined using the Table API or SQL. If you are curious about the details of the optimization process, have a look at [a blog post](http://flink.apache.org/news/2016/05/24/stream-sql.html) that we published last year. Since the Table API and SQL are equivalent in terms of semantics and only differ in syntax, we always refer to both APIs when we talk about SQL in this post.

在内部，两个API会被转换成同一个逻辑表达，并被Apache Calcite进行优化，最后编译成DataStream或DataSet API的执行程序。事实上，优化和翻译程序并不知道查询被定义成Table API还是SQL形式。如果很好奇优化过程的细节，可以参看去年发布的[一篇博文](http://flink.apache.org/news/2016/05/24/stream-sql.html)。由于Table API和SQL在语义层面是等价的，其差异仅仅表现在语法层面，接下来当我们提到SQL时事实上指的是这两类API。

In its current state (version 1.2.0), Flink’s relational APIs support a limited set of relational operators on data streams, including projections, filters, and windowed aggregates. All supported operators have in common that they never update result records which have been emitted. This is clearly not an issue for record-at-a-time operators such as projection and filter. However, it affects operators that collect and process multiple records as for instance windowed aggregates. Since emitted results cannot be updated, input records, which arrive after a result has been emitted, have to be discarded in Flink 1.2.0.

The limitations of the current version are acceptable for applications that emit data to storage systems such as Kafka topics, message queues, or files which only support append operations and no updates or deletes. Common use cases that follow this pattern are for example continuous ETL and stream archiving applications that persist streams to an archive or prepare data for further online (streaming) analysis or later offline analysis. Since it is not possible to update previously emitted results, these kinds of applications have to make sure that the emitted results are correct and will not need to be corrected in the future. The following figure illustrates such applications.

在当前情况（1.2.0版本），Flink关系API在数据流上，支持有限的关系操作符，包括：投影（projection）、过滤（filter）、窗口聚合（windowed aggregate）。所有支持的算子（操作符）都有一个共同点：永远不会修改已发射（emit）出去的记录。这对如投影、过滤这些一次只处理一条记录的算子来说不是问题。但对收集（collect）并处理（process）多条记录的算子会有影响，比如窗口聚合。由于无法更新发出的结果，在Flink 1.2.0中，那些迟到（在结果发出后才到达）的输入数据只能被丢弃。

当前版本的限制对一些应用程序来说是可接受的，这类应用程序的下游存储往往是是Kafka topic、消息管道（message queue）、仅支持追加（append）不支持更新删除操作的文件系统。这些常见用例遵循如下模式：连续的ETL和流存档应用将1.持久化流数据到存档位置；2.为将来的在线（流）分析或离线分析准备数据。由于不支持对已发出数据进行修改，这类应用必须确保发出的结果是正确的，并且将来不需要被修改。下图展示了这类应用：

![streaming data](./pics/dynamic-tables-2.png)

While queries that only support appends are useful for some kinds of applications and certain types of storage systems, there are many streaming analytics use cases that need to update results. This includes streaming applications that cannot discard late arriving records, need early results for (long-running) windowed aggregates, or require non-windowed aggregates. In each of these cases, previously emitted result records need to be updated. Result-updating queries often materialize their result to an external database or key-value store in order to make it accessible and queryable for external applications. Applications that implement this pattern are dashboards, reporting applications, or [other applications](http://2016.flink-forward.org/kb_sessions/joining-infinity-windowless-stream-processing-with-flink/), which require timely access to continuously updated results. The following figure illustrates these kind of applications.



![Continuous Queries](./pics/dynamic-tables-3.png)

## Continuous Queries on Dynamic Tables

Support for queries that update previously emitted results is the next big step for Flink’s relational APIs. This feature is so important because it vastly increases the scope of the APIs and the range of supported use cases. Moreover, many of the newly supported use cases can be challenging to implement using the DataStream API.

So when adding support for result-updating queries, we must of course preserve the unified semantics for stream and batch inputs. We achieve this by the concept of Dynamic Tables. A dynamic table is a table that is continuously updated and can be queried like a regular, static table. However, in contrast to a query on a batch table which terminates and returns a static table as result, a query on a dynamic table runs continuously and produces a table that is continuously updated depending on the modification on the input table. Hence, the resulting table is a dynamic table as well. This concept is very similar to materialized view maintenance as we discussed before.

Assuming we can run queries on dynamic tables which produce new dynamic tables, the next question is, “How do streams and dynamic tables relate to each other?” The answer is that streams can be converted into dynamic tables and dynamic tables can be converted into streams. The following figure shows the conceptual model of processing a relational query on a stream.

![Continuous Queries ](./pics/dynamic-tables-4.png)

First, the stream is converted into a dynamic table. The dynamic table is queried with a continuous query, which produces a new dynamic table. Finally, the resulting table is converted back into a stream. It is important to note that this is only the logical model and does not imply how the query is actually executed. In fact, a continuous query is internally translated into a conventional DataStream program.

In the following, we describe the different steps of this model:
- Defining a dynamic table on a stream,
- Querying a dynamic table, and
- Emitting a dynamic table.

## Defining a Dynamic Table on a Stream

The first step of evaluating a SQL query on a dynamic table is to define a dynamic table on a stream. This means we have to specify how the records of a stream modify the dynamic table. The stream must carry records with a schema that is mapped to the relational schema of the table. There are two modes to define a dynamic table on a stream: Append Mode and Update Mode.

In append mode each stream record is an insert modification to the dynamic table. Hence, all records of a stream are appended to the dynamic table such that it is ever-growing and infinite in size. The following figure illustrates the append mode.

![Dynamic Table on a Stream](./pics/dynamic-tables-5-1560x501.png)

In update mode a stream record can represent an insert, update, or delete modification on the dynamic table (append mode is in fact a special case of update mode). When defining a dynamic table on a stream via update mode, we can specify a unique key attribute on the table. In that case, update and delete operations are performed with respect to the key attribute. The update mode is visualized in the following figure.

![Dynamic Table on a Stream](./pics/dynamic-tables-6-1560x303.png)

## Querying a Dynamic Table

Once we have defined a dynamic table, we can run a query on it. Since dynamic tables change over time, we have to define what it means to query a dynamic table. Let’s imagine we take a snapshot of a dynamic table at a specific point in time. This snapshot can be treated as a regular static batch table. We denote a snapshot of a dynamic table A at a point t as A[t]. The snapshot can be queried with any SQL query. The query produces a regular static table as result. We denote the result of a query q on a dynamic table A at time t as q(A[t]). If we repeatedly compute the result of a query on snapshots of a dynamic table for progressing points in time, we obtain many static result tables which are changing over time and effectively constitute a dynamic table. We define the semantics of a query on a dynamic table as follows.

A query q on a dynamic table A produces a dynamic table R, which is at each point in time t equivalent to the result of applying q on A[t], i.e., R[t] = q(A[t]). This definition implies that running the same query on q on a batch table and on a streaming table produces the same result. In the following, we show two examples to illustrate the semantics of queries on dynamic tables.

In the figure below, we see a dynamic input table A on the left side, which is defined in append mode. At time t = 8, A consists of six rows (colored in blue). At time t = 9 and t = 12, one row is appended to A (visualized in green and orange, respectively). We run a simple query on table A which is shown in the center of the figure. The query groups by attribute k and counts the records per group. On the right hand side we see the result of query q at time t = 8 (blue), t = 9 (green), and t = 12 (orange). At each point in time t, the result table is equivalent to a batch query on the dynamic table A at time t.

![Dynamic Table on a Stream](./pics/dynamic-tables-7-1560x706.png)

The query in this example is a simple grouped (but not windowed) aggregation query. Hence, the size of the result table depends on the number of distinct grouping keys of the input table. Moreover, it is worth noticing that the query continuously updates result rows that it had previously emitted instead of merely adding new rows.

The second example shows a similar query which differs in one important aspect. In addition to grouping on the key attribute k, the query also groups records into tumbling windows of five seconds, which means that it computes a count for each value of k every five seconds. Again, we use Calcite’s [group window functions](https://calcite.apache.org/docs/reference.html#grouped-window-functions) to specify this query. On the left side of the figure we see the input table A and how it changes over time in append mode. On the right we see the result table and how it evolves over time.

![Dynamic Table on a Stream](./pics/dynamic-tables-8-1560x654.png)

In contrast to the result of the first example, the resulting table grows relative to the time, i.e., every five seconds new result rows are computed (given that the input table received more records in the last five seconds). While the non-windowed query (mostly) updates rows of the result table, the windowed aggregation query only appends new rows to the result table.

Although this blog post focuses on the semantics of SQL queries on dynamic tables and not on how to efficiently process such a query, we’d like to point out that it is not possible to compute the complete result of a query from scratch whenever an input table is updated. Instead, the query is compiled into a streaming program which continuously updates its result based on the changes on its input. This implies that not all valid SQL queries are supported but only those that can be continuously, incrementally, and efficiently computed. We plan discuss details about the evaluation of SQL queries on dynamic tables in a follow up blog post.

## Emitting a Dynamic Table

Querying a dynamic table yields another dynamic table, which represents the query’s results. Depending on the query and its input tables, the result table is continuously modified by insert, update, and delete changes just like a regular database table. It might be a table with a single row, which is constantly updated, an insert-only table without update modifications, or anything in between.

Traditional database systems use logs to rebuild tables in case of failures and for replication. There are different logging techniques, such as UNDO, REDO, and UNDO/REDO logging. In a nutshell, UNDO logs record the previous value of a modified element to revert incomplete transactions, REDO logs record the new value of a modified element to redo lost changes of completed transactions, and UNDO/REDO logs record the old and the new value of a changed element to undo incomplete transactions and redo lost changes of completed transactions. Based on the principles of these logging techniques, a dynamic table can be converted into two types of changelog streams, a REDO Stream and a REDO+UNDO Stream.

A dynamic table is converted into a redo+undo stream by converting the modifications on the table into stream messages. An insert modification is emitted as an insert message with the new row, a delete modification is emitted as a delete message with the old row, and an update modification is emitted as a delete message with the old row and an insert message with the new row. This behavior is illustrated in the following figure.

![Dynamic Table on a Stream](./pics/dynamic-tables-9-1560x631.png)

The left shows a dynamic table which is maintained in append mode and serves as input to the query in the center. The result of the query converted into a redo+undo stream which is shown at the bottom. The first record (1, A) of the input table results in a new record in the result table and hence in an insert message +(A, 1) to the stream. The second input record with k = ‘A’ (4, A) produces an update of the (A, 1) record in the result table and hence yields a delete message -(A, 1) and an insert message for +(A, 2). All downstream operators or data sinks need to be able to correctly handle both types of messages.

A dynamic table can be converted into a redo stream in two cases: either it is an append-only table (i.e., it only has insert modifications) or it has a unique key attribute. Each insert modification on the dynamic table results in an insert message with the new row to the redo stream. Due to the restriction of redo streams, only tables with unique keys can have update and delete modifications. If a key is removed from the keyed dynamic table, either because a row is deleted or because the key attribute of a row was modified, a delete message with the removed key is emitted to the redo stream. An update modification yields an update message with the updating, i.e., new row. Since delete and update modifications are defined with respect to the unique key, the downstream operators need to be able to access previous values by key. The figure below shows how the result table of the same query as above is converted into a redo stream.

![Dynamic Table on a Stream](./pics/dynamic-tables-10-1560x630.png)

The row (1, A) which yields an insert into the dynamic table results in the +(A, 1) insert message. The row (4, A) which produces an update yields the \*(A, 2) update message.

Common use cases for redo streams are to write the result of a query to an append-only storage system, like rolling files or a Kafka topic, or to a data store with keyed access, such as Cassandra, a relational DBMS, or a compacted Kafka topic. It is also possible to materialize a dynamic table as keyed state inside of the streaming application that evaluates the continuous query and make it queryable from external systems. With this design Flink itself maintains the result of a continuous SQL query on a stream and serves key lookups on the result table, for instance from a dashboard application.

## What will Change When Switching to Dynamic Tables?

In version 1.2, all streaming operators of Flink’s relational APIs, like filter, project, and group window aggregates, only emit new rows and are not capable of updating previously emitted results. In contrast, dynamic table are able to handle update and delete modifications. Now you might ask yourself, How does the processing model of the current version relate to the new dynamic table model? Will the semantics of the APIs completely change and do we need to reimplement the APIs from scratch to achieve the desired semantics?

The answer to all these questions is simple. The current processing model is a subset of the dynamic table model. Using the terminology we introduced in this post, the current model converts a stream into a dynamic table in append mode, i.e., an infinitely growing table. Since all operators only accept insert changes and produce insert changes on their result table (i.e., emit new rows), all supported queries result in dynamic append tables, which are converted back into DataStreams using the redo model for append-only tables. Consequently, the semantics of the current model are completely covered and preserved by the new dynamic table model.

## Conclusion and Outlook

Flink’s relational APIs are great to implement stream analytics applications in no time and used in several production settings. In this blog post we discussed the future of the Table API and SQL. This effort will make Flink and stream processing accessible to more people. Moreover, the unified semantics for querying historic and real-time data as well as the concept of querying and maintaining dynamic tables will enable and significantly ease the implementation of many exciting use cases and applications. As this post was focusing on the semantics of relational queries on streams and dynamic tables, we did not discuss the details of how a query will be executed, which includes the internal implementation of retractions, handling of late events, support for early results, and bounding space requirements. We plan to publish a follow up blog post on this topic at a later point in time.

In recent months, many members of the Flink community have been discussing and contributing to the relational APIs. We made great progress so far. While most work has focused on processing streams in append mode, the next steps on the agenda are to work on dynamic tables to support queries that update their results. If you are excited about the idea of processing streams with SQL and would like to contribute to this effort, please give feedback, join the discussions on the mailing list, or grab a JIRA issue to work on.