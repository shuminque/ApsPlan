package com.depository_manage.controller;

import com.depository_manage.security.bean.UserToken;
import com.depository_manage.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

@Controller
public class PageController {

    @Autowired
    private DepositoryService depositoryService;
    @Autowired
    private UserService userService;
    @Autowired
    private MaterialService materialService;
    @Autowired
    private DepositoryRecordService depositoryRecordService;
    @Autowired
    private NoticeService noticeService;
    @Autowired
    private MaterialEnginService materialEnginService;
    @Autowired
    private MaterialTypeService materialTypeService;
    @Autowired
    private CategoryService categoryService;
    @Autowired
    private ProductCategoryService productCategoryService;
    @Autowired
    private SteelGradeService steelGradeService;
    @Autowired
    private SteelTypeService steelTypeService;
    @Autowired
    private CustomerService customerService;
    @Autowired
    private TradeModeService  tradeModeService;
    @Autowired
    private SteelSizeService  steelSizeService;
    @GetMapping("/login")
    public String login() {
        return "pages/user/login";
    }
    @GetMapping("/s1")
    public String s1() {
        return "pages/qr/saoma1";
    }
    @GetMapping("/index")
    public ModelAndView index(HttpServletRequest request) {
        ModelAndView mv = new ModelAndView();
        UserToken userToken = (UserToken) request.getAttribute("userToken");
        mv.addObject("uname", userToken.getUser().getUname());
        mv.setViewName("index");
        return mv;
    }
    @GetMapping("/BearingStorage-in")
    public ModelAndView BearingStorageIn() {
        ModelAndView mv = new ModelAndView();
        mv.setViewName("pages/BearingStorage/BearingStorage-in");
//        mv.addObject("depositories", depositoryService.findDepositoryAll());
//        mv.addObject("reviewers", userService.findReviewers());
        return mv;
    }
    @GetMapping("/BearingStorage-out")
    public ModelAndView BearingStorageOut() {
        ModelAndView mv = new ModelAndView();mv.setViewName("pages/BearingStorage/BearingStorage-out");return mv;
    }
    @GetMapping("/BearingStorage-return")
    public ModelAndView BearingStorageReturn() {
        ModelAndView mv = new ModelAndView();mv.setViewName("pages/BearingStorage/BearingStorage-return");return mv;
    }
    @GetMapping("/BearingStorage-tranIn")
    public ModelAndView BearingStorageTranIn() {
        ModelAndView mv = new ModelAndView();mv.setViewName("pages/BearingStorage/BearingStorage-tranIn");return mv;
    }
    @GetMapping("/BearingStorage-tranOut")
    public ModelAndView BearingStorageTranOut() {
        ModelAndView mv = new ModelAndView();mv.setViewName("pages/BearingStorage/BearingStorage-tranOut");return mv;
    }
    @GetMapping("/Inquire-all")
    public ModelAndView InquireAll() {
        ModelAndView mv = new ModelAndView();mv.setViewName("pages/Inquire/Inquire-all");
        mv.addObject("productCategorys", productCategoryService.findAll());
        mv.addObject("steelGrades", steelGradeService.findAll());
        mv.addObject("steelTypes", steelTypeService.findAll());
        mv.addObject("customers", customerService.findAll());
        mv.addObject("steelSizes", steelSizeService.findAll());

        return mv;
    }
    @GetMapping("/Inquire-dissolve")
    public ModelAndView InquireDissolve() {
        ModelAndView mv = new ModelAndView();mv.setViewName("pages/Inquire/Inquire-dissolve");
        mv.addObject("productCategorys", productCategoryService.findAll());
        mv.addObject("steelGrades", steelGradeService.findAll());
        mv.addObject("steelTypes", steelTypeService.findAll());
        mv.addObject("customers", customerService.findAll());

        return mv;
    }
    @GetMapping("/GenerateAndPrintQR")
    public ModelAndView GenerateAndPrintQR() {
        ModelAndView mv = new ModelAndView();
        mv.setViewName("pages/Inquire/GenerateAndPrintQR");return mv;
    }
    @GetMapping("/Inquire-stock")
    public ModelAndView InquireStock() {
        ModelAndView mv = new ModelAndView();
        mv.setViewName("pages/Inquire/Inquire-stock");
        mv.addObject("productCategorys", productCategoryService.findAll());
        mv.addObject("steelGrades", steelGradeService.findAll());
        mv.addObject("steelTypes", steelTypeService.findAll());
        mv.addObject("customers", customerService.findAll());

        return mv;
    }
    @GetMapping("/bearings")
    public ModelAndView Bearings() {
        ModelAndView mv = new ModelAndView();
        mv.setViewName("pages/basicdata/bearings");
        return getModelAndView(mv);
    }
    @GetMapping("/bearing-ids")
    public ModelAndView ProductIds() {
        ModelAndView mv = new ModelAndView();
        mv.setViewName("pages/basicdata/bearing-ids");
        return mv;
    }
    @GetMapping("/bearing-category")
    public ModelAndView BearingCategory() {
        ModelAndView mv = new ModelAndView();
        mv.setViewName("pages/basicdata/bearing-category");return mv;
    }
    @GetMapping("/bearing-steel-grade")
    public ModelAndView steelGrade() {
        ModelAndView mv = new ModelAndView();
        mv.setViewName("pages/basicdata/bearing-steel-grade");return mv;
    }
    @GetMapping("/bearing-steel-type")
    public ModelAndView steelType() {
        ModelAndView mv = new ModelAndView();
        mv.setViewName("pages/basicdata/bearing-steel-type");return mv;
    }
    @GetMapping("/bearing-steel-size")
    public ModelAndView steelSize() {
        ModelAndView mv = new ModelAndView();
        mv.setViewName("pages/basicdata/bearing-steel-size");return mv;
    }
    @GetMapping("/trade-mode")
    public ModelAndView tradeMode() {
        ModelAndView mv = new ModelAndView();
        mv.setViewName("pages/basicdata/trade-mode");return mv;
    }
    @GetMapping("/customer-management")
    public ModelAndView customerManagement() {
        ModelAndView mv = new ModelAndView();
        mv.setViewName("pages/basicdata/customer-management");return mv;
    }
    @GetMapping("/every_pair")
    public String every_pair() {
        return "pages/chart/every_pair";
    }
    @GetMapping("/compre_transfer")
    public String compre_transfer() {
        return "pages/chart/compre_transfer";
    }
//
//    @GetMapping("/register")
//    public String register() {
//        return "pages/user/register";
//    }
//    public String formatWithCommas(BigDecimal number) {
//        NumberFormat numberFormat = NumberFormat.getInstance();
//        return numberFormat.format(number);
//    }


