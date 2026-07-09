# 🚀 Cẩm nang Triển khai ITX Attendance System (Deployment Guide)

Chào mừng bạn đến với bước cuối cùng của việc phát triển phần mềm: **Triển khai (Deployment)**. Do dự án của chúng ta đã được đóng gói hoàn toàn bằng **Docker Compose**, việc đưa hệ thống lên mạng internet thực tế dễ dàng hơn rất nhiều so với cách triển khai thủ công truyền thống.

Dưới đây là quy trình 6 bước chi tiết cùng giải thích "Tại sao" dành riêng cho người mới bắt đầu.

---

## Bước 1: Thuê Server ảo (VPS) và Tên miền (Domain)
Để phần mềm hoạt động 24/7 và ai cũng có thể truy cập, bạn cần một máy chủ trên Internet.

- **Cần làm gì?** 
  1. Mua một tên miền (VD: `chamcong.congty.com`) từ các nhà cung cấp như Namecheap, Mắt Bão, Tenten.
  2. Thuê một VPS (Virtual Private Server) chạy hệ điều hành **Ubuntu 22.04 LTS**. Các nhà cung cấp uy tín: DigitalOcean, Vultr, AWS (EC2), Hostinger, hoặc **Oracle Cloud Free Tier** (miễn phí vĩnh viễn, xem hướng dẫn riêng bên dưới). Cấu hình tối thiểu: **2GB RAM, 2 CPUs**.
- **Tại sao phải làm vậy?** Máy tính cá nhân (localhost) sẽ tắt khi bạn gập máy, và không có "IP tĩnh" để người ngoài truy cập. VPS là máy tính ảo đặt ở trung tâm dữ liệu, chạy liên tục và có IP Public. Tên miền giúp người dùng không phải gõ dãy số IP khó nhớ (VD: `142.250.19.14`).

### Hướng dẫn riêng: Tạo VPS miễn phí trên Oracle Cloud Free Tier

Oracle Cloud cho phép dùng **vĩnh viễn miễn phí** một VPS cấu hình rất mạnh (tối đa 4 vCPU ARM / 24GB RAM, gộp chung trong hạn mức "Always Free"), vượt xa yêu cầu tối thiểu của dự án. Đánh đổi là bước đăng ký hơi rườm rà và đôi khi hết chỗ trống ("Out of capacity") ở khu vực gần Việt Nam.

1. Đăng ký tài khoản tại [oracle.com/cloud/free](https://www.oracle.com/cloud/free/). Cần email, số điện thoại, và **thẻ Visa/Mastercard quốc tế** để xác minh danh tính (không bị trừ tiền nếu chỉ dùng tài nguyên Always Free).
2. Khi được hỏi chọn **Home Region**, chọn khu vực gần Việt Nam nhất có hỗ trợ Always Free (thường là Singapore hoặc Nhật Bản). **Lưu ý: không thể đổi Home Region sau khi tạo tài khoản**, nên cân nhắc kỹ.
3. Vào Console → menu ☰ → **Compute → Instances → Create Instance**:
   - **Name:** `itx-attendance-vps`.
   - **Image:** Canonical Ubuntu, phiên bản **22.04**.
   - **Shape:** đổi sang **Ampere (ARM) → VM.Standard.A1.Flex**, chọn phần "Always Free eligible", đặt **2 OCPU / 12GB RAM** trở lên (tổng hạn mức free toàn tài khoản là 4 OCPU / 24GB, có thể dồn hết vào 1 VPS).
   - **Networking:** để mặc định tạo VCN mới, nhớ bật **"Assign a public IPv4 address"**.
   - **SSH keys:** chọn **"Generate a key pair for me"** rồi tải về file `.pem` — đây chính là private key sẽ dùng làm secret `VPS_SSH_KEY` ở Bước 7.
   - Bấm **Create**.
4. Nếu gặp lỗi `Out of host capacity`: đây là lỗi tạm thời do khu vực đó hết chỗ cho shape ARM free, không phải lỗi cấu hình của bạn. Thử đổi **Availability Domain** khác (nếu region có nhiều AD), hoặc thử tạo lại sau vài giờ/ngày.
5. Sau khi Instance chuyển trạng thái **RUNNING**, ghi lại **Public IP** hiển thị trên trang chi tiết Instance — đây là IP dùng cho Bước 2 (trỏ domain) và Bước 3 (SSH vào server).
6. **Mở port 80/443 (khác biệt quan trọng so với VPS thường):** Oracle Cloud chặn traffic vào theo 2 lớp, cần mở cả hai:
   - **Lớp mạng (Console):** vào **Networking → Virtual Cloud Networks →** chọn VCN vừa tạo **→ Security Lists →** Default Security List **→ Add Ingress Rules**: thêm rule cho port `80` và `443`, Source CIDR `0.0.0.0/0`.
   - **Lớp hệ điều hành (trong VPS):** Ubuntu image của Oracle có `iptables`/`netfilter-persistent` chặn sẵn mọi port trừ 22. SSH vào server rồi chạy:
     ```bash
     sudo iptables -I INPUT -p tcp --dport 80 -j ACCEPT
     sudo iptables -I INPUT -p tcp --dport 443 -j ACCEPT
     sudo netfilter-persistent save
     ```
   - Bỏ qua bước này là nguyên nhân phổ biến nhất khiến domain "trỏ đúng IP nhưng không truy cập được".
7. SSH vào server: `ssh -i duong-dan-toi-key.pem ubuntu@<PUBLIC_IP>` (user mặc định là `ubuntu`, không phải `root`), rồi tiếp tục với **Bước 3** bên dưới. Khi cần dùng `sudo` cho các lệnh cài Docker.

## Bước 2: Trỏ Tên miền về IP của VPS (DNS Records)
- **Cần làm gì?** Vào trang quản lý tên miền bạn vừa mua, tạo bản ghi **A Record** trỏ tên miền (VD: `chamcong.congty.com`) về địa chỉ IP của VPS.
- **Tại sao phải làm vậy?** Đây là thao tác giống như "lưu danh bạ điện thoại". Bạn đang nói với hệ thống Internet toàn cầu rằng: "Bất cứ ai gõ `chamcong.congty.com` thì hãy điều hướng họ tới cái VPS này".

## Bước 3: Cài đặt Môi trường cơ bản trên VPS
Kết nối vào VPS của bạn qua SSH (Sử dụng Terminal trên Mac/Linux hoặc PuTTY/PowerShell trên Windows: `ssh root@IP_CUA_VPS`).

> **Nếu dùng Oracle Cloud:** user mặc định là `ubuntu`, không phải `root` (`ssh -i key.pem ubuntu@IP_CUA_VPS`), và bạn cần thêm `sudo` trước mỗi lệnh bên dưới.

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
