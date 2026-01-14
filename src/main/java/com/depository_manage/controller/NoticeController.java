package com.depository_manage.controller;

import com.depository_manage.entity.Notice;
import com.depository_manage.pojo.RestResponse;
import com.depository_manage.service.NoticeService;
import com.depository_manage.utils.CrudUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
public class NoticeController {
    @Autowired
    private NoticeService noticeService;
    @PostMapping("/notice")
    public RestResponse addNotice(@RequestBody Map<String,Object> map){
        return CrudUtil.postHandle(noticeService.addNotice(map),1);
    }
//    @GetMapping("/notices")
//    public RestResponse findNotices(@RequestParam Map<String,Object> map){
//        return new RestResponse(noticeService.findNoticeByCondition(map));
//    }
    @GetMapping("/notices")
    public RestResponse findNotices(@RequestParam Map<String,Object> map){
        List<Notice> notices = noticeService.findNoticeByCondition(map);
        int count = notices.size(); // 或者获取实际的数据总数
        return new RestResponse(200, "", count, notices);
    }


    @DeleteMapping("/notice/{id}")
    public RestResponse deleteNotice(@PathVariable("id") Integer id) {
        return CrudUtil.deleteHandle(noticeService.deleteNoticeById(id), 1);
    }
}
