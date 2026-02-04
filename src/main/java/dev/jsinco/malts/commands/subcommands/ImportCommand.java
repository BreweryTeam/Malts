package dev.jsinco.malts.commands.subcommands;

import dev.jsinco.malts.Malts;
import dev.jsinco.malts.api.events.ImportEvent;
import dev.jsinco.malts.commands.interfaces.SubCommand;
import dev.jsinco.malts.importers.Importer;
import dev.jsinco.malts.registry.Registry;
import dev.jsinco.malts.utility.Couple;
import dev.jsinco.malts.utility.Util;
import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static dev.jsinco.malts.utility.Text.CONSOLE;

public class ImportCommand implements SubCommand {

    @Override
    public boolean execute(Malts plugin, CommandSender sender, String label, List<String> args) {
        if (args.isEmpty()) {
            return false;
        }
        String importerName = args.getFirst();
        Importer imp = Registry.IMPORTERS.get(importerName);

        ImportEvent event = new ImportEvent(imp, false);
        event.setCancelled(imp == null || !imp.canImport());


        if (!event.callEvent()) {
            lng.entry(l -> l.command()._import().cannotImport(),
                    sender,
                    Couple.of("{importer}", importerName)
            );
            return true;
        }

        Importer importer = event.getImporter();

        lng.entry(l -> l.command()._import().startImport(),
                List.of(sender, CONSOLE),
                Couple.of("{importer}", importerName)
        );
        importer.importAll().thenAccept(results -> {
            Map<UUID, Importer.Result> failed = results.entrySet().stream()
                    .filter(e -> e.getValue() != Importer.Result.SUCCESS) // change method if different
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

            lng.entry(l -> l.command()._import().importComplete(),
                    List.of(sender, CONSOLE),
                    Couple.of("{amount}", results.size()),
                    Couple.of("{failedAmount}", failed.size())
            );

            if (!failed.isEmpty()) {
                failed.forEach((uuid, r) -> {
                    lng.entry(l -> l.command()._import().failedImport(),
                            List.of(sender, CONSOLE),
                            Couple.of("{uuid}", uuid),
                            Couple.of("{result}", Util.formatEnumerator(r))
                    );
                });
            }
        });
        return true;
    }

    @Override
    public List<String> tabComplete(Malts plugin, CommandSender sender, String label, List<String> args) {
        return List.copyOf(Registry.IMPORTERS.keySet());
    }

    @Override
    public String name() {
        return "import";
    }

    @Override
    public String permission() {
        return "malts.command.import";
    }

    @Override
    public boolean playerOnly() {
        return false;
    }
}
