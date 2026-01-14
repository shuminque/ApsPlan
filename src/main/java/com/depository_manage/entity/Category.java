package com.depository_manage.entity;

import lombok.Data;
import java.util.List;

@Data
public class Category {
    private Integer id;
    private Integer depositoryId;
    private Integer parentId;
    private String title;
    private List<Category> children;
}
