package com.depository_manage.pojo;

import com.depository_manage.entity.Rate;
import lombok.Data;

import java.util.Date;

@Data
public class RateP {
    private Integer id;
    private Date date;
    private String currency_from;
    private String currency_to;
    private Double rate;

    public RateP(Integer id, Date date, String currency_from, String currency_to, Double rate){
        this.id = id;
        this.date = date;
        this.currency_from = currency_from;
        this.currency_to = currency_to;
        this.rate = rate;
    }
    public RateP(Rate rate){
        this.id = rate.getId();
        this.date = rate.getDate();
        this.currency_from = rate.getCurrency_from();
        this.currency_to = rate.getCurrency_to();
        this.rate = rate.getRate();
    }
}
