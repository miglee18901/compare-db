-- CompareDB fixture: source environment (MySQL 5.7.16+)
-- Encoding: UTF-8.  Re-runnable: all fixture tables are dropped first.
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS compare_test_child;
DROP TABLE IF EXISTS compare_test_composite_key;
DROP TABLE IF EXISTS compare_test;
DROP TABLE IF EXISTS compare_test_parent;
SET FOREIGN_KEY_CHECKS = 1;

-- Parent table: identical parent data and a primary/unique key target for FKs.
CREATE TABLE compare_test_parent (
  parent_id INT NOT NULL COMMENT 'S01: parent primary key',
  parent_code VARCHAR(30) NOT NULL COMMENT 'S01: parent business code',
  parent_name VARCHAR(100) NOT NULL COMMENT 'Parent display name',
  PRIMARY KEY (parent_id),
  UNIQUE KEY uq_parent_code (parent_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='CompareDB parent fixture';

CREATE TABLE compare_test (
  id INT NOT NULL COMMENT 'S01: stable row identifier',
  business_code VARCHAR(64) NOT NULL COMMENT 'S01: unique business code',
  full_name VARCHAR(200) NOT NULL COMMENT 'S13 source: legal full name',
  email VARCHAR(100) NOT NULL COMMENT 'S04: source length is 100',
  age INT NOT NULL COMMENT 'S03: source integer age',
  salary DECIMAL(15,2) NOT NULL COMMENT 'S05: source decimal(15,2)',
  account_balance DECIMAL(15,2) NOT NULL COMMENT 'S14: balance minimum -1000000',
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT 'S07: source default ACTIVE',
  is_active TINYINT(1) NOT NULL DEFAULT 1 COMMENT 'Boolean as 1 or 0',
  description TEXT NULL COMMENT 'S06: source allows NULL',
  birth_date DATE NOT NULL COMMENT 'Date of birth',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'S01: creation timestamp',
  updated_at DATETIME(6) NULL DEFAULT NULL COMMENT 'S15: source nullable DATETIME(6), no auto update',
  binary_data BLOB NULL COMMENT 'Small binary payload',
  json_data JSON NULL COMMENT 'Native JSON payload',
  json_text_data TEXT NULL COMMENT 'D15: JSON text retained in input key order',
  nullable_value VARCHAR(100) NULL COMMENT 'D06: NULL versus empty string',
  version_number INT NOT NULL DEFAULT 1 COMMENT 'Record version',
  source_only_column VARCHAR(50) NULL COMMENT 'S02: only in crbt16m',
  phone_number VARCHAR(30) NULL COMMENT 'S08: source phone name',
  PRIMARY KEY (id),
  CONSTRAINT uq_compare_business_code UNIQUE (business_code),
  CONSTRAINT chk_compare_balance_source CHECK (account_balance >= -1000000),
  KEY idx_source_active (is_active),
  KEY idx_compare_email (email),
  KEY idx_compare_name_status (full_name, status),
  UNIQUE KEY ux_source_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='CompareDB source test table';

-- S12: source-only index and same-name index with a different column order.
CREATE TABLE compare_test_child (
  child_id INT NOT NULL,
  parent_id INT NOT NULL,
  parent_code VARCHAR(30) NOT NULL,
  child_name VARCHAR(100) NOT NULL,
  PRIMARY KEY (child_id),
  KEY idx_child_parent (parent_id),
  CONSTRAINT fk_child_parent_source FOREIGN KEY (parent_id)
    REFERENCES compare_test_parent(parent_id) ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT fk_child_parent_code_source FOREIGN KEY (parent_code)
    REFERENCES compare_test_parent(parent_code) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Source child: two named foreign keys';

-- S10/S11: composite PK/unique metadata source order.
CREATE TABLE compare_test_composite_key (
  part_a INT NOT NULL,
  part_b VARCHAR(20) NOT NULL,
  business_code VARCHAR(64) NOT NULL,
  version_no INT NOT NULL,
  note_text VARCHAR(100) NULL,
  PRIMARY KEY (part_a, part_b),
  UNIQUE KEY uq_composite_business_version (business_code, version_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Source composite-key metadata fixture';

START TRANSACTION;
INSERT INTO compare_test_parent (parent_id, parent_code, parent_name) VALUES
  (1, 'PARENT-A', 'Parent A'), (2, 'PARENT-B', 'Parent B');
INSERT INTO compare_test_child (child_id, parent_id, parent_code, child_name) VALUES
  (1, 1, 'PARENT-A', 'Child A'), (2, 2, 'PARENT-B', 'Child B');
INSERT INTO compare_test_composite_key (part_a, part_b, business_code, version_no, note_text) VALUES
  (1, 'A', 'CMP-A', 1, 'same logical row'), (2, 'B', 'CMP-B', 1, 'same logical row');

-- D01: identical row in both environments.
INSERT INTO compare_test (id,business_code,full_name,email,age,salary,account_balance,status,is_active,description,birth_date,created_at,updated_at,binary_data,json_data,json_text_data,nullable_value,version_number,source_only_column,phone_number)
VALUES (1,'SAME-001','Nguyễn Văn An','an@example.test',30,1000.00,0.00,'ACTIVE',1,'Identical baseline row','1994-01-15','2024-01-01 10:00:00.123456',NULL,X'010203',JSON_OBJECT('name','Nguyễn Văn An','roles',JSON_ARRAY('ADMIN','USER')),'{"name":"Nguyễn Văn An","roles":["ADMIN","USER"]}','same',1,'source-1','0901000001');
-- D01: identical row, including a leap-day birth date.
INSERT INTO compare_test VALUES (2,'SAME-002','Trần Thị Hồng','hong@example.test',28,2000.50,10.00,'ACTIVE',0,'Identical leap-day row','2024-02-29','2024-02-29 12:30:00.000001','2024-02-29 12:30:01.000001',X'0A0B',JSON_OBJECT('ok',TRUE),'{"ok":true}','',2,'source-2','0901000002');

-- D02: source-only rows.
INSERT INTO compare_test VALUES (101,'SOURCE-101','Source Only One','source101@example.test',31,101.01,-0.01,'ACTIVE',1,'Exists only in CRBT16M','1993-03-01','2024-03-01 00:00:00.000000',NULL,NULL,NULL,NULL,NULL,1,'source-only','0901000101');
INSERT INTO compare_test VALUES (102,'SOURCE-102','Source Only Two','source102@example.test',32,102.02,1.00,'PENDING',0,'Exists only in CRBT16M','1992-03-02','2024-03-02 00:00:00.000000',NULL,NULL,NULL,NULL,'source',1,'source-only','0901000102');

-- D04: same id, one changed field (full_name).
INSERT INTO compare_test VALUES (300,'DIFF-NAME','Nguyễn Văn An - Source','name@example.test',30,300.00,300.00,'ACTIVE',1,'Name differs only','1994-04-01','2024-04-01 08:00:00.000000',NULL,NULL,NULL,NULL,'name',1,'source','0901000300');
-- D05: same id, multiple changed fields in target.
INSERT INTO compare_test VALUES (301,'DIFF-MULTI','Multi Difference','multi.source@example.test',40,301.10,301.00,'ACTIVE',1,'Multi-field difference','1984-04-02','2024-04-02 08:00:00.000000','2024-04-02 08:00:10.000000',NULL,NULL,NULL,'multi',3,'source','0901000301');

-- D06: NULL in source; target has empty string.
INSERT INTO compare_test VALUES (400,'NULL-EMPTY','Null Empty','null-empty@example.test',25,400.00,400.00,'ACTIVE',1,'Nullable test','1999-01-01','2024-05-01 00:00:00.000000',NULL,NULL,NULL,NULL,NULL,1,'source','0901000400');
-- D07: target has one trailing space in full_name.
INSERT INTO compare_test VALUES (401,'WHITE-SPACE','Test Value','space@example.test',25,401.00,401.00,'ACTIVE',1,'Whitespace test','1999-01-02','2024-05-01 00:00:00.000000',NULL,NULL,NULL,NULL,'space',1,'source','0901000401');
-- D08: status casing differs in target.
INSERT INTO compare_test VALUES (402,'CASE-STATUS','Case Status','case@example.test',25,402.00,402.00,'ACTIVE',1,'Case-sensitive value test','1999-01-03','2024-05-01 00:00:00.000000',NULL,NULL,NULL,NULL,'case',1,'source','0901000402');

-- D09: Vietnamese Unicode; target intentionally has a different accent.
INSERT INTO compare_test VALUES (500,'UNICODE-VI','Đặng Minh Đức','dang@example.test',35,500.00,500.00,'ACTIVE',1,'Công ty Cổ phần Công nghệ Việt Nam','1989-06-01','2024-06-01 09:00:00.000000',NULL,NULL,NULL,NULL,'unicode',1,'source','0901000500');
-- D10: quotes, newline escape, tab escape, ampersand, angle brackets, slash, backslash and emoji.
INSERT INTO compare_test VALUES (501,'SPECIAL-CHAR','O''Connor "Quoted"','special@example.test',36,501.00,501.00,'ACTIVE',1,'Line one\nLine two\t& <tag> / \\ ✅','1988-06-02','2024-06-02 09:00:00.000000',NULL,NULL,JSON_OBJECT('quote','O''Connor','emoji','✅'),'{"quote":"O''Connor","emoji":"✅"}','special',1,'source','0901000501');

-- D11: decimals (0, 0.01, -0.01, 999999.99, and source scale two).
INSERT INTO compare_test VALUES (600,'DECIMAL-0','Decimal Zero','decimal0@example.test',20,0.00,0.00,'ACTIVE',1,'Decimal zero','2004-01-01','2024-07-01 00:00:00.000000',NULL,NULL,NULL,NULL,'0',1,'source','0901000600');
INSERT INTO compare_test VALUES (601,'DECIMAL-001','Decimal Cent','decimal1@example.test',21,0.01,0.01,'ACTIVE',1,'Decimal cent','2003-01-01','2024-07-01 00:00:00.000000',NULL,NULL,NULL,NULL,'0.01',1,'source','0901000601');
INSERT INTO compare_test VALUES (602,'DECIMAL-NEG','Decimal Negative','decimalneg@example.test',22,0.01,-0.01,'ACTIVE',1,'Target must remain non-negative','2002-01-01','2024-07-01 00:00:00.000000',NULL,NULL,NULL,NULL,'-0.01',1,'source','0901000602');
INSERT INTO compare_test VALUES (603,'DECIMAL-LARGE','Decimal Large','decimallarge@example.test',23,999999.99,999999.99,'ACTIVE',1,'Large decimal','2001-01-01','2024-07-01 00:00:00.000000',NULL,NULL,NULL,NULL,'large',1,'source','0901000603');
INSERT INTO compare_test VALUES (604,'DECIMAL-SCALE','Decimal Scale','decimalscale@example.test',24,123456789.12,600.00,'ACTIVE',1,'Target uses four decimal places','2000-01-01','2024-07-01 00:00:00.000000',NULL,NULL,NULL,NULL,'scale',1,'source','0901000604');

-- D12: timestamps: target differs exactly one second; updated_at NULL test is id 702.
INSERT INTO compare_test VALUES (700,'DATE-NORMAL','Date Normal','date@example.test',26,700.00,700.00,'ACTIVE',1,'Normal date','2023-12-31','2024-08-01 10:10:10.123456','2024-08-01 10:10:11.123456',NULL,NULL,NULL,'date',1,'source','0901000700');
INSERT INTO compare_test VALUES (701,'TIME-ONE-SECOND','Time One Second','time@example.test',27,701.00,701.00,'ACTIVE',1,'Timestamp differs by one second in target','2024-02-29','2024-08-02 10:10:10.123456','2024-08-02 10:10:10.123456',NULL,NULL,NULL,'time',1,'source','0901000701');
INSERT INTO compare_test VALUES (702,'UPDATED-NULL','Updated Null','updatednull@example.test',28,702.00,702.00,'ACTIVE',1,'updated_at is NULL in both','2024-02-29','2024-08-03 10:10:10.123456',NULL,NULL,NULL,NULL,'updated',1,'source','0901000702');

-- D15/D16: JSON, binary, raw JSON ordering and a deterministic long text (>1000 chars).
INSERT INTO compare_test VALUES (800,'JSON-SAME','JSON Same','jsonsame@example.test',29,800.00,800.00,'ACTIVE',1,'JSON identical','1995-01-01','2024-09-01 00:00:00.000000',NULL,X'CAFEBABE',JSON_OBJECT('name','Nguyễn Văn An','roles',JSON_ARRAY('ADMIN','USER'),'settings',JSON_OBJECT('language','vi','notification',TRUE)),'{"name":"Nguyễn Văn An","roles":["ADMIN","USER"],"settings":{"language":"vi","notification":true}}','json',1,'source','0901000800');
INSERT INTO compare_test VALUES (801,'JSON-DIFF','JSON Diff','jsondiff@example.test',30,801.00,801.00,'ACTIVE',1,'JSON value differs in target','1994-01-01','2024-09-01 00:00:00.000000',NULL,X'AA',JSON_OBJECT('value',1),'{"value":1}','json',1,'source','0901000801');
INSERT INTO compare_test VALUES (802,'JSON-ORDER','JSON Order','jsonorder@example.test',31,802.00,802.00,'ACTIVE',1,'Native JSON is equal; raw text order differs','1993-01-01','2024-09-01 00:00:00.000000',NULL,X'BB',JSON_OBJECT('a',1,'b',2),'{"a":1,"b":2}','json-order',1,'source','0901000802');
INSERT INTO compare_test VALUES (803,'JSON-NESTED','JSON Nested','jsonnested@example.test',32,803.00,803.00,'ACTIVE',1,'Nested JSON and array','1992-01-01','2024-09-01 00:00:00.000000',NULL,X'CC',JSON_OBJECT('nested',JSON_OBJECT('x',1),'items',JSON_ARRAY(1,2,3)),'{"nested":{"x":1},"items":[1,2,3]}','json-nested',1,'source','0901000803');
INSERT INTO compare_test VALUES (804,'JSON-NULL','JSON Null','jsonnull@example.test',33,804.00,804.00,'ACTIVE',1,'JSON and binary NULL','1991-01-01','2024-09-01 00:00:00.000000',NULL,NULL,NULL,NULL,'json-null',1,'source','0901000804');
INSERT INTO compare_test VALUES (805,'BINARY-SAME','Binary Same','binarysame@example.test',34,805.00,805.00,'ACTIVE',1,'Same binary','1990-01-01','2024-09-01 00:00:00.000000',NULL,X'00112233',NULL,NULL,'binary',1,'source','0901000805');
INSERT INTO compare_test VALUES (806,'BINARY-DIFF','Binary Diff','binarydiff@example.test',35,806.00,806.00,'ACTIVE',1,REPEAT('Deterministic long description for CompareDB. ',30),'1989-01-01','2024-09-01 00:00:00.000000',NULL,X'00112233',NULL,NULL,'binary',1,'source','0901000806');

-- D17/D18: near business keys and boundary values; no unique collision under the default collation.
INSERT INTO compare_test VALUES (900,'DUP-CASE-A','Boundary Minimum','boundarymin@example.test',-2147483648,900.00,0.00,'ACTIVE',1,'INT minimum','1900-01-01','2024-10-01 00:00:00.000000',NULL,NULL,NULL,NULL,'boundary',1,'source','0901000900');
INSERT INTO compare_test VALUES (901,'dup-case-b','Boundary Maximum','boundarymax@example.test',2147483647,9999999999999.99,1000000.00,'ACTIVE',1,'INT maximum and decimal near source precision','9999-12-31','2024-10-01 00:00:00.000000',NULL,NULL,NULL,NULL,'boundary',1,'source','0901000901');
INSERT INTO compare_test VALUES (902,' DUP-CASE-C','Leading Space Key','leadspace@example.test',40,902.00,902.00,'ACTIVE',1,'Leading-space business key','2000-02-02','2024-10-01 00:00:00.000000',NULL,NULL,NULL,NULL,'boundary',1,'source','0901000902');
COMMIT;

-- MySQL 5.7 accepts CHECK syntax but does not enforce it; do not insert values below -1000000.
