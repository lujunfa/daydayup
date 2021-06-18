# 								es学习笔记



#### ES Mapping parameters

1. es会默认为每个全文字段Text加一个KeyWord字段用于聚合，排序，脚本引用等操作。之所以加一个KeyWord是因为keyword能够生成一个docvalue的正排索引数据结构，该数据结构能够很好的实现数据的聚合，排序等操作。
2. es中不管是text，keyword，long或者其他精确类型字段，如果想被搜索，就必须被索引到倒排索引中。
3. 如果text字段禁用了keyword并且还想排序，聚合等操作，则可用fielddata数据结构在内存中通过从磁盘中读取整个倒排索引，然后反转他们的关系形成一个正排索引以便于排序，聚合。由于是在内存中生成这种数据结构比较耗内存，索引默认是禁用的。
4. 默认情况下，对所有字段值进行索引以使其可搜索，但不存储它们。这意味着可以查询该字段，但是无法检索原始字段值。通常这无关紧要。该字段值已经是_source字段的一部分，默认情况下已存储。如果只想检索单个字段或几个字段的值，而不是整个_source的值，则可以使用源过滤来实现。在某些情况下，存储字段可能很有意义。例如，如果您有一个带有标题，日期和很大的内容字段的文档，则可能只想检索标题和日期，而不必从较大的_source字段中提取这些字段。对应的Mapping Option是Store。可以合理地存储字段的另一种情况是，对于那些未出现在_source字段（例如copy_to字段）中的字段。

5. Field data是在聚合，排序或脚本编制中从全文字段访问分析的令牌的唯一方法。例如，像New York这样的全文字段将被分析为New和York。要汇总这些令牌，需要字段数据。官方原文(Field data is the only way to access the analyzed tokens from a full text field in aggregations, sorting, or scripting. For example, a full text field like `New York` would get analyzed as `new` and `york`. To aggregate on these tokens requires field data)