    @GetMapping("/welcome")
    public ModelAndView welcome() {
        ModelAndView mv = new ModelAndView();
        mv.setViewName("014/index");
        mv.addObject("customers", customerService.findAll());
        return mv;
    }
    @GetMapping("/calendar")
    public ModelAndView calendar() {
        ModelAndView mv = new ModelAndView();
        mv.setViewName("014/calendar");
        mv.addObject("customers", customerService.findAll());
        return mv;
    }
    @GetMapping("/productionOrder")
    public ModelAndView productionOrder() {
        ModelAndView mv = new ModelAndView();
        mv.setViewName("014/productionOrder");
        return getModelAndView(mv);
    }

    private ModelAndView getModelAndView(ModelAndView mv) {
        mv.addObject("productCategorys", productCategoryService.findAll());
        mv.addObject("steelGrades", steelGradeService.findAll());
        mv.addObject("steelTypes", steelTypeService.findAll());
        mv.addObject("customers", customerService.findAll());
        mv.addObject("tradeModes", tradeModeService.findAll());
        mv.addObject("steelSizes", steelSizeService.findAll());
        return mv;
    }

    @GetMapping("/production_order_list")
    public ModelAndView production_order_list() {
        ModelAndView mv = new ModelAndView();
        mv.setViewName("calendorder/production_order_list");
        return getModelAndView(mv);
    }
    @GetMapping("/production_line")
    public ModelAndView production_line() {
        ModelAndView mv = new ModelAndView();
        mv.setViewName("calendorder/production_line");
        return getModelAndView(mv);
    }

