# Juggling with Bits and Bytes
> 11 May 2015 by Fabian Hüske ([@fhueske](https://twitter.com/fhueske))

## How Apache Flink operates on binary data

Nowadays, a lot of open-source systems for analyzing large data sets are implemented in Java or other JVM-based programming languages. The most well-known example is Apache Hadoop, but also newer frameworks such as Apache Spark, Apache Drill, and also Apache Flink run on JVMs. A common challenge that JVM-based data analysis engines face is to store large amounts of data in memory - both for caching and for efficient processing such as sorting and joining of data. Managing the JVM memory well makes the difference between a system that is hard to configure and has unpredictable reliability and performance and a system that behaves robustly with few configuration knobs.

In this blog post we discuss how Apache Flink manages memory, talk about its custom data de/serialization stack, and show how it operates on binary data.

## Apache Flink在二进制数据上是如何工作的？
近来，很多用于分析大数据集的开源系统使用Java或其他基于JVM的编程语言来实现。其中最出名的例子就是Apache Hadoop，而且新近的框架比如Apache Spark、Apache Drill、同时包括Apache Flink也是运行在JVM之上的。基于JVM的数据分析引擎要应对一个通用的挑战：在内存中存储相当大数量的数据-无论是用于缓存还是更快速地处理数据，比如在对数据进行排序或进行链接操作时。很好地管理JVM的内存将产生极大的差异：从很难的配置项且具有不可预测的可靠性和性能的系统到具有鲁棒性表现且只需配置有限配置项的系统。

在这篇博客中，我们将讨论Apache Flink是如何管理内存的，讨论它的用户数据序列化/反序列化的栈，并展示它在二进制数据上是如何工作的。

## Data Objects? Let’s put them on the heap!

The most straight-forward approach to process lots of data in a JVM is to put it as objects on the heap and operate on these objects. Caching a data set as objects would be as simple as maintaining a list containing an object for each record. An in-memory sort would simply sort the list of objects. However, this approach has a few notable drawbacks. First of all it is not trivial to watch and control heap memory usage when a lot of objects are created and invalidated constantly. Memory overallocation instantly kills the JVM with an OutOfMemoryError. Another aspect is garbage collection on multi-GB JVMs which are flooded with new objects. The overhead of garbage collection in such environments can easily reach 50% and more. Finally, Java objects come with a certain space overhead depending on the JVM and platform. For data sets with many small objects this can significantly reduce the effectively usable amount of memory. Given proficient system design and careful, use-case specific system parameter tuning, heap memory usage can be more or less controlled and OutOfMemoryErrors avoided. However, such setups are rather fragile especially if data characteristics or the execution environment change.

## 数据对象？将他们放在堆区！
在JVM上处理大量数据（data）的最直接方式是将它看作堆区中的对象（object），并对这些对象进行操作。将一个数据集作为对象进行缓存将是十分简单的一件事，就像是维护一个列表，这个列表中包含的每一个记录（record）都是一个对象。内存中的排序也是十分简单的，如对一个对象列表进行排序。但是，这个方法有一些值得注意的缺点。第一，这不是对堆区内存的平凡使用方式，当大量对象连续被创建和废弃时，无论是对其查看还是控制操作都不是平凡的操作方式。内存过度分配将立即使用内存溢出错误（OutOfMemoryError）杀掉JVM。另一个问题是在数GB的充满大量新对象的JVM上进行垃圾回收操作，在上述环境上进行垃圾回收，其负载将很快超过50%，甚至更多。最后，Java对象伴随着一个固定空间开销，其依赖于JVM和平台。对于由大量小对象组成的数据集来说，这一点可以显著地降低内存可用空间的利用效率。对于给定的专业系统设计和专业地基于案例地定制化系统参数调优，堆内存使用可以或多或少控制，并避免内存溢出错误。但是，如果数据特征或执行环境稍有变换，这些设置内容是十分脆弱的（就是说，还需要再次调优）。

## What is Flink doing about that?

Apache Flink has its roots at a research project which aimed to combine the best technologies of MapReduce-based systems and parallel database systems. Coming from this background, Flink has always had its own way of processing data in-memory. Instead of putting lots of objects on the heap, Flink serializes objects into a fixed number of pre-allocated memory segments. Its DBMS-style sort and join algorithms operate as much as possible on this binary data to keep the de/serialization overhead at a minimum. If more data needs to be processed than can be kept in memory, Flink’s operators partially spill data to disk. In fact, a lot of Flink’s internal implementations look more like C/C++ rather than common Java. The following figure gives a high-level overview of how Flink stores data serialized in memory segments and spills to disk if necessary.

## 那Flink是如何做的呢？
Apache Flink来源于一项研究项目，而这个研究项目的目标是结合两项最佳的技术：基于Map-Reduce的系统和并行数据库系统。源于这个背景，Flink具有在内存中处理数据的独到的方式，与把大量对象放在堆内存的方式不同，Flink将序列化对象到一个固定大小的预先分配的内存片段（segment）。它具有DBMS风格的排序和join算法操作也尽可能运行在这些二进制数据上，从而使序列化/反序列化操作开销控制在一个最小值。如果处理的数据超过了内存大小，Flink的算子将把数据溢出到磁盘上。事实上，Flink的很多内部实现看上去更像是C/C++风格，而不是Java风格。下面这个图给出了一个高层次的视图，展示了Flink如何在内存片段中存储序列化数据，以及在需要时如何将其溢出到磁盘。

![](./pics/memory-mgmt.png)

Flink’s style of active memory management and operating on binary data has several benefits:

1. **Memory-safe execution & efficient out-of-core algorithms**. Due to the fixed amount of allocated memory segments, it is trivial to monitor remaining memory resources. In case of memory shortage, processing operators can efficiently write larger batches of memory segments to disk and later them read back. Consequently, OutOfMemoryErrors are effectively prevented.
2. **Reduced garbage collection pressure**. Because all long-lived data is in binary representation in Flink’s managed memory, all data objects are short-lived or even mutable and can be reused. Short-lived objects can be more efficiently garbage-collected, which significantly reduces garbage collection pressure. Right now, the pre-allocated memory segments are long-lived objects on the JVM heap, but the Flink community is actively working on allocating off-heap memory for this purpose. This effort will result in much smaller JVM heaps and facilitate even faster garbage collection cycles.
3. **Space efficient data representation**. Java objects have a storage overhead which can be avoided if the data is stored in a binary representation.
4. **Efficient binary operations & cache sensitivity**. Binary data can be efficiently compared and operated on given a suitable binary representation. Furthermore, the binary representations can put related values, as well as hash codes, keys, and pointers, adjacently into memory. This gives data structures with usually more cache efficient access patterns.

Flink的这种积极内存管理和操作二进制数据的风格有如下几点好处：
1. **内存安全的执行 & 高效地基于外存的算法**。由于申请的内存片段是固定大小的，监控剩余内存资源就是一个平凡的操作。在内存短缺的情况下，运行中的算子可以高效地将大量内存片段写出到磁盘上，并在需要时再将其读取回来。随之而来的，内存溢出错误就被有效地避免了。
2. **减缓了垃圾回收压力**。由于所有长时间存活的数据都是以二进制形式存在于Flink管理的内存中，所有数据对象都是短时间存活或可变能够被重新使用的。短时间存活的对象可以被高效地进行垃圾回收，这将显著减轻垃圾回收的压力。目前，预分配的内存片段是位于JVM堆内存中的长时间存活的对象（后续加入了堆外内存的概念，此处有变更，译者注），但Flink社区正积极工作于分配堆外内存。这个工作的结果将造就更小的JVM堆，促使更快的垃圾回收周期。
3. **高效利用空间的数据表达方式**。当数据使用二进制表达方式进行存储时，可以有效避免Java对象在存储上的开销。
4. **高效地二进制操作 & 对缓存敏感性**。在给定的合适的二进制表达方式下，二进制数据可以被高效比较和操作。进一步，二进制表达可以获取相关值，比如hash值，key值，和指针，在紧邻的内存中。使用高效缓存的数据结构可以使访问数据模式更快速。

These properties of active memory management are very desirable in a data processing systems for large-scale data analytics but have a significant price tag attached. Active memory management and operating on binary data is not trivial to implement, i.e., using java.util.HashMap is much easier than implementing a spillable hash-table backed by byte arrays and a custom serialization stack. Of course Apache Flink is not the only JVM-based data processing system that operates on serialized binary data. Projects such as Apache Drill, Apache Ignite (incubating) or Apache Geode (incubating) apply similar techniques and it was recently announced that also Apache Spark will evolve into this direction with Project Tungsten.

In the following we discuss in detail how Flink allocates memory, de/serializes objects, and operates on binary data. We will also show some performance numbers comparing processing objects on the heap and operating on binary data.

在应对大规模数据分析的数据处理系统中，积极内存管理的这些属性是十分需要的，但也有比较高的代价。积极内存管理策略、在二进制数据上进行操作都不是显而易见能够实现的，即，使用`java.util.HashMap`相比于下面的实现是十分容易的，基于字节数组和用户自定义的序列化栈实现一个可以溢出的`hash-table`。当然Apache Flink也不是唯一基于JVM并且实现了在序列化二进制数据上操作的数据处理系统。比如Apache Drill、Apache Ignite（孵化中）、Apache Geode（孵化中）项目应用了类似的技术，最近有报道说Apache Spark将在Tungsten项目中发展这个方向。

在接下来的内容中，我们将深入讨论Flink申请内存、序列化/反序列化对象、在二进制数据上进行操作。我们也将展示一些性能对比数字，对比在堆内存中处理对象和在二进制数据上进行操作。

## How does Flink allocate memory?

A Flink worker, called TaskManager, is composed of several internal components such as an actor system for coordination with the Flink master, an IOManager that takes care of spilling data to disk and reading it back, and a MemoryManager that coordinates memory usage. In the context of this blog post, the MemoryManager is of most interest.

The MemoryManager takes care of allocating, accounting, and distributing MemorySegments to data processing operators such as sort and join operators. A MemorySegment is Flink’s distribution unit of memory and is backed by a regular Java byte array (size is 32 KB by default). A MemorySegment provides very efficient write and read access to its backed byte array using Java’s unsafe methods. You can think of a MemorySegment as a custom-tailored version of Java’s NIO ByteBuffer. In order to operate on multiple MemorySegments like on a larger chunk of consecutive memory, Flink uses logical views that implement Java’s java.io.DataOutput and java.io.DataInput interfaces.

MemorySegments are allocated once at TaskManager start-up time and are destroyed when the TaskManager is shut down. Hence, they are reused and not garbage-collected over the whole lifetime of a TaskManager. After all internal data structures of a TaskManager have been initialized and all core services have been started, the MemoryManager starts creating MemorySegments. By default 70% of the JVM heap that is available after service initialization is allocated by the MemoryManager. It is also possible to configure an absolute amount of managed memory. The remaining JVM heap is used for objects that are instantiated during task processing, including objects created by user-defined functions. The following figure shows the memory distribution in the TaskManager JVM after startup.

## Flink如何申请内存？
Flink worker，也被称作是TaskManager，有几个内部组件组成，包括用于与Flink master进行协调通讯的actor system（执行者系统？）、负责将溢出数据写入磁盘并再读取的IOManager、协调内存使用的MemoryManager。这篇博客接下来的内容中，将重点讨论MemoryManager。

在排序或join等操作符进行数据处理时，MemoryManager负责申请、记录、分配内存片段。一个内存片段（MemorySegment）是Flink进行分配时的内存单元，它由一个固定的Java字节数组（默认的大小是32KB）组成。内存片段提供了非常高效地对字节数组的读写访问操作，使用的是Java不安全的方法（unsafe method，注：特指JDK中对应的unsafe包）。你可以把内存片段当作是Java NIO ByteBuffer的一个定制版本。为了像在连续内存上一样对多个内存片段进行操作，Flink使用逻辑视图实现了Java的`java.io.DataOutput`和`java.io.DataInput`接口。（译者注：也就是说可以通过调用DataInput、DataOutput接口的方法来操作多个内存片段，而不需要关心其内部是如何实现的）

在TaskManager启动时，内存片段就已经分配好了，当TaskManager被关闭时，它才会被销毁。因此，内存片段是被重复使用，并且在TaskManager的整个生命周期中是不会被垃圾回收掉的。在TaskManager所有内部数据结构被初始化并且所有核心服务启动之后，MemoryManager将开始创建内存片段。默认情况下，在服务初始化结束之后，JVM堆中可用内存的70%将被MemoryManager用来创建成内存片段。当然这个值可以通过配置设置成可管理内存的任意大小。JVM堆内存的剩余部分将被用于工作处理过程中创建对象（object），包括用户自定义函数中创建的对象。下面的图片展示了TaskManager JVM在启动之后的内存分配情况。

![](./pics/memory-alloc.png)

## How does Flink serialize objects?

The Java ecosystem offers several libraries to convert objects into a binary representation and back. Common alternatives are standard Java serialization, Kryo, Apache Avro, Apache Thrift, or Google’s Protobuf. Flink includes its own custom serialization framework in order to control the binary representation of data. This is important because operating on binary data such as comparing or even manipulating binary data requires exact knowledge of the serialization layout. Further, configuring the serialization layout with respect to operations that are performed on binary data can yield a significant performance boost. Flink’s serialization stack also leverages the fact, that the type of the objects which are going through de/serialization are exactly known before a program is executed.

Flink programs can process data represented as arbitrary Java or Scala objects. Before a program is optimized, the data types at each processing step of the program’s data flow need to be identified. For Java programs, Flink features a reflection-based type extraction component to analyze the return types of user-defined functions. Scala programs are analyzed with help of the Scala compiler. Flink represents each data type with a TypeInformation. Flink has TypeInformations for several kinds of data types, including:

## Flink如何序列化对象？

在Java生态系统中提供了几种库包，用于将对象转换成（包括反向）二进制表达方式。通常用于替换标准Java序列化过程的方式有：Kryo、Apache Avro、Apache Thrift、或是Google的Protobuf。Flink包含了它自己实现的一个序列化框架，以控制数据的二进制表达方式。这对基于二进制数据的操作是十分重要的，诸如比较或仅仅是操作二进制数据仍然需要精确的有关序列化设计的知识。更进一步，依据操作对序列化设计进行配置可以对二进制数据上操作的表现有着显著增加。Flink的序列化栈也利用了这一事实，在程序执行开始，对象的类型在贯穿序列化/反序列化过程中都是被精确了解的。

Flink程序可以对任意表达成Java或Scala对象的数据进行处理。在程序被优化前，程序数据流的各个处理阶段（step）的数据类型都是被指定的。对Java程序，Flink提供了一个基于反射的类型抽取组件，来分析用户自定义函数的返回类型。Scala程序可以利用Scala编译器进行分析。Flink使用TypeInformation来表达每种数据类型。Flink具有几种数据类型的TypeInformation，包括：

- BasicTypeInfo: Any (boxed) Java primitive type or java.lang.String.
- BasicArrayTypeInfo: Any array of a (boxed) Java primitive type or java.lang.String.
- WritableTypeInfo: Any implementation of Hadoop’s Writable interface.
- TupleTypeInfo: Any Flink tuple (Tuple1 to Tuple25). Flink tuples are Java representations for fixed-length tuples with typed fields.
- CaseClassTypeInfo: Any Scala CaseClass (including Scala tuples).
- PojoTypeInfo: Any POJO (Java or Scala), i.e., an object with all fields either being public or accessible through getters and setter that follow the common naming conventions.
- GenericTypeInfo: Any data type that cannot be identified as another type.

Each TypeInformation provides a serializer for the data type it represents. For example, a BasicTypeInfo returns a serializer that writes the respective primitive type, the serializer of a WritableTypeInfo delegates de/serialization to the write() and readFields() methods of the object implementing Hadoop’s Writable interface, and a GenericTypeInfo returns a serializer that delegates serialization to Kryo. Object serialization to a DataOutput which is backed by Flink MemorySegments goes automatically through Java’s efficient unsafe operations. For data types that can be used as keys, i.e., compared and hashed, the TypeInformation provides TypeComparators. TypeComparators compare and hash objects and can - depending on the concrete data type - also efficiently compare binary representations and extract fixed-length binary key prefixes.

- BasicTypeInfo：任意（装箱）Java基本类型或`java.lang.String`
- BasicArrayTypeInfo：任意（装箱）Java基本类型或`java.lang.String`的数组
- WritableTypeInfo：Hadoop Writable接口的任意实现
- TupleTypeInfo：任何Flink的tuple类型（从Tuple1到Tuple25）。Flink tuple表达的是固定长度元组，元组的每个元素（field）是可以确定的类型（typed）。
- CaseClassTypeInfo：任意Scala的CaseClass（包括Scala的tuple类型）
- PojoTypeInfo：任意POJO（Java或Scala）对象，即，对象的所有属性（field）要么是public的，要么可以通过getter和setter进行访问，其命名方式遵循通用的命名习惯。
- GenericTypeInfo：任意不能由另一种类型表达的数据类型。

每一种TypeInformation都为数据类型提供了一个序列化器。例如，BasicTypeInfo将返回一个序列化器，它将写出对应的基本类型；WritableTypeInfo的序列化器把序列化/反序列化方法委托给实现了Hadoop Writable接口的对象的`write()`和`readFields()`方法；而GenericTypeInfo的序列化器则将序列化过程交给Kryo。对象序列化成一个DataOutput，它将转换成Flink的内存片段，这通过Java高效的unsafe操作可以自动实现。数据类型可以当作key使用，比如，对比较和hash操作，TypeInformation提供了TypeComparator，它可以比较对象并对其进行hash操作，能够高效比较二进制的表达方式同时抽取固定长度的二进制key的前缀，当然这一点依赖于具体的数据类型。

Tuple, Pojo, and CaseClass types are composite types, i.e., containers for one or more possibly nested data types. As such, their serializers and comparators are also composite and delegate the serialization and comparison of their member data types to the respective serializers and comparators. The following figure illustrates the serialization of a (nested) Tuple3<Integer, Double, Person> object where Person is a POJO and defined as follows:

Tuple、POJO和CaseClass都是复合类型，即，容器中可能包含一个或多个嵌套数据类型。与之对应，它们的序列化器（serializer）和比较器（comparator）同样是复合的，通过代理其成员数据类型的序列化和比较过程来实现。下图展示了一个嵌套的`Tuple3<Integer,Double,Person>`对象的序列化过程，其中`Person`是一个POJO，其定义如下：

```java
public class Person {
    public int id;
    public String name;
}
```

![](./pics/data-serialization.png)

Flink’s type system can be easily extended by providing custom TypeInformations, Serializers, and Comparators to improve the performance of serializing and comparing custom data types.

Flink的类型系统能够轻松扩展出用户提供的TypeInformation、Serializer、Comparator，而这将大大提高用户数据类型的序列化和比较过程的性能表现。

## How does Flink operate on binary data?

Similar to many other data processing APIs (including SQL), Flink’s APIs provide transformations to group, sort, and join data sets. These transformations operate on potentially very large data sets. Relational database systems feature very efficient algorithms for these purposes since several decades including external merge-sort, merge-join, and hybrid hash-join. Flink builds on this technology, but generalizes it to handle arbitrary objects using its custom serialization and comparison stack. In the following, we show how Flink operates with binary data by the example of Flink’s in-memory sort algorithm.

Flink assigns a memory budget to its data processing operators. Upon initialization, a sort algorithm requests its memory budget from the MemoryManager and receives a corresponding set of MemorySegments. The set of MemorySegments becomes the memory pool of a so-called sort buffer which collects the data that is be sorted. The following figure illustrates how data objects are serialized into the sort buffer.

## Flink在二进制数据上如果工作？

与许多其他数据处理API（包括SQL）类似，Flink的API提供了转换操作来进行数据集的分组、排序、连接（join）操作。这些转换操作在一个潜在的非常巨大的数据集上工作。虽然历经了数十年，关系数据库系统为这些特定目的提供了非常有特色的高效算法，包括外部的merge-sort、merge-join和hybrid hash-join。Flink构建于这些技术之上，并将其泛化到处理任意对象，这些对象通过使用自定义的序列化和比较技术栈进行操作。在接下来，我们展示了Flink操作二进制数据的过程，这通过Flink基于内存的排序算法来展示。

Flink将内存预算分配给它的数据处理操作符。在初始化完成之后，排序算法向MemoryManager请求内存预算，并获得相应的内存片段集合。这个内存片段集合成为了一个内存池，被称作是排序缓存（buffer），它可以收集数据，并保证其是有序的。下图展示了数据对象是如何序列化到排序缓存的（sort buffer）

![](./pics/sorting-binary-data-1.png)


The sort buffer is internally organized into two memory regions. The first region holds the full binary data of all objects. The second region contains pointers to the full binary object data and - depending on the key data type - fixed-length sort keys. When an object is added to the sort buffer, its binary data is appended to the first region, and a pointer (and possibly a key) is appended to the second region. The separation of actual data and pointers plus fixed-length keys is done for two purposes. It enables efficient swapping of fix-length entries (key+pointer) and also reduces the data that needs to be moved when sorting. If the sort key is a variable length data type such as a String, the fixed-length sort key must be a prefix key such as the first n characters of a String. Note, not all data types provide a fixed-length (prefix) sort key. When serializing objects into the sort buffer, both memory regions are extended with MemorySegments from the memory pool. Once the memory pool is empty and no more objects can be added, the sort buffer is completely filled and can be sorted. Flink’s sort buffer provides methods to compare and swap elements. This makes the actual sort algorithm pluggable. By default, Flink uses a Quicksort implementation which can fall back to HeapSort. The following figure shows how two objects are compared.

排序缓存（sort buffer）其内部由两块内存区域。第一个区域存储着所有对象的完整二进制数据。第二个区域存储了指向完整二进制对象数据的指针和一个固定长度的排序键（sort key），其长度依赖于键数据类型。当一个对象加入到排序缓存时，其二进制数据会在第一个区域追加，同时其指针（可能也有键）在第二个区域追加。区分开真实数据和指针加固定长度键具有如下两个目的。它不仅能高效地交换固定长度条目（键+指针），还可以在排序时减少需要移动的数据。如果排序键是一个可变长度数据类型比如String，那么固定长度的排序键必须是String的固定n个字符的前缀。注意，并不是所有数据类型都提供一个固定长度（前缀）的排序键。在把对象序列化到排序缓存时，两个内存区域都是从内存池中利用内存片段扩展而成。一旦内存池空了同时不能再添加更多对象时，排序缓存完全放满，就可以被排序。Flink的排序缓存提供了比较和交换元素的方法。这使得真正的排序算法是可插拔的。默认情况下，Flink使用快拍的实现方式，它可以退化成堆排序。下图展示了两个对象比较的过程。
![](./pics/sorting-binary-data-2.png)


The sort buffer compares two elements by comparing their binary fix-length sort keys. The comparison is successful if either done on a full key (not a prefix key) or if the binary prefix keys are not equal. If the prefix keys are equal (or the sort key data type does not provide a binary prefix key), the sort buffer follows the pointers to the actual object data, deserializes both objects and compares the objects. Depending on the result of the comparison, the sort algorithm decides whether to swap the compared elements or not. The sort buffer swaps two elements by moving their fix-length keys and pointers. The actual data is not moved. Once the sort algorithm finishes, the pointers in the sort buffer are correctly ordered. The following figure shows how the sorted data is returned from the sort buffer.

排序缓存通过比较其二进制固定长度的排序键来比较两个元素。无论是在全键（不是前缀key）还是在二进制前缀key上只要其不相等，能够区分出大小，那么比较过程就成功结束了。如果前缀键相等（或者排序键数据类型不支持二进制前缀键），排序缓存会依据指针找到真实的对象数据，反序列化获得对象，并对其进行比较。依赖于比较过程的结果，排序算法决定了是否对参与比较的两个元素进行交换。当排序缓存内发生交换时是通过移动其固定长度键和指针来实现的。真实数据不发生移动。一旦排序算法结束，排序缓存中的指针就在正确的顺序上了。下图展示了如何从排序缓存中获得排好序的数据。

![](./pics/sorting-binary-data-3.png)

The sorted data is returned by sequentially reading the pointer region of the sort buffer, skipping the sort keys and following the sorted pointers to the actual data. This data is either deserialized and returned as objects or the binary representation is copied and written to disk in case of an external merge-sort (see this [blog post on joins in Flink](http://flink.apache.org/news/2015/03/13/peeking-into-Apache-Flinks-Engine-Room.html)).

通过读取排序缓存的指针区域，可以有序返回排序好的数据，读取时要跳过排序键同时利用排序好的指针找到真正的数据。这些数据可以通过反序列化以对象形式返回，也可以拷贝其二进制表达方式，在进行外部merge-sort排序时写入到磁盘中（可以参考Flink中有关join的[博客](http://flink.apache.org/news/2015/03/13/peeking-into-Apache-Flinks-Engine-Room.html)）

##Show me numbers!

So, what does operating on binary data mean for performance? We’ll run a benchmark that sorts 10 million `Tuple2<Integer, String>` objects to find out. The values of the Integer field are sampled from a uniform distribution. The String field values have a length of 12 characters and are sampled from a long-tail distribution. The input data is provided by an iterator that returns a mutable object, i.e., the same tuple object instance is returned with different field values. Flink uses this technique when reading data from memory, network, or disk to avoid unnecessary object instantiations. The benchmarks are run in a JVM with 900 MB heap size which is approximately the required amount of memory to store and sort 10 million tuple objects on the heap without dying of an OutOfMemoryError. We sort the tuples on the Integer field and on the String field using three sorting methods:

##数据说明

对于性能来说，在二进制数据上进行操作意味着什么？我们将进行一次基线测试，通过对1000万的`Tuple2<Integer,String>`对象来进行展示。其中整数域的值是取自均匀分布的一个样本。字符串域的值是长度为12个字节，来自一个长尾分布的样本。输入数据通过一个迭代器提供，其返回一个可变对象，也就是说，返回的同一个tuple对象实例其有不同的值（译者注：含义是说，每次返回都是同一个对象，但其整数域、字符串域有不同的取值）。Flink在从内存、网络或磁盘上读取数据时使用了这一技术，以避免不必要的对象实例化。基线测试运行在有900MB堆大小的JVM上，这个大小约等于在内存中存储和排序1000万元组对象在不发生OutOfMemoryError是需要的内存数量。我们在整数域和字符串域上对元组进行排序，并分别使用了三种排序方法：

1. Object-on-heap. The tuples are stored in a regular java.util.ArrayList with initial capacity set to 10 million entries and sorted using Java’s regular collection sort.
2. Flink-serialized. The tuple fields are serialized into a sort buffer of 600 MB size using Flink’s custom serializers, sorted as described above, and finally deserialized again. When sorting on the Integer field, the full Integer is used as sort key such that the sort happens entirely on binary data (no deserialization of objects required). For sorting on the String field a 8-byte prefix key is used and tuple objects are deserialized if the prefix keys are equal.
3. Kryo-serialized. The tuple fields are serialized into a sort buffer of 600 MB size using Kryo serialization and sorted without binary sort keys. This means that each pair-wise comparison requires two object to be deserialized.
All sort methods are implemented using a single thread. The reported times are averaged over ten runs. After each run, we call System.gc() to request a garbage collection run which does not go into measured execution time. The following figure shows the time to store the input data in memory, sort it, and read it back as objects.

1. 对象在堆上。使用标准的`java.util.ArrayList`对元组进行排序，并对ArrayList初始化容量为1000万，用Java标准的collection排序接口对其排序。
2. Flink序列化。元组域通过序列化到一个大小为600MB的排序缓存中，使用Flink自定义的序列化接口，像上面描述的一样进行排序，并在最后再进行一次反序列化操作。在整数域上进行排序时，将整个整数看成是排序键，这样就使得排序完全在二进制数据上进行（不需要反序列化成对象的过程）。在字符串域上进行排序时，使用8字节前缀键作为排序键，元组对象在前缀键相同时需要进行反序列化操作。
3. Kryo序列化。元组域序列化到一个大小为600MB的排序缓存中，使用Kryo序列化方法并对其进行排序，但无法使用二进制排序键。这意味着每一次元组对进行比较都需要请求的两个对象进行反序列化。

所有的排序方法都使用单线程的实现方法。这里提到的时间是在10次运行后的得到的平均时间。每一次运行，我们都调用`System.gc()`方法请求运行一次垃圾回收，而这并没有计入到统计的执行时间中。下图展示的时间包括了在内存中存储输入数据、对其排序、将其以对象形式读取回来。

![](./pics/sort-benchmark.png)

We see that Flink’s sort on binary data using its own serializers significantly outperforms the other two methods. Comparing to the object-on-heap method, we see that loading the data into memory is much faster. Since we actually collect the objects, there is no opportunity to reuse the object instances, but have to re-create every tuple. This is less efficient than Flink’s serializers (or Kryo serialization). On the other hand, reading objects from the heap comes for free compared to deserialization. In our benchmark, object cloning was more expensive than serialization and deserialization combined. Looking at the sorting time, we see that also sorting on the binary representation is faster than Java’s collection sort. Sorting data that was serialized using Kryo without binary sort key, is much slower than both other methods. This is due to the heavy deserialization overhead. Sorting the tuples on their String field is faster than sorting on the Integer field due to the long-tailed value distribution which significantly reduces the number of pair-wise comparisons. To get a better feeling of what is happening during sorting we monitored the executing JVM using VisualVM. The following screenshots show heap memory usage, garbage collection activity and CPU usage over the execution of 10 runs.

可以看到Flink在对二进制数据排序上使用了自身的序列化方法，这使得它与另外两种方法对比时在性能上有了显著改善。与对象置于堆中的方法相比，可以看到将数据加载到内存中速度更快。由于实际收集了对象，而又没有重复利用对象实例，但又不得不重建每一个元组。这使得它（对象置于堆中的方法）比Flink的序列化方法（也比Kryo序列化方法）低效。另一方面，它从堆中读取对象与反序列化过程相比是没有开销的。在我们的基线测试中，对象克隆比序列化和反序列化联合使用还要代价高。可以观察排序时间，在二进制上的排序也比Java的collection排序要快。对Kryo序列化后的数据进行排序，没有二进制排序键，这将比其他的方法慢得多，这是由于重型的反序列化操作过度使用。在字符串域上对元组进行排序比在整型域上排序要快，这是由于长尾分布将显著减少需要比较的元组对。在排序时，为了对究竟发生了什么有更直观的认识，我们通过VisualVM对执行中的JVM进行了观察。下面的截图展示了在10次运行执行过程中，堆内存使用情况、垃圾回收活跃情况和CPU使用情况。


||Garbage Collection|Memory Usage|
|---|---|---|
|Object-on-Heap (int)|![](./pics/objHeap-int-gc.png)|![](./pics/objHeap-int-mem.png)|
|Flink-Serialized (int)|![](./pics/flinkSer-int-gc.png)|![](./pics/flinkSer-int-mem.png)|
|Kryo-Serialized (int)|![](./pics/kryoSer-int-gc.png)|![](./pics/kryoSer-int-mem.png)|


The experiments run single-threaded on an 8-core machine, so full utilization of one core only corresponds to a 12.5% overall utilization. The screenshots show that operating on binary data significantly reduces garbage collection activity. For the object-on-heap approach, the garbage collector runs in very short intervals while filling the sort buffer and causes a lot of CPU usage even for a single processing thread (sorting itself does not trigger the garbage collector). The JVM garbage collects with multiple parallel threads, explaining the high overall CPU utilization. On the other hand, the methods that operate on serialized data rarely trigger the garbage collector and have a much lower CPU utilization. In fact the garbage collector does not run at all if the tuples are sorted on the Integer field using the flink-serialized method because no objects need to be deserialized for pair-wise comparisons. The kryo-serialized method requires slightly more garbage collection since it does not use binary sort keys and deserializes two objects for each comparison.

实验运行在一台8核机器上，使用单线程方式运行，所以充分利用一个核心后也仅仅意味着全部利用时的12.5%。这个截图展示了在二进制数据上操作可以显著降低垃圾回收的活跃情况。对于对象置于堆内存的方法，垃圾回收在很小的时间间隔就会被运行一次，因为其填满了排序缓存，而这将消耗大量CPU的使用，即使对于一个运行的线程来说（排序本身并不会触发垃圾回收）。JVM垃圾回收使用多个并行的线程，并产生了非常高的CPU使用。另一方面，对序列化数据进行操作的方法极少触发垃圾回收，并具有很少的CPU使用。事实上如果在整型域上使用基于flink的序列化方法排序，垃圾回收根本就不会运行，因为在元组对比较时没有一个对象需要被反序列化。而Kryo序列化方法需要更多的垃圾回收，因为其没有使用二进制排序键，对每一个需要比较的元组对，要反序列化两个对象。

The memory usage charts shows that the flink-serialized and kryo-serialized constantly occupy a high amount of memory (plus some objects for operation). This is due to the pre-allocation of MemorySegments. The actual memory usage is much lower, because the sort buffers are not completely filled. The following table shows the memory consumption of each method. 10 million records result in about 280 MB of binary data (object data plus pointers and sort keys) depending on the used serializer and presence and size of a binary sort key. Comparing this to the memory requirements of the object-on-heap approach we see that operating on binary data can significantly improve memory efficiency. In our benchmark more than twice as much data can be sorted in-memory if serialized into a sort buffer instead of holding it as objects on the heap.

内存使用图表展示了flink序列化和kryo序列化时常占用比较高的内存（加上操作的一些对象）。这是由于内存片段的预分配策略。实际内存使用是很小的，由于排序缓存并没有全部填满。下面的表格展示了每种方法的内存消费情况。1000万记录产生了280MB的二进制数据（对象数据加指针和排序键），这依赖于使用的序列化器和二进制排序键的大小。将此与对象置于堆内存的方法在内存需求上进行对比，我们看出在二进制数据上的操作可以显著提高内存效率。在基线测试中，如果用数据序列化到排序缓存替代对象置于堆内存，可以在内存中排序两倍以上的数据。


|Occupied Memory|Object-on-Heap|Flink-Serialized|Kryo-Serialized
|---|---|---|---|
|Sort on Integer|approx. 700 MB (heap)|277 MB (sort buffer)|266 MB (sort buffer)|
|Sort on String|approx. 700 MB (heap)|315 MB (sort buffer)|266 MB (sort buffer)|

To summarize, the experiments verify the previously stated benefits of operating on binary data.

总结一下，这个实验验证了上面有关操作二进制数据收益的论证。

##We’re not done yet!

Apache Flink features quite a bit of advanced techniques to safely and efficiently process huge amounts of data with limited memory resources. However, there are a few points that could make Flink even more efficient. The Flink community is working on moving the managed memory to off-heap memory. This will allow for smaller JVMs, lower garbage collection overhead, and also easier system configuration. With Flink’s Table API, the semantics of all operations such as aggregations and projections are known (in contrast to black-box user-defined functions). Hence we can generate code for Table API operations that directly operates on binary data. Further improvements include serialization layouts which are tailored towards the operations that are applied on the binary data and code generation for serializers and comparators.

The groundwork (and a lot more) for operating on binary data is done but there is still some room for making Flink even better and faster. If you are crazy about performance and like to juggle with lot of bits and bytes, join the Flink community!

##还有更多可以做
Apache Flink使用了相当多的先进技术来在内存受限的场景中安全高效处理大量数据。但仍有一些点可以使Flink更加有效率。Flink社区正致力于将管理的内存转移到堆外内存上（译者注：这一点在后续版本中已经实现）。这将允许更小的JVM，更少的垃圾回收负载，更简单的系统配置。使用Flink的Table API，所有操作的语义比如聚集、投影都是预知的（与之相应的是当作黑盒处理的用户定义函数）。因此可以为Table API操作产生的代码使其可以直接在二进制数据上进行操作。更进一步的提升包括序列化设计，使其裁剪得将所有操作应用在二进制数据上，并未序列化器和比较器做代码生成。
有关在二进制数据上操作的背景工作已经完成，但为使Flink更好更快仍有一些努力的空间。如果你对性能着迷，并喜欢操作位和字节，欢迎加入Flink社区！

TL;DR; Give me three things to remember!

- Flink’s active memory management avoids nasty OutOfMemoryErrors that kill your JVMs and reduces garbage collection overhead.
- Flink features a highly efficient data de/serialization stack that facilitates operations on binary data and makes more data fit into memory.
- Flink’s DBMS-style operators operate natively on binary data yielding high performance in-memory and destage gracefully to disk if necessary.

只需要记住如下三件事情：

- Flink的积极内存管理机制可以避免令人不快的将会杀掉JVM的OutOfMemoryError，还能降低垃圾回收的负载。
- Flink特性之一是具有非常高效的数据序列化/反序列化栈，这可以方便使用二进制数据上的操作并使更多数据装入内存。
- Flink的DBMS风格的操作可以更原生地在二进制数据上进行，这可以在内存中产生很高的性能表现，在需要时可以优雅地降级到磁盘上。