/*
 * Copyright (c) 2014, S.F. Express Inc. All rights reserved.
 */
package com.sf.pis.akka.demo.util;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @ClassName LRU
 * @Author 01381694
 * @Description TODO
 * @Date 2019/4/17 11:10
 **/


public class LRU<K,V> extends LinkedHashMap<K,V> {
    //定义缓存的容量
    private int capacity;
    private static final long serialVersionUID = 1L;
    //带参数的构造器
    public LRU(int capacity){
        //调用LinkedHashMap的构造器，传入以下参数
        super(16,0.75f,true);
        //传入指定的缓存最大容量
        this.capacity=capacity;
    }
    //实现LRU的关键方法，如果map里面的元素个数大于了缓存最大容量，则删除链表的顶端元素
    @Override
    public boolean removeEldestEntry(Map.Entry<K, V> eldest){
        return size()>capacity;
    }
}