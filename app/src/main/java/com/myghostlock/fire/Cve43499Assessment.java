package com.myghostlock.fire;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Read-only compatibility assessment for CVE-2026-43499 (GhostLock).
 *
 * This is deliberately not an exploit test. It combines the kernel release
 * reported by the device with the upstream stable fixes and lets the UI show
 * the separate question of whether this app has offsets for that release.
 */
public final class Cve43499Assessment {
    public enum Exposure {
        LIKELY_AFFECTED,
        LIKELY_PATCHED,
        INCONCLUSIVE
    }

    public enum PathSupport {
        READY_TO_TRY,
        NO_OFFSETS,
        UNSUPPORTED_ARCHITECTURE,
        NOT_RECOMMENDED
    }

    public static final class Result {
        public final String release;
        public final Exposure exposure;
        public final PathSupport pathSupport;
        public final String versionEvidence;

        private Result(String release, Exposure exposure, PathSupport pathSupport, String versionEvidence) {
            this.release = release;
            this.exposure = exposure;
            this.pathSupport = pathSupport;
            this.versionEvidence = versionEvidence;
        }
    }

    private static final Pattern RELEASE = Pattern.compile("^(\\d+)\\.(\\d+)\\.(\\d+)(?:[-+_].*)?$");

    private Cve43499Assessment() {
    }

    /**
     * Evaluate only the reported kernel release. Vendor backports and custom
     * kernels can make the final state differ, so callers must retain the
     * INCONCLUSIVE state rather than treating it as either safe or vulnerable.
     */
    public static Result evaluate(String release, boolean isArm64, boolean hasOffsets) {
        Version version = Version.parse(release);
        Exposure exposure = version == null ? Exposure.INCONCLUSIVE : exposureFor(version);
        PathSupport support;
        if (!isArm64) {
            support = PathSupport.UNSUPPORTED_ARCHITECTURE;
        } else if (exposure == Exposure.LIKELY_PATCHED) {
            support = PathSupport.NOT_RECOMMENDED;
        } else if (hasOffsets) {
            support = PathSupport.READY_TO_TRY;
        } else {
            support = PathSupport.NO_OFFSETS;
        }
        String evidence = versionEvidence(version, exposure);
        return new Result(release == null ? "" : release, exposure, support, evidence);
    }

    private static Exposure exposureFor(Version version) {
        int fixedPatch = fixedPatchFor(version.major, version.minor);
        if (fixedPatch >= 0) {
            return version.patch >= fixedPatch ? Exposure.LIKELY_PATCHED : Exposure.LIKELY_AFFECTED;
        }
        // The CVE record marks 7.1 and newer as unaffected; 7.0 is fixed at .4.
        if (version.major == 7 && version.minor >= 1) {
            return Exposure.LIKELY_PATCHED;
        }
        if (version.major == 7 && version.minor == 0) {
            return version.patch >= 4 ? Exposure.LIKELY_PATCHED : Exposure.LIKELY_AFFECTED;
        }
        // The vulnerability was introduced in 2.6.39, but Android vendors often
        // backport security fixes without changing the base release string.
        if (version.major > 2 || (version.major == 2 && version.minor == 6 && version.patch >= 39)) {
            return Exposure.INCONCLUSIVE;
        }
        return Exposure.LIKELY_PATCHED;
    }

    private static int fixedPatchFor(int major, int minor) {
        if (major == 5 && minor == 10) return 261;
        if (major == 5 && minor == 15) return 212;
        if (major == 6 && minor == 1) return 175;
        if (major == 6 && minor == 6) return 140;
        if (major == 6 && minor == 12) return 86;
        if (major == 6 && minor == 18) return 27;
        return -1;
    }

    private static String versionEvidence(Version version, Exposure exposure) {
        if (version == null) {
            return "Unable to parse the kernel version string; no conclusion can be drawn from upstream fix thresholds.";
        }
        switch (exposure) {
            case LIKELY_AFFECTED:
                return String.format(Locale.ROOT, "Kernel version %d.%d.%d is below the upstream fix threshold for this stable branch.", version.major, version.minor, version.patch);
            case LIKELY_PATCHED:
                return String.format(Locale.ROOT, "Kernel version %d.%d.%d meets or exceeds the upstream fix threshold for this stable branch.", version.major, version.minor, version.patch);
            default:
                return "This kernel branch cannot be classified directly using public stable-branch thresholds; the vendor may have backported the fix or applied custom patches.";
        }
    }

    private static final class Version {
        final int major;
        final int minor;
        final int patch;

        private Version(int major, int minor, int patch) {
            this.major = major;
            this.minor = minor;
            this.patch = patch;
        }

        static Version parse(String value) {
            if (value == null) {
                return null;
            }
            Matcher matcher = RELEASE.matcher(value.trim());
            if (!matcher.matches()) {
                return null;
            }
            try {
                return new Version(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)), Integer.parseInt(matcher.group(3)));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
    }
}