    @GetMapping("/production_line_model_config")
    public ModelAndView productionLineModelConfig() {
        ModelAndView mv = new ModelAndView();
        mv.setViewName("calendorder/production_line_model_config");
        return mv;
    }

    @GetMapping("/production_line_runtime")
    public ModelAndView productionLineRuntime() {
        ModelAndView mv = new ModelAndView();
        mv.setViewName("calendorder/production_line_runtime");
        return mv;
    }

    @GetMapping("/production_line_history")
    public ModelAndView productionLineHistory() {
        ModelAndView mv = new ModelAndView();
        mv.setViewName("calendorder/production_line_history");
        return mv;
    }

    @GetMapping("/safety_stock")
    public ModelAndView safety_stock() {
        ModelAndView mv = new ModelAndView();
        mv.setViewName("calendorder/safety_stock");
        return getModelAndView(mv);
    }
    @GetMapping("/welcome1")
    public ModelAndView welcome(HttpServletRequest request) {
        ModelAndView mv = new ModelAndView();
        mv.setViewName("pages/other/welcome");
        Map<String, Object> map = new HashMap<String, Object>(2) {
            {
                put("begin", 0);
                put("size",6);
            }
        };
        UserToken userToken = (UserToken) request.getAttribute("userToken");
//        if (userToken != null && userToken.getUser() != null) {
//            int userDepositoryId = userToken.getUser().getDepositoryId();
//            if (userDepositoryId != 0) {
//                // 如果用户的depository_id不为0，则根据depository_id过滤公告
//                map.put("depositoryId", userDepositoryId);
//            }
//            // 如果用户的depository_id为0，则不对公告进行厂区过滤
//            List<Notice> notices = noticeService.findNoticeByCondition(map);
//            mv.addObject("notices", notices);
//        }
//        mv.addObject("depositories", depositoryService.findDepositoryAll());
//        mv.addObject("materials", materialService.findMaterialAll());
//        mv.addObject("materialTypes", materialTypeService.findMaterialTypeAll());

//        DecimalFormat df = new DecimalFormat("#,##0"); // 这会格式化数字为千分位格式，并且始终有两位小数
//        BigDecimal sabPriceSum = materialService.findSABpriceSum();
//        BigDecimal zabPriceSum = materialService.findZABpriceSum();
//        mv.addObject("SABpriceSum", df.format(sabPriceSum));
//        mv.addObject("ZABpriceSum", df.format(zabPriceSum));
//        mv.addObject("SABcountSum", materialService.findSABcountSum());
//        mv.addObject("ZABcountSum", materialService.findZABcountSum());
        // 添加 materials 的总数
//        int count = materialService.findCount();
//        mv.addObject("count", count);
        return mv;
    }
//
//
    @GetMapping("/depository_add")
    public String depository_add() {
        return "pages/other/depository_add";
    }
//
//    @GetMapping("/materialType_add")
//    public String materialType_add() {
//        return "pages/other/materialType_add";
//    }
//
//    @GetMapping("/materialEngin_add")
//    public String materialEngin_add() {
//        return "pages/other/materialEngin_add";
//    }
//    @GetMapping("/total_table")
//    public String total_table() {
//        return "pages/chart/total_table";
//    }
//    @GetMapping("/every_type")
//    public String every_type() {
//        return "pages/chart/every_type";
//    }
//    @GetMapping("/transfer_table")
//    public String transfer_table() {
//        return "pages/chart/transfer_table";
//    }
//    @GetMapping("/rate_add")
//    public String rate_add() {
//        return "pages/other/rate_add";
//    }
//    @GetMapping("/productInfo_add")
//    public String productInfo_add() {
//        return "pages/other/productInfo_add";
//    }
//
//    @GetMapping("/dropData_add")
//    public String dropData_add() {
//        return "pages/other/dropData_add";
//    }
//    @GetMapping("/saoma")
//    public String saoma() {
//        return "pages/other/saoma";
//    }
//    @GetMapping("/once_add")
//    public ModelAndView once_add() {
//        ModelAndView mv = new ModelAndView();
//        mv.setViewName("pages/other/once_add");
//        mv.addObject("depositories", depositoryService.findDepositoryAll());
//        mv.addObject("materialTypes", materialTypeService.findMaterialTypeAll());
//        mv.addObject("materialEngins", materialEnginService.findMaterialEnginAll());
//        return mv;
//    }
//    @GetMapping("/once_table")
//    public ModelAndView once_table() {
//        ModelAndView mv = new ModelAndView();
//        mv.setViewName("pages/chart/once_table");
//        mv.addObject("depositories", depositoryService.findDepositoryAll());
//        mv.addObject("materialTypes", materialTypeService.findMaterialTypeAll());
//        mv.addObject("materialEngins", materialEnginService.findMaterialEnginAll());
//        return mv;
//    }
//
//    @GetMapping("/material_add")
//    public ModelAndView material_add() {
//        ModelAndView mv = new ModelAndView();
//        mv.setViewName("pages/other/material_add");
//        mv.addObject("depositories", depositoryService.findDepositoryAll());
//        mv.addObject("materialTypes", materialTypeService.findMaterialTypeAll());
//        mv.addObject("materialEngins", materialEnginService.findMaterialEnginAll());
//        return mv;
//    }
//
    @GetMapping("/application_in")
    public ModelAndView application_in() {
        ModelAndView mv = new ModelAndView();
        mv.setViewName("pages/application/application-in");
//        mv.addObject("depositories", depositoryService.findDepositoryAll());
//        mv.addObject("reviewers", userService.findReviewers());
        return mv;
    }
//
//    @GetMapping("/application_out")
//    public ModelAndView application_out() {
//        ModelAndView mv = new ModelAndView();
//        mv.setViewName("pages/application/application-out");
//        mv.addObject("depositories", depositoryService.findDepositoryAll());
//        mv.addObject("reviewers", userService.findReviewers());
//        return mv;
//    }
//
//    @GetMapping("/application_transfer")
//    public ModelAndView application_transfer() {
//        ModelAndView mv = new ModelAndView();
//        mv.setViewName("pages/application/application-transfer");
//        mv.addObject("depositories", depositoryService.findDepositoryAll());
//        mv.addObject("reviewers", userService.findReviewers());
//        return mv;
//    }
//
//    @GetMapping("/table_in")
//    public ModelAndView table_in() {
//        ModelAndView mv = new ModelAndView();
//        mv.setViewName("pages/depository/table-in");
//        mv.addObject("depositories", depositoryService.findDepositoryAll());
//        return mv;
//    }
//
//    @GetMapping("/table_out")
//    public ModelAndView table_out(HttpServletRequest request) {
//        ModelAndView mv = new ModelAndView();
//        mv.setViewName("pages/depository/table-out");
//
//        mv.addObject("depositories", depositoryService.findDepositoryAll());
//        mv.addObject("materialTypes", materialTypeService.findMaterialTypeAll());
//
//        // 获取用户信息
//        UserToken userToken = (UserToken) request.getAttribute("userToken");
//        // 确保userToken和user对象不为null
//        if (userToken != null && userToken.getUser() != null) {
//            int userDepositoryId = userToken.getUser().getDepositoryId();
//            // 根据 userDepositoryId 决定使用哪个 categories 列表
//            List<Category> categories;
//            if (userDepositoryId == 1) {
//                categories = categoryService.getSABCategories();
//            } else if (userDepositoryId == 2) {
//                categories = categoryService.getZABCategories();
//            } else {
//                categories = categoryService.getAllCategories();
//            }
//            mv.addObject("cas", categories);
//        } else {
//            // 处理 userToken 或 user 对象为 null 的情况，你可能需要重定向到登录页面或显示错误消息
//        }
//        return mv;
//    }
//
//
    @GetMapping("/table_user")
    public ModelAndView table_user() {
        ModelAndView mv = new ModelAndView();
        mv.setViewName("pages/user/table-user");
        mv.addObject("depositories", depositoryService.findDepositoryAll());
        return mv;
    }
//
//    @GetMapping("/table_stock")
//    public ModelAndView table_stock() {
//        ModelAndView mv = new ModelAndView();
//        mv.setViewName("pages/depository/table-stock");
//        mv.addObject("depositories", depositoryService.findDepositoryAll());
//        mv.addObject("materialTypes",materialTypeService.findMaterialTypeAll());
//        return mv;
//    }
//
//    @GetMapping("/my_task")
//    public String my_task() {
//        return "pages/application/my-task";
//    }
//
//    @GetMapping("/my_apply")
//    public String my_apply() {
//        return "pages/application/my-apply";
//    }
//
//    @GetMapping("/notice_edit")
//    public String notice_edit() {
//        return "pages/other/notice-edit";
//    }
//
//    @GetMapping("/chart_in")
//    public String chart_in() {
//        return "pages/chart/chart-in";
//    }
//
//    @GetMapping("/chart_out")
//    public String chart_out() {
//        return "pages/chart/chart-out";
//    }
//
//    @GetMapping("/chart_stock")
//    public String chart_stock() {
//        return "pages/chart/chart-stock";
//    }
//
//    @GetMapping("/user_password")
//    public String user_password() {
//        return "pages/user/user-password";
//    }
//
    @GetMapping("/user_add")
    public ModelAndView user_add() {
        ModelAndView mv = new ModelAndView();
        mv.setViewName("pages/user/user-add");
        mv.addObject("depositories", depositoryService.findDepositoryAll());
        return mv;
    }

