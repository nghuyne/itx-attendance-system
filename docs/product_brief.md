# Product Brief: Hệ thống Quản lý Chấm công & Check-in Thông minh

## 1. Tóm tắt Dự án (Executive Summary)
Dự án nhằm xây dựng một Web App quản lý chấm công, check-in/check-out nội bộ cho công ty. Hệ thống yêu cầu độ chính xác cao trong việc định danh nhân viên thông qua đa yếu tố (GPS, Wi-Fi MAC, Hình ảnh), đồng thời cung cấp luồng xử lý linh hoạt cho các quy định về làm thêm giờ (OT), nghỉ lễ đặc thù tại Việt Nam, và các ngoại lệ trong ca làm việc (đi muộn, về sớm) với cơ chế phân quyền duyệt theo cấp bậc nhóm (Leader/Admin). 
Hệ thống dự kiến triển khai với backend Spring Boot và frontend React.

## 2. Đối tượng Người dùng (User Personas)
*   **Employee (Nhân viên):** Thực hiện check-in/out hàng ngày, gửi các yêu cầu ngoại lệ (đi muộn, về sớm, nghỉ phép).
*   **Team Leader (Trưởng nhóm):** Được phân quyền quản lý một nhóm cụ thể, có thẩm quyền phê duyệt/từ chối các yêu cầu (đi muộn, về sớm, nghỉ phép) của nhân viên trong nhóm.
*   **Admin (Quản trị viên):** Cấu hình toàn bộ hệ thống (giờ làm, OT, ngày lễ), quản lý dữ liệu thiết bị/vị trí, và có quyền can thiệp vào mọi dữ liệu chấm công.

## 3. Tính năng Cốt lõi & Yêu cầu Nghiệp vụ (Core Features & Business Logic)

### 3.1. Xác thực Check-in/Check-out Đa yếu tố
*   **Xác thực Vị trí & Mạng:** Hệ thống thu thập tọa độ GPS và địa chỉ MAC Wi-Fi của thiết bị nhân viên theo thời gian thực để đối chiếu với dữ liệu hợp lệ của công ty.
*   **Chụp ảnh Bắt buộc:** Quá trình check-in và check-out bắt buộc phải kèm theo thao tác chụp ảnh trực tiếp từ camera để chống gian lận.
*   **Cơ chế Ngoại lệ Thiết bị/Vị trí (Admin Override):** Trong trường hợp nhân viên mới chưa được cập nhật dữ liệu, hoặc lỗi thiết bị/mạng không nhận diện được trùng khớp, Admin có quyền cấu hình thêm thủ công/xóa MAC Wi-Fi riêng hoặc Tọa độ GPS riêng biệt hợp lệ cho duy nhất nhân viên đó.

### 3.2. Quản lý Ca làm việc & Các ngoại lệ (Lark-inspired Shift Settings)
*   Hệ thống hỗ trợ cấu hình ca làm việc linh hoạt (Flex-time/Fixed-time) với các tham số thời gian chặt chẽ:
    *   **Biên độ Check-in/out:** Cấu hình thời gian cho phép check-in sớm (ví dụ: tối đa 30 phút trước giờ làm) và check-out muộn.
    *   **Quy tắc Đi muộn (Late In) / Về sớm (Early Out):** Thiết lập mốc thời gian đánh dấu "Đi muộn" (ví dụ: quá 0 phút) hoặc "Về sớm".
    *   **Quy tắc Nửa ngày (Half-day):** Thiết lập mốc thời gian vi phạm nghiêm trọng (ví dụ: muộn quá 30 phút) sẽ tự động chuyển trạng thái thành "Nghỉ nửa ngày không ghi nhận" (Half-day no record).
*   Nhân viên có quyền gửi đơn xin phép đi muộn/về sớm trên hệ thống để Leader phê duyệt, hợp thức hóa các vi phạm trên.

### 3.3. Quản lý Làm thêm giờ (Overtime - OT)
*   **Khung giờ OT:** OT không tự động tính ngay sau khi hết ca. Admin có quyền thiết lập khoảng đệm (Buffer time). Ví dụ: Ca làm kết thúc lúc 17:30, thời gian OT chính thức bắt đầu tính từ 18:00 (khoảng 17:30 - 18:00 không tính OT).
*   **Hệ số Lương OT:** Tự động phân loại và tính toán theo 3 trường hợp:
    *   **OT Ngày thường:** 150% lương/giờ tiêu chuẩn.
    *   **OT Ngày cuối tuần:** 200% lương/giờ tiêu chuẩn.
    *   **OT Ngày lễ:** 300% lương/giờ tiêu chuẩn.

### 3.4. Quản lý Ngày Lễ, Tết Đặc thù (Holiday Management)
Hệ thống phải xử lý mượt mà cả lịch Dương và lịch Âm, chia làm 2 nhóm:
*   **Ngày lễ cố định (Fixed Holidays):** Hệ thống tự động set sẵn theo Dương lịch hàng năm (ví dụ: 30/4, 1/5, 1/1).
*   **Ngày lễ linh hoạt (Dynamic/Lunar Holidays):** Hỗ trợ Admin thiết lập thủ công hoặc tùy chỉnh lịch nghỉ Tết Nguyên Đán, Giỗ tổ Hùng Vương,... theo từng năm do sự chênh lệch của Âm lịch.

## 4. Mục tiêu Thành công (Success Metrics)
*   100% check-in/out được ghi nhận chính xác theo thời gian thực kèm đủ 3 yếu tố: GPS, MAC Wi-Fi, và Hình ảnh.
*   Tự động hóa hoàn toàn việc phân loại giờ công tiêu chuẩn, giờ OT theo 3 mức hệ số, và các lỗi đi muộn/về sớm mà không cần nhân sự HR tính toán thủ công.
*   Cơ chế phê duyệt của Leader hoạt động trơn tru theo thời gian thực (Real-time update) lên bảng công của nhân viên.