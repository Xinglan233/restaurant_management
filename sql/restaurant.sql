CREATE DATABASE IF NOT EXISTS restaurant_management DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE restaurant_management;

DROP VIEW IF EXISTS v_order_detail;
DROP VIEW IF EXISTS v_dish_sales;
DROP VIEW IF EXISTS v_empty_tables;
DROP PROCEDURE IF EXISTS sp_revenue_between;
DROP TRIGGER IF EXISTS trg_order_insert_table_busy;
DROP TRIGGER IF EXISTS trg_order_update_table_empty;

DROP TABLE IF EXISTS order_items;
DROP TABLE IF EXISTS orders;
DROP TABLE IF EXISTS queue_numbers;
DROP TABLE IF EXISTS vip_customers;
DROP TABLE IF EXISTS dishes;
DROP TABLE IF EXISTS dish_categories;
DROP TABLE IF EXISTS dining_tables;
DROP TABLE IF EXISTS users;

CREATE TABLE users (
  id INT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(30) NOT NULL UNIQUE,
  password VARCHAR(64) NOT NULL,
  real_name VARCHAR(30) NOT NULL,
  role VARCHAR(20) NOT NULL DEFAULT '服务员',
  CHECK (role IN ('店长', '服务员'))
);

CREATE TABLE dining_tables (
  id INT PRIMARY KEY AUTO_INCREMENT,
  table_no VARCHAR(20) NOT NULL UNIQUE,
  seats INT NOT NULL,
  status VARCHAR(10) NOT NULL DEFAULT '空',
  CHECK (seats > 0),
  CHECK (status IN ('空', '占用'))
);

CREATE TABLE dish_categories (
  id INT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(40) NOT NULL UNIQUE
);

CREATE TABLE dishes (
  id INT PRIMARY KEY AUTO_INCREMENT,
  dish_no VARCHAR(30) NOT NULL UNIQUE,
  name VARCHAR(60) NOT NULL,
  category_id INT NOT NULL,
  price DECIMAL(10,2) NOT NULL DEFAULT 0,
  image_url VARCHAR(255),
  is_hot TINYINT NOT NULL DEFAULT 0,
  is_on_sale TINYINT NOT NULL DEFAULT 1,
  CHECK (price >= 0),
  FOREIGN KEY (category_id) REFERENCES dish_categories(id)
);

CREATE TABLE vip_customers (
  id INT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(40) NOT NULL,
  phone VARCHAR(20) NOT NULL UNIQUE,
  level_name VARCHAR(20) NOT NULL DEFAULT '普通VIP',
  discount DECIMAL(4,2) NOT NULL DEFAULT 1.00,
  points INT NOT NULL DEFAULT 0,
  CHECK (discount > 0 AND discount <= 1),
  CHECK (points >= 0)
);

CREATE TABLE queue_numbers (
  id INT PRIMARY KEY AUTO_INCREMENT,
  queue_no VARCHAR(30) NOT NULL UNIQUE,
  people_count INT NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT '等待',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  call_time DATETIME NULL,
  table_id INT NULL,
  CHECK (people_count > 0),
  CHECK (status IN ('等待', '已叫号', '已安排', '已取消')),
  FOREIGN KEY (table_id) REFERENCES dining_tables(id)
);

CREATE TABLE orders (
  id INT PRIMARY KEY AUTO_INCREMENT,
  order_no VARCHAR(40) NOT NULL UNIQUE,
  table_id INT NOT NULL,
  vip_id INT NULL,
  order_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  total_amount DECIMAL(10,2) NOT NULL DEFAULT 0,
  discount DECIMAL(4,2) NOT NULL DEFAULT 1.00,
  pay_amount DECIMAL(10,2) NOT NULL DEFAULT 0,
  status VARCHAR(20) NOT NULL DEFAULT '用餐中',
  CHECK (total_amount >= 0),
  CHECK (discount > 0 AND discount <= 1),
  CHECK (pay_amount >= 0),
  CHECK (status IN ('用餐中', '已结账', '已取消')),
  FOREIGN KEY (table_id) REFERENCES dining_tables(id),
  FOREIGN KEY (vip_id) REFERENCES vip_customers(id)
);

CREATE TABLE order_items (
  id INT PRIMARY KEY AUTO_INCREMENT,
  order_id INT NOT NULL,
  dish_id INT NOT NULL,
  dish_name VARCHAR(60) NOT NULL,
  price DECIMAL(10,2) NOT NULL,
  quantity INT NOT NULL DEFAULT 1,
  subtotal DECIMAL(10,2) NOT NULL,
  CHECK (price >= 0),
  CHECK (quantity > 0),
  CHECK (subtotal >= 0),
  FOREIGN KEY (order_id) REFERENCES orders(id),
  FOREIGN KEY (dish_id) REFERENCES dishes(id)
);

