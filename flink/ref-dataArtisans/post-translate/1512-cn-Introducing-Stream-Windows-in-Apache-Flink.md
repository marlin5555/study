#Apache Flink中的流窗口介绍
>04 Dec 2015 by Fabian Hueske ([@fhueske](https://twitter.com/fhueske))

数据分析领域正在见证在多种使用场景中从批处理转向流处理的变革。虽然批处理可以当作流处理过程的一个特殊形态进行操作，但分析没有终点的流数据时往往需要观念上的转变和与之相匹配的其自身的术语（例如，“windowing”[窗口]和“at-least-once”[至少一次]/“exactly-once”[一次且仅一次]处理）。这种转换和新的术语对于刚刚接触流处理领域的新手来说相当具有迷惑性。Apache Flink是一个生产就绪的流处理引擎，它具有易于上手、有富于表现力的API、数据流上非常灵活的窗口定义，使用这些可以定义出高级的流分析程序。Flink的API具有在流数据上非常灵活的窗口定义特点，这使它在众多开源流处理引擎中可以脱颖而出。

在本博客后续部分，将讨论流处理过程中有关窗口的概念、展示Flink内置的窗口操作、并阐述它对用户自定义窗口语义的支持情况。

##什么是窗口以及它擅长做什么？
考虑一个示例：一个交通传感器可以每隔15秒对经过特定区域的车辆数量进行统计。产生的结果流可以像下面这样：

![](./pics/window-stream.png)

如果你希望知道，有多少车辆经过了这个区域，你只需要对每个独立计数进行简单的累加。但是，一个传感器流的常态是它源源不断地产生数据。对这样一个没有终点的流，不可能在其上计算并最终返回一个累加值。相应地，计算一个滚动的累加结果是可能的，即，对每一个输入事件返回一个更新后的累加值记录。这将产生一个新的流，其具有部分累加结果。

![](./pics/window-rolling-sum.png)

但是，部分累加结果形成的流可能不是我们期望获得的，因为它连续不断地更新计数值，更重要的，一些信息如变量值随着时间丢失了。因此，我们可能希望改写问题，询问每分钟经过这个区域的车的数量。这要求我们对流上的元素进行分组（group）产生一个有限集合，每个集合对应着60秒钟。这个操作被称作是跳动窗口操作（Tumbling window）。

![](./pics/window-tumbling-window.png)

跳动窗口将流**离散化**成一个不重叠的窗口(序列)。对于特定应用来说，窗口是连续的更重要，因为应用可能需要平滑的聚集结果。例如：我们每个30秒计算一次前一分钟内经过这个区域的车数量。这样的窗口被称作是滑动窗口（sliding window）。

![](./pics/window-sliding-window.png)

在数据流上按上述讨论来定义窗口就不是一个并行化操作。这是由于流上的每个元素必须由同一个窗口操作符（也称算子，下面混用）进行处理，由其决定这个元素需要添加到那个窗口内。在全量流上的窗口在Flink中被称作是AllWindows。对很多应用来说，数据流需要分组成多个逻辑流，在每个逻辑流上应用一个窗口操作符。回想刚刚的示例，一个车辆统计的流可以来自多个交通传感器（代替前面示例中的一个传感器），每个传感器监控一个不同的区域。利用传感器id对流进行分组，可以**并行**为每个区域计算窗口内的交通统计信息。在Flink中，我们称分区的窗口为Window，这是因为对分布式流来说它是一种常见形式。下面的图片展示了在一个(sensorId, count)记录对的流上，Tumbling Window聚集两个元素的过程。

![](./pics/windows-keyed.png)

简单来说，窗口在无边界的流上定义了元素的有限集合。这个集合可以基于时间（正如前面的例子），元素个数，时间和个数的结合形式，或用户自定逻辑来把元素分配到窗口中。Flink的DataStream API为大多数常用的窗口操作提供了简明的操作符，同时有一套通用的窗口机制允许用户自定义窗口逻辑。接下来在更详细讨论窗口机制前，我们将先展示Flink基于时间和元素个数的窗口。

## Time Windows

如名称所示，时间窗口会在时间属性上将流中的元素进行分组。例如，一分钟的跳动时间窗口会每分钟收集这一分钟内的所有元素，在过完一分钟时会将函数（如：apply()）应用在这个窗口收集到的所有元素上。

在Apache Flink上定义一个跳动和滑动时间窗口是十分容易的：

```scala
// Stream of (sensorId, carCnt)
val vehicleCnts: DataStream[(Int, Int)] = ...

val tumblingCnts: DataStream[(Int, Int)] = vehicleCnts
  // key stream by sensorId
  .keyBy(0)
  // tumbling time window of 1 minute length
  .timeWindow(Time.minutes(1))
  // compute sum over carCnt
  .sum(1)

val slidingCnts: DataStream[(Int, Int)] = vehicleCnts
  .keyBy(0)
  // sliding time window of 1 minute length and 30 secs trigger interval
  .timeWindow(Time.minutes(1), Time.seconds(30))
  .sum(1)
```

还有一个角度我们并没有讨论到，就是“为这一分钟收集元素”的准确含义，浓缩一下，“流处理器如何解释时间？”。

Apache Flink给出了三种有特色的不同的时间定义，即：处理时间、业务时间、抽取时间。

1. 处理时间，利用当前机器时钟定义窗口，建立并处理一个窗口。即：一分钟的处理时间窗口将收集精确的一分钟内到来的元素。
2. 业务时间，窗口的定义与每一个事件记录中的时间戳相关。对于许多类型的时间来说这是常见的形式，如：日志记录、传感器数据等等，其中时间戳往往代表了这个事件发生的那个时点。业务时间与处理时间相比有几个好处。第一，它使程序语义与执行现状脱钩，使得数据源的供数速度与系统的处理性能跟程序结果无关（译者注：这一点使用处理时间是做不到的，当数据到达速度不稳定，其结果不唯一）。因此你可以使用相同的程序处理历史数据，它可以最大的速率供数，连续不断产生数据。在压力或失败恢复时造成延时的情况下，它也可以防止语义上不正确的结果（译者注：使用处理时间就会发生上面说的结果不稳定，而使用业务时间能有效应对）。第二，即使事件到来的顺序没办法保证按照其时间戳有序，业务时间窗口仍能计算出正确结果。而分布式数据源产生并收集事件的场景中，这种乱序是十分常见的。
3. 抽取时间是处理时间与业务时间的一种混合模式。它将记录在到达系统的第一时间就分配了一个当时机器的机器时钟时间戳，后续处理将基于被赋予的时间戳按照其业务时间语义进行处理。

## 统计窗口

Apache Flink同样提出了有特色的统计窗口。一个跳动的长度为100的统计窗口将在窗口中收集100各事件，当添加了第100个元素时将对窗口进行计算。

在Flink的数据流API中，跳动和滑动统计窗口如下定义：

```scala
// Stream of (sensorId, carCnt)
val vehicleCnts: DataStream[(Int, Int)] = ...

val tumblingCnts: DataStream[(Int, Int)] = vehicleCnts
  // key stream by sensorId
  .keyBy(0)
  // tumbling count window of 100 elements size
  .countWindow(100)
  // compute the carCnt sum
  .sum(1)

val slidingCnts: DataStream[(Int, Int)] = vehicleCnts
  .keyBy(0)
  // sliding count window of 100 elements size and 10 elements trigger interval
  .countWindow(100, 10)
  .sum(1)
```

## Dissecting Flink’s windowing mechanics

Flink内置了时间和统计窗口，这涵盖了用例中的很大范围的通用窗口。但是，在实际应用中仍有需要进行用户定义的窗口逻辑，而这些窗口逻辑可能不能使用Flink内置的窗口处理。为了支持这些需要特定窗口语义的应用，DataStream API将窗口机制内部的实现进行了抽象并将其提出成接口形式。这些接口给定了非常灵活的控制方式，使其可以创建并使用窗口。

下面的图片展示了Flink的窗口机制，其中涉及到的组件在接下来会详细介绍。

![](./pics/window-mechanics.png)

到达窗口算子的元素会先交给`WindowAssigner`进行处理。这个`WindowAssigner`将元素分配给一个或多个窗口，还可能创建新的窗口。一个窗口本身只是一个标识符，其指代了元素的列表并可能提供了可选的元数据信息，如在`TimeWindow`中使用到的开始时间(begin)和结束时间(end)。注意：一个元素可以被添加到多个窗口中，这意味着同一时间可以存在多个窗口。

每个窗口都拥有一个`Trigger`，它决定这个窗口何时被计算或清除。在每个元素插入到窗口中和注册的定时器到时间时，这个触发器都会被调用。在每一个事件上，触发器可以决定触发（即：计算：fire、evaluate）、清除（移除窗口并丢弃其内容）、或触发后清除窗口。触发器触发仅仅使对窗口进行计算，并将其保持原状，也就是说，所有的元素都留在窗口中，当触发器下一次触发时还会进行再次计算。窗口可以被计算多次，并存活到其被清除。注意：除非被清除，否则窗口将一直占据内存。

当触发器触发，窗口中的元素列表可以被给定一个可选的`Evictor`。这个驱逐者会迭代元素列表，并决定从列表中剪除一些元素，即：将开始加入到窗口内的元素移除。剩余的元素将交给计算函数。如果没有定义驱逐者，触发器将所有窗口内的元素直接交给计算函数。

计算函数收到窗口内的元素（可能使用了驱逐者进行过滤），并对窗口进行计算得到一个或多个结果元素。DataStream API接受多种类型的计算函数，包括预定义的聚集函数包括：`sum()`，`min()`，`max()`以及`ReduceFunction`、`FoldFunction`、`WindowFunction`。其中`WindowFunction`是最通用的计算函数，接收一个窗口对象（即：窗口的元数据），窗口元素的列表和窗口的key（在keyed window情况下）作为参数。

这些是构成Flink窗口机制的组件。现在我们将一步步展示使用DataStream API如何实现用户定义窗口逻辑。从类型为DataStream[IN]的流开始，使用key selector对其进行key分组，这个selector将抽取key的类型`KEY`并获得`KeyedStream[IN, KEY]`。

```scala
val input: DataStream[IN] = ...

// created a keyed stream using a key selector function
val keyed: KeyedStream[IN, KEY] = input
  .keyBy(myKeySel: (IN) => KEY)
```

我们应用一个`WindowAssigner[IN, WINDOW]`，它可以创建类型为`WINDOW`并构成结果`WindowedStream[IN, KEY, WINDOW]`。更多的，`WindowAssigner`提供了一个默认的`Trigger`的实现方式。

```scala
// create windowed stream using a WindowAssigner
var windowed: WindowedStream[IN, KEY, WINDOW] = keyed
  .window(myAssigner: WindowAssigner[IN, WINDOW])
```

我们可以明确指定一个`Trigger`来覆盖由`WindowAssigner`提供的默认实现`Trigger`。注意：明确指定触发器并不是添加一个触发器条件，而是替换掉当前的触发器。

```scala
// override the default trigger of the WindowAssigner
windowed = windowed
  .trigger(myTrigger: Trigger[IN, WINDOW])
```

我们可能需要指定一个如下可选的`Evictor`。

```scala
// specify an optional evictor
windowed = windowed
  .evictor(myEvictor: Evictor[IN, WINDOW])
```

最终，应用`WindowFunction`将返回类型为`OUT`的元素构成的`DataStream[OUT]`。

```scala
// apply window function to windowed stream
val output: DataStream[OUT] = windowed
  .apply(myWinFunc: WindowFunction[IN, OUT, KEY, WINDOW])
```

使用Flink内部窗口机制和其暴露的DataStream API，实现完全用户定义的窗口逻辑是可能的，比如会话窗口（session）或一旦结果超过给定阈值就迅速发射出去的窗口。

## 结论

在连续不断的数据流上支持多种类型的窗口对于现代的流处理器来说是一个必选项。Apache Flink作为一款流处理器，具有很丰富的特性集合，包括在连续不断数据流上非常灵活的创建和计算窗口的机制。针对通用的用例，Flink提供了预定义的窗口算子，同时给出了一个工具集，使用它可以定义非常灵活的用户定义窗口逻辑。一旦我们从用户那里学到新的需求，Flink社区将提供更多预定义窗口算子。
