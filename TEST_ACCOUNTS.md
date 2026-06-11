# 🔐 Tài Khoản Test Để Đăng Nhập

## 📋 Tài Khoản Mặc Định

### ADMIN Account (Hệ Thống)
```
Username: admin
Password: admin123
Email:    admin@itx.local
Role:     ADMIN
```
**Quyền**: Quản lý toàn bộ hệ thống, xem tất cả requests từ mọi nhân viên

---

## 👥 Tài Khoản Test (Cần Chạy Migration)

### LEADER Accounts (Trưởng Phòng)

#### Leader 1 - IT Department
```
Username: leader1
Password: test123
Email:    leader1@itx.local
Role:     LEADER
Team:     3 nhân viên (employee1, employee2, employee3)
```
**Quyền**: Duyệt requests của team IT (employee1, employee2, employee3)

#### Leader 2 - HR Department
```
Username: leader2
Password: test123
Email:    leader2@itx.local
Role:     LEADER
Team:     2 nhân viên (employee4, employee5)
```
**Quyền**: Duyệt requests của team HR (employee4, employee5)

---

### EMPLOYEE Accounts (Nhân Viên)

#### Team IT (under leader1)
```
Username: employee1
Password: test123
Email:    employee1@itx.local
Full Name: Nguyễn Văn B
Role:     EMPLOYEE
Leader:   leader1
```

```
Username: employee2
Password: test123
Email:    employee2@itx.local
Full Name: Phạm Thị C
Role:     EMPLOYEE
Leader:   leader1
```

```
Username: employee3
Password: test123
Email:    employee3@itx.local
Full Name: Đỗ Minh D
Role:     EMPLOYEE
Leader:   leader1
```

#### Team HR (under leader2)
```
Username: employee4
Password: test123
Email:    employee4@itx.local
Full Name: Hoàng Văn F
Role:     EMPLOYEE
Leader:   leader2
```

```
Username: employee5
Password: test123
Email:    employee5@itx.local
Full Name: Vũ Thanh G
Role:     EMPLOYEE
Leader:   leader2
```

---

## 🚀 Cách Sử Dụng

### Bước 1: Đảm Bảo Database Đã Chạy Migration
Migration file `V13__add_test_users.sql` sẽ tự động chạy khi backend khởi động.

### Bước 2: Truy Cập Ứng Dụng
1. Mở trình duyệt
2. Truy cập: `http://localhost:5174` (hoặc `http://localhost:5173`)

### Bước 3: Đăng Nhập
Tại trang Login, nhập:
- **Username**: (chọn một từ danh sách trên)
- **Password**: `admin123` (cho admin) hoặc `test123` (cho leader/employee)

---

## 🔍 Kiểm Tra Chức Năng

### Test với Admin Account
```
1. Đăng nhập: admin / admin123
2. Vào: Admin > Yêu cầu (Requests)
3. Kỳ vọng: Xem TẤT CẢ requests từ mọi nhân viên
   - employee1, employee2, employee3 từ leader1
   - employee4, employee5 từ leader2
```

### Test với Leader Account
```
1. Đăng nhập: leader1 / test123
2. Vào: Dashboard > Duyệt Yêu cầu (Review Requests)
3. Kỳ vọng: Chỉ xem requests từ team của mình
   - employee1, employee2, employee3
   - KHÔNG thấy requests từ employee4, employee5 (team khác)
```

### Test với Employee Account
```
1. Đăng nhập: employee1 / test123
2. Có thể:
   - Check In / Check Out
   - Xem lịch sử chấm công (History)
   - Gửi Exception Request hoặc Adjustment Request
   - Xem trạng thái requests của mình
3. KHÔNG thể:
   - Duyệt requests
   - Quản lý hệ thống
```

---

## 🔐 Hiểu Về Role Và Permission

### ADMIN (System Administrator)
```
✅ Quản lý ca làm việc (Shifts)
✅ Quản lý IP hợp lệ (Valid IPs)
✅ Quản lý ngày lễ (Holidays)
✅ Xem tất cả chấm công (Attendance)
✅ Xem TẤT CẢ requests từ mọi nhân viên
✅ Xem Audit Logs
✅ Ghi đè chấm công (Override)
❌ Không check in/out
❌ Không gửi requests
```