    @GetMapping("/user_edit")
    public ModelAndView user_edit(Integer id) {
        ModelAndView mv = new ModelAndView();
        mv.setViewName("pages/user/user-edit");
        mv.addObject("depositories", depositoryService.findDepositoryAll());
        mv.addObject("user", userService.findUserPById(id));
        return mv;
    }
//
//    @GetMapping("/form_step_look")
//    public ModelAndView form_step_look(Integer id) {
//        ModelAndView mv = new ModelAndView();
//        mv.setViewName("pages/application/form-step-look");
//        if (id != null) {
//            mv.addObject("record", depositoryRecordService.findDepositoryRecordById(id));
//        } else {
//            throw new MyException("缺少必要参数！");
//        }
//        return mv;
//    }
//
//    @GetMapping("/application_review")
//    public ModelAndView application_review(Integer id) {
//        ModelAndView mv = new ModelAndView();
//        mv.setViewName("pages/application/application-review");
//        DepositoryRecordP recordP = depositoryRecordService.findDepositoryRecordById(id);
//        mv.addObject("record", recordP);
//        mv.addObject("checkers", userService.findUsersByDepositoryId(recordP.getDepositoryId()));
//        return mv;
//    }
//
//    @GetMapping("/account_look")
//    public ModelAndView account_look(Integer id, HttpServletRequest request) {
//        ModelAndView mv = new ModelAndView();
//        mv.setViewName("pages/user/account-look");
//        UserToken userToken = (UserToken) request.getAttribute("userToken");
//        mv.addObject("user", userToken.getUser());
//        return mv;
//    }
//
//    @GetMapping("/user_email")
//    public ModelAndView user_email(HttpServletRequest request) {
//        ModelAndView mv = new ModelAndView();
//        mv.setViewName("pages/user/user-email");
//        UserToken userToken = (UserToken) request.getAttribute("userToken");
//        mv.addObject("email", userToken.getUser().getEmail());
//        return mv;
//    }
}
