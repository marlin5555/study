# Introducing Complex Event Processing (CEP) with Apache Flink
> 06 Apr 2016 by Till Rohrmann ([@stsffap](https://twitter.com/stsffap))

With the ubiquity of sensor networks and smart devices continuously collecting more and more data, we face the challenge to analyze an ever growing stream of data in near real-time. Being able to react quickly to changing trends or to deliver up to date business intelligence can be a decisive factor for a company’s success or failure. A key problem in real time processing is the detection of event patterns in data streams.

Complex event processing (CEP) addresses exactly this problem of matching continuously incoming events against a pattern. The result of a matching are usually complex events which are derived from the input events. In contrast to traditional DBMSs where a query is executed on stored data, CEP executes data on a stored query. All data which is not relevant for the query can be immediately discarded. The advantages of this approach are obvious, given that CEP queries are applied on a potentially infinite stream of data. Furthermore, inputs are processed immediately. Once the system has seen all events for a matching sequence, results are emitted straight away. This aspect effectively leads to CEP’s real time analytics capability.

Consequently, CEP’s processing paradigm drew significant interest and found application in a wide variety of use cases. Most notably, CEP is used nowadays for financial applications such as stock market trend and credit card fraud detection. Moreover, it is used in RFID-based tracking and monitoring, for example, to detect thefts in a warehouse where items are not properly checked out. CEP can also be used to detect network intrusion by specifying patterns of suspicious user behaviour.

Apache Flink with its true streaming nature and its capabilities for low latency as well as high throughput stream processing is a natural fit for CEP workloads. Consequently, the Flink community has introduced the first version of a new CEP library with Flink 1.0. In the remainder of this blog post, we introduce Flink’s CEP library and we illustrate its ease of use through the example of monitoring a data center.

随着传感器网络和智能设备的普及，其特点是连续不断地收集越来越多的数据，我们将面临下面的挑战：以准实时的方式在持续增长的数据流上进行分析。面对趋势改变的快速应对能力和提供最新商业智能（决策）的能力对一个公司的成败来说将是至关重要的因素。实时处理的一个关键问题是在数据流中检测事件模式（event pattern）。

复杂事件处理（CEP）就是要精确处理这个问题：在连续不断到来的事件上匹配一个确定的模式。匹配的结果往往是复杂事件，是从输入的事件中提取的。与传统的DBMS不同，DBMS在存储的数据上执行查询（query），CEP则需要在存储的查询上连续地“过”数据。与查询无关的数据可以被快速丢弃。这个方法的优势是显而易见的，给定的CEP查询被应用到潜在的无止尽的数据流上。更多的是，输入会被即时处理。一旦系统已经找到匹配序列的所有事件，结果将直接输出出去。这个特点使CEP具有实时分析能力。

作为结果，CEP的处理范式可以获得很重大的收益，并可以创造广泛用例的应用。值得提出的是，CEP正应用于金融应用中，如股票市场趋势和信用卡欺诈检测。更进一步，他也被应用在基于RFID的跟踪和监控，如，在商品未被合理检查的仓库中检测偷盗。CEP同样可以应用在检测网络入侵，通过确定监控用户行为的模式来实现。

Apache Flink具有原生的流特性和低延时高吞吐的流处理能力，利用这些可以说其是为CEP工作而生。随之而来的，Flink社区在[Flink 1.0](http://flink.apache.org/news/2016/03/08/release-1.0.0.html)引入了第一个版本的[CEP库](https://ci.apache.org/projects/flink/flink-docs-master/apis/streaming/libs/cep.html)。在这片博客接下来的部分，我们将介绍Flink CEP库，并通过监控数据中心的实例来展示其易用性。

## Monitoring and alert generation for data centers
![](http://flink.apache.org/img/blog/cep-monitoring.svg)

Assume we have a data center with a number of racks. For each rack the power consumption and the temperature are monitored. Whenever such a measurement takes place, a new power or temperature event is generated, respectively. Based on this monitoring event stream, we want to detect racks that are about to overheat, and dynamically adapt their workload and cooling.

For this scenario we use a two staged approach. First, we monitor the temperature events. Whenever we see two consecutive events whose temperature exceeds a threshold value, we generate a temperature warning with the current average temperature. A temperature warning does not necessarily indicate that a rack is about to overheat. But whenever we see two consecutive warnings with increasing temperatures, then we want to issue an alert for this rack. This alert can then lead to countermeasures to cool the rack.

假设我们有具有一定数量支架的数据中心。对每个支架消耗的能量和温度将被监控到。一旦发生测量，将会产生一个相应的能量或温度事件。基于这个监控事件流，我们希望监测那些可能过热的支架，并动态调配它们的负载并降温。

在这个场景中，我们使用两阶段的方法。首先，我们监控温度事件。一旦我们发现两个连续事件它们的温度都超过了给定阈值，将产生一个温度警告，取当前的平均温度。温度警告不足以确定支架已经过热。但一旦我们发现两个连续的警告并且其温度是增加的，那么我们希望给这个支架产生一个警告。这个警告可以引导对策来给支架降温。

### Implementation with Apache Flink

First, we define the messages of the incoming monitoring event stream. Every monitoring message contains its originating rack ID. The temperature event additionally contains the current temperature and the power consumption event contains the current voltage. We model the events as POJOs:

### 使用Apache Flink来实现

首先，我们定义输入的监控事件流。每个监控的信息包括其产生的支架ID。附加的温度事件包含当前温度，能量消耗事件包含当前的电压。我们使用POJO来构造模型：

``` java
public abstract class MonitoringEvent {
    private int rackID;
    ...
}

public class TemperatureEvent extends MonitoringEvent {
    private double temperature;
    ...
}

public class PowerEvent extends MonitoringEvent {
    private double voltage;
    ...
}
```

Now we can ingest the monitoring event stream using one of Flink’s connectors (e.g. Kafka, RabbitMQ, etc.). This will give us a `DataStream<MonitoringEvent> inputEventStream` which we will use as the input for Flink’s CEP operator. But first, we have to define the event pattern to detect temperature warnings. The CEP library offers an intuitive Pattern API to easily define these complex patterns.

Every pattern consists of a sequence of events which can have optional filter conditions assigned. A pattern always starts with a first event to which we will assign the name “First Event”.

现在可以使用Flink的连接器（如Kafka、RabbitMQ等）来抽取监控事件流。这将给我们提供一个`DataStream<MonitoringEvent> inputEventStream`，我们将其作为Flink CEP算子的输入。但首先，我们需要定义用于检测温度警告的事件模式。CEP库提供了直观的[Pattern API](https://ci.apache.org/projects/flink/flink-docs-master/apis/streaming/libs/cep.html#the-pattern-api)，可以简单地定义这些复杂事件。

每个模式有一系列事件组成，它可以分配一个可选的过滤条件。模式总是由一个事件开始，这里我们将其命名为“First Event”。

```scala
Pattern.<MonitoringEvent>begin("First Event");
```

This pattern will match every monitoring event. Since we are only interested in `TemperatureEvents` whose temperature is above a threshold value, we have to add an additional subtype constraint and a where clause:

这个模式将匹配每个监控事件。由于我们只对包含超过阈值温度的`TemperatureEvents`感兴趣，所以我们将添加一个subtype约束和一个where子句：

```scala
Pattern.<MonitoringEvent>begin("First Event")
    .subtype(TemperatureEvent.class)
    .where(evt -> evt.getTemperature() >= TEMPERATURE_THRESHOLD);
```

As stated before, we want to generate a `TemperatureWarning` if and only if we see two consecutive `TemperatureEvents` for the same rack whose temperatures are too high. The Pattern API offers the next call which allows us to add a new event to our pattern. This event has to follow directly the first matching event in order for the whole pattern to match.

如上所述，我们希望当遇到同一个温度过高的支架上两个连续`TemperatureEvents`时产生一个`TemperatureWarning`。模式API提供了`next`调用，它允许我们将新的事件添加到我们的模式中。这个事件必须紧接着第一个匹配的事件以使得整个模式都能够匹配。

```scala
Pattern<MonitoringEvent, ?> warningPattern = Pattern.<MonitoringEvent>begin("First Event")
    .subtype(TemperatureEvent.class)
    .where(evt -> evt.getTemperature() >= TEMPERATURE_THRESHOLD)
    .next("Second Event")
    .subtype(TemperatureEvent.class)
    .where(evt -> evt.getTemperature() >= TEMPERATURE_THRESHOLD)
    .within(Time.seconds(10));
```

The final pattern definition also contains the within API call which defines that two consecutive `TemperatureEvents` have to occur within a time interval of 10 seconds for the pattern to match. Depending on the time characteristic setting, this can either be processing, ingestion or event time.

Having defined the event pattern, we can now apply it on the `inputEventStream`.

最终的模式定义包含了API调用，它定义了两个连续`TemperatureEvents`必须在一个10秒的时间间隔内发生才算这个模式匹配上了。依赖于时间特征的设定，它可以是处理时间、抽取时间、事件时间。

在定义事件模式基础上，可以将之应用于`inputEventStream`。

```scala
PatternStream<MonitoringEvent> tempPatternStream = CEP.pattern(
    inputEventStream.keyBy("rackID"),
    warningPattern);
```

Since we want to generate our warnings for each rack individually, we `keyBy` the input event stream by the “rackID” POJO field. This enforces that matching events of our pattern will all have the same rack ID.

The `PatternStream<MonitoringEvent>` gives us access to successfully matched event sequences. They can be accessed using the `select` API call. The `select` API call takes a `PatternSelectFunction` which is called for every matching event sequence. The event sequence is provided as a `Map<String, MonitoringEvent>` where each `MonitoringEvent` is identified by its assigned event name. Our pattern select function generates for each matching pattern a `TemperatureWarning` event.

由于我们希望为每个支架独立产生警告，可以使用`keyBy`在输入事件流上利用“rackID”的POJO字段进行分区。这强制所有参与模式匹配的事件都有同样的支架ID。

`PatternStream<MonitoringEvent>`允许我们访问成功匹配事件序列。使用`select` API调用可以对其访问。`select` API调用将使用`PatternSelectFunction`，它对每一个匹配事件序列进行应用。事件序列以`Map<String, MonitoringEvent>`形式提供，其中每个`MonitoringEvent`由其赋予的事件名称标识。模式选择函数为每个匹配模式产生一个`TemperatureWarning`事件。

```scala
public class TemperatureWarning {
    private int rackID;
    private double averageTemperature;
    ...
}

DataStream<TemperatureWarning> warnings = tempPatternStream.select(
    (Map<String, MonitoringEvent> pattern) -> {
        TemperatureEvent first = (TemperatureEvent) pattern.get("First Event");
        TemperatureEvent second = (TemperatureEvent) pattern.get("Second Event");

        return new TemperatureWarning(
            first.getRackID(),
            (first.getTemperature() + second.getTemperature()) / 2);
    }
);
```

Now we have generated a new complex event stream `DataStream<TemperatureWarning> warnings`  from the initial monitoring event stream. This complex event stream can again be used as the input for another round of complex event processing. We use the `TemperatureWarnings` to generate `TemperatureAlerts` whenever we see two consecutive TemperatureWarnings for the same rack with increasing temperatures. The TemperatureAlerts have the following definition:

现在已经从初始的监控事件流产生了一个新的复杂事件流`DataStream<TemperatureWarning> warnings`。这个复杂事件流可以再次作为输入经过另一轮的复杂事件处理过程。我们使用`TemperatureWarnings`来产生`TemperatureAlerts`，使得一旦发现在同一个支架上产生两个连续的`TemperatureWarnings`，且其温度是增长的就能获得`Alert`。其中`TemperatureAlerts`按照如下代码定义：

```scala
public class TemperatureAlert {
    private int rackID;
    ...
}
```

At first, we have to define our alert event pattern:

起始，我们需要定义alert事件模式：

```scala
Pattern<TemperatureWarning, ?> alertPattern = Pattern.<TemperatureWarning>begin("First Event")
    .next("Second Event")
    .within(Time.seconds(20));
```

This definition says that we want to see two `TemperatureWarnings` within 20 seconds. The first event has the name “First Event” and the second consecutive event has the name “Second Event”. The individual events don’t have a where clause assigned, because we need access to both events in order to decide whether the temperature is increasing. Therefore, we apply the filter condition in the select clause. But first, we obtain again a `PatternStream`.

从定义中可以看出，两个`TemperatureWarnings`需要在20秒内产生。第一个事件命名成“First Event”，同时连续第二个时间命名成“Second Event”。每个事件并没有接where子句，因为需要访问两个事件来确定温度是否是在上升。因此，在select子句中应用一个过滤条件。但首先，我们获得一个`PatternStream`。

```scala
PatternStream<TemperatureWarning> alertPatternStream = CEP.pattern(
    warnings.keyBy("rackID"),
    alertPattern);
```

Again, we `keyBy` the warnings input stream by the "rackID" so that we generate our alerts for each rack individually. Next we apply the `flatSelect` method which will give us access to matching event sequences and allows us to output an arbitrary number of complex events. Thus, we will only generate a `TemperatureAlert` if and only if the temperature is increasing.

再一次，我们在告警输入流的“rockID”上应用`keyBy`，这样就为每一个支架产生了独立的alert。下一步应用`flatSelect`方法，它可以访问每一个匹配事件序列，并使我们可以输出任意数量的复杂事件。这样，当且仅当温度是增加时，产生一个`TemperatureAlert`。

```scala
DataStream<TemperatureAlert> alerts = alertPatternStream.flatSelect(
    (Map<String, TemperatureWarning> pattern, Collector<TemperatureAlert> out) -> {
        TemperatureWarning first = pattern.get("First Event");
        TemperatureWarning second = pattern.get("Second Event");

        if (first.getAverageTemperature() < second.getAverageTemperature()) {
            out.collect(new TemperatureAlert(first.getRackID()));
        }
    });
```

The `DataStream<TemperatureAlert> alerts` is the data stream of temperature alerts for each rack. Based on these alerts we can now adapt the workload or cooling for overheating racks.

The full source code for the presented example as well as an example data source which generates randomly monitoring events can be found in this repository.

`DataStream<TemperatureAlert> alerts`为每个支架产生温度alert的数据流。基于这些alert，我们可以调整工作负载或对过热支架进行降温。

示例中的完整代码以及随机产生监控事件的数据源的示例代码可以在[这个仓库](https://github.com/tillrohrmann/cep-monitoring)下载。

## Conclusion

In this blog post we have seen how easy it is to reason about event streams using Flink’s CEP library. Using the example of monitoring and alert generation for a data center, we have implemented a short program which notifies us when a rack is about to overheat and potentially to fail.

In the future, the Flink community will further extend the CEP library’s functionality and expressiveness. Next on the road map is support for a regular expression-like pattern specification, including Kleene star, lower and upper bounds, and negation. Furthermore, it is planned to allow the where-clause to access fields of previously matched events. This feature will allow to prune unpromising event sequences early.

## 结论

这篇博客中，我们可以看到使用Flink的CEP库在事件流上进行推理是如何容易。使用监控示例，对数据中心产生alert，可以实现一个简短程序当一个支架过热有潜在失败风险时对我们进行通知。

在后续，Flink社区将扩展CEP库的方法和表现力。在Road Map上下一阶段是对类正则表达方式的模式进行支持，包括Kleene start，上下界，和否定。更进一步，计划允许使用where子句来访问前序匹配事件的属性。这个特性将使对非预期的事件序列剪枝更容易。

> Note: The example code requires Flink 1.0.1 or higher.
