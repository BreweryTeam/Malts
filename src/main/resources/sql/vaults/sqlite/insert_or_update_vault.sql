INSERT INTO malts_vaults(
    owner, id, inventory, custom_name, icon, trusted_players, locked
) VALUES (?, ?, ?, ?, ?, ?, ?)
    ON CONFLICT(owner, id) DO UPDATE SET
    inventory = excluded.inventory,
    custom_name = excluded.custom_name,
    icon = excluded.icon,
    trusted_players = excluded.trusted_players,
    locked = excluded.locked;