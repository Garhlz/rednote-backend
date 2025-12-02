docker exec -it afternoon-postgres psql -U szu_deploy -d platform_db

```sql
INSERT INTO users (
    email, 
    nickname, 
    password, 
    openid, 
    role, 
    status, 
    created_at, 
    updated_at, 
    is_deleted
) VALUES (
    'test3@test.com', 
    '冒烟测试员', 
    '$2a$10$AnA40ijXKAiX0iUgxjBwS.e9TMaP5m8LPALBfjCebO9H1zy/vOTuK', 
    'smoke_test_openid_3', 
    'USER', 
    1, 
    NOW(), 
    NOW(), 
    0
);
```