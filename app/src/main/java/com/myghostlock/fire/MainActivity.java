package com.myghostlock.fire;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Insets;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.system.ErrnoException;
import android.system.Os;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;


import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class MainActivity extends Activity {
    private static final String TAG = "GhostLockApp";
    private static final String BINARY_NAME = "libghostlock.so";
    private static final String KSUD_NAME = "ksud";
    private static final String KSU_LOG_NAME = ".ghostlock_ksu.log";
    private static final String PREFS = "ghostlock_prefs";
    private static final String PREF_CPU_PAIR = "cpu_pair";
    private static final String[] KSU_MANAGER_PACKAGES = {"me.weishu.kernelsu", "com.resukisu.resukisu",};
    private static final int COLOR_RED = 0xFFFF6B6B;
    private static final int COLOR_GREEN = 0xFF5FD68A;
    private static final int COLOR_YELLOW = 0xFFFFC94D;
    private static final int COLOR_BLUE = 0xFF60A5FA;
    private static final int COLOR_NONE = -1;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final StringBuilder logBuffer = new StringBuilder();
    private final List<int[]> cpuPairs = new ArrayList<>();
    private final List<String> cpuPairLabels = new ArrayList<>();
    private final Map<View, ValueAnimator> viewAnimators = new HashMap<>();
    private TextView deviceInfo;
    private TextView logView;
    private LinearLayout kernelChip;
    private TextView kernelChipText;
    private Spinner cpuSpinner;
    private ScrollView logScroll;
    private Button runButton;
    private ImageButton advancedButton;
    private LinearLayout advancedPanel;
    private Button copyButton;
    private Button exportLogButton;
    private Button cveCheckButton;
    private View rootView;
    private int cpuPairIndex;

    /**
     * Returns the value when it looks like a real device name, else null.
     */
    private static String validDeviceName(String value) {
        if (value == null) {
            return null;
        }
        String v = value.trim();
        if (v.isEmpty()) {
            return null;
        }
        String lower = v.toLowerCase(Locale.ROOT);
        if (lower.contains("unknown") || lower.contains("null")) {
            return null;
        }
        return v;
    }

    @SuppressLint("PrivateApi")
    private static String getSystemProperty(String key) {
        try {
            Class<?> props = Class.forName("android.os.SystemProperties");
            Object value = props.getMethod("get", String.class).invoke(null, key);
            return value instanceof String ? (String) value : "";
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static boolean isArm64Device() {
        for (String abi : Build.SUPPORTED_ABIS) {
            if ("arm64-v8a".equals(abi)) {
                return true;
            }
        }
        return false;
    }

    private static String readSysFile(String path) {
        File f = new File(path);
        if (!f.isFile()) {
            return "";
        }
        try (BufferedReader r = new BufferedReader(new FileReader(f))) {
            String line = r.readLine();
            return line == null ? "" : line.trim();
        } catch (IOException ignored) {
            return "";
        }
    }

    private static List<Integer> parseCpuList(String s) {
        List<Integer> out = new ArrayList<>();
        if (s == null || s.isEmpty()) {
            return out;
        }
        for (String part : s.split(",")) {
            String[] range = part.split("-");
            try {
                int lo = Integer.parseInt(range[0].trim());
                int hi = range.length > 1 ? Integer.parseInt(range[1].trim()) : lo;
                for (int cpu = lo; cpu <= hi; cpu++) {
                    out.add(cpu);
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return out;
    }

    private static long readMaxFreq(int cpu) {
        String v = readSysFile("/sys/devices/system/cpu/cpu" + cpu + "/cpufreq/cpuinfo_max_freq");
        if (v.isEmpty()) {
            return -1;
        }
        try {
            return Long.parseLong(v);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static String formatFreq(long khz) {
        if (khz >= 1_000_000L) {
            return String.format(Locale.ROOT, "%.2f GHz", khz / 1_000_000.0);
        }
        return String.format(Locale.ROOT, "%.0f MHz", khz / 1000.0);
    }

    /**
     * Color a whole log line by its leading marker. The native binary only
     * colors the "[..]" prefix (message text stays default) and the script log
     * is plain text, so per-line coloring is what makes the log readable.
     */
    private static CharSequence colorize(String line) {
        int color = markerColor(line);
        if (color == COLOR_NONE) {
            return line;
        }
        SpannableStringBuilder sb = new SpannableStringBuilder(line);
        sb.setSpan(new ForegroundColorSpan(color), 0, line.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return sb;
    }

    /**
     * Marker of the leading "[x] " tag, or 0 if the line has none.
     */
    private static char markerOf(String line) {
        return line.length() > 2 && line.charAt(0) == '[' && line.charAt(2) == ']' ? line.charAt(1) : 0;
    }

    /**
     * True for W1/W2/W3 stage round lines (progress or in-round failure).
     */
    private static boolean isWriteRound(String msg) {
        String stage = msg.startsWith("=== ") ? msg.substring(4) : msg;
        return stage.startsWith("W1") || stage.startsWith("W2") || stage.startsWith("W3") || stage.startsWith("Write 1");
    }

    private static int markerColor(String line) {
        char marker = markerOf(line);
        /* W-round progress renders blue; in-round failures keep red. */
        if (isWriteRound(logMessage(line))) {
            if (marker == '-' || marker == '!') return COLOR_RED;
            return COLOR_BLUE;
        }
        if (marker == '+') return COLOR_GREEN;
        if (marker == '-' || marker == '!') return COLOR_RED;
        if (marker == '*') return COLOR_YELLOW;
        if (line.startsWith("error") || line.startsWith("Error")) return COLOR_RED;
        if (line.startsWith("warning")) return COLOR_YELLOW;
        return COLOR_NONE;
    }

    /**
     * Strip leading "[..] " tags (marker, TIMER) and return the message.
     */
    private static String logMessage(String line) {
        String s = line;
        while (s.startsWith("[")) {
            int end = s.indexOf(']');
            if (end < 0) break;
            s = s.substring(end + 1);
            if (s.startsWith(" ")) s = s.substring(1);
        }
        return s;
    }

    private static String stripAnsi(String input) {
        return input.replaceAll("\u001B\\[[;\\d]*m", "");
    }

    private static void copyStream(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) >= 0) {
            out.write(buf, 0, n);
        }
        out.flush();
    }

    private static void copyFile(File src, File dst) throws IOException {
        try (InputStream in = new FileInputStream(src); OutputStream out = new FileOutputStream(dst, false)) {
            copyStream(in, out);
        }
    }

    private static void setViewColor(View view, int color) {
        if (view.getBackground() instanceof GradientDrawable) {
            ((GradientDrawable) view.getBackground().mutate()).setColor(color);
        } else {
            view.setBackgroundTintList(ColorStateList.valueOf(color));
        }
    }

    private static String firstValidProperty(String... keys) {
        for (String key : keys) {
            String value = validDeviceName(getSystemProperty(key));
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    /**
     * A kernel is supported when its release or build ID is in the built-in tables.
     */
    private boolean isKernelSupported() {
        String version = System.getProperty("os.version", "");
        for (String supported : SupportedKernels.UNAMES) {
            if (supported.equals(version)) {
                return true;
            }
        }
        String compositeId = Build.MODEL + "_" + Build.DISPLAY;
        for (String supported : SupportedKernels.BUILD_IDS) {
            if (supported.equals(compositeId)) {
                return true;
            }
        }
        return false;
    }

    private void buildCpuPairs() {
        cpuPairs.clear();
        cpuPairLabels.clear();
        List<Integer> online = parseCpuList(readSysFile("/sys/devices/system/cpu/online"));
        if (!online.isEmpty()) {
            Map<Long, List<Integer>> byFreq = new TreeMap<>(Collections.reverseOrder());
            for (int cpu : online) {
                long freq = readMaxFreq(cpu);
                if (freq > 0) {
                    byFreq.computeIfAbsent(freq, k -> new ArrayList<>()).add(cpu);
                }
            }
            for (Map.Entry<Long, List<Integer>> entry : byFreq.entrySet()) {
                List<Integer> cluster = entry.getValue();
                Collections.sort(cluster);
                String freqText = " · " + formatFreq(entry.getKey());
                for (int i = 0; i + 1 < cluster.size(); i += 2) {
                    int main = cluster.get(i);
                    int consumer = cluster.get(i + 1);
                    cpuPairs.add(new int[]{main, consumer});
                    cpuPairLabels.add(main + "," + consumer + freqText);
                }
            }
        }
        // Safe 0,1 fallback last: big cores are the default and listed first.
        boolean hasSafe = false;
        for (int[] pair : cpuPairs) {
            if (pair[0] == 0 && pair[1] == 1) {
                hasSafe = true;
                break;
            }
        }
        if (!hasSafe) {
            cpuPairs.add(new int[]{0, 1});
            long autoFreq = readMaxFreq(0);
            cpuPairLabels.add("0,1" + (autoFreq > 0 ? " · " + formatFreq(autoFreq) : ""));
        }
    }

    private void restoreCpuPair() {
        cpuPairIndex = 0;
        String saved = getSharedPreferences(PREFS, MODE_PRIVATE).getString(PREF_CPU_PAIR, null);
        if (saved == null || saved.equals("auto")) {
            // Default to the big-core pair (first entry); a legacy "auto"
            // preference is treated the same.  The native side falls back to
            // 0/1 when the pair is unavailable.
            return;
        }
        String[] parts = saved.split(",");
        if (parts.length != 2) {
            return;
        }
        try {
            int main = Integer.parseInt(parts[0].trim());
            int consumer = Integer.parseInt(parts[1].trim());
            for (int i = 0; i < cpuPairs.size(); i++) {
                int[] pair = cpuPairs.get(i);
                if (pair[0] == main && pair[1] == consumer) {
                    cpuPairIndex = i;
                    return;
                }
            }
        } catch (NumberFormatException ignored) {
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setupSystemBars();

        rootView = findViewById(R.id.root);
        deviceInfo = findViewById(R.id.deviceInfo);
        logView = findViewById(R.id.logView);
        logScroll = findViewById(R.id.logScroll);
        runButton = findViewById(R.id.runButton);
        advancedButton = findViewById(R.id.advancedButton);
        advancedPanel = findViewById(R.id.advancedPanel);
        copyButton = findViewById(R.id.copyButton);
        exportLogButton = findViewById(R.id.exportLogButton);
        cveCheckButton = findViewById(R.id.cveCheckButton);
        kernelChip = findViewById(R.id.kernelChip);
        kernelChipText = findViewById(R.id.kernelChipText);
        cpuSpinner = findViewById(R.id.cpuSpinner);

        applyWindowInsetsPadding();
        deviceInfo.setText(buildDeviceSummary());
        buildCpuPairs();
        restoreCpuPair();
        applyKernelStatus();
        setRunState(RunState.IDLE);

        runButton.setOnClickListener(v -> startExploit());
        advancedButton.setOnClickListener(v -> {
            if (advancedPanel.getVisibility() == View.VISIBLE) {
                animateHide(advancedPanel);
            } else {
                animateShow(advancedPanel);
            }
        });
        copyButton.setOnClickListener(v -> copyLogs());
        exportLogButton.setOnClickListener(v -> exportLog());
        cveCheckButton.setOnClickListener(v -> checkCve43499Compatibility());


        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, R.layout.spinner_item_right, cpuPairLabels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        cpuSpinner.setAdapter(adapter);
        cpuSpinner.setSelection(cpuPairIndex);
        cpuSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                cpuPairIndex = position;
                int[] pair = cpuPairs.get(position);
                getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(PREF_CPU_PAIR, pair[0] + "," + pair[1]).apply();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    @Override
    protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }



    private void setupSystemBars() {
        WindowInsetsController controller = getWindow().getInsetsController();
        if (controller == null) {
            return;
        }
        int lightStatus = getResources().getBoolean(R.bool.window_light_status_bar) ? WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS : 0;
        int lightNav = getResources().getBoolean(R.bool.window_light_navigation_bar) ? WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS : 0;
        controller.setSystemBarsAppearance(lightStatus | lightNav, WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
    }


    /**
     * Read-only CVE-2026-43499 compatibility assessment. This deliberately
     * does not invoke a futex trigger or the GhostLock binary.
     */
    private void checkCve43499Compatibility() {
        if (running.get()) return;
        appendLog("[i] Initializing CVE-2026-43499 assessment...");

        worker.execute(() -> {
            String release = System.getProperty("os.version", "");
            boolean isArm64 = isArm64Device();
            boolean hasOffsets = isKernelSupported();
            Cve43499Assessment.Result vResult = Cve43499Assessment.evaluate(release, isArm64, hasOffsets);

            ui.post(() -> {
                appendLog("[i] Version check: " + vResult.versionEvidence);
                appendLog("[i] Starting runtime behavior probe...");
            });

            boolean runtimeConfirmed = false;
            try {
                File binary = resolveBinary();
                File workDir = getFilesDir();
                ProcessBuilder pb = new ProcessBuilder(binary.getAbsolutePath(), "--check");
                pb.directory(workDir);
                pb.redirectErrorStream(true);
                Process process = pb.start();
                try (BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        final String msg = line;
                        ui.post(() -> appendLog(msg));
                        if (msg.contains("VULNERABILITY CONFIRMED")) {
                            runtimeConfirmed = true;
                        }
                    }
                }
                process.waitFor(15, TimeUnit.SECONDS);
            } catch (Exception e) {
                ui.post(() -> appendLog("[!] Runtime probe failed: " + e.getMessage()));
            }

            final boolean isVulnerable = runtimeConfirmed;
            ui.post(() -> showCveResultDialog(vResult, isVulnerable, release));
        });
    }

    private void showCveResultDialog(Cve43499Assessment.Result vResult, boolean runtimeConfirmed, String release) {
        int statusText;
        if (runtimeConfirmed) {
            statusText = R.string.cve_status_confirmed;
        } else {
            switch (vResult.exposure) {
                case LIKELY_AFFECTED:
                    statusText = R.string.cve_status_affected;
                    break;
                case LIKELY_PATCHED:
                    statusText = R.string.cve_status_patched;
                    break;
                default:
                    statusText = R.string.cve_status_unknown;
                    break;
            }
        }

        int adviceText;
        switch (vResult.pathSupport) {
            case READY_TO_TRY:
                if (runtimeConfirmed) {
                    adviceText = R.string.cve_advice_try;
                } else if (vResult.exposure == Cve43499Assessment.Exposure.LIKELY_PATCHED) {
                    adviceText = R.string.cve_advice_patched;
                } else {
                    adviceText = R.string.cve_advice_unknown;
                }
                break;
            case NOT_RECOMMENDED:
                adviceText = R.string.cve_advice_patched;
                break;
            case UNSUPPORTED_ARCHITECTURE:
                adviceText = R.string.cve_advice_unsupported_arch;
                break;
            default:
                adviceText = R.string.cve_advice_no_offsets;
                break;
        }

        String message = getString(statusText)
                + (runtimeConfirmed ? " (confirmed by runtime probe)" : " (inferred from version only)")
                + "\n\n" + getString(adviceText)
                + "\n\n" + getString(R.string.cve_release, release.isEmpty() ? "unknown" : release)
                + "\n\n" + getString(R.string.cve_disclaimer);

        new AlertDialog.Builder(this)
                .setTitle(R.string.cve_title)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    /**
     * Export one kernel's offsets as a shareable stored offsets file entry.  The
     * candidate list only covers entries that a recipient would not already
     * have: new releases plus built-in releases whose values were overwritten
     * with different data.  The current device release is listed first.
     */

    private void exportLog() {
        File logFile = new File(getFilesDir(), "ghostlock.log");
        if (!logFile.exists()) {
            toast(R.string.export_log_not_found);
            return;
        }
        worker.execute(() -> {
            try {
                String name = "ghostlock-" + System.currentTimeMillis() + ".log";
                ContentValues values = new ContentValues();
                values.put(MediaStore.Downloads.DISPLAY_NAME, name);
                values.put(MediaStore.Downloads.MIME_TYPE, "text/plain");
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.put(MediaStore.Downloads.RELATIVE_PATH, "Download/ghostlock");
                }
                Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (uri == null) {
                    throw new IOException("cannot create download entry");
                }
                try (InputStream in = new FileInputStream(logFile);
                     OutputStream out = getContentResolver().openOutputStream(uri)) {
                    if (out == null) {
                        throw new IOException("cannot open download entry");
                    }
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = in.read(buffer)) > 0) {
                        out.write(buffer, 0, len);
                    }
                }
                final Uri sharedUri = uri;
                ui.post(() -> {
                    toast(R.string.export_log_success);
                    Intent send = new Intent(Intent.ACTION_SEND);
                    send.setType("text/plain");
                    send.putExtra(Intent.EXTRA_STREAM, sharedUri);
                    send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(Intent.createChooser(send, getString(R.string.action_export_log)));
                });
            } catch (Throwable t) {
                ui.post(() -> {
                    Toast.makeText(this, getString(R.string.export_log_failed, t.getMessage()), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    /**
     * Imported/parsed entries that a recipient would not already have: new
     * releases plus built-in releases whose values differ from the table.
     */

    /**
     * Always show the export button in advanced panel so users can share offsets anytime.
     */

    private void cancelViewAnimation(View view) {
        ValueAnimator running = viewAnimators.remove(view);
        if (running != null) {
            running.cancel();
        }
    }

    /**
     * Smoothly reveal a view: animate its height from 0 to the measured
     * content height while fading in.  The measure uses the parent width and
     * an unconstrained height, so padding and child margins are included in
     * the final size.
     */
    private void animateShow(View view) {
        cancelViewAnimation(view);
        final ViewGroup.LayoutParams lp = view.getLayoutParams();
        int width = view.getParent() instanceof View ? ((View) view.getParent()).getWidth() : 0;
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        final int target = Math.max(view.getMeasuredHeight(), 1);
        lp.height = 0;
        view.setVisibility(View.VISIBLE);
        view.setAlpha(0f);
        ValueAnimator anim = ValueAnimator.ofInt(0, target);
        anim.addUpdateListener(a -> {
            lp.height = (int) a.getAnimatedValue();
            view.setAlpha(a.getAnimatedFraction());
            view.requestLayout();
        });
        anim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                view.setAlpha(1f);
                view.requestLayout();
            }
        });
        anim.setDuration(220);
        viewAnimators.put(view, anim);
        anim.start();
    }

    /**
     * Smoothly hide a view: animate its height to 0 while fading out.
     */
    private void animateHide(View view) {
        cancelViewAnimation(view);
        final ViewGroup.LayoutParams lp = view.getLayoutParams();
        final int start = view.getHeight();
        ValueAnimator anim = ValueAnimator.ofInt(start, 0);
        anim.addUpdateListener(a -> {
            lp.height = (int) a.getAnimatedValue();
            view.setAlpha(1f - a.getAnimatedFraction());
            view.requestLayout();
        });
        anim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                view.setVisibility(View.GONE);
                lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                view.setAlpha(1f);
            }
        });
        anim.setDuration(180);
        viewAnimators.put(view, anim);
        anim.start();
    }

    private void applyWindowInsetsPadding() {
        rootView.setOnApplyWindowInsetsListener((v, insets) -> {
            int top;
            int bottom;
            Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
            top = bars.top;
            bottom = bars.bottom;
            int side = dp(20);
            v.setPadding(side, top + dp(12), side, bottom + dp(12));
            return insets;
        });
        rootView.requestApplyInsets();
    }

    private String buildDeviceSummary() {
        return getString(R.string.device_label) + ": " + resolveDeviceName() + "\n" + getString(R.string.kernel_label) + ": " + System.getProperty("os.version", "unknown");
    }

    private void applyKernelStatus() {
        boolean ok = isKernelSupported();
        int color = getColor(ok ? R.color.status_success : R.color.status_error);
        int bg = getColor(ok ? R.color.status_success_bg : R.color.status_error_bg);
        kernelChipText.setText(ok ? R.string.kernel_supported : R.string.kernel_unsupported);
        kernelChipText.setTextColor(color);
        setViewColor(kernelChip, bg);
    }

    /**
     * Sales/market name of the device, matching the language of the current region.
     */
    private String resolveDeviceName() {
        boolean cn = "CN".equalsIgnoreCase(Locale.getDefault().getCountry());
        String marketName = firstValidProperty(cn ? "ro.vendor.oplus.market.name" : "ro.vendor.oplus.market.enname", cn ? "ro.vendor.oplus.market.enname" : "ro.vendor.oplus.market.name", "ro.product.marketname");
        return marketName != null ? marketName : Build.MANUFACTURER + " " + Build.MODEL;
    }

    private void startExploit() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        setRunState(RunState.RUNNING);
        // keep the screen awake
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        appendLog("==== start ====");
        appendLog("cpu pair: " + cpuPairLabels.get(cpuPairIndex));
        worker.execute(() -> {
            int code = 1;
            try {
                File workDir = getFilesDir();
                File binary = resolveBinary();
                File ksud = prepareKsud(workDir);
                if (ksud != null) {
                    appendLog("binary ready (" + binary.length() + " bytes)");
                    appendLog("ksud ready");
                } else {
                    appendLog("warning: ksud not found");
                }

                File logFile = new File(workDir, KSU_LOG_NAME);
                logFile.delete();
                AtomicLong ksuOffset = new AtomicLong();
                Thread tailer = new Thread(() -> {
                    try {
                        while (!Thread.currentThread().isInterrupted()) {
                            tailKsuLog(logFile, ksuOffset);
                            Thread.sleep(200);
                        }
                    } catch (InterruptedException ignored) {
                    }
                }, "ksu-log-tailer");
                tailer.setDaemon(true);
                tailer.start();

                code = runBinary(binary, workDir);

                tailer.interrupt();
                try {
                    tailer.join(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                tailKsuLog(logFile, ksuOffset);

                appendLog("exit code=" + code);
            } catch (Throwable t) {
                appendLog("error: " + t.getClass().getSimpleName() + ": " + t.getMessage());
            } finally {
                int finalCode = code;
                ui.post(() -> {
                    running.set(false);
                    getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    if (finalCode == 0) {
                        setRunState(RunState.SUCCESS);
                    } else {
                        setRunState(RunState.FAILED);
                    }
                            });
            }
        });
    }





    private void setRunState(RunState state) {
        runButton.setEnabled(state != RunState.RUNNING);
        runButton.setText(state == RunState.RUNNING ? R.string.action_running : R.string.action_run);
    }

    private File resolveBinary() throws IOException {
        File packaged = new File(getApplicationInfo().nativeLibraryDir, BINARY_NAME);
        if (!packaged.isFile()) {
            throw new IOException("missing native binary: " + packaged.getAbsolutePath());
        }
        
        // Use the native library directory directly for execution to satisfy Android 14 restrictions.
        appendLog("binary ready (" + packaged.length() + " bytes)");
        return packaged;
    }

    private File prepareKsud(File workDir) {
        File out = new File(workDir, KSUD_NAME);
        boolean anyInstalled = false;
        for (String pkg : KSU_MANAGER_PACKAGES) {
            ApplicationInfo appInfo;
            try {
                appInfo = getPackageManager().getApplicationInfo(pkg, 0);
            } catch (PackageManager.NameNotFoundException ignored) {
                continue;
            }
            anyInstalled = true;
            File src = new File(appInfo.nativeLibraryDir, "libksud.so");
            if (!src.isFile()) {
                continue;
            }
            try {
                copyFile(src, out);
                try {
                    Os.chmod(out.getAbsolutePath(), 448);
                } catch (ErrnoException ignored) {
                }
                return out;
            } catch (Throwable t) {
                appendLog("copy ksud failed: " + t.getMessage());
            }
        }
        if (!anyInstalled) {
            appendLog("KernelSU/ReSukiSU app not installed");
        }
        return null;
    }

    private int runBinary(File binary, File workDir) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(binary.getAbsolutePath());
        pb.directory(workDir);
        pb.redirectErrorStream(true);
        pb.environment().put("GHOSTLOCK_HOME", workDir.getAbsolutePath());
        pb.environment().put("TMPDIR", workDir.getAbsolutePath());
        pb.environment().put("HOME", workDir.getAbsolutePath());
        pb.environment().put("GHOSTLOCK_BUILD_ID", Build.MODEL + "_" + Build.DISPLAY);
        int[] pair = cpuPairs.get(cpuPairIndex);
        if (pair[0] != 0 || pair[1] != 1) {
            pb.environment().put("GHOSTLOCK_CORE", String.valueOf(pair[0]));
            pb.environment().put("GHOSTLOCK_CONSUMER_CORE", String.valueOf(pair[1]));
        }
        return runProcess(pb);
    }

    private void tailKsuLog(File logFile, AtomicLong offset) {
        if (!logFile.isFile()) {
            return;
        }
        synchronized (offset) {
            try (RandomAccessFile raf = new RandomAccessFile(logFile, "r")) {
                long size = raf.length();
                long pos = offset.get();
                if (size < pos) {
                    pos = 0; // log was truncated/recreated by this run
                }
                raf.seek(pos);
                long lastComplete = pos;
                StringBuilder pending = new StringBuilder(512);
                int b;
                while ((b = raf.read()) != -1) {
                    if (b == '\n') {
                        if (pending.length() > 0) {
                            appendLog(pending.toString());
                            pending.setLength(0);
                        }
                        lastComplete = raf.getFilePointer();
                    } else {
                        pending.append((char) b);
                    }
                }
                offset.set(lastComplete);
            } catch (IOException ignored) {
            }
        }
    }

    private int runProcess(ProcessBuilder pb) throws IOException, InterruptedException {
        return runProcess(pb, 300);
    }

    private int runProcess(ProcessBuilder pb, long timeoutSeconds) throws IOException, InterruptedException {
        Process process = pb.start();
        Thread reader = new Thread(() -> {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    appendLog(line);
                }
            } catch (IOException ignored) {
            }
        }, "ghostlock-reader");
        reader.setDaemon(true);
        reader.start();

        boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        if (!finished) {
            process.destroy();
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
        }
        // Drain buffered output, then force-unblock the reader: a late-load
        // daemon (zygisk) can inherit our pipe and keep it open forever.
        try {
            reader.join(3000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        try {
            process.getInputStream().close();
        } catch (IOException ignored) {
        }
        try {
            reader.join(3000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        return finished ? process.exitValue() : -1;
    }

    private void copyLogs() {
        ClipboardManager cm = getSystemService(ClipboardManager.class);
        if (cm == null) {
            return;
        }
        String text;
        synchronized (logBuffer) {
            text = logBuffer.toString();
        }
        cm.setPrimaryClip(ClipData.newPlainText("ghostlock-log", text));
        Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show();
    }

    private void toast(int resId) {
        Toast.makeText(this, resId, Toast.LENGTH_SHORT).show();
    }

    private void appendLog(String line) {
        if (line == null) {
            return;
        }
        final String msg = line.endsWith("\n") ? line : line + "\n";
        final String plain = stripAnsi(msg);
        synchronized (logBuffer) {
            logBuffer.append(plain);
        }
        final CharSequence display = colorize(plain);
        ui.post(() -> {
            logView.append(display);
            logScroll.post(() -> logScroll.fullScroll(View.FOCUS_DOWN));
        });
        android.util.Log.i(TAG, plain.trim());
    }

    private int dp(int value) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(value * density);
    }

    private enum RunState {
        IDLE, RUNNING, SUCCESS, FAILED
    }
}
