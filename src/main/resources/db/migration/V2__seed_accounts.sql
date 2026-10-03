-- 2,000 demo accounts (MySQL 8 recursive CTE)
INSERT INTO subscriber_accounts (account_id, msisdn, plan_code, active)
WITH RECURSIVE seq (n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < 2000)
SELECT CONCAT('ACC-', LPAD(n, 6, '0')), CONCAT('98', LPAD(n, 8, '0')),
       IF(MOD(n, 2) = 0, 'UNL-599', 'DATA-299'), TRUE
FROM seq;
