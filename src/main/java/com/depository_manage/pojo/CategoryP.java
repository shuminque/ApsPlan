package com.depository_manage.pojo;

import com.depository_manage.entity.Category;
import lombok.Data;

import java.util.List;
@Data
public class CategoryP {
    private Integer id;
    private Integer depositoryId;
    private Integer parentId;
    private String title;
    private List<Category> children;

    public CategoryP(Integer id, Integer depositoryId, Integer parentId, String title, List<Category> children) {
        this.id = id;
        this.depositoryId = depositoryId;
        this.parentId = parentId;
        this.title = title;
        this.children = children;
    }
    public CategoryP(Category category) {
        this.id = category.getId();
        this.depositoryId = category.getDepositoryId();
        this.parentId = category.getParentId();
        this.title = category.getTitle();
        this.children = category.getChildren();
    }
}
