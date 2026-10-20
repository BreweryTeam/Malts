package dev.jsinco.malts.gui;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PageMemory {

    private static final Map<UUID, Map<String, Integer>> PAGES = new ConcurrentHashMap<>();

    private PageMemory() {
    }

    public static int get(UUID viewer, String key) {
        Map<String, Integer> pages = PAGES.get(viewer);
        return pages == null ? 0 : pages.getOrDefault(key, 0);
    }

    public static void set(UUID viewer, String key, int page) {
        PAGES.computeIfAbsent(viewer, k -> new ConcurrentHashMap<>()).put(key, page);
    }

    public static void clear(UUID viewer) {
        PAGES.remove(viewer);
    }
}
