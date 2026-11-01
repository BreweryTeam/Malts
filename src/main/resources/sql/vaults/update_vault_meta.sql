UPDATE malts_vaults
SET custom_name = ?, icon = ?, trusted_players = ?, locked = ?
WHERE owner = ? AND id = ?;
