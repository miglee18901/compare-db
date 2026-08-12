# CompareDB

CompareDB là ứng dụng dòng lệnh viết bằng Java dùng để đối chiếu dữ liệu giữa hai môi trường MySQL. Công cụ hỗ trợ so sánh chi tiết từng bản ghi hoặc chỉ đối chiếu số lượng bản ghi theo danh sách bảng được cấu hình.

## Tính năng chính

- Kết nối đồng thời tới hai cơ sở dữ liệu MySQL thông qua Hibernate.
- So sánh dữ liệu theo một cột khóa chính (`PRIMARY KEY`) hoặc khóa duy nhất (`UNIQUE KEY`).
- Đọc dữ liệu theo từng batch để hạn chế lượng dữ liệu giữ trong bộ nhớ.
- Cho phép bỏ qua các cột không cần đối chiếu.
- Phát hiện bản ghi thiếu, giá trị khác nhau và cột chỉ tồn tại ở một môi trường.
- Xuất báo cáo văn bản UTF-8 có tên kèm thời gian chạy.
- Có bộ test JUnit 5 sử dụng cơ sở dữ liệu H2 in-memory, không phụ thuộc MySQL thật.

## Yêu cầu môi trường

- JDK 11.
- Maven 3.6 trở lên hoặc Maven đi kèm IntelliJ IDEA.
- Hai cơ sở dữ liệu MySQL mà máy chạy ứng dụng có thể truy cập.
- Tài khoản database có quyền đọc metadata và thực hiện câu lệnh `SELECT` trên các bảng cần so sánh.

Các thư viện chính gồm Hibernate 3.1.3, MySQL Connector/J 5.1.49, Log4j2 2.20.0, JUnit 5.10.0 và H2 2.2.224 cho test.

## Cấu trúc thư mục

```text
CompareDB/
├── src/main/java/org/example/
│   ├── Main.java                 # Điểm khởi chạy ứng dụng
│   ├── compare/                  # Logic đọc metadata và so sánh
│   ├── model/                    # Mô hình cấu hình bảng
│   └── utils/                    # Đọc cấu hình, kết nối DB và tiện ích
├── src/main/resources/log4j2.xml # Cấu hình log
├── src/test/java/                # Test JUnit 5
├── 01_crbt16m.sql                # SQL fixture cho môi trường thứ nhất
├── 02_crbt21m.sql                # SQL fixture cho môi trường thứ hai
├── etc/                          # Toàn bộ cấu hình runtime
│   ├── config.properties
│   ├── tableList.txt
│   ├── crbt16m/hibernate_mysql.cfg.xml
│   └── crbt21m/hibernate_mysql.cfg.xml
└── pom.xml                       # Cấu hình Maven
```

Repository cung cấp sẵn cấu trúc và giá trị mẫu trong `etc/`. Trước khi chạy, cần thay thông tin kết nối mẫu trong hai file Hibernate. Thư mục `result/` được ứng dụng tự tạo và không được đưa vào Git.

## Cài đặt

Clone repository và chuyển vào thư mục project:

```powershell
git clone https://github.com/miglee18901/CompareDB.git
cd CompareDB
```

Kiểm tra phiên bản Java và Maven:

```powershell
java -version
mvn -version
```

Biên dịch project:

```powershell
mvn -DskipTests package
```

## Cấu hình kết nối database

Chỉnh file `etc/crbt16m/hibernate_mysql.cfg.xml` cho môi trường thứ nhất và `etc/crbt21m/hibernate_mysql.cfg.xml` cho môi trường thứ hai. Hai file đã có sẵn cấu trúc sau:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE hibernate-configuration PUBLIC
        "-//Hibernate/Hibernate Configuration DTD 3.0//EN"
        "http://hibernate.sourceforge.net/hibernate-configuration-3.0.dtd">
<hibernate-configuration>
    <session-factory>
        <property name="hibernate.connection.driver_class">com.mysql.jdbc.Driver</property>
        <property name="hibernate.connection.url">jdbc:mysql://localhost:3306/ten_database?useUnicode=true&amp;characterEncoding=UTF-8</property>
        <property name="hibernate.connection.username">ten_dang_nhap</property>
        <property name="hibernate.connection.password">mat_khau</property>
        <property name="hibernate.dialect">org.hibernate.dialect.MySQLDialect</property>
        <property name="hibernate.show_sql">false</property>
    </session-factory>
