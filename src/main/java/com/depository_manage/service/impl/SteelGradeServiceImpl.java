package com.depository_manage.service.impl;

import com.depository_manage.entity.SteelGrade;
import com.depository_manage.mapper.cpck.SteelGradeMapper;
import com.depository_manage.service.SteelGradeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SteelGradeServiceImpl implements SteelGradeService {

    @Autowired
    private SteelGradeMapper steelGradeMapper;

    @Override
    public List<SteelGrade> findAll() {
        return steelGradeMapper.findAll();
    }

    @Override
    public SteelGrade findById(Integer id) {
        return steelGradeMapper.findById(id);
    }

    @Override
    public boolean insert(SteelGrade steelGrade) {
        return steelGradeMapper.insert(steelGrade) > 0;
    }

    @Override
    public boolean update(SteelGrade steelGrade) {
        return steelGradeMapper.update(steelGrade) > 0;
    }

    @Override
    public boolean deleteById(Integer id) {
        return steelGradeMapper.deleteById(id) > 0;
    }
}
