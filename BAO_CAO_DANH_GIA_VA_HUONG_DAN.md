# Báo cáo đánh giá chương trình CodeAnalyzer

Ngày đánh giá: 11/05/2026

## 1. Kết luận tổng quan

Sau khi sửa các lỗi chính, chương trình hiện đã đáp ứng phần lớn yêu cầu đề bài. Mức hoàn thành hợp lý để tự đánh giá là khoảng **85-90%**.

Chương trình đã có:

- Giao diện web bằng Java/Javalin.
- Kết nối SQL Server.
- Chức năng nhập nick Codeforces.
- Kiểm tra nick có tồn tại trên nền tảng trước khi thêm vào hệ thống.
- Xóa nick khỏi hệ thống, kèm dữ liệu submissions/analysis/evaluation liên quan.
- Crawl thủ công từng nick và crawl toàn bộ nick đang active.
- Scheduler crawl định kỳ theo giờ cấu hình.
- Lưu submissions, source code, lịch sử crawl vào database.
- Gọi Gemini API để phân tích CTDL/thuật toán.
- Đánh giá tổng hợp năng lực từng nick.
- README và hướng dẫn sử dụng đã được sửa lại cho khớp chương trình thật.

Chưa nên tự nhận hoàn thành 100% nếu chưa có dữ liệu thử nghiệm thật. Phần còn thiếu quan trọng nhất là **báo cáo kết quả đánh giá một số nick sau khi chạy thực tế**, có số liệu, ảnh chụp giao diện hoặc dữ liệu DB để chứng minh.

## 2. Đối chiếu yêu cầu đề bài

| Yêu cầu | Trạng thái | Nhận xét |
|---|---|---|
| Viết chương trình Java có giao diện | Đạt | Có Web UI chạy tại `http://localhost:7070`. |
| Có kết nối CSDL | Đạt | Dùng SQL Server, schema đầy đủ cho accounts, submissions, analysis, evaluation, crawl jobs. |
| Nhập nick Codeforces | Đạt | Có tab `Quản lý Nick`, thêm username Codeforces. |
| Kiểm tra nick tồn tại trước khi thêm | Đạt | Backend kiểm tra Codeforces trước khi lưu nick. |
| Crawl code định kỳ | Đạt về chức năng | Đã có `CrawlScheduler`, API scheduler và tab `Crawl` để cấu hình lịch. |
| Crawl code mới | Đạt một phần | Có kiểm tra submission đã tồn tại để bỏ qua bài cũ. Crawler vẫn phụ thuộc giao diện website và session đăng nhập. |
| AI phân tích CTDL/thuật toán | Đạt | Gemini phân tích DS, algorithm, độ phức tạp, độ khó, chất lượng code. |
| Xác định có dùng AI để code hay không | Đạt ở mức ước lượng | Hệ thống chấm `AI Score` và lý do nghi ngờ. Không thể kết luận tuyệt đối chỉ từ source code. |
| Đánh giá nick | Đạt | Có điểm DS, Algo, mức AI usage, level, điểm mạnh/yếu, khuyến nghị. |
| Hướng dẫn cài đặt/sử dụng | Đạt | README và file này đã có hướng dẫn chạy/sử dụng. |
| Báo cáo kết quả thử nghiệm một số nick | Cần bổ sung dữ liệu thật | Cần chạy thật vài nick, chụp màn hình hoặc trích kết quả DB rồi điền vào bảng thử nghiệm. |

## 3. Các lỗi lớn đã sửa

1. **Crawl định kỳ chưa được nối vào app**
   - Đã nối `CrawlScheduler` vào `MainWeb`.
   - Đã thêm API `/api/scheduler/status`, `/api/scheduler/start`, `/api/scheduler/stop`, `/api/scheduler/restart`.
   - Đã thêm API `/api/crawl-all`.

2. **Giao diện thiếu phần quản lý crawl**
   - Đã thêm tab `Crawl`.
   - Có trạng thái scheduler, trạng thái crawl hiện tại, cấu hình giờ chạy, lịch sử crawl.

3. **Tên class CodeforcesCrawler có ký tự lạ**
   - Đã đổi từ tên có chữ `C` Cyrillic sang `CodeforcesCrawler` Latin chuẩn.

