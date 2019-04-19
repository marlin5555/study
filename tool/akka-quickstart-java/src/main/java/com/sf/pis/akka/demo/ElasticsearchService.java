package com.sf.pis.akka.demo;

import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.Scroll;
import org.elasticsearch.search.builder.SearchSourceBuilder;

import static org.elasticsearch.index.query.QueryBuilders.*;

/**
 * @author 01369519
 * @DESCRIPTION es操作方法
 * @create 2018-12-22 上午11:57
 **/
public class ElasticsearchService {

    private RestHighLevelClient restClient;


    public SearchRequest searchScroll(String index, String type, SearchSourceBuilder searchBuilder,Scroll scroll){
        SearchRequest request = new SearchRequest(index);
        request.scroll(scroll);
        request.source(searchBuilder);
        return request;

    }



    public SearchSourceBuilder buildTimeRangeQuery(String field, long start,long end){
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();

        //range query
        searchSourceBuilder.query(rangeQuery(field).gte(start).lte(end));
        searchSourceBuilder.size(1000);
        return searchSourceBuilder;
    }

    public SearchSourceBuilder buildPlane(String field,long time){
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        BoolQueryBuilder bool = QueryBuilders.boolQuery()
                .must(QueryBuilders.termQuery(field,time))
                .must(QueryBuilders.rangeQuery("transCapacityType").lte(2).gt(1))
                ;
        searchSourceBuilder.query(bool);
        searchSourceBuilder.size(1000);
        return searchSourceBuilder;
    }

    public SearchSourceBuilder buildTermQuery(String field,long time){
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.query(termQuery(field,time));
        searchSourceBuilder.size(1000);
        return searchSourceBuilder;
    }




    public SearchSourceBuilder buildTermQuery(String field,String flag){
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.query(termQuery(field,flag));
        searchSourceBuilder.size(1000);
        return searchSourceBuilder;
    }

    public SearchSourceBuilder buildMatchAll(){
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.query(matchAllQuery());
        searchSourceBuilder.size(1000);
        return searchSourceBuilder;
    }




}