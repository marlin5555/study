package com.sf.pis.akka.demo;

import org.apache.http.HttpHost;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.apache.http.impl.nio.reactor.IOReactorConfig;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.RestHighLevelClient;

import java.util.ArrayList;
import java.util.List;

/**
 * jstorm每个bolt单线程运行，可以不设计为单例
 * @author 01369519
 * @DESCRIPTION es rest client
 * @create 2018-12-21 下午5:39
 **/
public class EsRestClient {

    //包含端口信息 10.11.11.11:9200
    private String cluster;
    private RestHighLevelClient restClient;


    public EsRestClient(String cluster) {
        this.cluster = cluster;
        restClient = init();
    }

    private RestHighLevelClient init(){
        return new RestHighLevelClient(build(cluster));
    }


    private RestClientBuilder build(String searchCluster){

        String[] ipPorts = searchCluster.split(",");
        List<String> ips = new ArrayList<>();
        List<Integer> ports = new ArrayList<>();

        for (String ipPort : ipPorts) {
            String[] info = ipPort.split(":");
            ips.add(info[0]);
            ports.add(Integer.valueOf(info[1]));
        }

        if (ips.size() != ports.size()) {
            return null;
        }

        List<HttpHost> hosts = new ArrayList<>();
        for (int i = 0; i < ips.size(); i++) {

            hosts.add(new HttpHost(ips.get(i), ports.get(i)));
        }

        HttpHost[] httpHosts = hosts.toArray(new HttpHost[0]);
        RestClientBuilder builder = RestClient.builder(httpHosts);
        builder.setHttpClientConfigCallback(new RestClientBuilder.HttpClientConfigCallback() {
            @Override
            public HttpAsyncClientBuilder customizeHttpClient(HttpAsyncClientBuilder httpClientBuilder) {
                return httpClientBuilder.setDefaultIOReactorConfig(
                        IOReactorConfig.custom().setIoThreadCount(4).build())
                        .setMaxConnPerRoute(8)
                        .setMaxConnTotal(100);
            }
        });
        builder.setRequestConfigCallback(
                new RestClientBuilder.RequestConfigCallback() {
                    @Override
                    public RequestConfig.Builder customizeRequestConfig(
                            RequestConfig.Builder requestConfigBuilder) {
                        return requestConfigBuilder.setSocketTimeout(10000);
                    }
                });
        builder.setMaxRetryTimeoutMillis(20000);


        return builder;
    }

    public RestHighLevelClient getRestClient() {
        return restClient;
    }



    public void close(){
        try {
            restClient.close();
        }catch (Exception e){

        }
    }
}