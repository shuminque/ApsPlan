package com.depository_manage.service.aps;

import com.depository_manage.pojo.shift.OrderSchedulingEvaluationDTO;

import java.time.LocalDate;

public interface OrderSchedulingEvaluationService {

    OrderSchedulingEvaluationDTO evaluate(String model, String craft, Integer quantity, LocalDate deliveryDate);
}
