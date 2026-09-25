# ANILOVE — CHỨC NĂNG


| Vai trò | Quyền chính |
|---|---|
| Visitor | Xem danh sách và chi tiết tin công khai |
| Member | Đăng tin, lưu tin, gửi yêu cầu, nhắn tin, đăng bài |
| Admin | Duyệt nội dung, quản lý tài khoản và báo cáo |

## 2. Mobile App — người dùng

### 2.1. Tài khoản và hồ sơ
- **MB-01:** Đăng ký, đăng nhập, đăng xuất, quên/đổi mật khẩu.
- **MB-02:** Cập nhật tên, ảnh đại diện, khu vực, thông tin liên hệ và giới thiệu ngắn.
- **MB-03:** Xem tin, bài viết và yêu cầu do mình tạo hoặc gửi.

### 2.2. Khám phá thú cưng
- **MB-04:** Xem danh sách tin đã duyệt; tìm theo tên, loài và khu vực.
- **MB-05:** Lọc theo loài, giống (nếu có), độ tuổi, giới tính, tình trạng nhận nuôi; sắp xếp theo tin mới.
- **MB-06:** Xem chi tiết: ảnh, mô tả, đặc điểm, tình trạng sức khỏe do chủ nuôi cung cấp, khu vực, điều kiện nhận nuôi và thông tin người đăng.
- **MB-07:** Lưu/bỏ lưu tin yêu thích.

### 2.3. Đăng và quản lý thú cưng
- **MB-08:** Tạo tin gồm tên, loài, giống, tuổi ước tính, giới tính, mô tả, ảnh, khu vực, tình trạng sức khỏe và điều kiện nhận nuôi.
- **MB-09:** Lưu nháp, gửi duyệt, chỉnh sửa, ẩn hoặc đóng tin của mình.
- **MB-10:** Xem danh sách yêu cầu nhận nuôi; chấp nhận hoặc từ chối từng yêu cầu.
- **MB-11:** Xác nhận đã bàn giao; tin chuyển sang trạng thái hoàn tất.

### 2.4. Kết nối và chia sẻ
- **MB-12:** Gửi yêu cầu nhận nuôi kèm lời giới thiệu; theo dõi trạng thái hoặc rút yêu cầu.
- **MB-13:** Nhắn tin riêng giữa người gửi yêu cầu và người đăng; nhận thông báo tin nhắn mới.
- **MB-14:** Đăng bài chia sẻ ảnh, câu chuyện hoặc kinh nghiệm chăm sóc thú cưng.
- **MB-15:** Xem bảng tin; thích và bình luận bài viết.
- **MB-16:** Báo cáo tin, bài viết hoặc tài khoản có nội dung không phù hợp.
- **MB-17:** Nhận thông báo khi tin được duyệt, có yêu cầu mới, yêu cầu được xử lý hoặc có tin nhắn.

## 3. Server — Admin Website và Backend API

### 3.1. Admin Website
- **AD-01:** Dashboard: số người dùng, tin chờ duyệt, tin đang hoạt động, yêu cầu nhận nuôi và báo cáo.
- **AD-02:** Xem/tìm tài khoản; khóa hoặc mở khóa tài khoản vi phạm.
- **AD-03:** Duyệt/từ chối tin thú cưng, ghi lý do; ẩn tin vi phạm.
- **AD-04:** Xem và xử lý báo cáo về tin, bài viết, bình luận, tài khoản.
- **AD-05:** Quản lý danh mục loài, giống và khu vực (nếu dùng danh mục cố định).

### 3.2. Backend API
- **SV-01:** Xác thực, quản lý phiên, phân quyền và kiểm tra quyền sở hữu dữ liệu.
- **SV-02:** Lưu hồ sơ thú cưng, ảnh, bài viết; tìm kiếm, lọc và phân trang.
- **SV-03:** Quản lý vòng đời tin và yêu cầu nhận nuôi; ngăn xử lý yêu cầu không hợp lệ.
- **SV-04:** Lưu cuộc hội thoại, tin nhắn, yêu thích, lượt thích và bình luận.
- **SV-05:** Tạo thông báo theo sự kiện; lưu trạng thái đã đọc.
- **SV-06:** Kiểm tra dữ liệu đầu vào, giới hạn tải ảnh và ghi nhật ký thao tác Admin.

