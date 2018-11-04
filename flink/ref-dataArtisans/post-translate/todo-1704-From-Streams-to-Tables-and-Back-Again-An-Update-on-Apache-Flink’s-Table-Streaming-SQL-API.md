原文 url:	https://data-artisans.com/blog/flink-table-sql-api-update

# From Streams to Tables and Back Again: An Update on Apache Flink’s Table & Streaming SQL API

[April 3, 2017](https://data-artisans.com/blog/2017/04/03) - [Apache Flink](https://data-artisans.com/blog/category/apache-flink) , [Flink Features](https://data-artisans.com/blog/category/flink-features)
[Timo Walther](https://data-artisans.com/blog/author/twalthr)
Timo Walther ( [@twalthr](https://twitter.com/twalthr?lang=en) ) is a software engineer at data Artisans and an Apache Flink® committer and PMC member.
This post originally appeared on the [Apache Flink blog](http://flink.apache.org/news/2017/03/29/table-sql-api-update.html) . It was reproduced here under the [Apache License, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0.html) .
[DataStream](https://ci.apache.org/projects/flink/flink-docs-release-1.2/dev/datastream_api.html)
[Async IO](https://ci.apache.org/projects/flink/flink-docs-release-1.2/dev/stream/asyncio.html)
[ProcessFunction](https://ci.apache.org/projects/flink/flink-docs-release-1.2/dev/stream/process_function.html)
[CEP for complex event processing](https://ci.apache.org/projects/flink/flink-docs-release-1.2/dev/libs/cep.html)
[Table & SQL API](https://ci.apache.org/projects/flink/flink-docs-release-1.2/dev/table_api.html)
[Apache Calcite](http://calcite.apache.org/)
[DataStreams](https://ci.apache.org/projects/flink/flink-docs-release-1.2/dev/datastream_api.html)
[DataSets](https://ci.apache.org/projects/flink/flink-docs-release-1.2/dev/batch/index.html)

## Data Transformation and ETL

TableSources
CsvTableSource
KafkaTableSource
```
42|Bob Smith|2016-07-23 16:10:11|color=12,length=200,size=200
```

```
// set up execution environment val env = StreamExecutionEnvironment.getExecutionEnvironment val tEnv = TableEnvironment.getTableEnvironment(env) // configure table source val customerSource = CsvTableSource.builder() .path("/path/to/customer_data.csv") .ignoreFirstLine() .fieldDelimiter("|") .field("id", Types.LONG) .field("name", Types.STRING) .field("last_update", Types.TIMESTAMP) .field("prefs", Types.STRING) .build() // name your table source tEnv.registerTableSource("customers", customerSource) // define your table program val table = tEnv .scan("customers") .filter('name.isNotNull &amp;&amp; 'last_update &gt; "2016-01-01 00:00:00".toTimestamp) .select('id, 'name.lowerCase(), 'prefs) // convert it to a data stream val ds = table.toDataStream[Row] ds.print() env.execute()
```

CsvTableSource
ExecutionEnvironment
DataStream
DataSet
```
class Customer { var id: Int = _ var name: String = _ var update: Long = _ var prefs: java.util.Properties = _ }
```

```
val ds = tEnv .scan("customers") .select('id, 'name, 'last_update as 'update, parseProperties('prefs) as 'prefs) .toDataStream[Customer]
```

parseProperties
```
object parseProperties extends ScalarFunction { def eval(str: String): Properties = { val props = new Properties() str .split(",") .map(\_.split("=")) .foreach(split => props.setProperty(split(0), split(1))) props } }
```

open()
open()
[task lifecycle](https://ci.apache.org/projects/flink/flink-docs-release-1.3/internals/task_lifecycle.html)

## Unified Windowing for Static and Streaming Data

[Flink’s documentation](https://ci.apache.org/projects/flink/flink-docs-release-1.2/dev/windows.html)
[event or processing time](https://ci.apache.org/projects/flink/flink-docs-release-1.2/dev/event_time.html)
```
table .window(Tumble over 1.day on 'rowtime as 'w) .groupBy('id, 'w) .select('id, 'w.start as 'from, 'w.end as 'to, 'prefs.count as 'updates)
```

on()
```
// using processing-time table.window(Tumble over 100.rows as 'manyRowWindow) // using event-time table.window(Session withGap 15.minutes on 'rowtime as 'sessionWindow) table.window(Slide over 1.day every 1.hour on 'rowtime as 'dailyWindow)
```

[planned for Flink 1.3](https://cwiki.apache.org/confluence/display/FLINK/FLIP-11%3A+Table+API+Stream+Aggregations)
[borrowed from Apache Calcite](https://calcite.apache.org/docs/stream.html#hopping-windows)
```
table .window(Slide over 1.hour every 1.second as 'w) .groupBy('productId, 'w) .select( 'w.end, 'productId, ('unitPrice * ('rowtime - 'w.start).exp() / 1.hour).sum / (('rowtime - 'w.start).exp() / 1.hour).sum)
```


## User-defined Table Functions

[User-defined table functions](https://ci.apache.org/projects/flink/flink-docs-release-1.2/dev/table_api.html#user-defined-table-functions)
```
// create an instance of the table function val extractPrefs = new PropertiesExtractor() // derive rows and join them with original row table .join(extractPrefs('prefs) as ('color, 'size)) .select('id, 'username, 'color, 'size)
```

PropertiesExtractor
is a user-defined table function that extracts the color and size. We are not interested in customers that haven’t set these preferences and thus don’t emit anything if both properties are not present in the string value. Since we are using a (cross) join in the program, customers without a result on the right side of the join will be filtered out. 

```
class PropertiesExtractor extends TableFunction[Row] { def eval(prefs: String): Unit = { // split string into (key, value) pairs val pairs = prefs .split(",") .map { kv => val split = kv.split("=") (split(0), split(1)) } val color = pairs.find(\_.\_1 == "color").map(\_.\_2) val size = pairs.find(\_.\_1 == "size").map(\_.\_2) // emit a row if color and size are specified (color, size) match { case (Some(c), Some(s)) => collect(Row.of(c, s)) case _ => // skip } } override def getResultType = new RowTypeInfo(Types.STRING, Types.STRING) }
```


## Conclusion

[mailing lists](http://flink.apache.org/community.html#mailing-lists)
[JIRA](https://issues.apache.org/jira/browse/FLINK/?selectedTab=com.atlassian.jira.jira-projects-plugin:summary-panel)