# ANILOVE — ĐẶC TẢ CHỨC NĂNG

**Tên đồ án:** Ứng dụng AniLove: Trao đổi, chia sẻ thú cưng  
**Phiên bản:** 1.0 · **Nền tảng:** Mobile App + Admin Website + Backend API

## 1. Mục tiêu và phạm vi

AniLove giúp người dùng đăng thông tin thú cưng, tìm thú cưng phù hợp, trao đổi với chủ nuôi và gửi yêu cầu nhận nuôi. Trong tài liệu này, **“chia sẻ”** gồm chia sẻ hồ sơ, hình ảnh và bài viết về thú cưng; **“trao đổi”** gồm trao đổi thông tin, trò chuyện và thỏa thuận nhận nuôi/chuyển giao. MVP không xử lý mua bán hoặc thanh toán.

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

## 4. Quy tắc nghiệp vụ

1. Chỉ tin `PUBLISHED` hiển thị công khai và nhận yêu cầu.
2. Người dùng không được gửi yêu cầu nhận nuôi cho tin của chính mình.
3. Một tài khoản chỉ có **một yêu cầu đang chờ** trên mỗi tin; có thể gửi lại sau khi rút hoặc bị từ chối theo quy tắc sản phẩm.
4. Người đăng chỉ xử lý yêu cầu thuộc tin của mình; người gửi chỉ xem/rút yêu cầu của mình.
5. Khi xác nhận bàn giao, tin chuyển `COMPLETED`; các yêu cầu còn chờ chuyển `CLOSED`, và hệ thống thông báo cho người liên quan.
6. Thông tin sức khỏe do người đăng khai báo; hiển thị rõ nguồn thông tin và không tự gắn nhãn đã xác minh.
7. Tin nhắn chỉ hiển thị với các thành viên của cuộc hội thoại; số điện thoại không công khai mặc định.

**Trạng thái tin:** `DRAFT → PENDING_REVIEW → PUBLISHED → COMPLETED`; nhánh `REJECTED`, `HIDDEN`, `CLOSED`.

**Trạng thái yêu cầu:** `PENDING → ACCEPTED → COMPLETED`; nhánh `REJECTED`, `WITHDRAWN`, `CLOSED`. Chấp nhận yêu cầu là đồng ý tiếp tục trao đổi; chỉ xác nhận bàn giao mới hoàn tất tin.

## 5. Dữ liệu chính

| Bảng | Nội dung |
|---|---|
| `users` | Tài khoản và hồ sơ |
| `pets`, `pet_images` | Tin thú cưng và ảnh |
| `adoption_requests` | Yêu cầu nhận nuôi/chuyển giao |
| `conversations`, `messages` | Trò chuyện |
| `posts`, `post_images`, `comments`, `reactions` | Bài chia sẻ và tương tác |
| `favorites`, `notifications` | Tin yêu thích và thông báo |
| `reports`, `admin_logs` | Báo cáo và lịch sử quản trị |

## 6. API tham khảo

| Method | Endpoint | Quyền |
|---|---|---|
| POST | `/api/auth/register`, `/api/auth/login` | Public |
| GET | `/api/pets`, `/api/pets/{id}` | Public |
| POST / PATCH | `/api/pets`, `/api/pets/{id}` | Member/chủ tin |
| POST / GET | `/api/pets/{id}/requests`, `/api/requests/my` | Member |
| PATCH | `/api/requests/{id}/accept`, `/api/requests/{id}/reject` | Chủ tin |
| POST / GET | `/api/posts`, `/api/posts/{id}/comments` | Member/Public |
| GET / POST | `/api/conversations`, `/api/conversations/{id}/messages` | Người tham gia |
| GET / PATCH | `/api/admin/pets/pending`, `/api/admin/pets/{id}/review` | Admin |

## 7. Tiêu chí hoàn thành MVP

Người dùng tạo tin thú cưng → Admin duyệt → người khác tìm và xem tin → gửi yêu cầu → hai bên nhắn tin → người đăng chấp nhận yêu cầu → xác nhận bàn giao → tin được đóng. Người dùng cũng có thể đăng và tương tác với bài chia sẻ.

