package dev.jsinco.malts.registry;

import dev.jsinco.malts.commands.interfaces.SubCommand;
import dev.jsinco.malts.commands.subcommands.EditVaultCommand;
import dev.jsinco.malts.commands.subcommands.HelpCommand;
import dev.jsinco.malts.commands.subcommands.ImportCommand;
import dev.jsinco.malts.commands.subcommands.LogsCommand;
import dev.jsinco.malts.commands.subcommands.MaxCommand;
import dev.jsinco.malts.commands.subcommands.QuickReturnCommand;
import dev.jsinco.malts.commands.subcommands.ReloadCommand;
import dev.jsinco.malts.commands.subcommands.SearchCommand;
import dev.jsinco.malts.commands.subcommands.VaultAdminCommand;
import dev.jsinco.malts.commands.subcommands.VaultNameCommand;
import dev.jsinco.malts.commands.subcommands.VaultOtherCommand;
import dev.jsinco.malts.commands.subcommands.VaultsCommand;
import dev.jsinco.malts.commands.subcommands.WarehouseAdminCommand;
import dev.jsinco.malts.commands.subcommands.WarehouseCommand;
import dev.jsinco.malts.configuration.ConfigManager;
import dev.jsinco.malts.configuration.OkaeriFile;
import dev.jsinco.malts.configuration.files.Config;
import dev.jsinco.malts.configuration.files.GuiConfig;
import dev.jsinco.malts.configuration.files.Lang;
import dev.jsinco.malts.importers.AxVaultsImporter;
import dev.jsinco.malts.importers.Importer;
import dev.jsinco.malts.importers.PlayerVaultsImporter;
import dev.jsinco.malts.integration.compiled.UpdateCheckIntegration;
import dev.jsinco.malts.integration.external.CoreProtectIntegration;
import dev.jsinco.malts.integration.Integration;
import dev.jsinco.malts.integration.IntegrationCrafter;
import dev.jsinco.malts.integration.external.PlayerPointsIntegration;
import dev.jsinco.malts.integration.external.VaultIntegration;
import dev.jsinco.malts.integration.compiled.BStatsIntegration;
import dev.jsinco.malts.integration.external.papi.MaxVaultsPlaceholder;
import dev.jsinco.malts.integration.external.papi.Placeholder;
import dev.jsinco.malts.integration.external.papi.PlaceholderAPIIntegration;
import dev.jsinco.malts.integration.external.papi.VaultsPlaceholder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

public class Registry<T extends RegistryItem> implements Iterable<Map.Entry<String, T>> {

    public static final Registry<SubCommand> SUB_COMMANDS = fromClasses(VaultsCommand.class, WarehouseCommand.class, ImportCommand.class, VaultOtherCommand.class, WarehouseAdminCommand.class, MaxCommand.class, VaultAdminCommand.class, ReloadCommand.class, HelpCommand.class, QuickReturnCommand.class, VaultNameCommand.class, SearchCommand.class, EditVaultCommand.class, LogsCommand.class);
    public static final Registry<Importer> IMPORTERS = fromClasses(PlayerVaultsImporter.class, AxVaultsImporter.class);
    public static final Registry<OkaeriFile> CONFIGS = fromClassesWithCrafter(new ConfigManager(), Config.class, GuiConfig.class, Lang.class);
    public static final Registry<Integration> INTEGRATIONS = fromClassesWithCrafter(new IntegrationCrafter(), BStatsIntegration.class, CoreProtectIntegration.class, VaultIntegration.class, PlayerPointsIntegration.class, UpdateCheckIntegration.class, PlaceholderAPIIntegration.class);
    public static final Registry<Placeholder> PAPI_PLACEHOLDERS = fromClasses(MaxVaultsPlaceholder.class, VaultsPlaceholder.class);

    private final Map<String, T> map;

    public Registry(Collection<T> values) {
        this.map = new HashMap<>();
        values.forEach(item -> {
            map.put(item.name(), item);
        });
    }

    @Nullable
    public T get(String identifier) {
        return map.get(identifier);
    }