CREATE INDEX idx_orders_time ON orders(order_time);
CREATE INDEX idx_orders_status ON orders(status);
CREATE INDEX idx_order_items_dish ON order_items(dish_id);
CREATE INDEX idx_queue_status ON queue_numbers(status);

DELIMITER //
CREATE TRIGGER trg_order_insert_table_busy
AFTER INSERT ON orders
FOR EACH ROW
BEGIN
  UPDATE dining_tables SET status = '占用' WHERE id = NEW.table_id;
END //

CREATE TRIGGER trg_order_update_table_empty
AFTER UPDATE ON orders
FOR EACH ROW
BEGIN
  IF NEW.status = '已结账' AND OLD.status <> '已结账' THEN
    UPDATE dining_tables SET status = '空' WHERE id = NEW.table_id;
  END IF;
END //
DELIMITER ;

CREATE VIEW v_empty_tables AS
SELECT id, table_no, seats, status
FROM dining_tables
WHERE status = '空';

CREATE VIEW v_order_detail AS
SELECT
  o.id AS order_id,
  o.order_no,
  t.table_no,
  o.order_time,
  o.status,
  IFNULL(v.name, '临时顾客') AS customer_name,
  o.total_amount,
  o.discount,
  o.pay_amount,
  i.dish_name,
  i.price,
  i.quantity,
  i.subtotal
FROM orders o
JOIN dining_tables t ON o.table_id = t.id
LEFT JOIN vip_customers v ON o.vip_id = v.id
LEFT JOIN order_items i ON o.id = i.order_id;

CREATE VIEW v_dish_sales AS
SELECT
  d.id AS dish_id,
  d.dish_no,
  d.name,
  c.name AS category_name,
  d.price,
  d.is_hot,
  IFNULL(SUM(CASE WHEN o.id IS NULL THEN 0 ELSE i.quantity END), 0) AS sale_count,
  IFNULL(SUM(CASE WHEN o.id IS NULL THEN 0 ELSE i.subtotal END), 0) AS sale_amount
FROM dishes d
JOIN dish_categories c ON d.category_id = c.id
LEFT JOIN order_items i ON d.id = i.dish_id
LEFT JOIN orders o ON i.order_id = o.id AND o.status = '已结账'
GROUP BY d.id, d.dish_no, d.name, c.name, d.price, d.is_hot;

DELIMITER //
CREATE PROCEDURE sp_revenue_between(IN start_time DATETIME, IN end_time DATETIME)
BEGIN
  SELECT
    o.order_no,
    t.table_no,
    o.order_time,
    IFNULL(v.name, '临时顾客') AS customer_name,
    o.total_amount,
    o.discount,
    o.pay_amount
  FROM orders o
  JOIN dining_tables t ON o.table_id = t.id
  LEFT JOIN vip_customers v ON o.vip_id = v.id
  WHERE o.status = '已结账'
    AND o.order_time BETWEEN start_time AND end_time
  ORDER BY o.order_time DESC;

  SELECT
    COUNT(*) AS order_count,
    IFNULL(SUM(total_amount), 0) AS total_amount,
    IFNULL(SUM(pay_amount), 0) AS pay_amount
  FROM orders
  WHERE status = '已结账'
    AND order_time BETWEEN start_time AND end_time;
END //
DELIMITER ;

INSERT INTO users(username, password, real_name, role) VALUES
('admin', '8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92', '店长', '店长'),
('waiter', '8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92', '服务员小王', '服务员');

INSERT INTO dining_tables(table_no, seats, status) VALUES
('A01', 2, '空'),
('A02', 2, '空'),
('B01', 4, '空'),
('B02', 4, '空'),
('C01', 6, '空'),
('C02', 8, '空');

INSERT INTO dish_categories(name) VALUES
('热菜'), ('凉菜'), ('主食'), ('饮品');

INSERT INTO dishes(dish_no, name, category_id, price, image_url, is_hot) VALUES
('D001', '小笼包', 3, 28.00, 'https://images.unsplash.com/photo-1563245372-f21724e3856d?w=300', 1),
('D002', '海鲜烩饭', 3, 36.00, 'https://images.unsplash.com/photo-1512058564366-18510be2db19?w=300', 0),
('D003', '田园蔬菜沙拉', 2, 22.00, 'https://images.unsplash.com/photo-1540420773420-3366772f4999?w=300', 0),
('D004', '鸡蛋炒饭', 3, 24.00, 'https://images.unsplash.com/photo-1603133872878-684f208fb84b?w=300', 1),
('D005', '柠檬水果鸡尾酒', 4, 18.00, 'https://images.unsplash.com/photo-1544145945-f90425340c7e?w=300', 0);

INSERT INTO vip_customers(name, phone, level_name, discount, points) VALUES
('张三', '13800000001', '银卡VIP', 0.95, 120),
('李四', '13800000002', '金卡VIP', 0.90, 300);