</hibernate-configuration>
```

Hai file mẫu đang được Git theo dõi để project có đủ cấu trúc cấu hình. Không commit mật khẩu thật; trước khi tạo commit, luôn kiểm tra diff và thay thông tin nhạy cảm bằng giá trị mẫu. Nên dùng tài khoản chỉ có quyền đọc.

Nếu cần tạo dữ liệu thử trên MySQL, có thể tham khảo hai fixture `01_crbt16m.sql` và `02_crbt21m.sql`. Hãy đọc nội dung SQL và xác nhận đúng database đích trước khi thực thi.

## Cấu hình chạy

Chỉnh `etc/config.properties`:

```properties
MODE=1
BATCH_SIZE=1000
PATH_STATISTICS_FILE=result
```

Ý nghĩa các thuộc tính:

| Thuộc tính | Bắt buộc | Mặc định | Mô tả |
|---|---:|---:|---|
| `MODE` | Không | `1` | `1`: so sánh chi tiết; `2`: chỉ đếm bản ghi. |
| `BATCH_SIZE` | Không | `1000` | Số bản ghi đọc trong mỗi batch. Nên dùng số nguyên dương. |
| `PATH_STATISTICS_FILE` | Không | `.` | Thư mục chứa báo cáo. Ứng dụng sẽ tạo thư mục nếu chưa tồn tại. |

File `etc/config.properties` phải tồn tại thì chương trình mới khởi chạy. Giá trị mặc định chỉ được áp dụng khi thuộc tính tương ứng bị thiếu hoặc không hợp lệ.

### Chế độ 1: so sánh chi tiết

Với `MODE=1`, ứng dụng:

- Kiểm tra bảng và cột khóa ở cả hai môi trường.
- Yêu cầu cột khóa là `PRIMARY KEY` hoặc `UNIQUE KEY`.
- So sánh các cột chung sau khi loại cột khóa và danh sách cột bỏ qua.
- Nếu dùng `UNIQUE KEY` làm khóa so sánh, các cột khóa chính cũng được loại khỏi phép so sánh.
- Báo cáo giá trị khác nhau, bản ghi chỉ có ở một môi trường và cột bị thiếu.

### Chế độ 2: so sánh số lượng

Với `MODE=2`, ứng dụng chỉ đếm số bản ghi của từng bảng tồn tại ở cả hai môi trường. Chế độ này nhanh hơn và phù hợp để kiểm tra tổng quan trước khi so sánh chi tiết.

## Khai báo bảng cần so sánh

Mỗi dòng hợp lệ trong `etc/tableList.txt` có định dạng:

```text
TEN_BANG|COT_KHOA|COT_BO_QUA_1,COT_BO_QUA_2
```

Ví dụ:

```text
SUBS_INFO|MSISDN|UPDATED_AT,LAST_SYNC_TIME
PROD_SPEC|PROD_SPEC_ID|
```

Quy tắc:

- Dòng trống và dòng bắt đầu bằng `#` được bỏ qua.
- Tên bảng và cột khóa là bắt buộc.
- Chỉ hỗ trợ một cột khóa so sánh cho mỗi bảng; chưa hỗ trợ khóa ghép.
- Cột khóa phải tồn tại ở cả hai môi trường và là khóa chính hoặc khóa duy nhất.
- Cột bỏ qua được phân tách bằng dấu phẩy, phải tồn tại ở cả hai môi trường và không được trùng cột khóa.
- Để trống phần thứ ba nếu không có cột cần bỏ qua, nhưng vẫn giữ dấu `|` cuối dòng.

## Chạy ứng dụng

Cách thuận tiện nhất là mở project trong IntelliJ IDEA, chọn JDK 11, tải các dependency Maven, sau đó chạy class `org.example.main.Start`.

Working directory phải là thư mục gốc repository. Trước khi chạy, cấu trúc tối thiểu cần có là:

```text
CompareDB/
└── etc/
    ├── config.properties
    ├── tableList.txt
    ├── crbt16m/hibernate_mysql.cfg.xml
    └── crbt21m/hibernate_mysql.cfg.xml
```

Trong quá trình chạy, log được in ra console. Khi hoàn tất, chương trình in đường dẫn báo cáo dưới dạng:

```text
PATH_STATISTICS_FILE: D:\work\java\CompareDB\result\result_20260802123045.txt
```

## Định dạng kết quả

Tên file báo cáo có dạng `result_yyyyMMddHHmmss.txt`.

