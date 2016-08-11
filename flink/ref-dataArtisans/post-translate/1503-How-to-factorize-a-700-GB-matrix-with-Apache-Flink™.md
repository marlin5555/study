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

The following problem formulation is the summary of the work of [Zhou et al.](http://dx.doi.org/10.1007/978-3-540-68880-8_32) and [Hu et al.](http://citeseerx.ist.psu.edu/viewdoc/summary?doi=10.1.1.167.5120) Given the matrix of user-item ratings ![](http://latex.codecogs.com/png.latex?R=(r_{ui})) with ![](http://latex.codecogs.com/png.latex?u\\in[1...n]) and ![](http://latex.codecogs.com/png.latex?i\\in[1...m]) where ![](http://latex.codecogs.com/png.latex?u\\in[1...n]) and ![](http://latex.codecogs.com/png.latex?r_{ui}) represents the preference of user u for item i we can try to find the set of user- and item-factor vectors. It is noteworthy that R is intrinsically sparse because usually a user has only given feedback to a subset of all items. Therefore, we will only consider the rated items of every user to measure the performance of our latent factors model. By finding a model whose predictions are close to the actual ratings, we hope to be able to make predictions for unrated items.

Retrieving a suitable model boils down to a minimization problem of the root-mean-square error (RMSE) between existing ratings and their predicted values plus some regularization term to avoid overfitting:
minX,Y∑rui exists(rui−xTuyi)2+λ(∑unu||xu||2+∑ini||yi||2)
minX,Y∑rui exists(rui−xuTyi)2+λ(∑unu||xu||2+∑ini||yi||2)

. X=(x1,…,xn)X=(x1,…,xn) is the matrix of user-factor vectors and Y=(y1,…,ym)Y=(y1,…,ym) is the matrix of item-factor vectors. nunu and nini denotes the number of existing ratings of user uu and item ii, respectively. According to Zhou et al., this weighted-λλ-regularization gives best empirical results. If we write this in matrix notation, then we easily see that we are actually looking for a low-rank matrix factorization of RR such that R=XTYR=XTY.
![Alt text](https://rawgithub.com/potherca/StackOverflow/gh-pages/question.13808020.include-an-svg-hosted-on-github-in-markdown/controllers_brief.svg)

##问题的形式化描述