    @SuppressWarnings("unchecked")
    @Nullable
    public <A extends T> A get(Class<A> clazz) {
        return (A) map.values().stream()
                .filter(it -> it.getClass().equals(clazz))
                .findFirst()
                .orElse(null);
    }

    public Collection<T> values() {
        return map.values();
    }

    public Collection<String> keySet() {
        return map.keySet();
    }

    public Stream<Map.Entry<String, T>> stream() {
        return map.entrySet().stream();
    }

    @Override
    public @NotNull Iterator<Map.Entry<String, T>> iterator() {
        return map.entrySet().iterator();
    }

    @SuppressWarnings({"unchecked", "RedundantCast"})
    @SafeVarargs
    public static <E extends RegistryItem> Registry<E> fromClassesWithCrafter(RegistryCrafter crafter, Class<? extends E>... classes) {
        List<E> eClasses = new ArrayList<>();
        for (Class<?> clazz : classes) {
            if (crafter instanceof RegistryCrafter.Extension<?> crafter1) {
                eClasses.add((E) crafter1.craft(clazz));
            } else if (crafter instanceof RegistryCrafter.NoExtension crafter2) {
                eClasses.add((E) crafter2.craft(clazz));
            } else {
                throw new IllegalArgumentException("Unknown crafter type");
            }
        }
        return new Registry<>(eClasses.stream().filter(Objects::nonNull).toList());
    }

    @SafeVarargs
    public static <E extends RegistryItem> Registry<E> fromClasses(Class<? extends E>... classes) {
        return ConstructableClassBuilder.builder().addClasses(classes).build();
    }

    public static <E extends RegistryItem> Registry<E> fromClasses(Collection<Class<?>> constructorParamTypes, Collection<Object> constructorParams, Collection<Class<? extends E>> classes) {
        return ConstructableClassBuilder.builder().addConstructorParameters(constructorParamTypes, constructorParams).addClasses(classes).build();
    }


    public static class ConstructableClassBuilder {
        private final List<Class<?>> constructorClassTypes = new ArrayList<>();
        private final List<Object> constructorClassValues = new ArrayList<>();
        private final List<Class<?>> classes = new ArrayList<>();

        public static ConstructableClassBuilder builder() {
            return new ConstructableClassBuilder();
        }

        public ConstructableClassBuilder addConstructorParameter(Class<?> type, Object value) {
            constructorClassTypes.add(type);
            constructorClassValues.add(value);
            return this;
        }

        public ConstructableClassBuilder addConstructorParameters(Collection<Class<?>> types, Collection<Object> values) {
            constructorClassTypes.addAll(types);
            constructorClassValues.addAll(values);
            return this;
        }

        public ConstructableClassBuilder addClass(Class<?> clazz) {
            classes.add(clazz);
            return this;
        }

        public ConstructableClassBuilder addClasses(Class<?>... clazz) {
            classes.addAll(List.of(clazz));
            return this;
        }

        public <E> ConstructableClassBuilder addClasses(Collection<Class<? extends E>> clazz) {
            classes.addAll(clazz);
            return this;
        }


        public <T extends RegistryItem> Registry<T> build() {
            Class<?>[] constructorTypes = constructorClassTypes.toArray(new Class<?>[0]);
            Object[] constructorValues = constructorClassValues.toArray(new Object[0]);

            List<T> tClasses = new ArrayList<>();
            for (Class<?> clazz : classes) {
                if (!RegistryItem.class.isAssignableFrom(clazz)) {
                    throw new IllegalArgumentException("Class " + clazz.getName() + " does not implement RegistryItem");
                }

                try {
                    tClasses.add((T) clazz.getConstructor(constructorTypes).newInstance(constructorValues));
                } catch (NoSuchMethodException | InstantiationException | IllegalAccessException |
                         InvocationTargetException e) {
                    throw new RuntimeException("No constructor found for " + clazz.getName(), e);
                }
            }
            return new Registry<>(tClasses);
        }

    }
}
