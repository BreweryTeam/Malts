package dev.jsinco.malts.configuration;

import com.google.common.base.Preconditions;
import dev.jsinco.malts.Malts;
import dev.jsinco.malts.configuration.serdes.DriverConstantTransformer;
import dev.jsinco.malts.configuration.serdes.IntPairTransformer;
import dev.jsinco.malts.registry.Registry;
import dev.jsinco.malts.registry.RegistryCrafter;
import dev.jsinco.malts.utility.FileUtil;
import dev.jsinco.malts.utility.Text;
import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.serdes.standard.StandardSerdes;
import eu.okaeri.configs.yaml.bukkit.YamlBukkitConfigurer;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static dev.jsinco.malts.storage.DataSource.DATA_FOLDER;

@Getter
@Accessors(fluent = true)
public class ConfigManager implements RegistryCrafter.Extension<OkaeriConfig> {

    public static final String TRANSLATIONS_FOLDER = "translations";

    @Override
    public <T extends OkaeriConfig> T craft(Class<?> clazz) {
        OkaeriFileName annotation = clazz.getAnnotation(OkaeriFileName.class);
        if (annotation == null) {
            throw new IllegalStateException("OkaeriFile must be annotated with @OkaeriFileName");
        }
        String fileName = annotation.dynamicFileName() ? dynamicFileName(annotation) : annotation.value();
        Preconditions.checkNotNull(fileName, "Dynamic file name could not be resolved for " + clazz.getName());
        Path bindFile = DATA_FOLDER.resolve(fileName);

        T config = eu.okaeri.configs.ConfigManager.create((Class<T>) clazz, (it) -> {
            it.withConfigurer(new YamlBukkitConfigurer(), new StandardSerdes());
            it.withRemoveOrphans(false);
            it.withBindFile(bindFile);
            it.withSerdesPack(serdes -> {
                serdes.register(new IntPairTransformer());
                serdes.register(new DriverConstantTransformer());
            });

            it.saveDefaults();
            it.load(true);
        });

        if (annotation.dynamicFileName()) {
            NullKeyMerger.mergeWithInternalDefaults(config, bindFile);
        }

        return config;
    }

    private String dynamicFileName(OkaeriFileName annotation) {
        OkaeriFile config = craft(annotation.dynamicFileNameHolder());
        String value = config.get(annotation.dynamicFileNameKey(), String.class);
        if (value != null) {
            return String.format(annotation.dynamicFileNameFormat(), value);
        }
        return null;
    }

    public static <T extends OkaeriFile> T get(Class<T> clazz) {
        return Registry.CONFIGS.values().stream()
                .filter(it -> it.getClass().equals(clazz))
                .map(it -> (T) it)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No config found for class " + clazz.getName()));
    }

    public static void createTranslationConfigs() {
        Path targetDir = DATA_FOLDER.resolve(TRANSLATIONS_FOLDER);
        File[] internalLangs = FileUtil.listInternalFiles("/" + TRANSLATIONS_FOLDER);

        try {
            if (!Files.exists(targetDir)) {
                Files.createDirectories(targetDir);
            }

            for (File file : internalLangs) {
                Path targetFile = targetDir.resolve(file.getName());
                if (Files.exists(targetFile)) continue;

                try (InputStream in = Malts.class.getResourceAsStream("/" + TRANSLATIONS_FOLDER + "/" + file.getName())) {
                    if (in == null) {
                        continue;
                    }
                    Files.copy(in, targetFile, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } catch (IOException e) {
            Text.error("Failed to create translation config files", e);
        }
    }

}