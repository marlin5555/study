package com.sf.pis.akka.demo;

import com.alibaba.fastjson.JSONObject;
import com.sf.pis.akka.demo.model.Capacity;
import org.elasticsearch.search.SearchHit;

import java.util.*;

/**
 * @author 01369519
 * @DESCRIPTION 加载运输班次 从es索引pis_naga_transport 中读取authorZone字段
 * @create 2018-12-22 下午2:18
 **/
public class LoadTransport implements Load<Capacity> {

    public static int count = 0;
    private String getCrossDay(String crossDays){
        return crossDays.split("\\+")[0];
    }

    @Override
    public void load(SearchHit[] hits, List<Capacity> capacities) {

        List<Capacity> result = new ArrayList<>();
        for (SearchHit hit:hits){
            JSONObject data = JSONObject.parseObject(hit.getSourceAsString());
            String workDays = data.getString("workDays");
            String lineCode = data.getString("lineCode");
            String cvyName = data.getString("cvyName");
            String loadZoneCodeBelong = data.getString("loadZoneCodeBelong");
            String takegoodsZoneCodeBelong = data.getString("takegoodsZoneCodeBelong");
            String planSendTm = data.getString("planSendTm");
            String planArriveTm = data.getString("planArriveTm");
            String crossDay = getCrossDay(data.getString("crossDays"));
            String loadAreaCodes = data.getString("loadAreaCodes");
            String loadCityCodes = data.getString("loadCityCodes");
            String loadZoneCodes = data.getString("loadZoneCodes");
            if(!((loadAreaCodes!=null && !loadAreaCodes.isEmpty())
                    ||(loadCityCodes!=null && !loadCityCodes.isEmpty())
                    ||(loadZoneCodes!=null && !loadZoneCodes.isEmpty()))){
                count++;
                continue;
            }
            Capacity capacity = new Capacity();
            capacity.setWorkDays(workDays);
            capacity.setLineCode(lineCode);
            capacity.setCvyName(cvyName);
            capacity.setLoadZoneCodeBelong(loadZoneCodeBelong);
            capacity.setTakegoodsZoneCodeBelong(takegoodsZoneCodeBelong);
            capacity.setPlanSendTm(planSendTm);
            capacity.setPlanArriveTm(planArriveTm);
            capacity.setCrossDay(crossDay);

            result.add(capacity);
        }
        capacities.addAll(result);
    }
}