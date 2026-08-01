-- CompareDB fixture: target environment (MySQL 5.7.16+)
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS compare_test_child;
DROP TABLE IF EXISTS compare_test_composite_key;
DROP TABLE IF EXISTS compare_test;
DROP TABLE IF EXISTS compare_test_parent;
SET FOREIGN_KEY_CHECKS = 1;

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
  target_only_column VARCHAR(50) NULL COMMENT 'S02: only in crbt21m',
  full_name VARCHAR(200) NOT NULL COMMENT 'S13 target: customer legal name',
  email VARCHAR(255) NOT NULL COMMENT 'S04: target length is 255',
  age VARCHAR(10) NOT NULL COMMENT 'S03: target stores numeric-looking text',
  salary DECIMAL(18,4) NOT NULL COMMENT 'S05: target decimal(18,4)',
  account_balance DECIMAL(15,2) NOT NULL COMMENT 'S14: target balance minimum 0',
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'S07: target default PENDING',
  is_active TINYINT(1) NOT NULL DEFAULT 1 COMMENT 'Boolean as 1 or 0',
  description TEXT NOT NULL COMMENT 'S06: target NOT NULL',
  birth_date DATE NOT NULL COMMENT 'Date of birth',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'S01: creation timestamp',
  updated_at TIMESTAMP(3) NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP(3) COMMENT 'S15: target auto-updating TIMESTAMP(3)',
  binary_data BLOB NULL COMMENT 'Small binary payload',
  json_data JSON NULL COMMENT 'Native JSON payload',
  json_text_data TEXT NULL COMMENT 'D15: JSON text retained in input key order',
  nullable_value VARCHAR(100) NULL COMMENT 'D06: NULL versus empty string',
  version_number INT NOT NULL DEFAULT 1 COMMENT 'Record version',
  mobile_number VARCHAR(30) NULL COMMENT 'S08: target mobile name',
  PRIMARY KEY (id),
  CONSTRAINT uq_compare_business_status UNIQUE (business_code, status),
  CONSTRAINT chk_compare_balance_target CHECK (account_balance >= 0),
  KEY idx_target_status (status),
  KEY idx_compare_email (email),
  KEY idx_compare_name_status (status, full_name),
  KEY idx_target_email_nonunique (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='CompareDB target test table';

CREATE TABLE compare_test_child (
  child_id INT NOT NULL,
  parent_id INT NOT NULL,
  child_name VARCHAR(100) NOT NULL,
  PRIMARY KEY (child_id),
  KEY idx_child_parent (parent_id),
  CONSTRAINT fk_child_parent_target FOREIGN KEY (parent_id)
    REFERENCES compare_test_parent(parent_id) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Target child: one differently named foreign key';

-- S10/S11: the same logical fields, but reversed composite PK and unique-key order.
CREATE TABLE compare_test_composite_key (
  part_a INT NOT NULL,
  part_b VARCHAR(20) NOT NULL,
  business_code VARCHAR(64) NOT NULL,
  version_no INT NOT NULL,
  note_text VARCHAR(100) NULL,
  PRIMARY KEY (part_b, part_a),
  UNIQUE KEY uq_composite_business_version (version_no, business_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Target composite-key metadata fixture';

START TRANSACTION;
INSERT INTO compare_test_parent (parent_id, parent_code, parent_name) VALUES
  (1, 'PARENT-A', 'Parent A'), (2, 'PARENT-B', 'Parent B');
INSERT INTO compare_test_child (child_id, parent_id, child_name) VALUES
  (1, 1, 'Child A'), (2, 2, 'Child B');
INSERT INTO compare_test_composite_key (part_a, part_b, business_code, version_no, note_text) VALUES
  (1, 'A', 'CMP-A', 1, 'same logical row'), (2, 'B', 'CMP-B', 1, 'same logical row');

-- D01: identical row in both environments.
INSERT INTO compare_test (id,business_code,target_only_column,full_name,email,age,salary,account_balance,status,is_active,description,birth_date,created_at,updated_at,binary_data,json_data,json_text_data,nullable_value,version_number,mobile_number)
VALUES (1,'SAME-001','target-1','Nguyễn Văn An','an@example.test','30',1000.0000,0.00,'ACTIVE',1,'Identical baseline row','1994-01-15','2024-01-01 10:00:00.123456',NULL,X'010203',JSON_OBJECT('name','Nguyễn Văn An','roles',JSON_ARRAY('ADMIN','USER')),'{"name":"Nguyễn Văn An","roles":["ADMIN","USER"]}','same',1,'0902000001');
-- D01: identical values except the intentional S03 age storage type difference.
INSERT INTO compare_test VALUES (2,'SAME-002','target-2','Trần Thị Hồng','hong@example.test','28',2000.5000,10.00,'ACTIVE',0,'Identical leap-day row','2024-02-29','2024-02-29 12:30:00.000001','2024-02-29 12:30:01.000',X'0A0B',JSON_OBJECT('ok',TRUE),'{"ok":true}','',2,'0902000002');

-- D03: target-only rows.
INSERT INTO compare_test VALUES (201,'TARGET-201','target-only','Target Only One','target201@example.test','31',201.0100,0.01,'PENDING',1,'Exists only in CRBT21M','1993-03-01','2024-03-01 00:00:00.000000',NULL,NULL,NULL,NULL,NULL,1,'0902000201');
INSERT INTO compare_test VALUES (202,'TARGET-202','target-only','Target Only Two','target202@example.test','32',202.0200,1.00,'ACTIVE',0,'Exists only in CRBT21M','1992-03-02','2024-03-02 00:00:00.000000',NULL,NULL,NULL,NULL,'target',1,'0902000202');

-- D04: same id, one changed field (full_name).
INSERT INTO compare_test VALUES (300,'DIFF-NAME','target','Nguyễn Văn An - Target','name@example.test','30',300.0000,300.00,'ACTIVE',1,'Name differs only','1994-04-01','2024-04-01 08:00:00.000000',NULL,NULL,NULL,NULL,'name',1,'0902000300');
-- D05: same id, changed email/salary/status/is_active/updated_at.
INSERT INTO compare_test VALUES (301,'DIFF-MULTI','target','Multi Difference','multi.target@example.test','40',301.9999,301.00,'PENDING',0,'Multi-field difference','1984-04-02','2024-04-02 08:00:00.000000','2024-04-02 08:00:11.000',NULL,NULL,NULL,'multi',3,'0902000301');

-- D06: empty string in target, source has NULL.
INSERT INTO compare_test VALUES (400,'NULL-EMPTY','target','Null Empty','null-empty@example.test','25',400.0000,400.00,'ACTIVE',1,'Nullable test','1999-01-01','2024-05-01 00:00:00.000000',NULL,NULL,NULL,NULL,'',1,'0902000400');
-- D07: one trailing space.
INSERT INTO compare_test VALUES (401,'WHITE-SPACE','target','Test Value ','space@example.test','25',401.0000,401.00,'ACTIVE',1,'Whitespace test','1999-01-02','2024-05-01 00:00:00.000000',NULL,NULL,NULL,NULL,'space',1,'0902000401');
-- D08: lower-case status.
INSERT INTO compare_test VALUES (402,'CASE-STATUS','target','Case Status','case@example.test','25',402.0000,402.00,'active',1,'Case-sensitive value test','1999-01-03','2024-05-01 00:00:00.000000',NULL,NULL,NULL,NULL,'case',1,'0902000402');

-- D09: intentionally different Vietnamese accent.
INSERT INTO compare_test VALUES (500,'UNICODE-VI','target','Đặng Minh Duc','dang@example.test','35',500.0000,500.00,'ACTIVE',1,'Công ty Cổ phần Công nghệ Việt Nam','1989-06-01','2024-06-01 09:00:00.000000',NULL,NULL,NULL,NULL,'unicode',1,'0902000500');
-- D10: special characters, unchanged.
INSERT INTO compare_test VALUES (501,'SPECIAL-CHAR','target','O''Connor "Quoted"','special@example.test','36',501.0000,501.00,'ACTIVE',1,'Line one\nLine two\t& <tag> / \\ ✅','1988-06-02','2024-06-02 09:00:00.000000',NULL,NULL,JSON_OBJECT('quote','O''Connor','emoji','✅'),'{"quote":"O''Connor","emoji":"✅"}','special',1,'0902000501');

-- D11: target has no negative balance because of S14 target constraint.
INSERT INTO compare_test VALUES (600,'DECIMAL-0','target','Decimal Zero','decimal0@example.test','20',0.0000,0.00,'ACTIVE',1,'Decimal zero','2004-01-01','2024-07-01 00:00:00.000000',NULL,NULL,NULL,NULL,'0',1,'0902000600');
INSERT INTO compare_test VALUES (601,'DECIMAL-001','target','Decimal Cent','decimal1@example.test','21',0.0100,0.01,'ACTIVE',1,'Decimal cent','2003-01-01','2024-07-01 00:00:00.000000',NULL,NULL,NULL,NULL,'0.01',1,'0902000601');
INSERT INTO compare_test VALUES (602,'DECIMAL-NEG','target','Decimal Negative','decimalneg@example.test','22',0.0100,0.01,'ACTIVE',1,'Source has -0.01 balance; target uses 0.01','2002-01-01','2024-07-01 00:00:00.000000',NULL,NULL,NULL,NULL,'-0.01',1,'0902000602');
INSERT INTO compare_test VALUES (603,'DECIMAL-LARGE','target','Decimal Large','decimallarge@example.test','23',999999.9900,999999.99,'ACTIVE',1,'Large decimal','2001-01-01','2024-07-01 00:00:00.000000',NULL,NULL,NULL,NULL,'large',1,'0902000603');
INSERT INTO compare_test VALUES (604,'DECIMAL-SCALE','target','Decimal Scale','decimalscale@example.test','24',123456789.1234,600.00,'ACTIVE',1,'Target uses four decimal places','2000-01-01','2024-07-01 00:00:00.000000',NULL,NULL,NULL,NULL,'scale',1,'0902000604');

-- D12: updated_at is exactly one second later than source (id 701).
INSERT INTO compare_test VALUES (700,'DATE-NORMAL','target','Date Normal','date@example.test','26',700.0000,700.00,'ACTIVE',1,'Normal date','2023-12-31','2024-08-01 10:10:10.123456','2024-08-01 10:10:11.123',NULL,NULL,NULL,'date',1,'0902000700');
INSERT INTO compare_test VALUES (701,'TIME-ONE-SECOND','target','Time One Second','time@example.test','27',701.0000,701.00,'ACTIVE',1,'Timestamp differs by one second in target','2024-02-29','2024-08-02 10:10:10.123456','2024-08-02 10:10:11.123',NULL,NULL,NULL,'time',1,'0902000701');
INSERT INTO compare_test VALUES (702,'UPDATED-NULL','target','Updated Null','updatednull@example.test','28',702.0000,702.00,'ACTIVE',1,'updated_at is NULL in both','2024-02-29','2024-08-03 10:10:10.123456',NULL,NULL,NULL,NULL,'updated',1,'0902000702');

-- D15/D16: native JSON, raw JSON order, binary, and long text.
INSERT INTO compare_test VALUES (800,'JSON-SAME','target','JSON Same','jsonsame@example.test','29',800.0000,800.00,'ACTIVE',1,'JSON identical','1995-01-01','2024-09-01 00:00:00.000000',NULL,X'CAFEBABE',JSON_OBJECT('name','Nguyễn Văn An','roles',JSON_ARRAY('ADMIN','USER'),'settings',JSON_OBJECT('language','vi','notification',TRUE)),'{"name":"Nguyễn Văn An","roles":["ADMIN","USER"],"settings":{"language":"vi","notification":true}}','json',1,'0902000800');
INSERT INTO compare_test VALUES (801,'JSON-DIFF','target','JSON Diff','jsondiff@example.test','30',801.0000,801.00,'ACTIVE',1,'JSON value differs in target','1994-01-01','2024-09-01 00:00:00.000000',NULL,X'AA',JSON_OBJECT('value',2),'{"value":2}','json',1,'0902000801');
INSERT INTO compare_test VALUES (802,'JSON-ORDER','target','JSON Order','jsonorder@example.test','31',802.0000,802.00,'ACTIVE',1,'Native JSON is equal; raw text order differs','1993-01-01','2024-09-01 00:00:00.000000',NULL,X'BB',JSON_OBJECT('b',2,'a',1),'{"b":2,"a":1}','json-order',1,'0902000802');
INSERT INTO compare_test VALUES (803,'JSON-NESTED','target','JSON Nested','jsonnested@example.test','32',803.0000,803.00,'ACTIVE',1,'Nested JSON and array','1992-01-01','2024-09-01 00:00:00.000000',NULL,X'CC',JSON_OBJECT('nested',JSON_OBJECT('x',1),'items',JSON_ARRAY(1,2,3)),'{"nested":{"x":1},"items":[1,2,3]}','json-nested',1,'0902000803');
INSERT INTO compare_test VALUES (804,'JSON-NULL','target','JSON Null','jsonnull@example.test','33',804.0000,804.00,'ACTIVE',1,'JSON and binary NULL','1991-01-01','2024-09-01 00:00:00.000000',NULL,NULL,NULL,NULL,'json-null',1,'0902000804');
INSERT INTO compare_test VALUES (805,'BINARY-SAME','target','Binary Same','binarysame@example.test','34',805.0000,805.00,'ACTIVE',1,'Same binary','1990-01-01','2024-09-01 00:00:00.000000',NULL,X'00112233',NULL,NULL,'binary',1,'0902000805');
INSERT INTO compare_test VALUES (806,'BINARY-DIFF','target','Binary Diff','binarydiff@example.test','35',806.0000,806.00,'ACTIVE',1,REPEAT('Deterministic long description for CompareDB. ',30),'1989-01-01','2024-09-01 00:00:00.000000',NULL,X'00112234',NULL,NULL,'binary',1,'0902000806');

-- D17/D18: near business keys and boundary values.
INSERT INTO compare_test VALUES (900,'DUP-CASE-A','target','Boundary Minimum','boundarymin@example.test','-2147483648',900.0000,0.00,'ACTIVE',1,'INT minimum','1900-01-01','2024-10-01 00:00:00.000000',NULL,NULL,NULL,NULL,'boundary',1,'0902000900');
INSERT INTO compare_test VALUES (901,'dup-case-b','target','Boundary Maximum','boundarymax@example.test','2147483647',9999999999999.9900,1000000.00,'ACTIVE',1,'INT maximum and decimal near source precision','9999-12-31','2024-10-01 00:00:00.000000',NULL,NULL,NULL,NULL,'boundary',1,'0902000901');
INSERT INTO compare_test VALUES (902,' DUP-CASE-C','target','Leading Space Key','leadspace@example.test','40',902.0000,902.00,'ACTIVE',1,'Leading-space business key','2000-02-02','2024-10-01 00:00:00.000000',NULL,NULL,NULL,NULL,'boundary',1,'0902000902');
COMMIT;

-- MySQL 5.7 accepts CHECK syntax but does not enforce it; all target balances are non-negative.
