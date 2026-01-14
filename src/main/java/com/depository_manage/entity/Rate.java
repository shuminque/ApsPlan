package com.depository_manage.entity;

import lombok.Data;

import java.util.Date;

@Data
public class Rate {
    private Integer id;
    private Date date;
    private String currency_from;
    private String currency_to;
    private Double rate;

}
