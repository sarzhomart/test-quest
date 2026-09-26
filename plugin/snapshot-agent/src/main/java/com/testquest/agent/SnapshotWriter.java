package com.testquest.agent;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicBoolean;

public final class SnapshotWriter {
    private static final Object LOCK = new Object();
    private static final AtomicLong SEQUENCE = new AtomicLong();
    private static final AtomicBoolean ERROR_REPORTED = new AtomicBoolean();
    private static final AtomicBoolean HOOK_REPORTED = new AtomicBoolean();
    private static final AtomicBoolean EMPTY_DOM_REPORTED = new AtomicBoolean();
    private static final AtomicBoolean PAGE_SOURCE_ERROR_REPORTED = new AtomicBoolean();
    private static final AtomicBoolean SAVED_REPORTED = new AtomicBoolean();
    private static final ThreadLocal<Boolean> CAPTURING = ThreadLocal.withInitial(() -> false);
    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS").withZone(ZoneOffset.UTC);
    private static volatile long lastCaptureAt;
    private static volatile String lastDomHash = "";

    private SnapshotWriter() {
    }

    public static void capture(Object driver, String event) {
        if (driver == null || CAPTURING.get()) {
            return;
        }
        if (HOOK_REPORTED.compareAndSet(false, true)) {
            System.err.println("[Test Quest] Selenium action observed: " + event);
        }

        long now = System.currentTimeMillis();
        if (now - lastCaptureAt < 250L) {
            return;
        }

        synchronized (LOCK) {
            now = System.currentTimeMillis();
            if (now - lastCaptureAt < 250L) {
                return;
            }
            CAPTURING.set(true);
            try {
                String dom = pageSource(driver);
                if (dom == null || dom.isBlank()) {
                    if (EMPTY_DOM_REPORTED.compareAndSet(false, true)) {
                        System.err.println("[Test Quest] Snapshot skipped: page source is empty");
                    }
                    return;
                }
                String hash = sha256(dom);
                if (hash.equals(lastDomHash)) {
                    return;
                }

                Path directory = Path.of(System.getProperty("testquest.snapshotDir"))
                        .toAbsolutePath().normalize();
                Files.createDirectories(directory);

                String stem = FORMATTER.format(Instant.now()) + "-"
                        + String.format("%04d", SEQUENCE.incrementAndGet());
                writeAtomically(directory.resolve(stem + ".html"), dom.getBytes(StandardCharsets.UTF_8));

                byte[] screenshot = screenshot(driver);
                if (screenshot != null && screenshot.length > 0) {
                    writeAtomically(directory.resolve(stem + ".png"), screenshot);
                }

                String metadata = "{\n"
                        + "  \"capturedAt\": \"" + escape(Instant.now().toString()) + "\",\n"
                        + "  \"event\": \"" + escape(event) + "\",\n"
                        + "  \"url\": \"" + escape(stringMethod(driver, "getCurrentUrl")) + "\",\n"
                        + "  \"title\": \"" + escape(stringMethod(driver, "getTitle")) + "\",\n"
                        + "  \"domSha256\": \"" + hash + "\"\n"
                        + "}\n";
                writeAtomically(
                        directory.resolve(stem + ".json"),
                        metadata.getBytes(StandardCharsets.UTF_8)
                );

                lastDomHash = hash;
                lastCaptureAt = now;
                if (SAVED_REPORTED.compareAndSet(false, true)) {
                    System.err.println("[Test Quest] Snapshot saved: "
                            + directory.resolve(stem + ".html"));
                }
                prune(directory, 100);
            } catch (Throwable failure) {
                // Snapshotting must never change the result of the user's test.
                if (ERROR_REPORTED.compareAndSet(false, true)) {
                    System.err.println("[Test Quest] Snapshot capture failed: " + failure);
                }
            } finally {
                CAPTURING.set(false);
            }
        }
    }

    private static String pageSource(Object driver) {
        try {
            Object source = driver.getClass().getMethod("getPageSource").invoke(driver);
            return source == null ? "" : String.valueOf(source);
        } catch (ReflectiveOperationException failure) {
            Throwable cause = failure instanceof InvocationTargetException invocation
                    && invocation.getCause() != null ? invocation.getCause() : failure;
            if (PAGE_SOURCE_ERROR_REPORTED.compareAndSet(false, true)) {
                System.err.println("[Test Quest] Could not read page source: " + cause);
            }
            return "";
        }
    }

    private static String stringMethod(Object target, String name) {
        try {
            Method method = target.getClass().getMethod(name);
            Object result = method.invoke(target);
            return result == null ? "" : String.valueOf(result);
        } catch (ReflectiveOperationException ignored) {
            return "";
        }
    }

    private static byte[] screenshot(Object driver) {
        try {
            ClassLoader loader = driver.getClass().getClassLoader();
            Class<?> outputType = Class.forName("org.openqa.selenium.OutputType", true, loader);
            Field bytesField = outputType.getField("BYTES");
            Object bytesOutputType = bytesField.get(null);
            Method screenshot = driver.getClass().getMethod("getScreenshotAs", outputType);
            Object result = screenshot.invoke(driver, bytesOutputType);
            return result instanceof byte[] bytes ? bytes : null;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static void writeAtomically(Path target, byte[] data) throws Exception {
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        Files.write(temporary, data);
        try {
            Files.move(temporary, target,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void prune(Path directory, int maximumSnapshots) throws Exception {
        List<Path> metadata = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*.json")) {
            stream.forEach(metadata::add);
        }
        metadata.sort(Comparator.comparing(Path::getFileName));
        int toDelete = metadata.size() - maximumSnapshots;
        for (int index = 0; index < toDelete; index++) {
            Path json = metadata.get(index);
            String stem = json.getFileName().toString().replaceFirst("\\.json$", "");
            Files.deleteIfExists(json);
            Files.deleteIfExists(directory.resolve(stem + ".html"));
            Files.deleteIfExists(directory.resolve(stem + ".png"));
        }
    }

    private static String sha256(String value) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
    }
}
