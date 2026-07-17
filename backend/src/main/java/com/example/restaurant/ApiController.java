package com.example.restaurant;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = {"http://localhost:5173", "http://127.0.0.1:5173"})
public class ApiController {
    private final DbService db;
    private final AuthService authService;

    public ApiController(DbService db, AuthService authService) {
        this.db = db;
        this.authService = authService;
    }

    @ExceptionHandler(ApiException.class)
    public Map<String, Object> handleApiException(ApiException e) {
        return Map.of("success", false, "message", e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public Map<String, Object> handleException(Exception e) {
        return Map.of("success", false, "message", "操作失败：" + e.getMessage());
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, Object> body) {
        Map<String, Object> result = db.login(body);
        @SuppressWarnings("unchecked")
        Map<String, Object> user = (Map<String, Object>) result.get("user");
        return Map.of("success", true, "user", user, "token", authService.createToken(user));
    }

    @GetMapping("/dashboard")
    public Map<String, Object> dashboard() {
        return db.dashboard();
    }

    @GetMapping("/staff")
    public List<Map<String, Object>> staff() {
        return db.list("select id, username, real_name, role from users order by id");
    }

    @PostMapping("/staff")
    public Map<String, Object> addStaff(@RequestBody Map<String, Object> body) {
        db.addStaff(body);
        return ok("服务员添加成功");
    }

    @PutMapping("/staff/{id}")
    public Map<String, Object> updateStaff(@PathVariable int id, @RequestBody Map<String, Object> body) {
        db.updateStaff(id, body);
        return ok("员工修改成功");
    }

    @DeleteMapping("/staff/{id}")
    public Map<String, Object> deleteStaff(@PathVariable int id) {
        db.deleteStaff(id);
        return ok("员工删除成功");
    }

    @GetMapping("/tables")
    public List<Map<String, Object>> tables() {
        return db.list("select * from dining_tables order by table_no");
    }

    @PostMapping("/tables")
    public Map<String, Object> addTable(@RequestBody Map<String, Object> body) {
        db.addTable(body);
        return ok("餐桌添加成功");
    }

    @PutMapping("/tables/{id}")
    public Map<String, Object> updateTable(@PathVariable int id, @RequestBody Map<String, Object> body) {
        db.updateTable(id, body);
        return ok("餐桌修改成功");
    }

    @DeleteMapping("/tables/{id}")
    public Map<String, Object> deleteTable(@PathVariable int id) {
        db.deleteById("dining_tables", id);
        return ok("餐桌删除成功");
    }

    @GetMapping("/categories")
    public List<Map<String, Object>> categories() {
        return db.list("select * from dish_categories order by id");
    }

    @PostMapping("/categories")
    public Map<String, Object> addCategory(@RequestBody Map<String, Object> body) {
        db.addCategory(body);
        return ok("分类添加成功");
    }

    @GetMapping("/dishes")
    public List<Map<String, Object>> dishes() {
        return db.list("select d.*, c.name category_name from dishes d join dish_categories c on d.category_id=c.id order by d.id desc");
    }

    @GetMapping("/dishes/hot")
    public List<Map<String, Object>> hotDishes() {
        return db.list("select * from dishes where is_hot=1 and is_on_sale=1 order by id desc");
    }

    @PostMapping("/dishes")
    public Map<String, Object> addDish(@RequestBody Map<String, Object> body) {
        db.addDish(body);
        return ok("菜品添加成功");
    }

    @PutMapping("/dishes/{id}")
    public Map<String, Object> updateDish(@PathVariable int id, @RequestBody Map<String, Object> body) {
        db.updateDish(id, body);
        return ok("菜品修改成功");
    }

    @DeleteMapping("/dishes/{id}")
    public Map<String, Object> deleteDish(@PathVariable int id) {
        db.deleteById("dishes", id);
        return ok("菜品删除成功");
    }

    @GetMapping("/vips")
    public List<Map<String, Object>> vips() {
        return db.list("select * from vip_customers order by id desc");
    }

    @PostMapping("/vips")
    public Map<String, Object> addVip(@RequestBody Map<String, Object> body) {
        db.addVip(body);
        return ok("VIP 添加成功");
    }

    @PutMapping("/vips/{id}")
    public Map<String, Object> updateVip(@PathVariable int id, @RequestBody Map<String, Object> body) {
        db.updateVip(id, body);
        return ok("VIP 修改成功");
    }

    @DeleteMapping("/vips/{id}")
    public Map<String, Object> deleteVip(@PathVariable int id) {
        db.deleteById("vip_customers", id);
        return ok("VIP 删除成功");
    }

    @GetMapping("/queue")
    public List<Map<String, Object>> queue() {
        return db.list("select * from queue_numbers order by create_time desc");
    }

    @PostMapping("/queue/take")
    public Map<String, Object> takeNumber(@RequestBody Map<String, Object> body) {
        return db.takeNumber(body);
    }

    @PostMapping("/queue/call-next")
    public Map<String, Object> callNext() {
        return db.callNext();
    }

    @PostMapping("/queue/{id}/assign")
    public Map<String, Object> assignQueue(@PathVariable int id, @RequestBody Map<String, Object> body) {
        db.assignQueue(id, body);
        return ok("安排餐桌成功");
    }

    @PostMapping("/queue/{id}/cancel")
    public Map<String, Object> cancelQueue(@PathVariable int id) {
        db.cancelQueue(id);
        return ok("已取消排队");
    }

    @GetMapping("/orders")
    public List<Map<String, Object>> orders() {
        return db.list("select o.*, t.table_no, ifnull(v.name,'临时顾客') customer_name from orders o join dining_tables t on o.table_id=t.id left join vip_customers v on o.vip_id=v.id order by o.id desc");
    }

    @GetMapping("/orders/{id}")
    public Map<String, Object> orderDetail(@PathVariable int id) {
        return db.orderDetail(id);
    }

    @PostMapping("/orders")
    public Map<String, Object> createOrder(@RequestBody Map<String, Object> body) {
        return db.createOrder(body);
    }

    @PostMapping("/orders/{id}/items")
    public Map<String, Object> addOrderItem(@PathVariable int id, @RequestBody Map<String, Object> body) {
        db.addOrderItem(id, body);
        return ok("追加菜品成功");
    }

    @PostMapping("/orders/{id}/checkout")
    public Map<String, Object> checkout(@PathVariable int id, @RequestBody Map<String, Object> body) {
        return db.checkout(id, body);
    }

    @PostMapping("/reports/revenue")
    public Map<String, Object> revenue(@RequestBody Map<String, Object> body) {
        return db.revenue(body);
    }

    @PostMapping("/reports/dish-sales")
    public List<Map<String, Object>> dishSales(@RequestBody Map<String, Object> body) {
        return db.dishSales(body);
    }

    @PostMapping("/reports/mark-hot")
    public Map<String, Object> markHot(@RequestBody Map<String, Object> body) {
        db.markHot(body);
        return ok("热门菜标注成功");
    }

    private Map<String, Object> ok(String message) {
        return Map.of("success", true, "message", message);
    }
}
