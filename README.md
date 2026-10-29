# AniLove: Ứng dụng trao đổi, chia sẻ thú cưng

AniLove là đồ án ứng dụng Android hỗ trợ người dùng chia sẻ thông tin thú cưng, tìm người nhận nuôi và theo dõi quá trình chăm sóc sau bàn giao. Ứng dụng sử dụng giao diện Android native và các dịch vụ Firebase để quản lý tài khoản, dữ liệu, hình ảnh và thông báo.

## Thành viên thực hiện

| MSSV | Họ và tên |
| --- | --- |
| 31241021765 | Lê Trung Phong *(Nhóm trưởng)* |
| 31241020790 | Đỗ Sơn Thành |
| 31241020025 | Nguyễn Tuấn Khôi |
| 31241025109 | Nguyễn Minh Thức |

## Chức năng chính

- **Tài khoản và hồ sơ:** đăng ký, đăng nhập bằng email hoặc Google, chỉnh sửa hồ sơ và xác minh số điện thoại.
- **Quản lý thú cưng:** tạo và cập nhật hồ sơ, hình ảnh, thông tin sức khỏe và chăm sóc.
- **Nhận nuôi:** tìm kiếm tin đăng, gửi và xét yêu cầu, trao đổi, hợp đồng, bàn giao và theo dõi sau nhận nuôi.
- **Cộng đồng:** đăng bài kèm hình ảnh, thích, bình luận, chia sẻ và báo cáo nội dung.
- **Yêu thích và nội dung cá nhân:** xem bài viết đã thích, thú cưng đã lưu và bài viết của tài khoản.
- **Trò chuyện và thông báo:** tin nhắn thông thường, trò chuyện nhận nuôi và thông báo liên quan đến hoạt động của người dùng.
- **Quản trị:** quản lý tài khoản, nội dung, báo cáo và các ca hỗ trợ theo quyền được cấp.

## Công nghệ sử dụng

| Thành phần | Công nghệ |
| --- | --- |
| Ứng dụng Android | Java, XML layouts, AndroidX, Material Components |
| Tổ chức mã | Activity/Fragment → ViewModel hoặc bộ điều phối → Repository |
| Xác thực | Firebase Authentication, Google Identity và Credential Manager |
| Dữ liệu | Cloud Firestore |
| Xử lý phía máy chủ | Cloud Functions for Firebase, Node.js 22 |
| Hình ảnh | Cloud Storage thông qua luồng media phía máy chủ |
| Thông báo | Firebase Cloud Messaging |
| Kiểm thử | JUnit, Espresso, UI Automator, Node.js Test Runner, Firebase Emulator Suite |
| Công cụ build | Gradle Wrapper, Kotlin DSL, JDK 21 |

## Cấu trúc thư mục

```text
Mobile_Project/
├── app/
│   └── src/
│       ├── main/          # Mã Java, giao diện XML và tài nguyên Android
│       ├── debug/         # Cấu hình Firebase dành cho emulator
│       ├── test/          # Unit test Android
│       └── androidTest/   # Kiểm thử trên thiết bị hoặc Android emulator
├── firebase-domain/      # Các module logic nghiệp vụ Firebase
├── firebase-tests/       # Test rules, fixture và công cụ kiểm tra Firebase
├── functions/            # Cloud Functions và test phía máy chủ
├── scripts/              # Công cụ hỗ trợ build và đồng bộ
├── gradle/               # Gradle Wrapper, dependency catalog và toolchain
├── firebase.json         # Cấu hình Firebase và emulator mặc định
├── firestore.rules       # Quy tắc truy cập Firestore
├── firestore.indexes.json
└── storage.rules         # Quy tắc truy cập Storage
```

## Yêu cầu môi trường

- Android Studio và Android SDK tương thích với dự án; cấu hình hiện tại dùng **compile SDK 37**.
- Thiết bị hoặc Android emulator chạy **Android 10 / API 29 trở lên**.
- **JDK 21 đầy đủ**, bao gồm `java`, `javac`, `jlink` và `jmod`. Mã Java của ứng dụng được cấu hình tương thích Java 11.
- **Node.js 22 và npm** nếu chạy test Firebase hoặc phát triển Cloud Functions.