4. **README mô tả sai công nghệ**
   - Đã sửa từ Swing/MySQL sang Web UI Javalin/SQL Server.
   - Đã sửa lệnh chạy đúng: `com.codeanalyzer.MainWeb`.

5. **Hardcode thông tin nhạy cảm**
   - Đã bỏ mật khẩu SQL Server mẫu khỏi `application.properties`.
   - `DatabaseConfig` hỗ trợ biến môi trường `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`.

6. **Máy không tải được msedgedriver**
   - Đã sửa `BrowserManager` để tự dùng `msedgedriver.exe` trong thư mục project nếu có.

7. **AI analysis thiếu lý do AI**
   - Prompt đã bổ sung `ai_reason`, `time`, `space`.
   - UI hiển thị lý do AI và độ phức tạp.

## 4. Những điểm còn hạn chế

- Crawler Codeforces vẫn phụ thuộc HTML của website, login session và Edge profile. Nếu website đổi layout, cần sửa selector.
- AI usage score chỉ là suy luận từ style code, không phải bằng chứng tuyệt đối.
- Chưa có test tự động trong `src/test`.
- Chưa có báo cáo thử nghiệm thật với số liệu cụ thể từ vài nick.

## 5. Hướng dẫn cài đặt

### 5.1. Yêu cầu môi trường

- Java JDK 17+
- Maven
- SQL Server
- Microsoft Edge
- Gemini API key

### 5.2. Tạo database

Chạy trong PowerShell tại thư mục project:

```powershell
sqlcmd -S localhost -U sa -P "MAT_KHAU_SQL_SERVER" -i src\main\resources\db\schema.sql
```

Nếu dùng SQL Express:

```powershell
sqlcmd -S localhost\SQLEXPRESS -U sa -P "MAT_KHAU_SQL_SERVER" -i src\main\resources\db\schema.sql
```

### 5.3. Cấu hình `application.properties`

Mở file:

```text
src/main/resources/application.properties
```

Cấu hình mẫu:

```properties
db.url=jdbc:sqlserver://localhost;databaseName=code_analyzer;encrypt=true;trustServerCertificate=true;
db.username=sa
db.password=MAT_KHAU_SQL_SERVER

gemini.api.key=GEMINI_API_KEY_CUA_BAN
gemini.model=gemini-2.5-flash
gemini.request.delay.ms=10000

edge.profile.path=D:\\CodeAnalyzerProfile
edge.driver.path=D:\\java\\Java_LastProject\\msedgedriver.exe

crawl.interval.hours=24
crawl.start.time=02:00
crawl.max.submissions=500
```

Nếu dùng SQL Express, URL có thể là:

```properties
db.url=jdbc:sqlserver://localhost\\SQLEXPRESS;databaseName=code_analyzer;encrypt=true;trustServerCertificate=true;
```

### 5.4. Chuẩn bị Edge profile

Chạy:

```powershell
& "C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe" --user-data-dir="D:\CodeAnalyzerProfile"
```

Sau đó:

1. Đăng nhập Codeforces nếu cần.
2. Bật `Remember me`.
3. Đóng hoàn toàn Edge.
4. Đảm bảo `edge.profile.path=D:\\CodeAnalyzerProfile`.

Không dùng profile Edge đang lướt web hằng ngày vì Selenium cần quyền độc chiếm thư mục profile.

## 6. Hướng dẫn chạy chương trình

### 6.1. Tắt app cũ nếu đang chạy

Nếu port `7070` bị chiếm:

```powershell
netstat -ano | findstr :7070
```

Ví dụ thấy PID là `27068`, tắt bằng:

```powershell
taskkill /PID 27068 /F
```

### 6.2. Build

```powershell
mvn clean package -DskipTests
```

Nếu Maven báo không xóa được file JAR trong `target`, nghĩa là app cũ vẫn đang chạy. Tắt process Java cũ rồi build lại.

### 6.3. Chạy app

```powershell
java -jar target\code-analyzer-1.0.0.jar
```

Mở trình duyệt:

```text
http://localhost:7070
```

Nếu chạy thành công, terminal sẽ có dòng:

```text
Web server started at http://localhost:7070
```

## 7. Hướng dẫn sử dụng chương trình

### Bước 1: Thêm nick

Vào tab `Quản lý Nick`:

