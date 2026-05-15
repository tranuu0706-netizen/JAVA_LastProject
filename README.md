# Hướng dẫn cài đặt và sử dụng CodeAnalyzer

CodeAnalyzer là ứng dụng Java Swing dùng để quản lý tài khoản Codeforces, crawl submissions, lưu source code vào SQL Server, phân tích bài bằng Gemini AI và tổng hợp năng lực lập trình theo từng tài khoản.

## 1. Yêu cầu cài đặt

Cần cài sẵn:

| Thành phần          | Mục đích                                            |
| ------------------- | --------------------------------------------------- |
| Java JDK 17 trở lên | Biên dịch và chạy chương trình                      |
| Maven               | Tải thư viện và build project                       |
| SQL Server          | Lưu tài khoản, submissions, log crawl và kết quả AI |
| Microsoft Edge      | Trình duyệt dùng cho Selenium crawl Codeforces      |
| Gemini API key      | Gọi AI để phân tích source code                     |

Kiểm tra Java và Maven:

```powershell
java -version
mvn -version
```

Nếu `java -version` nhỏ hơn 17, cần cài JDK 17 trở lên.

## 2. Tạo database

Mở PowerShell tại thư mục project và chạy:

```powershell
sqlcmd -S localhost -U sa -P "MAT_KHAU_SQL_SERVER" -i src\main\resources\db\schema.sql
```

Nếu dùng SQL Server Express:

```powershell
sqlcmd -S localhost\SQLEXPRESS -U sa -P "MAT_KHAU_SQL_SERVER" -i src\main\resources\db\schema.sql
```

Nếu không dùng `sqlcmd`, mở file `src/main/resources/db/schema.sql` bằng SQL Server Management Studio rồi nhấn `Execute`.

## 3. Cấu hình chương trình

Mở file:

```text
src/main/resources/application.properties
```

Cấu hình các giá trị chính:

```properties
db.url=jdbc:sqlserver://localhost;databaseName=code_analyzer;encrypt=true;trustServerCertificate=true;
db.username=sa
db.password=MAT_KHAU_SQL_SERVER

gemini.api.key=
gemini.model=gemini-2.5-flash
gemini.request.delay.ms=10000
gemini.max.output.tokens=4096

edge.profile.path=D:\\CodeAnalyzerProfile
edge.driver.path=

crawl.interval.hours=24
crawl.start.time=02:00
crawl.max.submissions=500

analysis.batch.size=5
analysis.max.code.length=4000
```

Lưu ý:

- Có thể để trống `gemini.api.key` trong file, sau đó nhập trực tiếp trong trang `Cài đặt` của ứng dụng.
- Nếu dùng SQL Server Express, đổi `db.url` thành:

```properties
db.url=jdbc:sqlserver://localhost\\SQLEXPRESS;databaseName=code_analyzer;encrypt=true;trustServerCertificate=true;
```

## 4. Chuẩn bị Edge profile

Selenium nên dùng một profile Edge riêng để crawl ổn định và tránh xung đột với trình duyệt cá nhân.

Chạy lệnh:

```powershell
& "C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe" --user-data-dir="D:\CodeAnalyzerProfile"
```

Sau đó:

1. Mở Codeforces trong cửa sổ Edge vừa hiện.
2. Đăng nhập tài khoản Codeforces đã được đánh giá trên website(Vì tài khoản mơi không thể crawl code về)
3. Đóng toàn bộ cửa sổ Edge trước khi chạy ứng dụng.

## 5. Build chương trình

Tại thư mục project, chạy:

```powershell
mvn clean package -DskipTests
```

Nếu build thành công, file chạy nằm trong thư mục `target`.

## 6. Chạy chương trình

click chuột phải vào file MainSwing.java chọn lệnh run java

Chạy bằng lệnh:

```powershell
java -jar target\code-analyzer-1.0.0.jar
```

## 7. Hướng dẫn sử dụng

### 7.1. Trang chính

Trang chính hiển thị tổng quan hệ thống:

