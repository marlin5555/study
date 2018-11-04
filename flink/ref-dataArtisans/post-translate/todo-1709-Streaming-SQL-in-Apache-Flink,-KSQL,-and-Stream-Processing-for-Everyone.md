原文 url:	https://data-artisans.com/blog/flink-streaming-sql-ksql-stream-processing

# Streaming SQL in Apache Flink, KSQL, and Stream Processing for Everyone

[September 1, 2017](https://data-artisans.com/blog/2017/09/01) - [Apache Flink](https://data-artisans.com/blog/category/apache-flink) , [Flink Features](https://data-artisans.com/blog/category/flink-features)
[Stephan Ewen](https://data-artisans.com/blog/author/stephan) and [Fabian Hueske](https://data-artisans.com/blog/author/fabian)
If you read our blog, you’ve likely seen this week’s news that Confluent shared an early version of its new offering
[KSQL](https://www.confluent.io/product/ksql/)
, streaming SQL for Apache Kafka.

 The data Artisans team and Apache Flink community have been working on
[Flink’s SQL offering](https://ci.apache.org/projects/flink/flink-docs-release-1.3/dev/table/index.html)
for the past couple of years, and so we care a lot about the evolution of the streaming SQL space. That means that KSQL is relevant to us, as it represents a different approach for delivering the benefit of modern stream processing to a broader user base. In the last few days, we’ve had a chance to look around in the current KSQL offering to get to know it better.

 [As mentioned here, ](https://twitter.com/miguno/status/902890979128107008)
users will have questions
about how KSQL compares to streaming SQL on Flink and other systems, and in this post, we’ll share what we’ve learned in the past few days. We’ve organized our thoughts around the most relevant evaluation criteria for a streaming SQL system based on our experience with users.

 Software projects are loaded with opinions and worldviews just the same as any other form of creative output, and as a company made up of Flink contributors and committers, it’s natural for us to bring a particular viewpoint from our position.

 And so we don’t want to pass up this opportunity to start a dialogue about a topic that is near and dear to us: how to make stream processing–one of the most promising emerging technologies in the world–accessible to more users.

## SQL + Java/Scala vs. SQL-like Only

**Apache Flink:**
As noted in [the tweet we linked to above](https://twitter.com/miguno/status/902890979128107008) , the current implementation of Flink SQL does require some amount of Java / Scala code in addition to SQL–specifically, to ingest a data stream and to set up a table.  
**Confluent KSQL:**
KSQL consists of SQL and SQL-like statements only 

**What does it mean for end users?**
We have to admit that we were a bit perplexed that this was noted as a key differentiator between Flink SQL and KSQL. There are many valid use cases for both pure SQL text and SQL statements embedded in programs.

 The first, pure SQL text, is best suited for ad hoc queries and for use by data analysts, and the second, SQL statements embedded in programs, is best suited for data pipelines.

 The Flink community chose to start with SQL statements embedded in programs because it addressed the cases of early production Flink SQL users. This embedding also supports seamless and type-checkable mixing and matching of SQL and DataStream API within the same program.

 However, we agree that tools to enable pure SQL text are useful, and there’s no reason that Flink shouldn’t have a SQL-only CLI. We created a
[simple wrapper](https://github.com/StephanEwen/incubator-flink/blob/sql_cli/flink-examples/flink-examples-table/src/main/java/org/apache/flink/table/examples/java/SqlCli.java)
that executes a SQL statement from a templated Java program as a simple wrapper in <20 lines of code. Testament to Flink’s deployment flexibility, this can actually run the SQL statement locally, embedded, on Yarn, Mesos, or against existing Flink clusters, depending on how it is invoked! We are also working on a more feature-rich Flink SQL CLI to present at our
[Flink Forward conference](http://berlin.flink-forward.org)
in 2 weeks. We’ll be sure to share the demo video (and the tool itself) as soon as it’s ready.

## Production Scale

**Apache Flink:**
Apache Flink’s SQL is running in production at massive scale at
[Alibaba](https://berlin.flink-forward.org/kb_sessions/large-scale-stream-processing-with-blink-sql-at-alibaba/)
and
[Uber](http://sf.flink-forward.org/kb_sessions/athenax-ubers-streaming-processing-platform-on-flink/)
, among other companies, and Huawei announced their own
[hosted cloud service](http://www.hwclouds.com/product/stream.html)
for Flink streaming SQL earlier this week. 

When we hear from Flink SQL users, the motivation behind these projects is very much consistent, and it’s neatly summarized in Uber’s description of their
[Flink Forward SF presentation](http://sf.flink-forward.org/kb_sessions/athenax-ubers-streaming-processing-platform-on-flink/)
earlier this year:
*“The mission of Uber is to make transportation as reliable as running water. The business is fundamentally driven by real-time data — more than half of the employees in Uber, many of whom are non-technical, use SQL on a regular basis to analyze data and power their business decisions. We are building AthenaX, a stream processing platform built on top of Apache Flink to enable our users to write SQL to process real-time data efficiently and reliably at Uber’s scale.”*
**Confluent KSQL:**
The
[KSQL README](https://github.com/confluentinc/ksql/blob/0.1.x/README.md)
opens with the announcement that KSQL is not yet considered to be production-ready:
*Important: This release is a developer preview and is free and open-source from Confluent under the Apache 2.0 license. Do not run KSQL against a production cluster.*

 Of course, we expect both products to mature and look forward to the evolution of KSQL as it reaches production status in the future.
**What does it mean for end users?**

 At least with regard to measuring and comparing business impact in production, we’ll have to wait and see. If you want streaming SQL in production right now, we suggest Flink 😉 
 
 And if you have questions, you can
[contact us](https://data-artisans.com/contact)
.

## Unified Batch and Stream Processing

**Apache Flink:**
A critical piece of Flink SQL’s larger vision is that SQL should serve as a vehicle to unify batch and stream processing, both of which are supported by Flink. We’ve
[written previously](https://data-artisans.com/blog/flink-table-sql-api-update)
about how Flink SQL enables this unification, and it’s at the heart of our “where we believe all data processing is headed” thesis.  
**Confluent KSQL:**
At the present moment, Kafka Streams and KSQL does not support batch processing.
**What does it mean for end users?**
In Flink, it’s possible to point the same Flink SQL query at a message queue like Kafka for infinite results or at  a static dataset in a file system for finite results, and the
*results, too, will be exactly the same.*

## Industry-compliant SQL vs. SQL-like

**Apache Flink:**
Flink implements industry-standard SQL based on
[Apache Calcite](http://calcite.apache.org)
(the same basis that
[Apache Beam will use](https://beam.apache.org/documentation/dsls/sql/)
in its SQL DSL API)
**Confluent KSQL:**
KSQL supports a SQL-like language with its own set of commands rather than industry-standard SQL.
**What does it mean for end users?**
We believe that much of SQL’s value is derived from its familiarity–the fact that its concepts can be applied
*precisely*
across any range of datasets, domains, and use cases. And most SQL in this world is in fact not typed by people, but generated by tools, which requires standard-compliant SQL.

 Certain important constructs for streaming are a bit clumsy to express in standard SQL (some types of windows, for example). To solve for that, Flink and Calcite provide syntactic sugar to make the constructs easier to express. This works as an extension of standard SQL, though, and retains full compatibility.

## Supported Syntax

**Apache Flink:**
Flink SQL’s supported syntax, including UDFs and a range of aggregations, is
[documented here](https://ci.apache.org/projects/flink/flink-docs-release-1.3/dev/table/sql.html#supported-syntax)
, and joins, a Connector API, and Registries are in the works for the Flink 1.4 release scheduled later this year.
**Confluent KSQL:**
KSQL’s syntax is
[documented here](https://github.com/confluentinc/ksql/blob/0.1.x/docs/syntax-reference.md#ksql-statements)
.
**What does it mean for end users?**

 Frankly, it all depends on the use case. Some business requirements can be fulfilled by a relatively limited syntax while others require something more complete. At first glance, we don’t yet see support for UDFs and a number of common aggregations in KSQL, but we’ll leave it to users to compare docs and decide if the problems they need to solve are addressed by either tool.

## Open vs Closed Development

**Apache Flink:**
Flink SQL is part of Apache Flink, it is developed in the open in the Apache Way and has contributions from a number of companies and individuals beyond data Artisans, including Alibaba, Huawei, and Uber. There are a number of public discussions that users can join to influence the project (for history buffs, a couple of the early design and planning docs are available [here](https://docs.google.com/document/d/1qVVt_16kdaZQ8RTfA_f4konQPW4tnl8THw6rzGUdaqU/edit#)  and [here](https://docs.google.com/document/d/1sITIShmJMGegzAjGqFuwiN_iw1urwykKsLiacokxSw0/edit#heading=h.kdgexc4bw670) ).
**Confluent KSQL:**
KSQL code is open source under the Apache License but is a Confluent project, not part of Apache Kafka, and developed behind closed doors before being announced as a preview version.
**What does it mean for end users?**
A huge benefit of SQL is its broad applicability across different problem domains, and so we believe that Flink SQL in particular benefits from open discussion and input from many perspectives.

## Looking Ahead

There’s more exciting stuff planned for Flink SQL in the near future, including the [integration of Flink CEP (complex event processing) with SQL](https://cwiki.apache.org/confluence/display/FLINK/FLIP-20%3A+Integration+of+SQL+and+CEP) . And as the streaming SQL space evolves, we expect that users will continue to share their opinions, priorities, and most pressing problems. We’d love to hear from you, whether you prefer to
[contact the data Artisans team directly](https://data-artisans.com/contact)
, send an email to the
[Flink user mailing list](http://flink.apache.org/community.html#mailing-lists)
, or find us
[on Twitter](https://twitter.com/dataArtisans)
. 

And if you’re interested in learning more about the topic and can make it to [Flink Forward Berlin](https://berlin.flink-forward.org/#tickets) this September 11-13, we’ll be featuring Flink SQL sessions from
[data Artisans software engineer and Apache Flink committer Timo Walther](https://berlin.flink-forward.org/kb_sessions/from-streams-to-tables-and-back-again-a-demo-of-flinks-table-sql-api/)
as well as
[Shaoxuan Wang, senior manager at Alibaba](https://berlin.flink-forward.org/kb_sessions/large-scale-stream-processing-with-blink-sql-at-alibaba/)
.
Tags: [sql api](https://data-artisans.com/blog/tag/sql-api) , [streaming sql](https://data-artisans.com/blog/tag/streaming-sql) , [table api](https://data-artisans.com/blog/tag/table-api)