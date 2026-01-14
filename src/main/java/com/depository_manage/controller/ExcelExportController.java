package com.depository_manage.controller;

import com.depository_manage.service.ReportService;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.xssf.usermodel.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.core.io.ByteArrayResource;

import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.util.Map;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/export")
public class ExcelExportController {

    @Autowired
    private ReportService reportService;

    @GetMapping("/report")
    public ResponseEntity<ByteArrayResource> exportReportToExcel(
            @RequestParam("startDate") String startDate,
            @RequestParam("endDate") String endDate,
            @RequestParam("depositoryId") int depositoryId) throws Exception {
        List<Map<String, Object>> data = reportService.fetchReportData(startDate, endDate, depositoryId);
        XSSFWorkbook workbook = new XSSFWorkbook();
        XSSFSheet sheet = workbook.createSheet("Report");
// 创建单元格样式并设置边框
        XSSFCellStyle style = workbook.createCellStyle();
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
// 创建表头行并设置样式
        XSSFRow headerRow = sheet.createRow(0);
        createCellWithStyle(headerRow, 0, "分类", style);
        createCellWithStyle(headerRow, 1, "AT号", style);
        createCellWithStyle(headerRow, 2, "品名", style);
        createCellWithStyle(headerRow, 3, "规格", style);
        createCellWithStyle(headerRow, 4, "入库数量", style);
        createCellWithStyle(headerRow, 5, "入库金额", style);
        createCellWithStyle(headerRow, 6, "出库数量", style);
        createCellWithStyle(headerRow, 7, "出库金额", style);
        createCellWithStyle(headerRow, 8, "库存数量", style);
        createCellWithStyle(headerRow, 9, "在库金额", style);

        String previousCategory = "";  // 这个变量用于存储前一行的分类
        for (int i = 0; i < data.size(); i++) {
            Map<String, Object> record = data.get(i);
            XSSFRow row = sheet.createRow(i + 1);
            // 获取当前行的分类
            String currentCategory = (String) getOrDefault(record, "分类", "");

            // 检查当前行的分类是否与上一行的分类相同
            if(currentCategory.equals(previousCategory)) {
                // 如果相同，则在这一行的分类列中设置为空字符串
                createCellWithStyle(row, 0, "", style);
            } else {
                // 如果不同，则使用分类的名称，并更新previousCategory变量
                createCellWithStyle(row, 0, currentCategory, style);
                previousCategory = currentCategory;
            }
            // 其他列的处理保持不变
            createCellWithStyle(row, 1, ((Integer) getOrDefault(record, "AT号", 0)).intValue(), style);
            createCellWithStyle(row, 2, (String) getOrDefault(record, "品名", ""), style);
            createCellWithStyle(row, 3, (String) getOrDefault(record, "规格", ""), style);
            createCellWithStyle(row, 4, ((Double) getOrDefault(record, "入库数量", 0.0)).doubleValue(), style);
//            createCellWithStyle(row, 5, Double.parseDouble((String) getOrDefault(record, "入库金额", "0.00")), style);
            createCellWithStyle(row, 6 , ((Double) getOrDefault(record, "出库数量", 0.0)).doubleValue(), style);
//            createCellWithStyle(row, 7, Double.parseDouble((String) getOrDefault(record, "出库金额", "0.00")), style);
            createCellWithStyle(row, 8, ((Double) getOrDefault(record, "库存数量", 0.0)).doubleValue(), style);
//            createCellWithStyle(row, 9, Double.parseDouble((String) getOrDefault(record, "在库金额", "0.00")), style);
            createCellWithStyle(row, 5, Double.parseDouble(((String) getOrDefault(record, "入库金额", "0.00")).replace(",", "")), style);
            createCellWithStyle(row, 7, Double.parseDouble(((String) getOrDefault(record, "出库金额", "0.00")).replace(",", "")), style);
            createCellWithStyle(row, 9, Double.parseDouble(((String) getOrDefault(record, "在库金额", "0.00")).replace(",", "")), style);
        }

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        workbook.write(outputStream);
        workbook.close();

        String filename = String.format("物品库存报表_%s_to_%s.xlsx", startDate, endDate);
        String encodedFilename = URLEncoder.encode(filename, "UTF-8").replace("+", "%20");
        ByteArrayResource resource = new ByteArrayResource(outputStream.toByteArray());
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename*=UTF-8''" + encodedFilename)
                .body(resource);
    }
    private void createCellWithStyle(XSSFRow row, int column, String value, XSSFCellStyle style) {
        XSSFCell cell = row.createCell(column);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private void createCellWithStyle(XSSFRow row, int column, double value, XSSFCellStyle style) {
        XSSFCell cell = row.createCell(column);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private void createCellWithStyle(XSSFRow row, int column, int value, XSSFCellStyle style) {
        XSSFCell cell = row.createCell(column);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    @GetMapping("/every")
    public ResponseEntity<ByteArrayResource> everyTypeToExcel(
            @RequestParam("startDate") String startDate,
            @RequestParam("endDate") String endDate,
            @RequestParam("depositoryId") int depositoryId) throws Exception {
        List<Map<String, Object>> data = reportService.everyTypeData(startDate, endDate, depositoryId);
        XSSFWorkbook workbook = new XSSFWorkbook();
        XSSFSheet sheet = workbook.createSheet("Report");
        // Create header row
        XSSFRow headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("日期");
        headerRow.createCell(1).setCellValue("分类");
        headerRow.createCell(2).setCellValue("入库金额");
        headerRow.createCell(3).setCellValue("出库金额");
        headerRow.createCell(4).setCellValue("在库金额");
        for (int i = 0; i < data.size(); i++) {
            Map<String, Object> record = data.get(i);
            XSSFRow row = sheet.createRow(i + 1);
            row.createCell(0).setCellValue((String) getOrDefault(record, "日期", ""));
            row.createCell(1).setCellValue((String) getOrDefault(record, "分类", "aaa"));
            row.createCell(2).setCellValue((String) getOrDefault(record, "入库金额", "0.00"));
            row.createCell(3).setCellValue((String) getOrDefault(record, "出库金额", "0.00"));
            row.createCell(4).setCellValue((String) getOrDefault(record, "在库金额", "0.00"));
        }
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        workbook.write(outputStream);
        workbook.close();
        String filename = String.format("分类报表_%s_to_%s.xlsx", startDate, endDate);
        String encodedFilename = URLEncoder.encode(filename, "UTF-8").replace("+", "%20");
        ByteArrayResource resource = new ByteArrayResource(outputStream.toByteArray());
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename*=UTF-8''" + encodedFilename)
                .body(resource);
    }


    @GetMapping("/transfer")
    public ResponseEntity<ByteArrayResource> transferToExcel(
            @RequestParam("startDate") String startDate,
            @RequestParam("endDate") String endDate,
            @RequestParam("depositoryId") int depositoryId) throws Exception {
        List<Map<String, Object>> data = reportService.transferData(startDate, endDate);
        XSSFWorkbook workbook = new XSSFWorkbook();
        XSSFSheet sheet = workbook.createSheet("Report");
// 分割数据
        List<Map<String, Object>> zabToSabData = data.stream()
                .filter(record -> "ZAB转入SAB".equals(record.get("备注")))
                .collect(Collectors.toList());
        List<Map<String, Object>> sabToZabData = data.stream()
                .filter(record -> "SAB转入ZAB".equals(record.get("备注")))
                .collect(Collectors.toList());

// 创建 ZAB向SAB部分
        int currentRowIndex = 0;
        XSSFRow titleRowZabToSab = sheet.createRow(currentRowIndex++);
        titleRowZabToSab.createCell(0).setCellValue("SAB向ZAB借用物资清单");
//        currentRowIndex += 2; // 留空一行
        createHeaderRow(sheet, currentRowIndex++);
        currentRowIndex = fillDataRows(sheet, currentRowIndex, zabToSabData);
        currentRowIndex += 2; // 留空一行

// 创建 SAB向ZAB部分
        XSSFRow titleRowSabToZab = sheet.createRow(currentRowIndex++);
        titleRowSabToZab.createCell(0).setCellValue("ZAB向SAB借用物资清单");
//        currentRowIndex += 2; // 留空一行
        createHeaderRow(sheet, currentRowIndex++);
        fillDataRows(sheet, currentRowIndex, sabToZabData);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        workbook.write(outputStream);
        workbook.close();

        String filename = String.format("物品转移报表_%s_to_%s.xlsx", startDate, endDate);

        String encodedFilename = URLEncoder.encode(filename, "UTF-8").replace("+", "%20");
        ByteArrayResource resource = new ByteArrayResource(outputStream.toByteArray());
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename*=UTF-8''" + encodedFilename)
                .body(resource);
    }
    private void createHeaderRow(XSSFSheet sheet, int rowIndex) {
        XSSFRow headerRow = sheet.createRow(rowIndex);
        headerRow.createCell(0).setCellValue("日期");
        headerRow.createCell(1).setCellValue("分类");
        headerRow.createCell(2).setCellValue("品名");
        headerRow.createCell(3).setCellValue("型号");
        headerRow.createCell(4).setCellValue("单价");
        headerRow.createCell(5).setCellValue("数量");
        headerRow.createCell(6).setCellValue("总价");
        headerRow.createCell(7).setCellValue("备注");
    }

    private int fillDataRows(XSSFSheet sheet, int startingRowIndex, List<Map<String, Object>> data) {
        int rowIndex = startingRowIndex;
        for (Map<String, Object> record : data) {
            XSSFRow row = sheet.createRow(rowIndex++);
            row.createCell(0).setCellValue((String) getOrDefault(record, "日期", ""));
            row.createCell(1).setCellValue((String) getOrDefault(record, "分类", "aaa"));
            row.createCell(2).setCellValue((String) getOrDefault(record, "品名", "aaa"));
            row.createCell(3).setCellValue((String) getOrDefault(record, "型号", "aaa"));
            row.createCell(4).setCellValue((String) getOrDefault(record, "单价", "0.00"));
            row.createCell(5).setCellValue(((Double) getOrDefault(record, "数量", 0.0)).doubleValue());
            row.createCell(6).setCellValue((String) getOrDefault(record, "总价", "0.00"));
            row.createCell(7).setCellValue((String) getOrDefault(record, "备注", "aaa"));
        }
        return rowIndex;
    }

    private Object getOrDefault(Map<String, Object> map, String key, Object defaultValue) {
        return map.containsKey(key) && map.get(key) != null ? map.get(key) : defaultValue;
    }

}
