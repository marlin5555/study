#How to factorize a 700 GB matrix with Apache Flink™

A story full of valuable insights told in 3 acts

Posted on Mar 30th, 2015 by	[Till Rohrmann](http://data-artisans.com/author/till/)

This article is a follow-up post to the earlier published article about Computing recommendations at extreme scale with Apache Flink. We discuss how we implemented the alternating least squares (ALS) algorithm in Apache Flink, starting from a straightforward implementation of the algorithm, and moving to a blocked ALS implementation optimizing performance on the way. Similar observations have been made by others, and the final algorithm we arrive to is also the one implemented in Apache Spark’s MLlib. Furthermore, we describe the improvements contributed to Flink in the wake of implementing ALS.

这篇文章是对前一篇文章《Computing recommendations at extreme scale with Apache Flink》的增补。我们讨论了如何在Apache Flink中实现ALS算法，从算法的简单实现开始，再利用Block ALS思路对齐优化性能。[其他人](http://de.slideshare.net/MrChrisJohnson/music-recommendations-at-scale-with-spark)已经做过类似的结论，最终我们实现的算法是在[Apache Spark MLLib](http://spark.apache.org/)中实现的类似思路。此外，作为实现ALS的结果，我们也将此过程中的改进贡献到Flink中。

##Prologue

Recommending the right products to users is the cornerstone for e-commerce and several internet companies. Examples of recommendations include recommending items at amazon.com, movies on netflix, songs at music services like Spotify, etc. Providing good recommendations improves the user experience and plays an important role in sales growth.

There are two main approaches to recommendation: The first one is called content-based recommendation. Content-based methods try to create a feature vector for each item describing its properties. By knowing which items the user consumed before, we can look for other items which are similar with respect to some metric and recommend these items to the user. The drawback of this approach is that it is not always possible to find the right features to describe an item.

##序

给用户推荐恰当的产品是电子商务和部分互联网公司基石。推荐系统包括在amazon上推荐商品，在netflix上推荐电影，在Spotify类似的音乐服务上推荐歌曲等等。提供好的推荐改善了用户体验，并在提高销售的过程中扮演了重要角色。

推荐系统有两种主要实现：第一种成为基于内容的推荐。基于内容的方法试图为每个商品（item）创建一个用来描述其属性（property）的特征向量（feature vector）。通过获知用户此前消费过什么商品，我们可以在某些度量标准下查找其他类似商品，并将其推荐给用户。这种方法的缺点是并不总是能够为一个商品找到其恰当的描述特征。

The alternative approach is recommending items based on the past behaviour of a group of users. Depending on the preferences of users who behave similarly, one can make predictions about what other items a user might like. This approach is called collaborative filtering (CF). The advantage of CF is that it automatically detects relationships between users and interdependencies among items which are hard to capture by a content-based method. However, CF suffers from the cold start problem which arises if one introduces a new item for which no past behaviour exists. For these items it is hard to make predictions until some users have given initial feedback.

Latent factor models

One way to solve the problem of collaborative filtering are latent factor models. Latent factor models try to find a user-factor and item-factor vector ![factor-vector](http://latex.codecogs.com/png.latex?x_u,y_i\\in\\mathbb%20R^f) for each user uu and item ii with ff being the number of latent factors such that the inner product calculates the prediction value ![](http://latex.codecogs.com/png.latex?\\hat{r}_{ui}%20=%20x_{u}^{T}y_i). The latent factors are the representation of user preferences and item characteristics in an abstract feature space. One could think of these variables as denoting the color, shape, price or the genre of an item. In general, the latent factors represent more abstract concepts which cannot be directly grasped.

另一个方法是基于过去一段时间人群的行为进行商品推荐。基于相似行为用户的表现，我们可以预测这个用户可能喜欢其他什么商品。这个方法称之为协同过滤（CF），它的优势在于其自动检测用户间的关系，产品间的相关性，而这些很难在基于内容的方法中发现。但是CF在冷启动时效果受损，其发生在如下情况：一个没有历史行为记录的人买了一个新商品时（推荐受到挑战，冷启动）。在一些用户给出初始反馈前，这些商品很难对其进行预测。

###潜在因素模型
使用协同过滤的方式解决这个问题称之为[潜在因素模型](http://www2.research.att.com/~volinsky/papers/ieeecomputer.pdf)。它试图找到一个用户因子和商品因子，即向量![factor-vector](http://latex.codecogs.com/png.latex?x_u,y_i\\in\\mathbb%20R^f)，为每个用户u和商品i具有f个潜在因素，使得进行内积计算后即可以得到预测值![](http://latex.codecogs.com/png.latex?\\hat{r}_{ui}%20=%20x_{u}^{T}y_i)。潜在因子是用户偏好和商品特征在抽象特征空间的一种描述。我们可以把那些变量想象成描述的是颜色、形状价格或者商品的流派。通常来说，潜在因子描述的抽象概念都是很难控制的（grasped）。
<img src="http://www.forkosh.com/mathtex.cgi? \Large x=\frac{-b\pm\sqrt{b^2-4ac}}{2a}">
##Problem formulation

The following problem formulation is the summary of the work of [Zhou et al.](http://dx.doi.org/10.1007/978-3-540-68880-8_32) and [Hu et al.](http://citeseerx.ist.psu.edu/viewdoc/summary?doi=10.1.1.167.5120) Given the matrix of user-item ratings ![](http://latex.codecogs.com/png.latex?R=%28r_{ui}%29) with ![](http://latex.codecogs.com/png.latex?u\\in[1...n]) and ![](http://latex.codecogs.com/png.latex?i\\in[1...m]) where ![](http://latex.codecogs.com/png.latex?u\\in[1...n]) and ![](http://latex.codecogs.com/png.latex?r_{ui}) represents the preference of user u for item i we can try to find the set of user- and item-factor vectors. It is noteworthy that R is intrinsically sparse because usually a user has only given feedback to a subset of all items. Therefore, we will only consider the rated items of every user to measure the performance of our latent factors model. By finding a model whose predictions are close to the actual ratings, we hope to be able to make predictions for unrated items.

Retrieving a suitable model boils down to a minimization problem of the root-mean-square error (RMSE) between existing ratings and their predicted values plus some regularization term to avoid overfitting:
![](http://latex.codecogs.com/png.latex?\\min_{X,Y}\\sum_{r_{ui}\\text{exists}}\\left%28r_{ui}-x_u^Ty_i\\right%29^2+\\lambda\\left%28\\sum_{u}n_u||x_{u}||^2+\sum_{i}n_i||y_{i}||^2\\right%29)

.![](http://latex.codecogs.com/png.latex?X=%28x_1,\\ldots,x_n%29)is the matrix of user-factor vectors and ![](http://latex.codecogs.com/png.latex?Y=%28y_1,\\ldots,y_m%29) is the matrix of item-factor vectors. ![](http://latex.codecogs.com/png.latex?n_u) and ![](http://latex.codecogs.com/png.latex?n_i) denotes the number of existing ratings of user u and item i, respectively. According to [Zhou et al.](http://dx.doi.org/10.1007/978-3-540-68880-8_32), this weighted-λ-regularization gives best empirical results. If we write this in matrix notation, then we easily see that we are actually looking for a low-rank matrix factorization of RR such that ![](http://latex.codecogs.com/png.latex?R=X^TY).


##问题的形式化描述

下面有关问题形式化描述的内容摘要自 [Zhou et al.](http://dx.doi.org/10.1007/978-3-540-68880-8_32) and [Hu et al.](http://citeseerx.ist.psu.edu/viewdoc/summary?doi=10.1.1.167.5120)的相关工作。给定用户-商品的评价矩阵![](http://latex.codecogs.com/png.latex?R=%28r_{ui}%29)其中 ![](http://latex.codecogs.com/png.latex?u\\in[1...n]) 、![](http://latex.codecogs.com/png.latex?i\\in[1...m])，而![](http://latex.codecogs.com/png.latex?r_{ui})代表了用户u对商品i的偏爱，我们可以尝试从中发现用户和商品的特征向量。值得注意的是R本质上是稀疏的，因为通常境况下用户只能给出所有商品一个子集的反馈意见。因此，我们只使用每个用户评价过的商品对我们的潜在因素模型进行效果度量。我们要找的模型，其预测值应当与实际评价尽量接近，我们希望使用这个预测模型给没有评价的商品进行预测。
获取一个合适的模型归纳起来是一个最小化均方根误差（RMSE）的问题，该误差描述了当前评价与模型预测值之间的差异，并公国一些正则化项以避免过拟合：
![](http://latex.codecogs.com/png.latex?\\min_{X,Y}\\sum_{r_{ui}\\text{exists}}\\left%28r_{ui}-x_u^Ty_i\\right%29^2+\\lambda\\left%28\\sum_{u}n_u||x_{u}||^2+\sum_{i}n_i||y_{i}||^2\\right%29)
其中![](http://latex.codecogs.com/png.latex?X=%28x_1,\\ldots,x_n%29)是用户特征向量矩阵，![](http://latex.codecogs.com/png.latex?Y=%28y_1,\\ldots,y_m%29)是商品特征向量矩阵。 ![](http://latex.codecogs.com/png.latex?n_u) and ![](http://latex.codecogs.com/png.latex?n_i)代表了用户u和商品i已存在的评价数量。根据[Zhou et al.](http://dx.doi.org/10.1007/978-3-540-68880-8_32)的描述，加权的正则化系数λ应该通过最佳实践获得。如果我们使用矩阵的记号对其书写，可以很容易看到我们实际上寻找的是R的低秩矩阵分解，并使得 ![](http://latex.codecogs.com/png.latex?R=X^TY)。

By fixing one of the sought-after matrices we obtain a quadratic form which can be easily minimized with respect to the remaining matrix. If this step is applied alternately to both matrices we can guarantee that for each step we converge closer to the solution. This method is called alternating least squares (ALS). If we fix the item-factor matrix and solve for the user-factor matrix we obtain the following equation which we have to solve for each user-factor vector:

通过固定其中一个矩阵（较好的），我们可以获得有关剩下矩阵的一个二次方程形式，而这是容易最小化的。如果在两个矩阵上交替执行这个步骤，我们可以保证每一步都是收敛地接近答案。这个方法被称之为交替最小二乘法（ALS）。如果我们固定商品特征矩阵，并对用户特征矩阵求解，我们可以为得到下面的公式，而这个就是我们要为每个用户特征向量求解的：
![](http://latex.codecogs.com/png.latex?x_{u}=\\left%28YS^uY^T+\\lambda%20n_u%20I\\right%29^{-1}Yr_u^T)

with ![](http://latex.codecogs.com/png.latex?r_u) being the rating vector of user u (the uth row vector of R) and ![](http://latex.codecogs.com/png.latex?S^u\\in\\mathbb{R}^{m\\times%20m}) is the diagonal matrix where
![](http://latex.codecogs.com/png.latex?S^u_{ii}=\\begin{cases}1&\\text{if%20}r_{ui}\\not=0\\\\0&\\text{else}\\end{cases})

. For the sake of simplicity we set ![](http://latex.codecogs.com/png.latex?A_u=YS^uY^T+\\lambda%20n_uI) and ![](http://latex.codecogs.com/png.latex?V_u=Yr_u^T). The item-factor vectors can be calculated in a similar fashion:

![](http://latex.codecogs.com/png.latex?y_{i}=\\left%28XS^iX^T+\\lambda%20n_iI\\right%29^{-1}Xr^i)

with ![](http://latex.codecogs.com/png.latex?r^i) being the rating vector of item i (iith column vector of R) and ![](http://latex.codecogs.com/png.latex?S^i\\in\\mathbb{R}^{n\times%20n}) is the diagonal matrix where
![](http://latex.codecogs.com/png.latex?S^i_{uu}=\\begin{cases}1&\\text{if%20}r_{ui}\\not=0\\\\0&\\text{else}\end{cases})

. Again we can simplify the equation by ![](http://latex.codecogs.com/png.latex?A_i=XS^iX^T+\\lambda%20n_iI) and ![](http://latex.codecogs.com/png.latex?V_i=Xr^i).


其中，![](http://latex.codecogs.com/png.latex?r_u)是用户u的评价向量（也就是R的第u行向量），![](http://latex.codecogs.com/png.latex?S^u\\in\\mathbb{R}^{m\\times%20m})是对角线矩阵，其中

![](http://latex.codecogs.com/png.latex?S^u_{ii}=\\begin{cases}1&\\text{if%20}r_{ui}\\not=0\\\\0&\\text{else}\\end{cases})

为了简单起见，我们假设![](http://latex.codecogs.com/png.latex?A_u=YS^uY^T+\\lambda%20n_uI) 、![](http://latex.codecogs.com/png.latex?V_u=Yr_u^T)。商品特征向量可以通过类似的方式计算：

![](http://latex.codecogs.com/png.latex?y_{i}=\\left%28XS^iX^T+\\lambda%20n_iI\\right%29^{-1}Xr^i)

其中![](http://latex.codecogs.com/png.latex?r^i)是商品i的评价向量（也就是R的第i列向量），![](http://latex.codecogs.com/png.latex?S^i\\in\\mathbb{R}^{n\times%20n})是对角线矩阵，其中

![](http://latex.codecogs.com/png.latex?S^i_{uu}=\\begin{cases}1&\\text{if%20}r_{ui}\\not=0\\\\0&\\text{else}\end{cases})

并且我们使用简化的![](http://latex.codecogs.com/png.latex?A_i=XS^iX^T+\\lambda%20n_iI) 、 ![](http://latex.codecogs.com/png.latex?V_i=Xr^i)。

Since we want to factorize rating matrices which are so big that they no longer fit into the main memory of a single machine, we have to solve the problem in parallel. Furthermore, the ALS computation is inherently iterative, consisting of a series of optimization steps. Apache Flink constitutes an excellent fit for this task, since it offers an expressive API combined with support for iterations. An in-depth description of Apache Flink’s programming API can be found here.

The rating matrix RR is sparse and consequently we should represent it as a set of tuples (rowIndex, columnIndex, entryValue). The resulting user-factor and item-factor matrices will be dense and we represent them as a set of column vectors (columnIndex, columnVector). If we want to distribute the rating and user/item matrices, we can simply use Flink’s DataSet, which is the basic abstraction to represent distributed data. Having defined the input and output types, we can look into the different implementations of ALS.

因为我们希望因式分解的评价矩阵太大，以至于在一台机器的内存中都不能够放下，我们不得不并行化解决这个问题。更进一步，ALS的计算从本质上说是迭代的，包含一些列的求取最优化的步骤。Apache Flink对于这类任务是十分合适的，因为它提供很强表达能力的API，它可以很好地支持迭代过程。Apache Flink的编程API的深入描述参照[这里](http://flink.apache.org/docs/0.8/programming_guide.html)。
评价矩阵R是稀疏的，因此我们应该使用如下的tuple集合来表达这个矩阵（rowIndex，columnIndex，entryValue）。作为结果的用户特征和商品特征矩阵将是稠密的，我们将它们表述成列向量的集合（columnIndex，columnVector）。如果我们希望将评价矩阵和用户/商品矩阵分布式处理，可以使用Flink的DataSet，它是分布式数据的一个基本抽象表达。在定义好输入输出类型后，我们可以深入ALS的具体实现。


