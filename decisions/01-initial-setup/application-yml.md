# 기술 결정: application.yml 설정

관련 이슈: #3 Spring Boot 프로젝트 초기 환경 세팅

## 주요 설정 항목

### DB 연결 (spring.datasource)
- PostgreSQL, localhost:5432, DB명 interview_db
- username/password는 Docker Compose 설정과 일치시킴
- 배포 시에는 환경변수로 분리 예정

### ddl-auto: create
- 개발 초기라 엔티티 구조가 자주 바뀔 수 있어 매 실행 시 테이블 재생성
- create-drop 대신 create를 선택한 이유: 앱 종료 후에도 테이블이 남아있어 DB 도구로 데이터 확인 가능
- 개발 중기에는 update(데이터 유지), 운영에서는 none(직접 관리)으로 변경 예정

### show-sql / format_sql: true
- 실행되는 SQL을 콘솔에 출력하여 JPA 동작 학습 및 디버깅에 활용
- 운영에서는 false로 변경
