#Announcing Google Cloud Dataflow on Flink and easy Flink deployment on Google Cloud

Posted on Apr 5th, 2015 by	Maximilian Michels

今天，我们很荣幸宣告Google和DataArtisans之间进行的深入合作，Apache Flink社区将Flink的部署搬到了Google云平台上，允许Google云平台上数据流用户在后台可以使用Apache Flink

##在Google云平台上部署Flink

最近，我们贡献了一个patch到bdutil上，这是在GCE上部署数据流处理系统的一个Google开源工具。为了在GCE上管理Hadoop，bdutil是你能够很容易地部署Flink，如下：

```
	bdutil -e extensions/flink/flink_env.sh deploy
```

详细指引参考[这里](https://ci.apache.org/projects/flink/flink-docs-master/setup/gce_setup.html)。
在经历了我们最近在GCE上使用Flink的经历，这次在40节点的集群上花费5个小时完成了分解280亿元素矩阵的工作，在GCE上对Flink进行自动化部署是很自然的一步。这个工作可以在最近的blog中找到相关内容。

##基于Flink的Google云数据流

Google云数据流是运行在Google基础设施上的数据分析服务。它允许用户编写复杂的数据分析流水线，可以兼容批处理和流处理程序，可以在Google云平台上可扩展地运行。数据流提供了批处理和流处理的统一视图，同时提供的还有灵活的窗口（window）语义，这可以支持复杂事件流分析模式。


云数据流是Google [FlumeJava](http://pages.cs.wisc.edu/~akella/CS838/F12/838-CloudPapers/FlumeJava.pdf)和[MillWheel](http://research.google.com/pubs/pub41378.html)项目的后续产物。Google最近开源发布了[数据流的SDK](https://github.com/GoogleCloudPlatform/DataflowJavaSDK)。这个SDK利用插件“runners”将编程模型从执行引擎中分离出来。Google为运行数据流程序提供了runners，无论是在google云平台还是在本地机器（为开发准备）上。

今天，我们很荣幸宣布云数据流上的Flink runner。数据流用户可以将Apache Flink作为后台执行运行他们的程序。目前Flink runner支持所有数据流上的批处理函数。当前，我们集中工作在将数据流流处理函数引入到Flink runner。幸运的是，Flink已经支持灵活的窗口语义，这同样可以应用在云数据流上。

Flink 和云数据流已经很好地调整过（aligned），他们共享本地引擎的版本，这个引擎可以统一流处理和批处理。Flink使用同一个流（流水线）引擎执行批处理和流处理程序。Flink加入到数据流SDK runners家族中（目前runners家族中已经加入了Google的云平台、本地runner、由Cloudera贡献的Apache Spark runner），这对于用户来说适合好消息，使得用户可以在云上运行同一个混合分析流水线（pipeline），即使是有前提的。

点这里开始使用Google数据流。为安装Flink数据流runner，可以依据这个导引。同时，我们很乐于知道你的想法，所以你可以在[这里](https://github.com/dataArtisans/flink-dataflow/issues)给我们提意见。

更多信息，可以参考[Google 云平台的博客](http://googlecloudplatform.blogspot.de/2015/03/announcing-Google-Cloud-Dataflow-runner-for-Apache-Flink.html)。
