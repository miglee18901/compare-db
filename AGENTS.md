# Hướng dẫn Repository

## Cấu trúc Project & Tổ chức Module

Đây là ứng dụng Maven Java 11 để so sánh hai môi trường MySQL. Mã nguồn chính nằm tại `src/main/java/org/example`: `Main.java` điều phối chương trình, `compare/` chứa logic so sánh, `model/` chứa cấu hình bảng đã phân tích, và `utils/` chứa tiện ích cấu hình và kết nối database. Cấu hình log nằm tại `src/main/resources/log4j2.xml`.

File kết nối database là `crbt16m/hibernate_mysql.cfg.xml` và `crbt21m/hibernate_mysql.cfg.xml`. Khai báo bảng và khóa so sánh trong `tableList.txt`; cấu hình chạy nằm trong `config.properties`. SQL fixture là `01_crbt16m.sql` và `02_crbt21m.sql`. Test nằm tại `src/test/java/org/example/compare/`.

## Lệnh Build, Test & Phát triển

Chạy lệnh tại thư mục gốc repository:

```powershell
mvn -DskipTests package  # Biên dịch và đóng gói, không chạy test
mvn test                 # Chạy test JUnit 5
mvn package              # Biên dịch, chạy test và đóng gói
```

Project dùng Java 11, Hibernate 3.1.3, MySQL Connector/J 5.1.49, Log4j2 và JUnit 5. Nếu `mvn` chưa có trong PATH, dùng Maven đi kèm IntelliJ. Chạy `org.example.Main` trên IntelliJ sau khi kiểm tra hai file cấu hình database và `tableList.txt`. Kết quả được ghi vào thư mục `result/` đã cấu hình.

## Quy ước Code & Đặt tên

Dùng bốn dấu cách để thụt lề, lưu file UTF-8 và dùng ngoặc nhọn Java chuẩn. Package viết thường (`org.example.compare`), class theo PascalCase, biến và method theo camelCase, hằng số theo UPPER_SNAKE_CASE. Ưu tiên log Log4j2 có tham số, ví dụ `logger.info("Đã so sánh bảng {}", tableName)`, thay vì nối chuỗi. Kết quả so sánh phải xác định và hiển thị rõ giá trị có dấu nháy.

## Hướng dẫn Test

Thêm test JUnit 5 tập trung tại `src/test/java`, tên class kết thúc bằng `Test`, cùng package với mã được sửa. Cần bao phủ dữ liệu khớp, bản ghi thiếu, cột chỉ có ở một môi trường và thay đổi định dạng report. Dùng fixture in-memory độc lập khi tương thích với Hibernate phiên bản cũ. Chạy `mvn test` trước khi gửi thay đổi; nếu lỗi hạ tầng test không liên quan, mô tả rõ trong pull request.

## Commit & Pull Request

Không đọc được lịch sử Git trong môi trường hiện tại, vì vậy dùng tiêu đề commit ngắn, dạng mệnh lệnh, ví dụ `Sửa định dạng report cột thiếu`. Mỗi commit chỉ nên có một phạm vi thay đổi. Pull request cần nêu mode so sánh bị ảnh hưởng, giả định cấu hình/schema, kết quả report mong đợi và lệnh đã kiểm tra. Khi thay đổi format output, đính kèm đoạn report đại diện; không commit mật khẩu database hoặc file kết quả sinh ra.