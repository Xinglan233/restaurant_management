package com.example.restaurant;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class DbService {
    private final JdbcTemplate jdbc;

    public DbService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Map<String, Object>> list(String sql) {
        return jdbc.queryForList(sql);
    }

    public Map<String, Object> login(Map<String, Object> body) {
        String username = text(body, "username");
        String password = text(body, "password");
        if (username.isBlank() || password.isBlank()) {
            throw new ApiException("请输入用户名和密码");
        }
        List<Map<String, Object>> users = jdbc.queryForList(
                "select id, username, real_name, role from users where username=? and password=?",
                username, passwordHash(password));
        if (users.isEmpty()) {
            throw new ApiException("用户名或密码错误");
        }
        return Map.of("success", true, "user", users.get(0));
    }

    public Map<String, Object> dashboard() {
        Map<String, Object> data = new HashMap<>();
        data.put("tableCount", oneInt("select count(*) from dining_tables"));
        data.put("emptyTableCount", oneInt("select count(*) from dining_tables where status='空'"));
        data.put("waitingCount", oneInt("select count(*) from queue_numbers where status='等待'"));
        data.put("todayRevenue", jdbc.queryForObject(
                "select ifnull(sum(pay_amount),0) from orders where status='已结账' and date(order_time)=curdate()",
                BigDecimal.class));
        data.put("hotDishes", jdbc.queryForList("select name, price from dishes where is_hot=1 and is_on_sale=1 order by id desc limit 5"));
        return data;
    }

    public void addStaff(Map<String, Object> body) {
        String username = text(body, "username");
        String password = textOr(body, "password", "123456");
        String realName = text(body, "real_name");
        String role = textOr(body, "role", "服务员");
        if (username.isBlank() || realName.isBlank()) {
            throw new ApiException("用户名和姓名不能为空");
        }
        if (!"店长".equals(role) && !"服务员".equals(role)) {
            throw new ApiException("角色只能是店长或服务员");
        }
        jdbc.update("insert into users(username,password,real_name,role) values(?,?,?,?)",
                username, passwordHash(password), realName, role);
    }

    public void updateStaff(int id, Map<String, Object> body) {
        String username = text(body, "username");
        String password = text(body, "password");
        String realName = text(body, "real_name");
        String role = textOr(body, "role", "服务员");
        if (username.isBlank() || realName.isBlank()) {
            throw new ApiException("用户名和姓名不能为空");
        }
        if (!"店长".equals(role) && !"服务员".equals(role)) {
            throw new ApiException("角色只能是店长或服务员");
        }
        if (password.isBlank()) {
            jdbc.update("update users set username=?, real_name=?, role=? where id=?",
                    username, realName, role, id);
        } else {
            jdbc.update("update users set username=?, password=?, real_name=?, role=? where id=?",
                    username, passwordHash(password), realName, role, id);
        }
    }

    public void deleteStaff(int id) {
        if (id == 1) {
            throw new ApiException("默认店长账号不能删除");
        }
        jdbc.update("delete from users where id=?", id);
    }

    public void addTable(Map<String, Object> body) {
        int seats = number(body, "seats");
        if (seats <= 0) {
            throw new ApiException("座位数必须大于 0");
        }
        String tableNo = nextTableNo(seats);
        jdbc.update("insert into dining_tables(table_no,seats,status) values(?,?,?)", tableNo, seats, textOr(body, "status", "空"));
    }

    public void updateTable(int id, Map<String, Object> body) {
        String tableNo = text(body, "table_no");
        int seats = number(body, "seats");
        String status = textOr(body, "status", "空");
        if (tableNo.isBlank() || seats <= 0) {
            throw new ApiException("餐桌号不能为空，座位数必须大于 0");
        }
        jdbc.update("update dining_tables set table_no=?, seats=?, status=? where id=?", tableNo, seats, status, id);
    }

    public void addCategory(Map<String, Object> body) {
        String name = text(body, "name");
        if (name.isBlank()) {
            throw new ApiException("分类名称不能为空");
        }
        jdbc.update("insert into dish_categories(name) values(?)", name);
    }

    public void addDish(Map<String, Object> body) {
        String dishNo = nextSimpleCode("D", "dishes", "dish_no");
        checkDish(body, false);
        jdbc.update("insert into dishes(dish_no,name,category_id,price,image_url,is_hot,is_on_sale) values(?,?,?,?,?,?,?)",
                dishNo, text(body, "name"), number(body, "category_id"),
                money(body, "price"), text(body, "image_url"), boolInt(body, "is_hot"), boolInt(body, "is_on_sale"));
    }

    public void updateDish(int id, Map<String, Object> body) {
        checkDish(body, true);
        jdbc.update("update dishes set dish_no=?, name=?, category_id=?, price=?, image_url=?, is_hot=?, is_on_sale=? where id=?",
                text(body, "dish_no"), text(body, "name"), number(body, "category_id"),
                money(body, "price"), text(body, "image_url"), boolInt(body, "is_hot"), boolInt(body, "is_on_sale"), id);
    }

    private void checkDish(Map<String, Object> body, boolean needDishNo) {
        if ((needDishNo && text(body, "dish_no").isBlank()) || text(body, "name").isBlank()) {
            throw new ApiException("菜名不能为空");
        }
        if (number(body, "category_id") <= 0 || money(body, "price").compareTo(BigDecimal.ZERO) < 0) {
            throw new ApiException("请选择分类，价格不能小于 0");
        }
    }

    public void addVip(Map<String, Object> body) {
        checkVip(body);
        jdbc.update("insert into vip_customers(name,phone,level_name,discount,points) values(?,?,?,?,?)",
                text(body, "name"), text(body, "phone"), textOr(body, "level_name", "普通VIP"),
                money(body, "discount"), numberOr(body, "points", 0));
    }

    public void updateVip(int id, Map<String, Object> body) {
        checkVip(body);
        jdbc.update("update vip_customers set name=?, phone=?, level_name=?, discount=?, points=? where id=?",
                text(body, "name"), text(body, "phone"), textOr(body, "level_name", "普通VIP"),
                money(body, "discount"), numberOr(body, "points", 0), id);
    }

    private void checkVip(Map<String, Object> body) {
        BigDecimal discount = money(body, "discount");
        if (text(body, "name").isBlank() || text(body, "phone").isBlank()) {
            throw new ApiException("VIP 姓名和手机号不能为空");
        }
        if (discount.compareTo(BigDecimal.ZERO) <= 0 || discount.compareTo(BigDecimal.ONE) > 0) {
            throw new ApiException("折扣必须大于 0 且小于等于 1，例如 0.95");
        }
    }

    public void deleteById(String table, int id) {
        jdbc.update("delete from " + table + " where id=?", id);
    }

    public Map<String, Object> takeNumber(Map<String, Object> body) {
        int peopleCount = number(body, "people_count");
        if (peopleCount <= 0) {
            throw new ApiException("用餐人数必须大于 0");
        }
        String queueNo = nextCode("Q", "queue_numbers", "queue_no");
        jdbc.update("insert into queue_numbers(queue_no, people_count) values(?,?)", queueNo, peopleCount);
        return Map.of("success", true, "message", "取号成功", "queue_no", queueNo);
    }

    public Map<String, Object> callNext() {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "select * from queue_numbers where status='等待' order by create_time limit 1");
        if (rows.isEmpty()) {
            throw new ApiException("当前没有等待顾客");
        }
        int id = ((Number) rows.get(0).get("id")).intValue();
        jdbc.update("update queue_numbers set status='已叫号', call_time=now() where id=?", id);
        return Map.of("success", true, "message", "叫号成功", "queue", rows.get(0));
    }

    @Transactional
    public void assignQueue(int id, Map<String, Object> body) {
        String queueStatus = jdbc.queryForObject("select status from queue_numbers where id=?", String.class, id);
        if (!"已叫号".equals(queueStatus)) {
            throw new ApiException("只有已叫号的排队号可以安排餐桌，取消后请重新取号");
        }
        int tableId = number(body, "table_id");
        if (tableId <= 0) {
            throw new ApiException("请选择餐桌");
        }
        String status = jdbc.queryForObject("select status from dining_tables where id=?", String.class, tableId);
        if (!"空".equals(status)) {
            throw new ApiException("只能安排空餐桌");
        }
        jdbc.update("update queue_numbers set status='已安排', table_id=? where id=?", tableId, id);
        jdbc.update("update dining_tables set status='占用' where id=?", tableId);
    }

    @Transactional
    public void cancelQueue(int id) {
        Map<String, Object> queue = jdbc.queryForMap("select status, table_id from queue_numbers where id=?", id);
        if ("已取消".equals(queue.get("status"))) {
            throw new ApiException("该排队号已经取消，请重新取号");
        }
        Object tableIdValue = queue.get("table_id");
        jdbc.update("update queue_numbers set status='已取消' where id=?", id);
        if (tableIdValue != null) {
            int tableId = ((Number) tableIdValue).intValue();
            Integer diningOrderCount = jdbc.queryForObject(
                    "select count(*) from orders where table_id=? and status='用餐中'",
                    Integer.class, tableId);
            if (diningOrderCount == null || diningOrderCount == 0) {
                jdbc.update("update dining_tables set status='空' where id=?", tableId);
            }
        }
    }

    public Map<String, Object> orderDetail(int id) {
        Map<String, Object> order = jdbc.queryForMap(
                "select o.*, t.table_no, ifnull(v.name,'临时顾客') customer_name from orders o join dining_tables t on o.table_id=t.id left join vip_customers v on o.vip_id=v.id where o.id=?",
                id);
        List<Map<String, Object>> items = jdbc.queryForList("select * from order_items where order_id=? order by id", id);
        return Map.of("order", order, "items", items);
    }

    @Transactional
    public Map<String, Object> createOrder(Map<String, Object> body) {
        int tableId = number(body, "table_id");
        if (tableId <= 0) {
            throw new ApiException("请选择餐桌");
        }
        if (!canCreateOrderForTable(tableId)) {
            throw new ApiException("该餐桌已被占用");
        }
        String orderNo = nextCode("OD", "orders", "order_no");
        jdbc.update("insert into orders(order_no, table_id) values(?,?)", orderNo, tableId);
        int orderId = jdbc.queryForObject("select id from orders where order_no=?", Integer.class, orderNo);
        List<?> items = (List<?>) body.get("items");
        if (items == null || items.isEmpty()) {
            throw new ApiException("请至少选择一个菜品");
        }
        for (Object item : items) {
            addOrderItem(orderId, castMap(item));
        }
        return Map.of("success", true, "message", "下单成功", "order_id", orderId, "order_no", orderNo);
    }

    @Transactional
    public void addOrderItem(int orderId, Map<String, Object> body) {
        String status = jdbc.queryForObject("select status from orders where id=?", String.class, orderId);
        if (!"用餐中".equals(status)) {
            throw new ApiException("只有用餐中的订单可以追加菜品");
        }
        int dishId = number(body, "dish_id");
        int quantity = numberOr(body, "quantity", 1);
        if (dishId <= 0 || quantity <= 0) {
            throw new ApiException("请选择菜品，数量必须大于 0");
        }
        Map<String, Object> dish = jdbc.queryForMap("select id,name,price,is_on_sale from dishes where id=?", dishId);
        if (((Number) dish.get("is_on_sale")).intValue() != 1) {
            throw new ApiException("该菜品已下架");
        }
        BigDecimal price = (BigDecimal) dish.get("price");
        BigDecimal subtotal = price.multiply(BigDecimal.valueOf(quantity));
        jdbc.update("insert into order_items(order_id,dish_id,dish_name,price,quantity,subtotal) values(?,?,?,?,?,?)",
                orderId, dishId, dish.get("name"), price, quantity, subtotal);
        updateOrderTotal(orderId);
    }

    @Transactional
    public Map<String, Object> checkout(int orderId, Map<String, Object> body) {
        String status = jdbc.queryForObject("select status from orders where id=?", String.class, orderId);
        if (!"用餐中".equals(status)) {
            throw new ApiException("该订单不能重复结账");
        }
        Integer vipId = nullableNumber(body, "vip_id");
        BigDecimal discount = BigDecimal.ONE;
        if (vipId != null && vipId > 0) {
            discount = jdbc.queryForObject("select discount from vip_customers where id=?", BigDecimal.class, vipId);
        } else {
            vipId = null;
        }
        updateOrderTotal(orderId);
        BigDecimal total = jdbc.queryForObject("select total_amount from orders where id=?", BigDecimal.class, orderId);
        BigDecimal pay = total.multiply(discount).setScale(2, BigDecimal.ROUND_HALF_UP);
        jdbc.update("update orders set vip_id=?, discount=?, pay_amount=?, status='已结账' where id=?",
                vipId, discount, pay, orderId);
        markHot(Map.of("top", 3));
        return Map.of("success", true, "message", "结账成功", "total_amount", total, "discount", discount, "pay_amount", pay);
    }

    public Map<String, Object> revenue(Map<String, Object> body) {
        String start = text(body, "start_time");
        String end = text(body, "end_time");
        if (start.isBlank() || end.isBlank()) {
            throw new ApiException("请选择开始时间和结束时间");
        }
        List<Map<String, Object>> details = jdbc.queryForList("call sp_revenue_between(?, ?)", start, end);
        Map<String, Object> summary = jdbc.queryForMap(
                "select count(*) order_count, ifnull(sum(total_amount),0) total_amount, ifnull(sum(pay_amount),0) pay_amount " +
                        "from orders where status='已结账' and order_time between ? and ?",
                start, end);
        return Map.of("details", details, "summary", summary);
    }

    public List<Map<String, Object>> dishSales(Map<String, Object> body) {
        String start = text(body, "start_time");
        String end = text(body, "end_time");
        if (start.isBlank() || end.isBlank()) {
            return jdbc.queryForList("select * from v_dish_sales order by sale_count desc, sale_amount desc");
        }
        return jdbc.queryForList(
                "select d.id dish_id,d.dish_no,d.name,c.name category_name,d.price,d.is_hot," +
                        "ifnull(sum(case when o.id is null then 0 else i.quantity end),0) sale_count," +
                        "ifnull(sum(case when o.id is null then 0 else i.subtotal end),0) sale_amount " +
                        "from dishes d join dish_categories c on d.category_id=c.id " +
                        "left join order_items i on d.id=i.dish_id " +
                        "left join orders o on i.order_id=o.id and o.status='已结账' and o.order_time between ? and ? " +
                        "group by d.id,d.dish_no,d.name,c.name,d.price,d.is_hot order by sale_count desc, sale_amount desc",
                start, end);
    }

    public void markHot(Map<String, Object> body) {
        int top = numberOr(body, "top", 3);
        jdbc.update("update dishes set is_hot=0");
        jdbc.update("update dishes set is_hot=1 where id in (select dish_id from (select dish_id from v_dish_sales order by sale_count desc, sale_amount desc limit ?) x)", top);
    }

    private void updateOrderTotal(int orderId) {
        jdbc.update("update orders set total_amount=(select ifnull(sum(subtotal),0) from order_items where order_id=?), pay_amount=(select ifnull(sum(subtotal),0) from order_items where order_id=?)*discount where id=?",
                orderId, orderId, orderId);
    }

    private String nextCode(String prefix, String table, String column) {
        String day = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String like = prefix + day + "%";
        Integer count = jdbc.queryForObject("select count(*) from " + table + " where " + column + " like ?", Integer.class, like);
        return prefix + day + String.format("%04d", count + 1);
    }

    private String nextSimpleCode(String prefix, String table, String column) {
        String like = prefix + "%";
        Integer count = jdbc.queryForObject("select count(*) from " + table + " where " + column + " like ?", Integer.class, like);
        return prefix + String.format("%03d", count + 1);
    }

    private String nextTableNo(int seats) {
        String prefix;
        if (seats <= 2) {
            prefix = "A";
        } else if (seats <= 4) {
            prefix = "B";
        } else {
            prefix = "C";
        }
        Integer maxNo = jdbc.queryForObject(
                "select ifnull(max(cast(substring(table_no, 2) as unsigned)), 0) from dining_tables where table_no like ?",
                Integer.class, prefix + "%");
        return prefix + String.format("%02d", maxNo + 1);
    }

    private boolean canCreateOrderForTable(int tableId) {
        String tableStatus = jdbc.queryForObject("select status from dining_tables where id=?", String.class, tableId);
        if ("空".equals(tableStatus)) {
            return true;
        }
        Integer assignedCount = jdbc.queryForObject(
                "select count(*) from queue_numbers q where q.table_id=? and q.status='已安排' " +
                        "and not exists (select 1 from orders o where o.table_id=q.table_id and o.status='用餐中')",
                Integer.class, tableId);
        return assignedCount != null && assignedCount > 0;
    }

    private int oneInt(String sql) {
        return jdbc.queryForObject(sql, Integer.class);
    }

    private String text(Map<String, Object> body, String key) {
        Object value = body.get(key);
        return value == null ? "" : value.toString().trim();
    }

    private String textOr(Map<String, Object> body, String key, String defaultValue) {
        String value = text(body, key);
        return value.isBlank() ? defaultValue : value;
    }

    private String passwordHash(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(password.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new ApiException("密码加密失败");
        }
    }

    private int number(Map<String, Object> body, String key) {
        return numberOr(body, key, 0);
    }

    private int numberOr(Map<String, Object> body, String key, int defaultValue) {
        Object value = body.get(key);
        if (value == null || value.toString().isBlank()) {
            return defaultValue;
        }
        return Integer.parseInt(value.toString());
    }

    private Integer nullableNumber(Map<String, Object> body, String key) {
        Object value = body.get(key);
        if (value == null || value.toString().isBlank()) {
            return null;
        }
        return Integer.parseInt(value.toString());
    }

    private BigDecimal money(Map<String, Object> body, String key) {
        Object value = body.get(key);
        if (value == null || value.toString().isBlank()) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(value.toString());
    }

    private int boolInt(Map<String, Object> body, String key) {
        Object value = body.get(key);
        if (value == null) {
            return 0;
        }
        if (value instanceof Boolean b) {
            return b ? 1 : 0;
        }
        return "1".equals(value.toString()) || "true".equalsIgnoreCase(value.toString()) ? 1 : 0;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object item) {
        return (Map<String, Object>) item;
    }
}
