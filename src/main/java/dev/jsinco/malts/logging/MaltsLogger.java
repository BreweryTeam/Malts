package dev.jsinco.malts.logging;

import dev.jsinco.malts.configuration.ConfigManager;
import dev.jsinco.malts.configuration.files.Config;
import dev.jsinco.malts.enums.WarehouseMode;
import dev.jsinco.malts.model.Vault;
import dev.jsinco.malts.storage.DataSource;
import dev.jsinco.malts.utility.Executors;
import dev.jsinco.malts.utility.Text;
import dev.jsinco.malts.utility.Util;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public final class MaltsLogger {

    private static final String LOGS_DIR = "logs";
    private static final Pattern DATE_PREFIX = Pattern.compile("^(\\d{4}-\\d{2}-\\d{2})");

    public static final int MAX_QUERY_RESULTS = 5000;

    private static MaltsLogger instance;

    private final Path logsDir = DataSource.DATA_FOLDER.resolve(LOGS_DIR);
    private final ExecutorService writer = Executors.newSingleThreadExecutor();
    private final ConcurrentLinkedQueue<LogEntry> buffer = new ConcurrentLinkedQueue<>();
    private final AtomicInteger buffered = new AtomicInteger();
    private final AtomicBoolean flushQueued = new AtomicBoolean();

    private volatile boolean shuttingDown;
    private ScheduledTask flushTask;

    private LocalDate currentFileDate;
    private long currentFileBytes;
    private long currentFileLines;

    private MaltsLogger() {
    }

    public static MaltsLogger get() {
        return instance;
    }

    public static void init() {
        if (instance != null) {
            instance.shutdown();
        }
        MaltsLogger logger = new MaltsLogger();
        instance = logger;

        Config.Logging config = config();
        long interval = Math.max(1, config.flushIntervalSeconds());

        logger.writer.execute(logger::startup);
        logger.flushTask = Executors.runRepeatingAsync(interval, interval, TimeUnit.SECONDS, t -> logger.submitFlush());
    }

    private static Config.Logging config() {
        return ConfigManager.get(Config.class).logging();
    }

    public boolean enabled() {
        return !shuttingDown && config().enabled();
    }

    private boolean actionEnabled(LogAction action) {
        return enabled() && config().loggedActions().contains(action);
    }

    public void logVaultChanges(HumanEntity player, Vault vault, ItemStack[] before, ItemStack[] after) {
        boolean logDeposit = actionEnabled(LogAction.VAULT_DEPOSIT);
        boolean logWithdraw = actionEnabled(LogAction.VAULT_WITHDRAW);
        if (!logDeposit && !logWithdraw) {
            return;
        }

        Map<ItemStack, Integer> beforeCounts = counts(before);
        Map<ItemStack, Integer> afterCounts = counts(after);
        Set<ItemStack> keys = new HashSet<>(beforeCounts.keySet());
        keys.addAll(afterCounts.keySet());

        String ownerSuffix = player.getUniqueId().equals(vault.getOwner()) ? "" : " (owner=" + actor(vault.getOwner()) + ")";

        LogDetail detail = config().detail();
        List<String> pdcKeys = config().loggedPersistentDataKeys();
        int containerDepth = config().containerContentDepth();
        for (ItemStack key : keys) {
            int delta = afterCounts.getOrDefault(key, 0) - beforeCounts.getOrDefault(key, 0);
            if (delta > 0 && logDeposit) {
                log(LogAction.VAULT_DEPOSIT, actor(player) + " deposited " + ItemLogFormatter.format(key, delta, detail, pdcKeys, containerDepth)
                        + " into vault " + vault.getId() + ownerSuffix);
            } else if (delta < 0 && logWithdraw) {
                log(LogAction.VAULT_WITHDRAW, actor(player) + " withdrew " + ItemLogFormatter.format(key, -delta, detail, pdcKeys, containerDepth)
                        + " from vault " + vault.getId() + ownerSuffix);
            }
        }
    }

    public void logWarehouse(LogAction action, CommandSender actor, UUID owner, Material material, int amount) {
        if (amount <= 0 || !actionEnabled(action)) {
            return;
        }
        boolean stock = action == LogAction.WAREHOUSE_STOCK;
        String verb = stock ? "stocked" : "destocked";
        String direction = stock ? "into" : "from";
        log(action, actorOrOwner(actor, owner) + " " + verb + " " + amount + "x " + Util.formatEnumerator(material)
                + " " + direction + " warehouse" + ownerSuffix(actor, owner));
    }

    public void logWarehouseCompartment(LogAction action, CommandSender actor, UUID owner, Material material) {
        if (!actionEnabled(action)) {
            return;
        }
        boolean add = action == LogAction.WAREHOUSE_ADD;
        String verb = add ? "added" : "removed";
        String direction = add ? "to" : "from";
        log(action, actorOrOwner(actor, owner) + " " + verb + " compartment " + Util.formatEnumerator(material)
                + " " + direction + " warehouse" + ownerSuffix(actor, owner));
    }

    public void logWarehouseMode(CommandSender actor, UUID owner, WarehouseMode from, WarehouseMode to) {
        if (!actionEnabled(LogAction.WAREHOUSE_MODE)) {
            return;
        }
        log(LogAction.WAREHOUSE_MODE, actor(actor) + " changed warehouse mode from " + from.name()
                + " to " + to.name() + ownerSuffix(actor, owner));
    }

    public void logVaultIcon(CommandSender actor, Vault vault, Material from, Material to) {
        if (!actionEnabled(LogAction.VAULT_EDIT_ICON)) {
            return;
        }
        log(LogAction.VAULT_EDIT_ICON, actor(actor) + " changed icon of vault " + vault.getId()
                + " from " + Util.formatEnumerator(from) + " to " + Util.formatEnumerator(to)
                + ownerSuffix(actor, vault.getOwner()));
    }

    public void logVaultName(CommandSender actor, Vault vault, String from, String to) {
        if (!actionEnabled(LogAction.VAULT_EDIT_NAME)) {
            return;
        }
        log(LogAction.VAULT_EDIT_NAME, actor(actor) + " renamed vault " + vault.getId()
                + " from \"" + from + "\" to \"" + to + "\"" + ownerSuffix(actor, vault.getOwner()));
    }

    public void logVaultTrust(CommandSender actor, Vault vault, UUID target, boolean trusted) {
        if (!actionEnabled(LogAction.VAULT_EDIT_TRUST)) {
            return;
        }
        String verb = trusted ? "trusted" : "untrusted";
        log(LogAction.VAULT_EDIT_TRUST, actor(actor) + " " + verb + " " + actor(target)
                + " on vault " + vault.getId() + ownerSuffix(actor, vault.getOwner()));
    }

    public void logVaultDelete(CommandSender actor, UUID owner, int vaultId) {
        if (!actionEnabled(LogAction.VAULT_DELETE)) {
            return;
        }
        log(LogAction.VAULT_DELETE, actor(actor) + " deleted vault " + vaultId + ownerSuffix(actor, owner));
    }

    public void logVaultTransfer(CommandSender actor, UUID first, int firstCount, UUID second, int secondCount) {
        if (!actionEnabled(LogAction.VAULT_TRANSFER)) {
            return;
        }
        log(LogAction.VAULT_TRANSFER, actor(actor) + " transferred vaults between "
                + actor(first) + " (" + firstCount + ") and " + actor(second) + " (" + secondCount + ")");
    }

    private void log(LogAction action, String message) {
        if (!actionEnabled(action)) {
            return;
        }
        buffer.add(new LogEntry(action, message));
        if (buffered.incrementAndGet() >= Math.max(1, config().flushThreshold())) {
            submitFlush();
        }
    }

    private void submitFlush() {
        if (!shuttingDown && flushQueued.compareAndSet(false, true)) {
            writer.execute(this::flush);
        }
    }

    public void flushNow() {
        if (shuttingDown) {
            return;
        }
        try {
            writer.submit(this::flush).get(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            Text.error("Failed to flush Malts logs", e);
        }
    }

    public CompletableFuture<LogQueryResult> query(LogFilter filter) {
        CompletableFuture<LogQueryResult> future = new CompletableFuture<>();
        if (shuttingDown) {
            future.complete(new LogQueryResult(List.of(), 0, false, false));
            return future;
        }
        try {
            writer.execute(() -> {
                try {
                    future.complete(runQuery(filter));
                } catch (Throwable t) {
                    Text.error("Failed to query Malts logs", t);
                    future.complete(new LogQueryResult(List.of(), 0, false, false));
                }
            });
        } catch (RejectedExecutionException e) {
            future.complete(new LogQueryResult(List.of(), 0, false, false));
        }
        return future;
    }

    public CompletableFuture<Void> flushAsync() {
        CompletableFuture<Void> future = new CompletableFuture<>();
        if (shuttingDown) {
            future.complete(null);
            return future;
        }
        try {
            writer.execute(() -> {
                try {
                    flush();
                } finally {
                    future.complete(null);
                }
            });
        } catch (RejectedExecutionException e) {
            future.complete(null);
        }
        return future;
    }

    private void flush() {
        flushQueued.set(false);

        List<LogEntry> batch = new ArrayList<>();
        LogEntry entry;
        while ((entry = buffer.poll()) != null) {
            buffered.decrementAndGet();
            batch.add(entry);
        }
        if (batch.isEmpty()) {
            return;
        }

        Map<LocalDate, StringBuilder> byDate = new TreeMap<>();
        for (LogEntry e : batch) {
            byDate.computeIfAbsent(e.timestamp().toLocalDate(), d -> new StringBuilder())
                    .append(e.render()).append(System.lineSeparator());
        }

        try {
            Files.createDirectories(logsDir);
            for (Map.Entry<LocalDate, StringBuilder> group : byDate.entrySet()) {
                LocalDate date = group.getKey();
                rolloverIfNeeded(date);

                String text = group.getValue().toString();
                Files.writeString(logFile(date), text, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);

                currentFileBytes += text.getBytes(StandardCharsets.UTF_8).length;
                currentFileLines += text.chars().filter(c -> c == '\n').count();
                rollIfOversized(date);
            }
        } catch (IOException e) {
            Text.error("Failed to write Malts log file", e);
        }
    }

    private void rolloverIfNeeded(LocalDate today) {
        if (currentFileDate == null) {
            beginFile(today);
            return;
        }
        if (currentFileDate.isBefore(today)) {
            if (config().compressOnRollover()) {
                compress(logFile(currentFileDate));
            }
            purge();
            beginFile(today);
        }
    }

    private void rollIfOversized(LocalDate date) {
        Config.Logging cfg = config();
        long maxBytes = cfg.maxFileSizeKb() * 1024L;
        boolean overSize = maxBytes > 0 && currentFileBytes >= maxBytes;
        boolean overLines = cfg.maxLines() > 0 && currentFileLines >= cfg.maxLines();
        if (overSize || overLines) {
            compress(logFile(date));
            currentFileBytes = 0;
            currentFileLines = 0;
        }
    }

    private void beginFile(LocalDate date) {
        currentFileDate = date;
        Path file = logFile(date);
        try {
            if (Files.exists(file)) {
                currentFileBytes = Files.size(file);
                currentFileLines = countLines(file);
                return;
            }
        } catch (IOException e) {
            Text.error("Failed to read existing Malts log file size", e);
        }
        currentFileBytes = 0;
        currentFileLines = 0;
    }

    private static long countLines(Path file) throws IOException {
        try (Stream<String> lines = Files.lines(file, StandardCharsets.UTF_8)) {
            return lines.count();
        }
    }

    private void startup() {
        LocalDate today = LocalDate.now();
        if (!Files.isDirectory(logsDir)) {
            beginFile(today);
            return;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(logsDir, "*.log")) {
            for (Path path : stream) {
                LocalDate date = dateOf(path.getFileName().toString());
                if (date != null && date.isBefore(today) && config().compressOnRollover()) {
                    compress(path);
                }
            }
        } catch (IOException e) {
            Text.error("Failed to scan Malts logs directory on startup", e);
        }
        purge();
        beginFile(today);
    }

    public void shutdown() {
        if (shuttingDown) {
            return;
        }
        shuttingDown = true;
        if (flushTask != null) {
            flushTask.cancel();
        }
        writer.execute(this::flush);
        writer.shutdown();
        try {
            if (!writer.awaitTermination(5, TimeUnit.SECONDS)) {
                writer.shutdownNow();
            }
        } catch (InterruptedException e) {
            writer.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private Path logFile(LocalDate date) {
        return logsDir.resolve(date + ".log");
    }

    private void compress(Path logFile) {
        if (!Files.exists(logFile)) {
            return;
        }
        Path target = uniqueGzipTarget(logFile);
        try (InputStream in = Files.newInputStream(logFile);
             OutputStream out = new GZIPOutputStream(Files.newOutputStream(target))) {
            in.transferTo(out);
        } catch (IOException e) {
            Text.error("Failed to compress Malts log file: " + logFile.getFileName(), e);
            return;
        }
        try {
            Files.deleteIfExists(logFile);
        } catch (IOException e) {
            Text.error("Failed to delete Malts log file after compression: " + logFile.getFileName(), e);
        }
    }

    private Path uniqueGzipTarget(Path logFile) {
        String base = logFile.getFileName().toString().replaceFirst("\\.log$", "");
        Path candidate = logsDir.resolve(base + ".log.gz");
        int index = 1;
        while (Files.exists(candidate)) {
            candidate = logsDir.resolve(base + "-" + index++ + ".log.gz");
        }
        return candidate;
    }

    private void purge() {
        Config.Logging.Purge purge = config().purge();
        if (!purge.enabled() || purge.afterDays() <= 0) {
            return;
        }
        LocalDate cutoff = LocalDate.now().minusDays(purge.afterDays());
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(logsDir)) {
            for (Path path : stream) {
                LocalDate date = dateOf(path.getFileName().toString());
                if (date != null && date.isBefore(cutoff)) {
                    Files.deleteIfExists(path);
                }
            }
        } catch (IOException e) {
            Text.error("Failed to purge old Malts log files", e);
        }
    }

    private static LocalDate dateOf(String fileName) {
        Matcher matcher = DATE_PREFIX.matcher(fileName);
        if (!matcher.find()) {
            return null;
        }
        try {
            return LocalDate.parse(matcher.group(1));
        } catch (Exception e) {
            return null;
        }
    }

    private static String actor(HumanEntity player) {
        return player.getName() + " (" + player.getUniqueId() + ")";
    }

    private static String actor(UUID uuid) {
        String name = nameOf(uuid);
        return name != null ? name + " (" + uuid + ")" : uuid.toString();
    }

    private static String nameOf(UUID uuid) {
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            return online.getName();
        }
        return Bukkit.getOfflinePlayer(uuid).getName();
    }

    private static String actor(CommandSender sender) {
        if (sender instanceof HumanEntity human) {
            return actor(human);
        }
        return sender.getName();
    }

    private static String actorOrOwner(CommandSender actor, UUID owner) {
        return actor != null ? actor(actor) : actor(owner);
    }

    private static String ownerSuffix(CommandSender actor, UUID owner) {
        if (actor == null || (actor instanceof HumanEntity human && human.getUniqueId().equals(owner))) {
            return "";
        }
        return " (owner=" + actor(owner) + ")";
    }

    private LogQueryResult runQuery(LogFilter filter) {
        LogLines.Compiled compiled;
        try {
            compiled = LogLines.compile(filter);
        } catch (PatternSyntaxException e) {
            return LogQueryResult.ofInvalidRegex();
        }

        Map<LocalDate, List<LogLines.Parsed>> bufferByDate = new HashMap<>();
        for (LogEntry entry : buffer) {
            LocalDate date = entry.timestamp().toLocalDate();
            if (date.isBefore(filter.from()) || date.isAfter(filter.to())) {
                continue;
            }
            LogLines.Parsed parsed = new LogLines.Parsed(date, entry.timestamp().toLocalTime(), entry.action(), entry.render());
            if (LogLines.matches(parsed, filter, compiled)) {
                bufferByDate.computeIfAbsent(date, d -> new ArrayList<>()).add(parsed);
            }
        }

        List<LogQueryResult.Line> lines = new ArrayList<>();
        int total = 0;
        boolean capped = false;

        for (LocalDate date = filter.to(); !date.isBefore(filter.from()); date = date.minusDays(1)) {
            List<LogLines.Parsed> dayMatches = new ArrayList<>();
            for (Path file : filesForDate(date)) {
                readMatching(file, date, filter, compiled, dayMatches);
            }
            List<LogLines.Parsed> buffered = bufferByDate.get(date);
            if (buffered != null) {
                dayMatches.addAll(buffered);
            }
            dayMatches.sort(Comparator.comparing(
                    (LogLines.Parsed p) -> p.time() == null ? LocalTime.MIN : p.time()).reversed());

            for (LogLines.Parsed parsed : dayMatches) {
                total++;
                if (lines.size() < MAX_QUERY_RESULTS) {
                    lines.add(new LogQueryResult.Line(parsed.date(), parsed.raw()));
                } else {
                    capped = true;
                }
            }
        }

        return new LogQueryResult(lines, total, capped, false);
    }

    private List<Path> filesForDate(LocalDate date) {
        List<Path> files = new ArrayList<>();
        if (!Files.isDirectory(logsDir)) {
            return files;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(logsDir, date + "*.log*")) {
            for (Path path : stream) {
                String name = path.getFileName().toString();
                if (name.endsWith(".log") || name.endsWith(".log.gz")) {
                    files.add(path);
                }
            }
        } catch (IOException e) {
            Text.error("Failed to list Malts log files for " + date, e);
        }
        return files;
    }

    private void readMatching(Path file, LocalDate date, LogFilter filter, LogLines.Compiled compiled,
                              List<LogLines.Parsed> out) {
        boolean gzip = file.getFileName().toString().endsWith(".gz");
        try (InputStream in = Files.newInputStream(file);
             InputStream decoded = gzip ? new GZIPInputStream(in) : in;
             BufferedReader reader = new BufferedReader(new InputStreamReader(decoded, StandardCharsets.UTF_8))) {
            String raw;
            while ((raw = reader.readLine()) != null) {
                if (raw.isEmpty()) {
                    continue;
                }
                LogLines.Parsed parsed = LogLines.parse(date, raw);
                if (LogLines.matches(parsed, filter, compiled)) {
                    out.add(parsed);
                }
            }
        } catch (IOException e) {
            Text.error("Failed to read Malts log file: " + file.getFileName(), e);
        }
    }

    private static Map<ItemStack, Integer> counts(ItemStack[] contents) {
        Map<ItemStack, Integer> map = new HashMap<>();
        if (contents == null) {
            return map;
        }
        for (ItemStack item : contents) {
            if (item == null || item.getType().isAir()) {
                continue;
            }
            ItemStack key = item.clone();
            key.setAmount(1);
            map.merge(key, item.getAmount(), Integer::sum);
        }
        return map;
    }
}
