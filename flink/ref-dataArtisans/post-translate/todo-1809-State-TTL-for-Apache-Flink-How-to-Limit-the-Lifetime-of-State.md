原文 url:	https://data-artisans.com/blog/state-ttl-for-apache-flink-how-to-limit-the-lifetime-of-state

# State TTL for Apache Flink: How to Limit the Lifetime of State

[September 25, 2018](https://data-artisans.com/blog/2018/09/25) - [Apache Flink](https://data-artisans.com/blog/category/apache-flink) , [Flink Features](https://data-artisans.com/blog/category/flink-features)
[Andrey Zagrebin](https://data-artisans.com/blog/author/andrey-zagrebin) and [Fabian Hueske](https://data-artisans.com/blog/author/fabian)
A common requirement for many stateful streaming applications is the ability to control how long application state can be accessed (e.g., due to legal regulations like GDPR) and when to discard it. This blog post introduces the state time-to-live (TTL) feature that was added to Apache Flink with the [1.6.0 release](https://data-artisans.com/blog/apache-flink-1-6-0-whats-new-in-the-latest-apache-flink-release) .
We outline the motivation and discuss use cases for the new State TTL feature. Moreover, we show how to use and configure it and explain how Flink internally manages state with TTL. The blog post concludes with an outlook on future improvements and extensions.

 ![](./pics/0cbea3a6-8bc8-42a6-b4bb-53c02c032e2f)


## Introduction to Stateful Stream Processing with Apache Flink

Any non-trivial real-time streaming application includes stateful operations. Apache Flink provides many powerful features for fault-tolerant [stateful stream processing](https://data-artisans.com/what-is-stream-processing) . Users can choose from different state primitives (atomic value, list, map) and backends (heap memory, RocksDB) that maintain the state. Application logic in processing functions can access and modify the state. Typically, the state is associated with a key, allowing for scalable processing and storage similar to key/value stores. Apache Flink transparently manages the distribution of state (including support for scaling out or in) and periodically performs checkpoints to be able to recover jobs in case of a failure with exactly-once state consistency guarantees.
In the remainder of this blog post, we use the example of a stateful application that ingests a stream of user login events and stores for each user the time of the last login to improve the experience of frequent visitors.



## The Transient Nature of State

There are two major reasons why state should be maintained only for a limited time.

- **Complying with data protection regulations**

Recent developments around data privacy regulations, such as the new General Data Protection Regulation (GDPR) introduced by the European Union, make compliance with such data requirements an important topic in the IT industry.  Especially the requirement for keeping data for a limited amount of time and preventing access to it thereafter is a common challenge for companies that provide short-term services to their customers and process their personal data. In our application that stores the time of the last login, it would not be acceptable to keep the information forever to prevent an unnecessary insight into the user’s privacy. Consequently, the application would need to remove the information after a certain period of time.

- **Managing the size of state storage more efficiently**

Another concern is the ever-growing size of state storage. Oftentimes, data needs to be persisted temporarily while there is some user activity around it, e.g. web sessions. When the activity ends there is no longer interest in that data while it still occupies storage. The application has to take extra actions and explicitly remove the useless state to clean up the storage. Following our previous example of storing the time of the last login, it might not be necessary after some time because the user can be treated as “infrequent” later on.
Both requirements can be addressed by a feature that “magically” removes the state for a key once it must not be accessed anymore or once it is not valuable enough to keep it in the storage.

## What Can We Do About It?


The [1.6.0 release of Apache Flink](https://data-artisans.com/blog/apache-flink-1-6-0-whats-new-in-the-latest-apache-flink-release) introduced the State TTL feature. Developers of stream processing applications can configure the state of operators to expire if it has not been touched within a certain period of time (time-to-live). The expired state is later garbage-collected by a lazy clean-up strategy.

 In Flink’s DataStream API, application state is defined by a [state descriptor](https://ci.apache.org/projects/flink/flink-docs-release-1.6/dev/stream/state/state.html#using-managed-keyed-state) . State TTL is configured by passing a StateTtlConfiguration to a state descriptor. The following **Java example** shows how to create a state TTL configuration and provide it to the state descriptor that holds the last login time of a user as a Long value:

import org.apache.flink.api.common.state.StateTtlConfig;
 import org.apache.flink.api.common.time.Time; import org.apache.flink.api.common.state.ValueStateDescriptor;
StateTtlConfig ttlConfig = StateTtlConfig
  .newBuilder(Time.days( 7 ))
  .setUpdateType(StateTtlConfig.UpdateType.OnCreateAndWrite)
  .setStateVisibility(StateTtlConfig.StateVisibility.NeverReturnExpired)
  .build();

 ValueStateDescriptor<Long> lastUserLogin =
     new ValueStateDescriptor<>( "lastUserLogin" , Long.class);
lastUserLogin.enableTimeToLive(ttlConfig);
Flink provides multiple options to configure the behavior of the state TTL functionality.

 - **When is the Time-to-Live reset?**
 By default, the expiration time of a state entry is updated when the state is modified. Optionally, It can also be updated on read access at the cost of an additional write operation to update the timestamp.
 - **Which time semantics are used for the Time-to-Live timers?**
 With Flink 1.6.0, users can only define a state TTL in terms of processing time. The support for event time is planned for future Apache Flink releases.
 - **Can the expired state be accessed one last time?**
 Let’s assume that some state has expired but it is still available in storage and has not been removed yet. If this state is being read out, the user can set different visibility types for its value. In both cases, the state is subsequently removed.


- The first option is to never return the expired state. This way the state is hidden from the user and does not exist anymore which prevents from accessing any personal data after the required period of time.
- The second available option is to return expired-but-not-yet-garbage-collected state. This alternative addresses applications where eventual storage cleanup is important but the application can make some use of the still available, but expired state.

You can read more about how to use state TTL [in the Apache Flink documentation](https://ci.apache.org/projects/flink/flink-docs-release-1.6/dev/stream/state/state.html#state-time-to-live-ttl) .

 Internally, the State TTL feature is implemented by storing an additional timestamp of the last modification along with the user value in this case. While this approach adds some storage overhead, it allows Flink to check for the expired state during state access, checkpointing, recovery, or dedicated storage cleanup procedures.

## “Taking out the Garbage”

When a state object is accessed in a read operation, Flink will check its timestamp and clear the state if it is expired (depending on the configured state visibility, the expired state is returned or not). Due to this lazy removal, expired state that is never accessed again will forever occupy storage space unless it is garbage collected.

 So how can the expired state be removed without the application logic explicitly taking care of it? In general, there are different possible strategies to remove it in the background.

 Flink 1.6.0 supports automatic eviction of the expired state only when a full snapshot for a checkpoint or savepoint is taken. Note that state eviction is not applied for incremental checkpoints. State eviction on full snapshots must be explicitly enabled as shown in the following example:

 StateTtlConfig ttlConfig = StateTtlConfig
  .newBuilder(Time.days( 7 ))
  .cleanupFullSnapshot()
  .build();

 The local storage stays untouched at the moment but the size of the stored snapshot is reduced. The local state of an operator will only be cleaned up, when the operator reloads its state from a snapshot, i.e., in case of a recovery or when starting from a savepoint. In the future, this cleanup strategy can be improved to asynchronously propagate state removal to the running local storage.

 Due to these limitations, applications still need to actively remove state after it expired in Flink 1.6.0. A common approach is based on timers to manually cleanup state after a certain time. The idea is to register a timer with the TTL per state value and access. When the timer elapses, the state can be cleared if no other state access happened since the timer was registered. This approach introduces additional costs because the timers consume storage along with the original state. However, Flink 1.6 added significant improvements for timer handling, such as efficient timer deletions [(FLINK-9423)](https://issues.apache.org/jira/browse/FLINK-9423)  and a [RocksDB-backed timer service](https://ci.apache.org/projects/flink/flink-docs-stable/ops/state/large_state_tuning.html#tuning-rocksdb) .


The open source community of Apache Flink is currently working on additional garbage collection strategies for the expired state. The different ideas are still in a work-in-progress mode and planned for future releases

 One approach is based on Flink timers and works similar to the manual cleanup as described above. However, users do not need to implement the cleanup logic themselves and state is automatically cleanup for them.

 More sophisticated ideas depend on the characteristics of specific state backends and include:
 An incremental partial cleanup in the heap backend triggered on state access or record processing.
 A RocksDB-specific filter which filters out expired values during the regular compaction process.

 We encourage you to join the conversation and share your thoughts and ideas in the [Apache Flink JIRA board](https://issues.apache.org/jira/projects/FLINK/summary) or by subscribing to the Apache Flink mailing list below.

## Summary

Time-based state access restrictions and automatic state clean-up are common challenges in the world of stateful stream processing. With its [1.6.0 release, Apache Flink](https://data-artisans.com/blog/apache-flink-1-6-0-whats-new-in-the-latest-apache-flink-release) introduces the first implementation of State TTL support to tackle these problems. In the current version, State TTL guarantees state inaccessibility after a configured timeout to comply with GDPR or any other data compliance rules. The Flink community is working on several extensions to improve and extend the State TTL functionality in future releases.

 Stay tuned by subscribing to our newsletter or following [@ApacheFlink](https://twitter.com/ApacheFlink) on Twitter to get the latest updates and new features regarding Apache Flink and State TTL.

 We encourage any feedback or suggestions through the Apache Flink mailing lists or our contact forms below.
 [![](./pics/410283a9-776b-48b2-926b-a7c2af92b9ff)](https://flink.apache.org/community.html) [![](./pics/3bdc0070-047f-4692-9ec4-da7f4f6f98bd)](https://data-artisans.com/contact)   









Tags: [apache flink](https://data-artisans.com/blog/tag/apache-flink) , [flink community](https://data-artisans.com/blog/tag/flink-community)
