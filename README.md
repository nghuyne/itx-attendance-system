<div align="center">

# 🕰️ ITX Attendance System

**Hệ thống Quản lý Chấm công Chuyên nghiệp & An toàn dành cho Doanh nghiệp**

[![React](https://img.shields.io/badge/React-18.x-blue?style=for-the-badge&logo=react)](https://reactjs.org/)
[![TypeScript](https://img.shields.io/badge/TypeScript-5.x-blue?style=for-the-badge&logo=typescript)](https://www.typescriptlang.org/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.x-brightgreen?style=for-the-badge&logo=spring-boot)](https://spring.io/projects/spring-boot)
[![MySQL](https://img.shields.io/badge/MySQL-8.0-orange?style=for-the-badge&logo=mysql)](https://www.mysql.com/)
[![Docker](https://img.shields.io/badge/Docker-Ready-2496ED?style=for-the-badge&logo=docker)](https://www.docker.com/)

</div>

---

## 🌟 Giới thiệu (Overview)

**ITX Attendance System** là một giải pháp chấm công số hóa toàn diện, giúp doanh nghiệp quản lý thời gian làm việc của nhân viên một cách chính xác, minh bạch và an toàn. Hệ thống được thiết kế nguyên khối (Monolithic) nhưng triển khai theo kiến trúc Container hóa (Docker) hiện đại, đảm bảo tính dễ dàng trong vận hành và mở rộng.

Hệ thống cung cấp 3 phân hệ chính dành cho: **Nhân viên (Employee)**, **Quản lý (Leader)**, và **Quản trị viên (Admin)**.

## ✨ Tính năng Nổi bật (Key Features)

### 👨‍💼 Dành cho Nhân viên (Employee)
- **Chấm công On-site & Client-site:** Check-in/Check-out an toàn với cơ chế xác thực IP nội bộ của công ty.
- **Quản lý Yêu cầu (Requests):** Gửi các yêu cầu Điều chỉnh giờ về, Yêu cầu ngoại lệ (Đi muộn, Về sớm) trực tiếp trên từng bản ghi bị lỗi.
- **Theo dõi Lịch sử:** Giao diện trực quan xem lại lịch sử làm việc, thống kê đi muộn, về sớm.

### 👔 Dành cho Quản lý (Leader)
- **Phê duyệt Yêu cầu:** Bảng điều khiển riêng biệt để duyệt hoặc từ chối các yêu cầu từ nhân sự cấp dưới.
- **Thống kê Nhóm:** Nắm bắt nhanh tình hình làm việc của toàn bộ team.

### 🛡️ Dành cho Quản trị viên (Admin)
- **Quản lý Ca làm việc (Shifts) & Ngày Lễ (Holidays):** Thiết lập ca làm linh hoạt, tự động loại trừ ngày lễ khỏi lịch chấm công.
- **Kiểm soát Truy cập IP:** Quản lý Whitelist IP của toàn công ty hoặc gán IP cụ thể cho từng nhân sự đặc biệt.
- **Audit Logs:** Lưu vết toàn bộ thay đổi dữ liệu quan trọng, đảm bảo tính minh bạch.

## 🛠️ Công nghệ Sử dụng (Tech Stack)

| Phân tầng | Công nghệ |
| --- | --- |
| **Frontend** | React (Vite), TypeScript, Tailwind CSS, TanStack Query |
| **Backend** | Java 17, Spring Boot 3.x, Spring Security (JWT), Hibernate |
| **Database & Storage** | MySQL 8.x (Primary DB), MinIO (Object Storage), Flyway (Migrations) |
| **Infrastructure** | Docker, Docker Compose, Nginx (Reverse Proxy & Rate Limiter) |

## 🚀 Hướng dẫn Triển khai (Getting Started)

### 1. Yêu cầu Hệ thống (Prerequisites)
- [Docker](https://docs.docker.com/get-docker/) & [Docker Compose](https://docs.docker.com/compose/install/)
- Java 17 (Nếu muốn build backend local)
- Node.js 18+ (Nếu muốn chạy frontend local)

### 2. Cài đặt Biến môi trường (Environment Setup)

Nhân bản (copy) file mẫu và điền các thông tin bảo mật của bạn:

```bash
cp .env.example .env
```

> **Lưu ý:** Tuyệt đối không commit file `.env` lên GitHub.

### 3. Khởi động Hệ thống (Run with Docker)

Chỉ với 1 lệnh duy nhất, toàn bộ hệ thống (Nginx, Backend, MySQL, MinIO) sẽ được khởi chạy:

```bash
docker-compose up -d --build
```

- **Frontend/API Gateway:** `http://localhost:5173`
- **Tài khoản Admin mặc định:** `admin` / `admin123` (Cần đổi ngay khi lên Production)

## 🔒 Bảo mật (Security Posture)

Dự án đã trải qua đợt Security Audit khắt khe trước khi lên Production:
- Cơ sở hạ tầng đóng hoàn toàn (Chỉ mở duy nhất Port `80` cho Nginx).
- Cơ chế JWT được bảo vệ bởi Token Blacklist (Ngăn chặn tái sử dụng sau khi Logout).
- Tích hợp Nginx Rate Limiting chống Brute-force Login.
- Quản lý Secret hoàn toàn qua Biến môi trường (`.env`).
- Database Constraints an toàn và xử lý Timezone tuyệt đối ở chuẩn `UTC`.

## 🤝 Đóng góp (Contributing)

Chúng tôi hoan nghênh các đóng góp từ cộng đồng!
Vui lòng tạo Pull Request hoặc mở Issue để thảo luận về các thay đổi lớn trước khi thực hiện.

---
*Phát triển bởi ITX Team. © 2026 All Rights Reserved.*
