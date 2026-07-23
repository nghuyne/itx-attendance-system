# 🔐 Tài Khoản Test Để Đăng Nhập

## 📋 Tài Khoản Mặc Định

### ADMIN Account (Hệ Thống)
```
Username: admin
Email:    admin@itx.local
Role:     ADMIN
```
**Quyền**: Quản lý toàn bộ hệ thống, xem tất cả requests từ mọi nhân viên

⚠️ **Không còn password cố định.** Từ commit `b23bcdb` (fix bảo mật, 2026-07-13),
`admin` không được insert sẵn bằng migration nữa. `AdminUserSeeder`
(`backend/src/main/java/com/itx/attendance/config/AdminUserSeeder.java`) tự tạo
tài khoản này **một lần duy nhất** khi backend khởi động lần đầu trên DB trống:
- Nếu env var `ADMIN_BOOTSTRAP_PASSWORD` được set (xem `.env.example`) → dùng
  password đó.
- Nếu không set → sinh password ngẫu nhiên bằng `SecureRandom`, chỉ log **một
  lần** ra console lúc khởi động (dòng `Seeded default admin account with a
  generated one-time password: ...`) rồi không lưu lại ở đâu khác.
- Tài khoản luôn được tạo với `must_change_password = true`.

Nếu bạn cần password `admin123` cho môi trường dev/local:
1. **Trước khi backend chạy lần đầu** (DB rỗng, chưa có row `admin`): set
   `ADMIN_BOOTSTRAP_PASSWORD=admin123` trong `.env` rồi khởi động.
2. **Nếu `admin` đã tồn tại và bạn không biết password hiện tại** (log gốc đã
   mất, hoặc ai đó đã đổi password qua flow force-change): không có cách khôi
   phục từ log — phải reset trực tiếp trong DB:
   ```bash
   docker exec itx-mysql-1 mysql -uroot -p<DB_ROOT_PASSWORD> itx_attendance -e \
     "UPDATE users SET password_hash='\$2a\$10\$XjCq.Z6n1spYh4K0Ac14HOnbegYb9HnaB1MbFCT7/qFic1/nUO7yK', must_change_password=1 WHERE username='admin';"
   ```
   (hash trên là BCrypt của `admin123`, cùng hash dùng cho các test user bên dưới.)

---

## 👥 Tài Khoản Test (Cần Chạy Migration)

### LEADER Accounts (Trưởng Phòng)

#### Leader 1 - IT Department
```
Username: leader1
Password: admin123
Email:    leader1@itx.local
Role:     LEADER
Team:     3 nhân viên (employee1, employee2, employee3)
```
**Quyền**: Duyệt requests của team IT (employee1, employee2, employee3)

#### Leader 2 - HR Department
```
Username: leader2
Password: admin123
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
Password: admin123
Email:    employee1@itx.local
Full Name: Nguyễn Văn B
Role:     EMPLOYEE
Leader:   leader1
```

```
Username: employee2
Password: admin123
Email:    employee2@itx.local
Full Name: Phạm Thị C
Role:     EMPLOYEE
Leader:   leader1
```

```
Username: employee3
Password: admin123
Email:    employee3@itx.local
Full Name: Đỗ Minh D
Role:     EMPLOYEE
Leader:   leader1
```

#### Team HR (under leader2)
```
Username: employee4
Password: admin123
Email:    employee4@itx.local
Full Name: Hoàng Văn F
Role:     EMPLOYEE
Leader:   leader2
```

```
Username: employee5
Password: admin123
Email:    employee5@itx.local
Full Name: Vũ Thanh G
Role:     EMPLOYEE
Leader:   leader2
```

---

## 🚀 Cách Sử Dụng

### Bước 1: Đảm Bảo Database Đã Chạy Migration
Migration file nằm ở `backend/src/main/resources/db/migration/dev/V13__add_test_users.sql`.

⚠️ **Caveat**: `application.yml` cấu hình `flyway.locations: classpath:db/migration`
(không liệt kê `db/migration/dev` riêng). Vì `dev/` là thư mục con của
`db/migration`, Flyway coi đây là vị trí trùng lặp và **discard** nó khi khởi
động (log sẽ có dòng `WARN ... Discarding location 'classpath:db/migration/dev'
as it is a sub-location of 'classpath:db/migration'`) — nghĩa là trên **DB mới
hoàn toàn**, V13 sẽ **không** tự chạy như mô tả trước đây. Trên DB dev hiện tại
V13 đã từng chạy thành công từ trước (kiểm tra `flyway_schema_history`), nên
leader1/employee1-5 vẫn tồn tại — nhưng đừng giả định điều này đúng cho mọi
môi trường mới. Nếu setup fresh DB mà không thấy các user này, đây là lý do.

### Bước 2: Truy Cập Ứng Dụng
1. Mở trình duyệt
2. Truy cập: `http://localhost:5174` (hoặc `http://localhost:5173`)

### Bước 3: Đăng Nhập
Tại trang Login, nhập:
- **Username**: (chọn một từ danh sách trên)
- **Password**: `admin123` cho leader/employee. Riêng **admin không dùng
  chung quy tắc này** — xem phần "ADMIN Account" ở đầu file.

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
1. Đăng nhập: leader1 / admin123
2. Vào: Dashboard > Duyệt Yêu cầu (Review Requests)
3. Kỳ vọng: Chỉ xem requests từ team của mình
   - employee1, employee2, employee3
   - KHÔNG thấy requests từ employee4, employee5 (team khác)
```

### Test với Employee Account
```
1. Đăng nhập: employee1 / admin123
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
1. Đăng nhập: employee1 / admin123
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
1. Đăng nhập: leader1 / admin123
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
    '$2a$10$8P5/Q7.E.R5nJ9pYQ5R5He0PJpVpvTOv3Qr4pGzR7W4mK3A9L1tl2', -- password: admin123
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
- Test users (leader/employee) mặc định: `admin123`
- **Admin không có password mặc định cố định** — xem phần "ADMIN Account" ở
  đầu file để biết cách lấy/reset password (không phải lỗi nếu `admin123`
  không vào được, đó là hành vi thiết kế từ commit `b23bcdb`)

### Lỗi: "User not found"
- Migration file chưa chạy, HOẶC Flyway đang discard `db/migration/dev` (xem
  caveat ở "Bước 1" phía trên) trên DB mới
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
