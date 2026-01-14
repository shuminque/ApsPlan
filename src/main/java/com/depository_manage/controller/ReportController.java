package com.depository_manage.controller;

import com.depository_manage.service.ReportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@RestController
@RequestMapping("/api")
public class ReportController {

    @Autowired
    private ReportService reportService;
    @GetMapping("/report")
    public List<Map<String, Object>> getReportData(
            @RequestParam(name = "startDate") String startDate,
            @RequestParam(name = "endDate") String endDate,
            @RequestParam(name = "depositoryId") int depositoryId) {
        return reportService.fetchReportData(startDate, endDate, depositoryId);
    }
    @GetMapping("/every")
    public List<Map<String, Object>> everyTypeData(
            @RequestParam(name = "startDate") String startDate,
            @RequestParam(name = "endDate") String endDate,
            @RequestParam(name = "depositoryId") int depositoryId) {
        return fetchData(startDate, endDate, date -> reportService.everyTypeData(date[0], date[1], depositoryId));
    }
    @GetMapping("/transfer")
    public List<Map<String, Object>> transferData(
            @RequestParam(name = "startDate") String startDate,
            @RequestParam(name = "endDate") String endDate) {
        return fetchData(startDate, endDate, date -> reportService.transferData(date[0], date[1]));
    }
    public List<Map<String, Object>> fetchData(String startDate, String endDate,
                                               Function<String[], List<Map<String, Object>>> dataFetcher) {
        return dataFetcher.apply(new String[]{startDate, endDate});
    }

    private int[] setCurrentYearAndMonth(int year, int month) {
        if (year == -1) {
            year = LocalDate.now().getYear();
        }
        if (month == -1) {
            month = LocalDate.now().getMonthValue();
        }
        return new int[]{year, month};
    }
}
