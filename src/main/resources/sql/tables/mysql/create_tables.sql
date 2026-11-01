CREATE TABLE IF NOT EXISTS malts_vaults(
    owner VARCHAR(36) NOT NULL,
    id INTEGER NOT NULL,
    inventory MEDIUMTEXT NOT NULL,
    custom_name TEXT,
    icon TEXT,
    trusted_players TEXT,
    locked BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (owner, id)
);

CREATE TABLE IF NOT EXISTS malts_players(
    uuid VARCHAR(36) NOT NULL PRIMARY KEY,
    max_vaults INTEGER NOT NULL,
    max_warehouse_stock INTEGER NOT NULL,
    warehouse_mode TEXT,
    quick_return_click_type TEXT
);

CREATE TABLE IF NOT EXISTS malts_warehouses(
    owner VARCHAR(36) NOT NULL,
    material VARCHAR(64) NOT NULL,
    quantity INTEGER NOT NULL,
    last_update BIGINT NOT NULL,
    PRIMARY KEY (owner, material),
    FOREIGN KEY (owner) REFERENCES malts_players(uuid)
);

-- Migration code
ALTER TABLE malts_players ADD COLUMN IF NOT EXISTS quick_return_click_type TEXT;
ALTER TABLE malts_vaults ADD COLUMN IF NOT EXISTS locked BOOLEAN NOT NULL DEFAULT FALSE;