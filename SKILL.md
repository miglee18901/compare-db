---
name: comparedb-maintenance
description: Phát triển, sửa lỗi, kiểm thử và mở rộng dự án CompareDB bằng Java 11, Maven và Hibernate 3.1.3. Dùng khi làm việc với việc cấu hình hai cơ sở dữ liệu MySQL, đọc danh sách bảng, đối chiếu dữ liệu hoặc thay đổi mã nguồn Java của dự án này.
---

# Skill phát triển CompareDB

## Mục đích dự án

CompareDB so sánh dữ liệu giữa hai cơ sở dữ liệu CRBT16M và CRBT21M. Ứng dụng đọc cấu hình kết nối Hibernate của từng môi trường, đọc danh sách bảng từ `tableList.txt`, sau đó sinh báo cáo so sánh.

Hỗ trợ hai chế độ:

1. **Chế độ 1:** Đối chiếu từng bản ghi theo khóa chính hoặc khóa duy nhất; báo cáo bản ghi khác nhau hoặc thiếu ở một môi trường.
2. **Chế độ 2:** Chỉ so sánh số lượng bản ghi của từng bảng.

## Công nghệ và các ràng buộc bắt buộc

- Java 11, Maven.
- Hibernate **3.1.3**; đây là phiên bản bắt buộc, không nâng cấp hoặc thay API sang Hibernate mới nếu không có yêu cầu rõ ràng.
- MySQL Connector/J 5.1.49; H2 chỉ được dùng ở phạm vi test.
- Log4j 2 và JUnit Jupiter 5.
- Không dùng Spring Boot, Spring Data JPA, Jakarta Persistence hay API Hibernate 5/6.

### Tương thích Hibernate 3.1.3

Chỉ dùng các API có trong Hibernate 3.1.3. Đặc biệt:

- Lấy JDBC connection từ `session.connection()`.
- Không dùng `Session.doWork(...)`, `createNativeQuery(...)`, `getJdbcConnectionAccess()` hoặc bất kỳ API nào chỉ có ở Hibernate mới.
- Tạo `SessionFactory` từ `new Configuration().configure(file).buildSessionFactory()`.
- Luôn đóng `Session` trước `SessionFactory`.

## Cấu trúc mã nguồn

```text
src/main/java/org/example/
  Main.java                         Điểm vào CLI
  compare/DbComparator.java         Điều phối so sánh và tạo báo cáo
  compare/TableDataContext.java     Đọc dữ liệu theo lô
  compare/TableMetadata.java        Đọc metadata bảng
  compare/TableCompareResult.java   Kết quả một bảng
  model/TableConfig.java            Cấu hình một dòng trong tableList.txt
  utils/DbHelper.java               Tạo SessionFactory
  utils/DBUtils.java                JDBC: đếm, đọc dữ liệu, metadata
  utils/ProcessUtils.java           So sánh và định dạng giá trị
src/test/java/org/example/compare/DbComparatorTest.java
crbt16m/hibernate_mysql.cfg.xml     Cấu hình DB nguồn thứ nhất
crbt21m/hibernate_mysql.cfg.xml     Cấu hình DB nguồn thứ hai
tableList.txt                       Danh sách bảng cần so sánh
config.properties                   Cấu hình chạy ứng dụng
```

## Quy ước `tableList.txt`

Mỗi dòng có định dạng:

```text
TEN_BANG|COT_KHOA|cot_bo_qua_1,cot_bo_qua_2
```

- `TEN_BANG`: tên bảng cần đối chiếu.
- `COT_KHOA`: khóa chính hoặc cột có unique key.
- Phần cột bỏ qua là tùy chọn.
- Bỏ qua dòng trống và dòng bắt đầu bằng `#`.
- Luôn xác thực bảng, cột khóa và cột bỏ qua trên cả hai database trước khi truy vấn dữ liệu.

## Quy trình thay đổi mã

