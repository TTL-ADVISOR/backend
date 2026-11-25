-- B17 프로젝트 소유자
-- 1) 컬럼을 NULL 허용으로 추가 → 2) 기존 프로젝트에 소유자를 채움 → 3) NOT NULL + FK + 인덱스

ALTER TABLE project
    ADD COLUMN owner_id BIGINT NULL COMMENT '소유자 회원 ID' AFTER id;

-- 로그인 기능 전에 만든 프로젝트(소유자 없음)는 가장 먼저 가입한 회원에게 준다.
-- 운영 데이터는 아직 없고, 개발 DB의 데이터를 지우지 않기 위한 처리다.
-- 회원이 한 명도 없는데 프로젝트가 있으면 다음 단계(NOT NULL)에서 실패해 마이그레이션을 멈춘다.
UPDATE project
SET owner_id = (SELECT first_user.id FROM (SELECT MIN(id) AS id FROM users) AS first_user)
WHERE owner_id IS NULL;

ALTER TABLE project
    MODIFY COLUMN owner_id BIGINT NOT NULL COMMENT '소유자 회원 ID',
    -- CASCADE 없음: 회원을 지우면 수백만 행의 요청 로그가 연쇄 삭제될 수 있다.
    -- 회원 탈퇴(v2)는 프로젝트 삭제와 같은 배치 삭제 절차를 먼저 거친다.
    ADD CONSTRAINT fk_project_owner FOREIGN KEY (owner_id) REFERENCES users (id),
    -- 목록 조회(소유자별 최근 생성 순)용
    ADD INDEX idx_project_owner_created (owner_id, created_at);
