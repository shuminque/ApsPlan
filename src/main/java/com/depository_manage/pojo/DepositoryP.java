package com.depository_manage.pojo;

import com.depository_manage.entity.Depository;

import lombok.Data;

@Data
public class DepositoryP {
    private Integer id;

    private String dname;

    private String address;

    private String introduce;

    public DepositoryP(Integer id,String dname,String address,String introduce) {
        this.id=id;
        this.dname=dname;
        this.address=address;
        this.introduce=introduce;
    }
    public DepositoryP(Depository depository) {
        this.id = depository.getId();
        this.dname = depository.getDname();
        this.address =depository.getAddress();
        this.introduce =depository.getIntroduce();
    }
}