1. Đọc các lớp liên quan và xác định chế độ so sánh bị ảnh hưởng.
2. Giữ nguyên tương thích Java 11 và Hibernate 3.1.3.
3. Dùng `PreparedStatement` cho truy vấn có giá trị đầu vào; chỉ ghép SQL từ tên bảng/cột đã được xác thực bằng metadata.
4. Đọc dữ liệu theo lô qua `TableDataContext`; không tải toàn bộ bảng vào bộ nhớ.
5. Giữ thứ tự dữ liệu theo cột khóa để thuật toán so sánh hai luồng hoạt động đúng.
6. Bổ sung hoặc cập nhật unit test H2 cho hành vi mới.
7. Chạy `mvn test` khi Maven/Maven Wrapper khả dụng. Nếu không có Maven, biên dịch kiểm tra bằng `javac` với dependency cục bộ và báo rõ giới hạn đó.

## Quy tắc nghiệp vụ

### Chế độ 1

- Cột khóa phải là primary key hoặc unique key trên cả hai môi trường.
- So sánh tập hợp cột chung của hai bảng, trừ cột khóa và các cột được khai báo bỏ qua.
- Nếu khóa so sánh là unique key nhưng không phải primary key, loại primary key của cả hai bảng khỏi tập cột so sánh.
- Bản ghi chỉ có ở một bên là `NOT_MATCH`.
- Báo cáo tổng hợp phải có `TOTAL`, `MATCH`, `NOT_MATCH`; chi tiết phải chỉ rõ khóa và từng cột khác nhau.

### Chế độ 2

- Kiểm tra bảng tồn tại trên cả hai môi trường.
- Đếm bản ghi bằng `SELECT COUNT(*)` qua JDBC connection của Hibernate Session.
- Báo cáo số lượng dưới dạng `TABLE = ..., CRBT16M = ..., CRBT21M = ...`.

## Xử lý lỗi và logging

- Lỗi cấu hình từng bảng không được làm dừng việc so sánh các bảng còn lại; thêm dòng `[Error]` vào báo cáo và tiếp tục.
- Dùng `IllegalArgumentException` cho dòng cấu hình `tableList.txt` sai định dạng.
- Bọc lỗi JDBC/metadata không thể xử lý thành `IllegalStateException` với tên bảng và nguyên nhân gốc.
- Log `INFO` khi bắt đầu kiểm tra/so sánh bảng, `WARN` khi bỏ qua cấu hình không hợp lệ, `ERROR` khi lỗi không thể phục hồi.
- Không log URL chứa thông tin nhạy cảm, mật khẩu database hoặc dữ liệu bí mật.

## Kiểm thử

Giữ các test trong `DbComparatorTest` độc lập bằng hai H2 in-memory database. Khi thêm chức năng, tối thiểu kiểm thử:

- Parse cấu hình bảng hợp lệ, dòng trống, comment và dữ liệu lỗi.
- Bản ghi khớp, khác dữ liệu, thiếu ở từng phía và cột bị bỏ qua.
- Khóa chính và unique key.
- Chế độ đếm bản ghi.
- Bảng/cột không tồn tại, khóa không hợp lệ và cột bỏ qua không hợp lệ.

Khi tạo schema hoặc dữ liệu test, dùng `session.connection().createStatement()` và try-with-resources:

```java
try (Statement statement = session.connection().createStatement()) {
    statement.execute("CREATE TABLE SAMPLE (ID INT PRIMARY KEY)");
}
```

## Tiêu chí hoàn thành

- Mã biên dịch với Java 11.
- Không xuất hiện API không tương thích Hibernate 3.1.3.
- Các Session/SessionFactory/JDBC Statement/ResultSet được đóng đúng cách.
- Test hiện có và test bổ sung đều chạy thành công.
- Báo cáo vẫn giữ đúng định dạng mà người dùng CLI đang sử dụng.
