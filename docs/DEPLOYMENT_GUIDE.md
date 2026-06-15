# 🚀 Cẩm nang Triển khai ITX Attendance System (Deployment Guide)

Chào mừng bạn đến với bước cuối cùng của việc phát triển phần mềm: **Triển khai (Deployment)**. Do dự án của chúng ta đã được đóng gói hoàn toàn bằng **Docker Compose**, việc đưa hệ thống lên mạng internet thực tế dễ dàng hơn rất nhiều so với cách triển khai thủ công truyền thống.

Dưới đây là quy trình 6 bước chi tiết cùng giải thích "Tại sao" dành riêng cho người mới bắt đầu.

---

## Bước 1: Thuê Server ảo (VPS) và Tên miền (Domain)
Để phần mềm hoạt động 24/7 và ai cũng có thể truy cập, bạn cần một máy chủ trên Internet.

- **Cần làm gì?** 
  1. Mua một tên miền (VD: `chamcong.congty.com`) từ các nhà cung cấp như Namecheap, Mắt Bão, Tenten.
  2. Thuê một VPS (Virtual Private Server) chạy hệ điều hành **Ubuntu 22.04 LTS**. Các nhà cung cấp uy tín: DigitalOcean, Vultr, AWS (EC2), hoặc Hostinger. Cấu hình tối thiểu: **2GB RAM, 2 CPUs**.
- **Tại sao phải làm vậy?** Máy tính cá nhân (localhost) sẽ tắt khi bạn gập máy, và không có "IP tĩnh" để người ngoài truy cập. VPS là máy tính ảo đặt ở trung tâm dữ liệu, chạy liên tục và có IP Public. Tên miền giúp người dùng không phải gõ dãy số IP khó nhớ (VD: `142.250.19.14`).

## Bước 2: Trỏ Tên miền về IP của VPS (DNS Records)
- **Cần làm gì?** Vào trang quản lý tên miền bạn vừa mua, tạo bản ghi **A Record** trỏ tên miền (VD: `chamcong.congty.com`) về địa chỉ IP của VPS.
- **Tại sao phải làm vậy?** Đây là thao tác giống như "lưu danh bạ điện thoại". Bạn đang nói với hệ thống Internet toàn cầu rằng: "Bất cứ ai gõ `chamcong.congty.com` thì hãy điều hướng họ tới cái VPS này".

## Bước 3: Cài đặt Môi trường cơ bản trên VPS
Kết nối vào VPS của bạn qua SSH (Sử dụng Terminal trên Mac/Linux hoặc PuTTY/PowerShell trên Windows: `ssh root@IP_CUA_VPS`).

- **Cần làm gì?** Chạy các lệnh cài đặt Git và Docker:
  ```bash
  # Cập nhật hệ thống
  apt update && apt upgrade -y

  # Cài đặt Git để kéo code
  apt install git -y

  # Cài đặt Docker và Docker Compose (Rất quan trọng)
  curl -fsSL https://get.docker.com -o get-docker.sh
  sh get-docker.sh
  apt install docker-compose-plugin -y
  ```
- **Tại sao phải làm vậy?** 
  Thay vì phải cài thủ công Java, Node.js, MySQL, MinIO,... cực kỳ vất vả và dễ sinh lỗi xung đột, chúng ta chỉ cần cài duy nhất **Docker**. Docker sẽ đóng vai trò như một bến cảng, và dự án của chúng ta là những container (thùng hàng) chạy độc lập bên trên bến cảng đó.

## Bước 4: Tải Mã nguồn và Cấu hình Bảo mật (.env)
- **Cần làm gì?**
  1. Kéo code từ GitHub về VPS:
     ```bash
     git clone https://github.com/nghuyne/itx-attendance-system.git
     cd itx-attendance-system
     ```
  2. Tạo file cấu hình bảo mật:
     ```bash
     cp .env.example .env
     nano .env
     ```
  3. **LƯU Ý QUAN TRỌNG NHẤT:** Trong file `.env`, bạn phải thay đổi `CORS_ALLOWED_ORIGINS` thành tên miền thực tế.
     ```env
     # Ví dụ:
     CORS_ALLOWED_ORIGINS=https://chamcong.congty.com
     JWT_SECRET=mot_chuoi_that_dai_va_kho_doan_123456789
     DB_ROOT_PASSWORD=mat_khau_mysql_that_kho
     ```
- **Tại sao phải làm vậy?** File `.env` chứa mật khẩu và thông tin tuyệt mật. Nó không bao giờ được đưa lên GitHub (để chống hacker). Do đó, bạn bắt buộc phải tự tay tạo ra nó trên môi trường Production. Việc cấu hình `CORS_ALLOWED_ORIGINS` đảm bảo Frontend của bạn được phép gọi API tới Backend, nếu không cấu hình đúng, trình duyệt sẽ chặn đứng mọi thao tác đăng nhập.

## Bước 5: Khởi chạy Toàn bộ Hệ thống 🚀
- **Cần làm gì?** Đứng tại thư mục dự án trên VPS, chạy lệnh:
  ```bash
  docker compose up -d --build
  ```
- **Tại sao phải làm vậy?** 
  Lệnh này là "Phép thuật" của kiến trúc Container. Nó sẽ:
  - Tự động Build Frontend (React) thành file tĩnh.
  - Tự động Build Backend (Spring Boot) thành file `.jar`.
  - Tải MySQL và MinIO về.
  - Khởi động tất cả các dịch vụ (5 containers) và nối mạng chúng lại với nhau. Chữ `-d` giúp hệ thống chạy ngầm, bạn có thể tắt terminal mà web vẫn sống.

## Bước 6: Thiết lập Chứng chỉ Bảo mật (HTTPS/SSL) - Bắt buộc
Vì ứng dụng của bạn yêu cầu xin quyền Camera (chụp ảnh điểm danh) và Vị trí, trình duyệt như Chrome/Safari **BẮT BUỘC** trang web phải chạy trên giao thức `https://` an toàn, nếu không tính năng camera sẽ bị vô hiệu hóa.

- **Cần làm gì?** (Gợi ý cách nhanh nhất bằng Certbot)
  ```bash
  apt install certbot python3-certbot-nginx -y
  ```
  *(Lưu ý: Để Certbot tự động cấu hình, bạn cần chỉnh sửa file `nginx.conf` trong dự án một chút để Nginx trỏ đúng tên miền).*

## 🎉 Tóm Lược
Triển khai phần mềm nghe có vẻ phức tạp, nhưng cốt lõi chỉ là mang mã nguồn đặt lên một chiếc máy tính không bao giờ tắt (VPS). Nhờ chúng ta đã làm tốt phần **Phase 1: Infrastructure** (Cấu hình Nginx, Docker) ở bước Audit, dự án của bạn hiện tại thuộc dạng "Plug-and-Play" (Cắm là chạy) — cực kỳ nhàn cho người vận hành!
