/*
 * Copyright (c) 2014, S.F. Express Inc. All rights reserved.
 */
package com.sf.pis.akka.demo;

/**
 * @ClassName Constant
 * @Author 01381694
 * @Description TODO
 * @Date 2019/4/10 16:45
 **/

public interface Constants {
    // 班次类型
    /*** 收件班次*/
    int PICKUP_PHASE = 1;
    /*** 收件仓库班次*/
    int PICKUP_DEPOT = 2;
    /*** 中转班次*/
    int TRANSFER = 3;
    /*** 运输班次*/
    int TRANSPORT = 4;
    /*** 派件仓库班次*/
    int DELIVER_DEPOT = 5;
    /*** 派件班次*/
    int DELIVER_PHASE = 6;

    /**囤货班次*/
    int STORE_GOODS = 7;
}
