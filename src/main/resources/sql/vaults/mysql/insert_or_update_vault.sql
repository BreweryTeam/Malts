INSERT INTO malts_vaults(
    owner, id, inventory, custom_name, icon, trusted_players, locked
) VALUES (?, ?, ?, ?, ?, ?, ?)
ON DUPLICATE KEY UPDATE
   inventory = VALUES(inventory),
   custom_name = VALUES(custom_name),
   icon = VALUES(icon),
   trusted_players = VALUES(trusted_players),
   locked = VALUES(locked);