package com.depository_manage.service;

import com.depository_manage.entity.SteelGrade;
import java.util.List;

public interface SteelGradeService {

    List<SteelGrade> findAll();

    SteelGrade findById(Integer id);

    boolean insert(SteelGrade steelGrade);

    boolean update(SteelGrade steelGrade);

    boolean deleteById(Integer id);
}
