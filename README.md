# CodeAnalyzer

CodeAnalyzer là chương trình Java có giao diện web để quản lý nick Codeforces, crawl source code submission, phân tích CTDL/thuật toán bằng Gemini AI và đánh giá năng lực từng nick.

## Chức năng chính

- Thêm và quản lý nick Codeforces.
- Crawl source code các submission Accepted.
- Crawl thủ công từng nick hoặc toàn bộ nick đang active.
- Crawl định kỳ bằng scheduler cấu hình theo giờ.
- Lưu dữ liệu vào SQL Server.
- Phân tích code bằng Gemini API:
  - Cấu trúc dữ liệu chính.
  - Thuật toán chính.
  - Độ khó tương đối.
  - Điểm chất lượng code.
  - Điểm nghi ngờ sử dụng AI.
- Đánh giá tổng hợp theo nick:
  - Điểm CTDL.
  - Điểm thuật toán.
  - Mức độ sử dụng AI.
  - Level tổng thể.
  - Điểm mạnh, điểm yếu, khuyến nghị.

Lưu ý: điểm "sử dụng AI" là ước lượng dựa trên dấu hiệu trong source code, không phải bằng chứng tuyệt đối.

## Công nghệ sử dụng

- Java 17
- Maven
- Javalin Web UI tại `http://localhost:7070`
- SQL Server + HikariCP
- Selenium Edge WebDriver
- Google Gemini API
- Gson/Jackson

## Cài đặt

### 1. Yêu cầu môi trường

| Thành phần | Phiên bản khuyến nghị |
|---|---|
| Java JDK | 17+ |
| Maven | 3.8+ |
| SQL Server | 2019/2022 hoặc Express |
| Microsoft Edge | Bản mới |
| Gemini API Key | Tạo tại Google AI Studio |

### 2. Tạo database

Chạy schema:

```bash
sqlcmd -S localhost -U sa -P YOUR_SQLSERVER_PASSWORD -i src/main/resources/db/schema.sql
```

Nếu SQL Server của bạn không chạy ở `localhost`, thay `localhost` bằng tên server/instance tương ứng.

### 3. Cấu hình ứng dụng

Sửa `src/main/resources/application.properties` hoặc dùng biến môi trường.

Ví dụ dùng file properties:

```properties
db.url=jdbc:sqlserver://localhost;databaseName=code_analyzer;encrypt=true;trustServerCertificate=true;
db.username=sa
db.password=YOUR_SQLSERVER_PASSWORD

gemini.api.key=YOUR_GEMINI_API_KEY
gemini.model=gemini-2.5-flash

edge.profile.path=D:\\CodeAnalyzerProfile
edge.driver.path=

crawl.interval.hours=24
crawl.start.time=02:00
crawl.max.submissions=500
```

Khuyến nghị dùng biến môi trường để tránh lưu mật khẩu/API key trong source:

```bash
set DB_URL=jdbc:sqlserver://localhost;databaseName=code_analyzer;encrypt=true;trustServerCertificate=true;
set DB_USERNAME=sa
set DB_PASSWORD=YOUR_SQLSERVER_PASSWORD
set GEMINI_API_KEY=YOUR_GEMINI_API_KEY
```

### 4. Chuẩn bị Edge profile

Tạo profile Edge riêng cho Selenium:

```bash
"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe" --user-data-dir="D:\CodeAnalyzerProfile"
```

Trong cửa sổ Edge vừa mở, đăng nhập Codeforces nếu cần, bật "Remember me", sau đó đóng hoàn toàn Edge. Không dùng chung profile Edge đang lướt web vì Selenium cần quyền độc chiếm thư mục profile.

### 5. Build và chạy

```bash
mvn clean package -DskipTests
java -jar target/code-analyzer-1.0.0.jar
```

Mở trình duyệt tại:

```text
http://localhost:7070
```

Chạy qua Maven:

```bash
mvn exec:java -Dexec.mainClass="com.codeanalyzer.MainWeb"
```

## Hướng dẫn sử dụng

### 1. Thêm nick

Mở tab `Quản lý Nick`, nhập username Codeforces rồi nhấn `Thêm`.

Khi thêm nick, hệ thống sẽ kiểm tra nick có tồn tại trên Codeforces trước khi lưu vào database. Nếu nick không tồn tại hoặc không thể kiểm tra do lỗi mạng, hệ thống sẽ báo lỗi và không thêm.

