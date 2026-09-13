ALTER TABLE package_orders ADD COLUMN IF NOT EXISTS plan_name_snapshot VARCHAR(100);
ALTER TABLE package_orders ADD COLUMN IF NOT EXISTS quota_snapshot INT;
ALTER TABLE package_orders ADD COLUMN IF NOT EXISTS duration_days_snapshot INT;
ALTER TABLE package_orders ADD COLUMN IF NOT EXISTS bank_bin_snapshot VARCHAR(6);
ALTER TABLE package_orders ADD COLUMN IF NOT EXISTS account_number_snapshot VARCHAR(19);
ALTER TABLE package_orders ADD COLUMN IF NOT EXISTS account_name_snapshot VARCHAR(150);

UPDATE package_orders o SET
    plan_name_snapshot = COALESCE(o.plan_name_snapshot, p.name),
    quota_snapshot = COALESCE(o.quota_snapshot, p.listing_quota),
    duration_days_snapshot = COALESCE(o.duration_days_snapshot, p.duration_days)
FROM service_plans p WHERE p.code = o.plan_code;

UPDATE package_orders o SET
    bank_bin_snapshot = COALESCE(o.bank_bin_snapshot, b.bank_bin),
    account_number_snapshot = COALESCE(o.account_number_snapshot, b.account_number),
    account_name_snapshot = COALESCE(o.account_name_snapshot, b.account_name)
FROM bank_settings b WHERE b.singleton_id = 1;
