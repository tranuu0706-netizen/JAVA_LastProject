# Hướng dẫn cài đặt và sử dụng CodeAnalyzer

## 1. Yêu cầu trước khi cài đặt

Cần cài sẵn các phần mềm sau:

| Thành phần | Mục đích |
| Java JDK 17+ | Biên dịch và chạy chương trình |
| Maven | Tải thư viện và build project |
| SQL Server | Lưu nick, submissions, kết quả AI và đánh giá |
| Microsoft Edge | Trình duyệt dùng cho Selenium crawl Codeforces |
| Gemini API key | Gọi AI để phân tích source code |

Kiểm tra nhanh:

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

Nếu không dùng `sqlcmd`, có thể mở file `src/main/resources/db/schema.sql` bằng SQL Server Management Studio rồi nhấn `Execute`.

Sau khi chạy xong, database `code_analyzer` sẽ được tạo.

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
 Lưu ý: không nên bỏ api key vào đây vì nó không an toàn.Cứ để trống vậy , Khi xuống phần chạy chương trình tôi sẽ hướng dẫn

edge.profile.path=D:\\CodeAnalyzerProfile
edge.driver.path=

crawl.interval.hours=24
crawl.start.time=02:00
crawl.max.submissions=500
```

Nếu dùng SQL Server Express, đổi `db.url` thành:

```properties
db.url=jdbc:sqlserver://localhost\\SQLEXPRESS;databaseName=code_analyzer;encrypt=true;trustServerCertificate=true;
```

## 4. Chuẩn bị Edge profile

Selenium cần một profile Edge riêng để crawl Codeforces ổn định.

Chạy lệnh:

```powershell
& "C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe" --user-data-dir="D:\CodeAnalyzerProfile"
```

Sau đó:

1. Mở Codeforces trong cửa sổ Edge vừa hiện.
2. Đăng nhập nick Codeforces đã có đánh giá nếu cần.
3. Chọn `Remember me` nếu có.
4. Đóng toàn bộ cửa sổ Edge.

Không dùng profile Edge cá nhân đang mở hằng ngày, vì Selenium cần quyền sử dụng riêng thư mục profile.

## 5. Build chương trình

Tại thư mục project, chạy:

```powershell
mvn clean package -DskipTests
```

Nếu build thành công, file chạy sẽ nằm tại:

```text
target\code-analyzer-1.0.0.jar
```

Nếu Maven báo không xóa được file trong `target`, hãy tắt chương trình Java đang chạy rồi build lại.

## 6. Chạy chương trình

setx GEMINI_API_KEY "KEY_MOI_CUA_BAN"  
 Lưu ý: khi đạt giới hạn limit thì sẽ đổi key vì key free nên limit rất nhanh
sau đó copy nguyên lệnh này :
$env:GEMINI_API_KEY = [Environment]::GetEnvironmentVariable("GEMINI_API_KEY", "User")
Cuối cùng cho chạy chương trình:  
 java -jar target\code-analyzer-1.0.0.jar

Khi chạy thành công, giao diện desktop Java Swing sẽ hiện ra.

## 7. Hướng dẫn sử dụng

### Bước 1: Thêm nick Codeforces

Vào mục `Quản lý tài khoản`:

1. Nhập username Codeforces.
2. Nhập tên hiển thị nếu muốn.
3. Nhấn `Thêm Nick`.

Chương trình sẽ kiểm tra nick có tồn tại trên Codeforces trước khi lưu vào database.

### Bước 2: Crawl submissions

Có 2 cách crawl:

| Cách | Thao tác |
| Crawl từng nick | Chọn nick trong `Quản lý tài khoản`, sau đó nhấn `Crawl` |
| Crawl tất cả | Vào mục `Crawl`, sau đó nhấn `Crawl tất cả` |

Sau khi crawl xong, dữ liệu submissions và source code sẽ được lưu vào database.

### Bước 3: Cấu hình crawl định kỳ

Vào mục `Crawl`:

1. Nhập khoảng cách chạy, ví dụ `24`.
2. Nhập giờ bắt đầu, ví dụ `02:00`.
3. Nhấn `Lưu lịch`.
4. Dùng các nút `Bật`, `Tắt`, `Khởi động lại` để điều khiển scheduler.

### Bước 4: Xem submissions

Vào mục `Submissions` để xem các bài đã crawl được, gồm:

- Submission ID.
- Tên người dùng.
- Tên bài.
- Verdict.
- Ngôn ngữ.
- Thời gian nộp.

### Bước 5: Phân tích AI

Vào mục `Phan tich AI`:

1. Nhấn `Bắt đầu phân tích`.
2. Chờ Gemini phân tích các submission chưa có kết quả.
3. Nhấn `Làm mới` để xem dữ liệu mới.

Kết quả phân tích gồm CTDL, thuật toán, độ phức tạp, độ khó, AI Score, lý do nghi ngờ AI và chất lượng code.

### Bước 6: Đánh giá nick

Vào mục `Danh gia`:

1. Nhấn `Đánh giá tất cả`, hoặc đánh giá từng nick.
2. Xem DS Score, Algo Score, AI Usage, level, điểm mạnh/yếu và khuyến nghị.

Cần có dữ liệu crawl và phân tích AI trước thì kết quả đánh giá mới đầy đủ.

## 8. Lỗi thường gặp

| Lỗi                           | Cách xử lý                                                             |
| ----------------------------- | ---------------------------------------------------------------------- |
| Không kết nối được SQL Server | Kiểm tra SQL Server đang chạy, đúng username/password và đúng `db.url` |
| Sai database                  | Đảm bảo đã chạy `schema.sql` và có database `code_analyzer`            |
| Thiếu Gemini API key          | Kiểm tra `gemini.api.key` trong `application.properties`               |
| Edge profile đang bị dùng     | Đóng toàn bộ Microsoft Edge rồi chạy lại app                           |
| EdgeDriver sai phiên bản      | Cập nhật Microsoft Edge hoặc tải đúng `msedgedriver.exe`               |
| Crawl không ra dữ liệu        | Kiểm tra đăng nhập Codeforces, mạng và profile Edge                    |
| Phân tích AI chậm             | Giảm số lượng phân tích mỗi lượt hoặc tăng thời gian delay Gemini      |