Để xóa nick, nhấn nút `Xóa` trong danh sách tài khoản. Khi xóa nick, các submissions, kết quả phân tích AI và đánh giá liên quan cũng bị xóa theo.

### 2. Crawl dữ liệu

Trong tab `Quản lý Nick`, nhấn `Crawl` để crawl một nick.

Trong tab `Crawl`, nhấn `Crawl tất cả` để crawl toàn bộ nick active. Bảng lịch sử crawl hiển thị thời điểm chạy, trạng thái, số nick xử lý, số submission mới và lỗi nếu có.

### 3. Cấu hình crawl định kỳ

Mở tab `Crawl`, nhập:

- `Khoảng cách chạy`: ví dụ `24` giờ.
- `Giờ bắt đầu`: ví dụ `02:00`.

Nhấn `Lưu lịch`, sau đó dùng các nút `Bật`, `Tắt`, `Khởi động lại` scheduler. Khi ứng dụng chạy, scheduler tự động lên lịch theo cấu hình này.

### 4. Phân tích AI

Mở tab `Phân tích AI`, nhấn `Bắt đầu phân tích`. Chương trình sẽ phân tích các submission chưa có kết quả AI và lưu vào bảng `ai_analysis`.

Mỗi lần bấm phân tích, hệ thống xử lý tối đa 10 submissions để tránh vượt quota Gemini và tránh chờ quá lâu.

Có thể phân tích riêng từng nick bằng nút `Phân tích` trong tab `Quản lý Nick`.

### 5. Đánh giá nick

Sau khi đã có kết quả phân tích AI, mở tab `Đánh giá` và nhấn `Đánh giá tất cả`, hoặc nhấn `Đánh giá` riêng từng nick ở tab `Quản lý Nick`.

Bảng đánh giá hiển thị level, điểm CTDL, điểm thuật toán, mức nghi ngờ dùng AI, điểm mạnh, điểm yếu và khuyến nghị.

## Báo cáo thử nghiệm cần nộp

Sau khi chạy thật với một số nick, ghi lại bảng kết quả theo mẫu dưới đây. Không nên dùng số liệu giả vì người chấm có thể đối chiếu với dữ liệu trong DB/UI.

| Ngày chạy | Nick | Platform | Submission crawl được | Submission đã phân tích | DS Score | Algo Score | AI Usage | Level | Nhận xét |
|---|---|---|---:|---:|---:|---:|---|---|---|
| YYYY-MM-DD | example_user | CODEFORCES | 0 | 0 | 0 | 0 | CLEAN/LOW/... | BEGINNER/... | Điền từ tab Đánh giá |

Thông tin nên ghi kèm:

- Model Gemini dùng để phân tích.
- Số submission tối đa mỗi lần crawl.
- Ảnh chụp tab `Crawl`, `Phân tích AI`, `Đánh giá`.
- Một vài nhận xét cụ thể: nick mạnh ở CTDL nào, thuật toán nào, dấu hiệu AI cao/thấp ra sao.

## Cấu trúc dữ liệu chính

- `accounts`: danh sách nick.
- `submissions`: source code đã crawl.
- `ai_analysis`: kết quả phân tích từng submission.
- `account_evaluations`: đánh giá tổng hợp từng nick.
- `crawl_jobs`: lịch sử crawl.
- `system_config`: cấu hình runtime như lịch crawl.

## Lỗi thường gặp

### Không kết nối được database

Kiểm tra SQL Server đang chạy, database `code_analyzer` đã tạo, URL/user/password đúng. Có thể chạy:

```bash
mvn exec:java -Dexec.mainClass="TestDB"
```

### Selenium báo `session not created` hoặc Edge không mở

Đảm bảo `edge.profile.path` trỏ tới profile riêng, và không có cửa sổ Edge nào đang dùng profile đó.

### Không tải được EdgeDriver

Nếu WebDriverManager bị chặn mạng, tải `msedgedriver.exe` thủ công rồi đặt:

```properties
edge.driver.path=D:\\path\\to\\folder-or-msedgedriver.exe
```

### Gemini báo thiếu API key

Đặt `GEMINI_API_KEY` trong terminal đang chạy app, hoặc điền `gemini.api.key` trong `application.properties`.

### Phân tích AI chậm hoặc bị quota

Tăng `gemini.request.delay.ms`, giảm `analysis.batch.size`, hoặc chờ quota Gemini reset.

## Build kiểm tra

```bash
mvn clean package -DskipTests
```