### LEADER (Team Manager)
```
✅ Xem dashboard team
✅ Duyệt requests từ nhân viên dưới quyản
✅ Phê duyệt hoặc từ chối requests
❌ Quản lý hệ thống
❌ Xem requests từ teams khác
❌ Check in/out
❌ Gửi requests
```

### EMPLOYEE (Staff)
```
✅ Check In / Check Out
✅ Xem lịch sử chấm công cá nhân
✅ Gửi Exception Request
✅ Gửi Adjustment Request
✅ Xem trạng thái requests
❌ Duyệt requests
❌ Xem requests của người khác
❌ Quản lý hệ thống
```

---

## 💡 Scenario Test Thực Tế

### Scenario 1: Nhân viên Submit Exception Request
```
1. Đăng nhập: employee1 / test123
2. Check In và quên Check Out
3. Vào History → Tìm record INCOMPLETE
4. Click "Adjustment Request" → Đề xuất checkout time
5. Leader1 đăng nhập và xem request
6. Leader1 duyệt request
7. Check in lại employee1 → Attendance đã cập nhật
```

### Scenario 2: Admin Xem Tất Cả Requests
```
1. Đăng nhập: admin / admin123
2. Vào Admin > Yêu cầu
3. Thấy requests từ:
   - employee1, employee2, employee3 (team của leader1)
   - employee4, employee5 (team của leader2)
4. Admin có thể duyệt bất kỳ request nào
```

### Scenario 3: Leader Không Thấy Requests Từ Teams Khác
```
1. Đăng nhập: leader1 / test123
2. Vào Dashboard > Duyệt Yêu cầu
3. Chỉ thấy requests từ:
   - employee1, employee2, employee3
4. KHÔNG thấy requests từ employee4, employee5
   (Vì họ thuộc team của leader2)
```

---

## 🛠️ Nếu Muốn Thêm Tài Khoản Khác

### Cách 1: Thêm vào Migration File
1. Mở `backend/src/main/resources/db/migration/V13__add_test_users.sql`
2. Thêm INSERT statement tương tự
3. Restart backend

### Cách 2: Thêm Trực Tiếp Vào Database
Chạy SQL query:
```sql
INSERT INTO users (id, username, email, password_hash, full_name, role, is_active, leader_id)
VALUES (
    UUID(),
    'username_moi',
    'email@itx.local',
    '$2a$10$8P5/Q7.E.R5nJ9pYQ5R5He0PJpVpvTOv3Qr4pGzR7W4mK3A9L1tl2', -- password: test123
    'Full Name',
    'EMPLOYEE',  -- hoặc 'LEADER'
    TRUE,
    'leader-001'  -- nếu là EMPLOYEE, gán trưởng
);
```

---

## 🔑 Password Hash Reference

Nếu muốn tạo tài khoản mới với password khác, generate BCrypt hash:

```python
# Python example
import bcrypt
password = "your_password"
hashed = bcrypt.hashpw(password.encode(), bcrypt.gensalt(10))
print(hashed.decode())
```

```java
// Java example
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(10);
String hash = encoder.encode("your_password");
System.out.println(hash);
```

---

## ✅ Checklist Test

- [ ] Đăng nhập admin → Xem tất cả requests
- [ ] Đăng nhập leader1 → Xem chỉ team của mình
- [ ] Đăng nhập leader2 → Xem chỉ team của mình
- [ ] Đăng nhập employee1 → Check in/out
- [ ] Employee submit Exception Request
- [ ] Leader duyệt request
- [ ] Admin override attendance record
- [ ] Kiểm tra audit logs

---

## 🐛 Troubleshooting

### Lỗi: "Invalid username or password"
- Kiểm tra username/password có chính xác không
- Admin mặc định: `admin` / `admin123`
- Test users mặc định: `test123`

### Lỗi: "User not found"
- Migration file chưa chạy
- Kiểm tra backend logs xem V13 đã chạy chưa
- Restart backend

### Lỗi: "Cannot login as employee"
- Employee không cần quyền duyệt requests
- Chỉ có thể submit requests (Check In/Out, gửi Exception/Adjustment)

---

## 📞 Support

Nếu gặp vấn đề:
1. Kiểm tra backend logs
2. Đảm bảo database đã run migration
3. Xóa browser cache (Ctrl+Shift+Delete)
4. Restart frontend
