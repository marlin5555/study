/*
 * Copyright (c) 2014, S.F. Express Inc. All rights reserved.
 */
package com.sf.pis.akka.demo.model;

import java.util.Objects;

/**
 * @ClassName Capacity
 * @Author 01381694
 * @Description TODO
 * @Date 2019/4/10 17:35
 **/

public class Capacity {
    private String workDays;
    private String lineCode;
    private String cvyName;
    private String loadZoneCodeBelong;
    private String takegoodsZoneCodeBelong;
    private String planSendTm;
    private String planArriveTm;
    private String crossDay;

    public String getLineCode() {
        return lineCode;
    }

    public void setLineCode(String lineCode) {
        this.lineCode = lineCode;
    }

    public String getWorkDays() {
        return workDays;
    }

    public void setWorkDays(String workDays) {
        this.workDays = workDays;
    }

    public String getCvyName() {
        return cvyName;
    }

    public void setCvyName(String cvyName) {
        this.cvyName = cvyName;
    }

    public String getLoadZoneCodeBelong() {
        return loadZoneCodeBelong;
    }

    public void setLoadZoneCodeBelong(String loadZoneCodeBelong) {
        this.loadZoneCodeBelong = loadZoneCodeBelong;
    }

    public String getTakegoodsZoneCodeBelong() {
        return takegoodsZoneCodeBelong;
    }

    public void setTakegoodsZoneCodeBelong(String takegoodsZoneCodeBelong) {
        this.takegoodsZoneCodeBelong = takegoodsZoneCodeBelong;
    }

    public String getPlanSendTm() {
        return planSendTm;
    }

    public void setPlanSendTm(String planSendTm) {
        this.planSendTm = planSendTm;
    }

    public String getPlanArriveTm() {
        return planArriveTm;
    }

    public void setPlanArriveTm(String planArriveTm) {
        this.planArriveTm = planArriveTm;
    }

    public String getCrossDay() {
        return crossDay;
    }

    public void setCrossDay(String crossDay) {
        this.crossDay = crossDay;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Capacity capacity = (Capacity) o;
        return Objects.equals(workDays, capacity.workDays) &&
                Objects.equals(cvyName, capacity.cvyName) &&
                Objects.equals(loadZoneCodeBelong, capacity.loadZoneCodeBelong) &&
                Objects.equals(takegoodsZoneCodeBelong, capacity.takegoodsZoneCodeBelong) &&
                Objects.equals(planSendTm, capacity.planSendTm) &&
                Objects.equals(planArriveTm, capacity.planArriveTm) &&
                Objects.equals(crossDay, capacity.crossDay);
    }

    @Override
    public int hashCode() {
        return Objects.hash(workDays, cvyName, loadZoneCodeBelong, takegoodsZoneCodeBelong, planSendTm, planArriveTm, crossDay);
    }

    @Override
    public String toString() {
        return "Capacity{" +
                "workDays='" + workDays + '\'' +
                ", lineCode='" + lineCode + '\'' +
                ", cvyName='" + cvyName + '\'' +
                ", loadZoneCodeBelong='" + loadZoneCodeBelong + '\'' +
                ", takegoodsZoneCodeBelong='" + takegoodsZoneCodeBelong + '\'' +
                ", planSendTm='" + planSendTm + '\'' +
                ", planArriveTm='" + planArriveTm + '\'' +
                ", crossDay='" + crossDay + '\'' +
                '}';
    }
}