Ví dụ báo cáo ở chế độ chi tiết:

```text
TABLE = SUBS_INFO, TOTAL = 4, MATCH = 1, NOT_MATCH = 3

COMPARE:
TABLE = SUBS_INFO
MSISDN key = '2':
SINGER (CRBT16M) = 'S2_old', SINGER (CRBT21M) = 'S2_new'
MSISDN key = 3: CRBT21M = <does not exist>
```

Ý nghĩa:

- `TOTAL`: tổng số khóa được duyệt trên cả hai môi trường.
- `MATCH`: số bản ghi khớp ở các cột được so sánh.
- `NOT_MATCH`: số bản ghi khác dữ liệu, thiếu bản ghi hoặc chịu ảnh hưởng bởi khác biệt cột.
- `<does not exist>`: khóa chỉ tồn tại ở môi trường còn lại.

Ví dụ báo cáo ở chế độ đếm:

```text
TABLE = SUBS_INFO, CRBT16M = 1200, CRBT21M = 1198
TABLE = PROD_SPEC, CRBT16M = 450, CRBT21M = 450
```

Nếu xảy ra lỗi hệ thống sau khi đã tạo đường dẫn kết quả, ứng dụng cố gắng ghi thông tin lỗi vào file báo cáo tương ứng. Chi tiết đầy đủ hơn được ghi trên console qua Log4j2.

## Build và test

Chạy toàn bộ test:

```powershell
mvn test
```

Đóng gói và chạy test:

```powershell
mvn package
```

Chỉ biên dịch và đóng gói:

```powershell
mvn -DskipTests package
```

Artifact sau khi build nằm trong thư mục `target/`. POM hiện chưa cấu hình executable/fat JAR, vì vậy JAR mặc định không thể chạy độc lập bằng `java -jar` nếu không tự bổ sung classpath và `Main-Class`.

## Xử lý sự cố

### Không tìm thấy file cấu hình trong `etc/`

Đảm bảo working directory là thư mục gốc repository và tên file đúng chính tả.

### Không tìm thấy file cấu hình Hibernate

Kiểm tra đủ hai đường dẫn cố định:

```text
etc/crbt16m/hibernate_mysql.cfg.xml
etc/crbt21m/hibernate_mysql.cfg.xml
```

### Không kết nối được MySQL

Kiểm tra hostname, port, tên database, tài khoản, mật khẩu, quyền truy cập và firewall. Với MySQL mới, có thể cần bổ sung tham số kết nối phù hợp với cấu hình SSL hoặc múi giờ của server.

### Bảng bị bỏ qua khi chạy chế độ 1

Xem cảnh báo trên console. Nguyên nhân thường gặp là bảng không tồn tại ở một môi trường, cột khóa không phải `PRIMARY KEY`/`UNIQUE KEY`, cột bỏ qua không tồn tại hoặc trùng với cột khóa.

### Kết quả tốn nhiều thời gian hoặc dung lượng

Giảm phạm vi bảng, dùng `MODE=2` để kiểm tra tổng quan trước, điều chỉnh `BATCH_SIZE`, hoặc thêm các cột biến động không cần thiết vào danh sách bỏ qua. Chế độ chi tiết có thể tạo báo cáo lớn khi hai môi trường khác nhau nhiều.

## Lưu ý bảo mật và vận hành

- Không commit mật khẩu, file cấu hình kết nối hoặc báo cáo chứa dữ liệu thật.
- Nên dùng tài khoản database chỉ có quyền đọc.
- Báo cáo có thể chứa dữ liệu nghiệp vụ; cần lưu trữ và chia sẻ theo quy định bảo mật của hệ thống.
- Nên chạy thử trên phạm vi nhỏ và kiểm tra dung lượng thư mục kết quả trước khi đối chiếu database lớn.
- Nên sao lưu hoặc dùng môi trường thử nghiệm khi chạy các SQL fixture đi kèm.

## Đóng góp

Khi sửa mã nguồn, giữ chuẩn Java 11, thụt lề bốn dấu cách và dùng log Log4j2 có tham số. Test mới đặt trong `src/test/java`, cùng package với mã được kiểm thử và tên class kết thúc bằng `Test`.

Trước khi tạo pull request, chạy:

```powershell
mvn test
```

Pull request nên nêu rõ chế độ so sánh bị ảnh hưởng, giả định schema/cấu hình, kết quả mong đợi và đoạn báo cáo đại diện nếu định dạng output thay đổi.