1. Nhập username.
2. Nhập tên hiển thị nếu muốn.
3. Nhấn `Thêm`.

Hệ thống sẽ kiểm tra nick có tồn tại trên Codeforces. Nếu nick sai hoặc không kiểm tra được do lỗi mạng, nick sẽ không được thêm vào database.

Để xóa nick, nhấn nút `Xóa` ở dòng tương ứng. Khi xóa, toàn bộ submissions, kết quả phân tích AI và đánh giá liên quan cũng bị xóa theo.

### Bước 2: Crawl code

Cách 1: Crawl từng nick

- Vào tab `Quản lý Nick`.
- Nhấn nút `Crawl` ở nick cần crawl.

Cách 2: Crawl toàn bộ nick active

- Vào tab `Crawl`.
- Nhấn `Crawl tất cả`.
- Theo dõi bảng lịch sử crawl.

Nếu log có dòng sau là app đang dùng driver local đúng:

```text
[EdgeDriver] Using local project driver: D:\java\Java_LastProject\msedgedriver.exe
```

### Bước 3: Cấu hình crawl định kỳ

Vào tab `Crawl`:

1. Nhập `Khoảng cách chạy`, ví dụ `24`.
2. Nhập `Giờ bắt đầu`, ví dụ `02:00`.
3. Nhấn `Lưu lịch`.
4. Dùng nút `Bật`, `Tắt`, hoặc `Khởi động lại` scheduler.

### Bước 4: Xem submissions

Vào tab `Submissions` để xem các bài đã crawl được:

- Submission ID
- Người code
- Tên bài
- Platform
- Verdict
- Ngôn ngữ
- Thời gian nộp

### Bước 5: Phân tích AI

Vào tab `Phân tích AI`:

1. Nhấn `Bắt đầu phân tích`.
2. Chờ Gemini phân tích tối đa 10 submission chưa phân tích trong lượt hiện tại.
3. Nhấn `Làm mới` để xem kết quả.

Kết quả gồm:

- CTDL sử dụng.
- Thuật toán sử dụng.
- Độ phức tạp thời gian/bộ nhớ.
- Độ khó.
- AI Score.
- Lý do nghi ngờ AI.
- Chất lượng code.

### Bước 6: Đánh giá nick

Vào tab `Đánh giá`:

1. Nhấn `Đánh giá tất cả`, hoặc đánh giá từng nick ở tab `Quản lý Nick`.
2. Xem level, DS score, Algo score, AI usage, điểm mạnh/yếu, khuyến nghị.

## 8. Cách làm báo cáo thử nghiệm để nộp

Sau khi chạy thật, nên thử ít nhất 3 nick:

- 1 nick mạnh.
- 1 nick trung bình.
- 1 nick mới/beginner.

Mẫu bảng kết quả:

| Nick | Platform | Số submissions crawl | Số submissions phân tích | DS Score | Algo Score | AI Usage | Level | Nhận xét |
|---|---|---:|---:|---:|---:|---|---|---|
| nick_1 | CODEFORCES |  |  |  |  |  |  |  |
| nick_2 | CODEFORCES |  |  |  |  |  |  |  |
| nick_3 | CODEFORCES |  |  |  |  |  |  |  |

Nên chụp màn hình các tab:

- `Quản lý Nick`
- `Crawl`
- `Phân tích AI`
- `Đánh giá`

Kết luận báo cáo nên ghi rõ: hệ thống dùng AI để ước lượng dấu hiệu, không khẳng định tuyệt đối người dùng có dùng AI hay không.

## 9. Đánh giá cuối cùng

Chương trình hiện đã đủ tốt để demo và bảo vệ ở mức khá. Nếu bổ sung được số liệu thử nghiệm thật, ảnh chụp giao diện và một vài test cơ bản, chương trình có thể xem là đạt gần đầy đủ yêu cầu đề bài.

Mức đánh giá hiện tại:

```text
85-90% yêu cầu đề bài
```

Điểm cần làm cuối để tiến gần 100%:

1. Chạy thử thật vài nick.
2. Điền bảng kết quả thử nghiệm.
3. Chụp ảnh giao diện làm minh chứng.
4. Kiểm tra lại crawl Codeforces trên máy trước ngày nộp.
5. Ghi rõ AI usage là điểm nghi ngờ, không phải kết luận tuyệt đối.
