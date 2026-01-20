package com.depository_manage.entity.aps;

import lombok.Data;
import java.util.Date;

@Data
public class ShiftTeam {
    private Integer teamID;
    private String teamName;
    private String managerName;
    private Date creationDate;
}

