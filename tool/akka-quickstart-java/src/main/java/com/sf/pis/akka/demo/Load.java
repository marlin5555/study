package com.sf.pis.akka.demo;

import org.elasticsearch.search.SearchHit;

import java.util.List;
import java.util.Map;

/**
 * @author 01369519
 * @DESCRIPTION 加载es数据的公共接口
 * @create 2018-12-22 下午2:18
 **/
public interface Load<T>{
    /**
     *
     */
     void load(SearchHit[] hits, List<T> batchMap);
}
