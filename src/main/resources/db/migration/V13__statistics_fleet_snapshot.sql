-- No INSERT/SELECT from legacy vehicle data: historical membership must never be invented by migration.
CREATE TABLE statistics_fleet_snapshot (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    company_id BIGINT NOT NULL,
    target_date DATE NOT NULL,
    captured_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_statistics_fleet_snapshot UNIQUE (company_id,target_date),
    CONSTRAINT fk_statistics_fleet_company FOREIGN KEY (company_id) REFERENCES company(id)
);

CREATE TABLE statistics_fleet_member (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    company_id BIGINT NOT NULL,
    target_date DATE NOT NULL,
    vehicle_id BIGINT NOT NULL,
    CONSTRAINT uk_statistics_fleet_member UNIQUE (company_id,target_date,vehicle_id),
    CONSTRAINT fk_statistics_fleet_parent FOREIGN KEY (company_id,target_date)
        REFERENCES statistics_fleet_snapshot(company_id,target_date)
);