Dự án đã cấu hình Gradle Daemon JVM toolchain 21. Lần build đầu tiên có thể tải JDK tự động; cần để quá trình tải hoàn tất.

## Cài đặt và chạy ứng dụng

### 1. Lấy mã nguồn

```powershell
git clone https://github.com/kannar04/Mobile_Project.git
cd Mobile_Project
git switch dev
```

Mở thư mục dự án bằng Android Studio và thực hiện Gradle Sync. Android Studio sẽ tạo `local.properties` với đường dẫn Android SDK trên máy.

### 2. Cấu hình Firebase

Để chạy bản kết nối Firebase thật, đặt file cấu hình Android của dự án Firebase tại:

```text
app/google-services.json
```

Application ID của ứng dụng là `com.gmail.manhle240406.project`. Cấu hình Spark mặc định kiểm tra project ID `aniloveproject`; file cấu hình phải khớp với Firebase project được sử dụng.

Khi build trên máy khác, cần đăng ký SHA-1/SHA-256 của signing certificate tương ứng trong Firebase để sử dụng Google sign-in. Các dịch vụ Authentication, Firestore, Cloud Functions, Storage và Messaging cần được cấu hình cho project đó; việc build APK không tự triển khai backend hoặc security rules.

### 3. Chọn build variant

| Variant | Môi trường | Media upload |
| --- | --- | --- |
| **spark** | Firebase thật; dùng cho bản chạy và kiểm thử tích hợp của dự án | Bật trong cấu hình build |
| debug | Firebase emulator, project `demo-anilove` | Tắt |
| release | Cần cấu hình và xác nhận project trước khi sử dụng | Tắt theo cấu hình hiện tại |

Tên variant `spark` là tên cấu hình build của dự án, không xác định gói thanh toán hiện tại của Firebase.

Trong Android Studio, chọn **spark** tại Build Variants, kết nối thiết bị và chạy ứng dụng. Hoặc build bằng PowerShell từ thư mục gốc:

```powershell
.\gradlew.bat :app:assembleSpark
```

APK được tạo tại `app/build/outputs/apk/spark/app-spark.apk`.

## Kiểm thử

### Unit test Android

```powershell
.\gradlew.bat :app:testSparkUnitTest
```

### Test Cloud Functions

```powershell
npm --prefix functions ci
npm --prefix functions test
```

### Test domain và Firebase security rules

```powershell
npm --prefix firebase-tests ci
npm --prefix firebase-tests run test:adoption-domain
npm --prefix firebase-tests run test:adoption-rules
```

Test rules sử dụng Firebase Emulator Suite. Các test trên thiết bị cần thiết bị hoặc Android emulator được kết nối; test live cần tài khoản và dữ liệu kiểm thử riêng. Danh sách script đầy đủ nằm trong [firebase-tests/package.json](firebase-tests/package.json) và [functions/package.json](functions/package.json).

## Lưu ý khi phát triển

- Không commit `local.properties`, service-account key, signing keystore hoặc thông tin đăng nhập. `google-services.json` của Firebase thật được bỏ qua bởi `.gitignore`; cấu hình demo trong `app/src/debug/` được giữ trong repo.
- Khi gặp lỗi thiếu `jlink`, kiểm tra Gradle đang dùng **JDK đầy đủ**, không phải JRE. Nếu daemon cũ đang sử dụng Java runtime khác, dừng daemon rồi build lại:

  ```powershell
  .\gradlew.bat --stop
  .\gradlew.bat :app:assembleSpark
  ```

- Dùng các script trong `firebase-tests/` theo đúng môi trường của chúng. Script seed, vận hành hoặc kiểm tra live có thể thay đổi dữ liệu Firebase; chúng không phải bước bắt buộc để build ứng dụng.