- Số tài khoản đang theo dõi.
- Tổng số submissions đã lưu.
- Số submissions đã phân tích AI.
- Lần crawl gần nhất.
- Top cấu trúc dữ liệu và thuật toán phổ biến.
- Thanh trạng thái dưới cùng hiển thị lịch crawl định kỳ và trạng thái kết nối database.

### 7.2. Quản lý tài khoản

Vào `Quản lý tài khoản` để:

1. Thêm username Codeforces.
2. Xóa tài khoản không cần theo dõi.
3. Crawl riêng một tài khoản.
4. Crawl tất cả tài khoản.
5. Dừng crawl khi tiến trình đang chạy.

### 7.3. Lịch sử Crawl

Vào `Lịch sử Crawl` để xem các lần crawl đã chạy.

Ý nghĩa các cột:

| Cột      | Ý nghĩa                                 |
| -------- | --------------------------------------- |
| Scanned  | Số submission đã quét qua               |
| New      | Số submission mới được lưu vào database |
| Skipped  | Số submission đã tồn tại nên bỏ qua     |
| Analyzed | Số submission đã phân tích AI           |

Khi chọn một dòng lịch sử, phần log bên dưới sẽ hiển thị chi tiết quá trình crawl của job đó.

### 7.4. Submissions

Vào `Submissions` để xem danh sách bài đã crawl.

Có thể:

1. Lọc theo username bằng combobox.
2. Double-click một submission để mở chi tiết.
3. Xem source code của submission.
4. Xem kết quả đánh giá AI nếu đã phân tích.
5. Bấm `Phân tích bài này` để phân tích riêng submission đang mở.
6. Bấm `Phân tích lại` nếu muốn chạy lại AI cho bài đó.

### 7.5. Phân tích AI

Vào `Phân tích AI` để chạy phân tích hàng loạt các submissions chưa có kết quả AI.

Trang này chủ yếu hiển thị log tiến trình phân tích. Nội dung phân tích chi tiết của từng bài xem trong trang `Submissions`.

### 7.6. Đánh giá

Vào `Đánh giá` để xem đánh giá tổng hợp theo tài khoản.

Kết quả gồm:

- Điểm cấu trúc dữ liệu.
- Điểm thuật toán.
- Mức nghi ngờ có hỗ trợ AI.
- Mức năng lực tổng hợp.
- Điểm mạnh.
- Điểm yếu.
- Khuyến nghị học tập.

Lưu ý: `AI Score` chỉ là mức nghi ngờ dựa trên dấu hiệu trong code, không phải kết luận chắc chắn người dùng có sử dụng AI.

### 7.7. Cài đặt

Vào `Cài đặt` để chỉnh trực tiếp trong ứng dụng:

- Cấu hình database.
- Gemini API key.
- Edge profile path.
- Edge driver path.
- Khoảng cách crawl định kỳ.
- Giờ bắt đầu crawl.
- Số submissions tối đa mỗi lần crawl.
- Số bài phân tích AI mỗi batch.

Sau khi lưu cấu hình, ứng dụng sẽ ưu tiên dùng cấu hình mới trong trang `Cài đặt`.

## 8. Lỗi thường gặp

| Lỗi                           | Cách xử lý                                                                          |
| ----------------------------- | ----------------------------------------------------------------------------------- |
| Không kết nối được SQL Server | Kiểm tra SQL Server đang chạy, đúng `db.url`, username và password                  |
| Không có database             | Chạy lại file `src/main/resources/db/schema.sql`                                    |
| Thiếu Gemini API key          | Nhập key trong trang `Cài đặt` hoặc cấu hình trong `application.properties`         |
| Edge profile đang bị dùng     | Đóng toàn bộ Microsoft Edge rồi chạy lại app                                        |
| Crawl không ra dữ liệu        | Kiểm tra mạng, Codeforces, Edge profile và username cần crawl                       |
| AI đạt giới hạn quota         | Chờ quota reset hoặc đổi Gemini API key hợp lệ                                      |
| Phân tích AI quá ngắn         | Tăng `gemini.max.output.tokens` và `analysis.max.code.length` trong trang `Cài đặt` |
