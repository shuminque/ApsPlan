package com.depository_manage.pojo;

import com.depository_manage.entity.MaterialEngin;
import lombok.Data;

@Data
public class MaterialEnginP {
    private Integer id;
    private String ename;
    public MaterialEnginP(Integer id, String ename) {
        this.id=id;
        this.ename=ename;
    }
    public MaterialEnginP(MaterialEngin materialEngin) {
        this.id = materialEngin.getId();
        this.ename = materialEngin.getEname();
    }
}
