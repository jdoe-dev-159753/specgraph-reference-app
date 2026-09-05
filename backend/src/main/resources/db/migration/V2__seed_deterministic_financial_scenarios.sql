-- Deterministic synthetic R2 scenarios exercise reviewer paths without asserting criminal conduct.
-- Rule weights are source-fixture contributions displayed as facts, not scores computed by this app.
INSERT INTO customers(customer_id) VALUES
('11111111-1111-1111-1111-111111111111'),
('22222222-2222-2222-2222-222222222222'),
('33333333-3333-3333-3333-333333333333'),
('44444444-4444-4444-4444-444444444444');
INSERT INTO risk_rules(rule_id, rule_name, applies_to, threshold_logic, weight) VALUES
('10000000-0000-0000-0000-000000000001', 'Card not present high value', 'CARD', 'Source-shaped synthetic threshold; not evaluated by the application', 12.50),
('10000000-0000-0000-0000-000000000002', 'New crypto destination', 'CRYPTO', 'Source-shaped synthetic threshold; not evaluated by the application', 18.00),
('10000000-0000-0000-0000-000000000003', 'Growing cross-border payment activity', 'PAYMENT', 'Source-shaped synthetic threshold; not evaluated by the application', 15.00),
('10000000-0000-0000-0000-000000000004', 'Repeated card failures', 'CARD', 'Source-shaped synthetic threshold; not evaluated by the application', 20.00),
('10000000-0000-0000-0000-000000000005', 'High-value transfer anomaly', 'PAYMENT', 'Source-shaped synthetic threshold; not evaluated by the application', 25.00),
('10000000-0000-0000-0000-000000000006', 'Rapid movement across counterparties', 'ALL', 'Source-shaped synthetic threshold; not evaluated by the application', 22.00);
-- 111... preserves the R1 reviewer story, now persisted in PostgreSQL.
INSERT INTO transactions(transaction_id, customer_id, activity_type, amount, currency, status, created_at) VALUES
('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1', '11111111-1111-1111-1111-111111111111', 'CARD', 248.50, 'CHF', 'Completed', '2026-08-28 09:15:00'),
('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa2', '11111111-1111-1111-1111-111111111111', 'PAYMENT', 1250.00, 'CHF', 'Completed', '2026-08-29 11:30:00'),
('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa3', '11111111-1111-1111-1111-111111111111', 'CRYPTO', 0.42, 'BTC', 'Pending', '2026-08-30 14:05:00');
INSERT INTO card_activity VALUES
('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1', '**** **** **** 4242', 'VISA', 'Alpine Camera', '5946', false, 'A12345', NULL);
INSERT INTO payment_activity VALUES
('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa2', 'BANK_TRANSFER', 'CH00-SYNTHETIC-01', 'DE00-SYNTHETIC-02', 'DE');
INSERT INTO crypto_activity VALUES
('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa3', 'Bitcoin', 'bc1q-demo-from', 'bc1q-demo-to', 'synthetic-tx-hash', 'Demo Exchange');
-- 222... is deliberately ordinary: stable local CHF activity and no risk assessments.
INSERT INTO transactions(transaction_id, customer_id, activity_type, amount, currency, status, created_at) VALUES
('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb1', '22222222-2222-2222-2222-222222222222', 'CARD', 42.90, 'CHF', 'Completed', '2026-08-24 08:10:00'),
('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb2', '22222222-2222-2222-2222-222222222222', 'PAYMENT', 320.00, 'CHF', 'Completed', '2026-08-26 16:20:00'),
('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb3', '22222222-2222-2222-2222-222222222222', 'CARD', 85.40, 'CHF', 'Completed', '2026-08-29 17:45:00');
INSERT INTO card_activity VALUES
('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb1', '**** **** **** 1100', 'MASTERCARD', 'Lakeside Grocer', '5411', true, 'B10001', NULL),
('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb3', '**** **** **** 1100', 'MASTERCARD', 'Central Pharmacy', '5912', true, 'B10002', NULL);
INSERT INTO payment_activity VALUES
('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb2', 'BANK_TRANSFER', 'CH00-STABLE-01', 'CH00-RENT-01', 'CH');
-- 333... grows cross-border after conventional activity, then introduces crypto.
INSERT INTO transactions(transaction_id, customer_id, activity_type, amount, currency, status, created_at) VALUES
('cccccccc-cccc-cccc-cccc-ccccccccccc1', '33333333-3333-3333-3333-333333333333', 'PAYMENT', 900.00, 'CHF', 'Completed', '2026-08-12 10:00:00'),
('cccccccc-cccc-cccc-cccc-ccccccccccc2', '33333333-3333-3333-3333-333333333333', 'PAYMENT', 1800.00, 'EUR', 'Completed', '2026-08-21 10:05:00'),
('cccccccc-cccc-cccc-cccc-ccccccccccc3', '33333333-3333-3333-3333-333333333333', 'PAYMENT', 3200.00, 'EUR', 'Completed', '2026-08-28 10:10:00'),
('cccccccc-cccc-cccc-cccc-ccccccccccc4', '33333333-3333-3333-3333-333333333333', 'CRYPTO', 4.75, 'ETH', 'Completed', '2026-08-30 18:30:00');
INSERT INTO payment_activity VALUES
('cccccccc-cccc-cccc-cccc-ccccccccccc1', 'BANK_TRANSFER', 'CH00-GROWTH-01', 'CH00-SUPPLIER-01', 'CH'),
('cccccccc-cccc-cccc-cccc-ccccccccccc2', 'BANK_TRANSFER', 'CH00-GROWTH-01', 'DE00-SUPPLIER-02', 'DE'),
('cccccccc-cccc-cccc-cccc-ccccccccccc3', 'BANK_TRANSFER', 'CH00-GROWTH-01', 'NL00-SUPPLIER-03', 'NL');
INSERT INTO crypto_activity VALUES
('cccccccc-cccc-cccc-cccc-ccccccccccc4', 'Ethereum', '0xgrowth-from', '0xnew-destination', 'eth-synthetic-growth-001', 'Synthetic Exchange');
-- 444... combines failures, high-value cross-border transfers, rapid movement and crypto.
INSERT INTO transactions(transaction_id, customer_id, activity_type, amount, currency, status, created_at) VALUES
('dddddddd-dddd-dddd-dddd-ddddddddddd1', '44444444-4444-4444-4444-444444444444', 'CARD', 4200.00, 'USD', 'Declined', '2026-08-30 07:55:00'),
('dddddddd-dddd-dddd-dddd-ddddddddddd2', '44444444-4444-4444-4444-444444444444', 'CARD', 3800.00, 'USD', 'Declined', '2026-08-30 08:02:00'),
('dddddddd-dddd-dddd-dddd-ddddddddddd3', '44444444-4444-4444-4444-444444444444', 'PAYMENT', 25000.00, 'EUR', 'Completed', '2026-08-30 08:25:00'),
('dddddddd-dddd-dddd-dddd-ddddddddddd4', '44444444-4444-4444-4444-444444444444', 'PAYMENT', 24800.00, 'EUR', 'Completed', '2026-08-30 08:41:00'),
('dddddddd-dddd-dddd-dddd-ddddddddddd5', '44444444-4444-4444-4444-444444444444', 'CRYPTO', 12.00, 'ETH', 'Completed', '2026-08-30 09:05:00');
INSERT INTO card_activity VALUES
('dddddddd-dddd-dddd-dddd-ddddddddddd1', '**** **** **** 9009', 'VISA', 'International Electronics', '5732', false, 'D90001', 'Issuer declined'),
('dddddddd-dddd-dddd-dddd-ddddddddddd2', '**** **** **** 9009', 'VISA', 'International Electronics', '5732', false, 'D90002', 'Issuer declined');
INSERT INTO payment_activity VALUES
('dddddddd-dddd-dddd-dddd-ddddddddddd3', 'BANK_TRANSFER', 'CH00-MIXED-01', 'GB00-COUNTERPARTY-01', 'GB'),
('dddddddd-dddd-dddd-dddd-ddddddddddd4', 'BANK_TRANSFER', 'CH00-MIXED-01', 'AE00-COUNTERPARTY-02', 'AE');
INSERT INTO crypto_activity VALUES
('dddddddd-dddd-dddd-dddd-ddddddddddd5', 'Ethereum', '0xmixed-from', '0xunusual-counterparty', 'eth-synthetic-mixed-001', 'Synthetic Exchange');
INSERT INTO risk_assessments(assessment_id, transaction_id, rule_id, triggered_at, score_contribution) VALUES
('20000000-0000-0000-0000-000000000001', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1', '10000000-0000-0000-0000-000000000001', '2026-08-28 09:15:01', 12.50),
('20000000-0000-0000-0000-000000000002', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa3', '10000000-0000-0000-0000-000000000002', '2026-08-30 14:05:01', 18.00),
('20000000-0000-0000-0000-000000000003', 'cccccccc-cccc-cccc-cccc-ccccccccccc2', '10000000-0000-0000-0000-000000000003', '2026-08-21 10:05:01', 10.00),
('20000000-0000-0000-0000-000000000004', 'cccccccc-cccc-cccc-cccc-ccccccccccc3', '10000000-0000-0000-0000-000000000003', '2026-08-28 10:10:01', 15.00),
('20000000-0000-0000-0000-000000000005', 'cccccccc-cccc-cccc-cccc-ccccccccccc4', '10000000-0000-0000-0000-000000000002', '2026-08-30 18:30:01', 18.00),
('20000000-0000-0000-0000-000000000006', 'dddddddd-dddd-dddd-dddd-ddddddddddd1', '10000000-0000-0000-0000-000000000004', '2026-08-30 08:02:01', 20.00),
('20000000-0000-0000-0000-000000000007', 'dddddddd-dddd-dddd-dddd-ddddddddddd3', '10000000-0000-0000-0000-000000000005', '2026-08-30 08:25:01', 25.00),
('20000000-0000-0000-0000-000000000008', 'dddddddd-dddd-dddd-dddd-ddddddddddd4', '10000000-0000-0000-0000-000000000006', '2026-08-30 08:41:01', 22.00),
('20000000-0000-0000-0000-000000000009', 'dddddddd-dddd-dddd-dddd-ddddddddddd5', '10000000-0000-0000-0000-000000000002', '2026-08-30 09:05:01', 18.00),
('20000000-0000-0000-0000-000000000010', 'dddddddd-dddd-dddd-dddd-ddddddddddd5', '10000000-0000-0000-0000-000000000006', '2026-08-30 09:05:02', 22.00);
