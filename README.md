# GhostLock Fire

Fire-only build for the Redmi 12 4G powered by MediaTek MT6768.

## Supported target

This source supports one exact kernel release:

```text
6.6.58-android15-8-g33c1ba9ffede-4k
```

The matching device build identifier is:

```text
23053RN02A_AP3A.240905.015.A2
```

The runtime offset entry is [src/kernels/fire/offsets.h](src/kernels/fire/offsets.h). It contains the Fire-specific kernel symbols, task layout, physical load address, and pselect waiter shift. The payload layout is defined separately in [src/core/target.h](src/core/target.h).

Other kernel releases are rejected. Do not reuse the Fire offsets for another device or kernel build.

## Build

The Makefile uses the Android NDK side-by-side toolchain and builds an arm64 PIE binary:

```powershell
cd ghostlock-app
make clean
make ghostlock
```

The output binary is:

```text
ghostlock-app/ghostlock
```

On Windows, set `ANDROID_HOME` or `LOCALAPPDATA` so the Makefile can locate the installed Android NDK.

## ADB testing

Push the binary and make it executable:

```powershell
adb push ghostlock /data/local/tmp/ghostlock
adb shell chmod 755 /data/local/tmp/ghostlock
```

Run the non-destructive runtime compatibility check first:

```powershell
adb shell /data/local/tmp/ghostlock --check
```

The check should report the exact kernel release and the runtime PI result. A successful compatibility check only confirms the trigger condition; it does not confirm that every later exploit stage will succeed.

## CPU selection

The default pair is main CPU `0` and consumer CPU `1`. Override it from the shell when testing a permitted pair:

```powershell
adb shell "GHOSTLOCK_CORE=6 GHOSTLOCK_CONSUMER_CORE=7 /data/local/tmp/ghostlock"
```

The program prints the selected pair at startup. Keep the two CPUs different and use the device's available CPU range.

## Offset evidence

Fire layout data is kept in the workspace for verification:

- `btf_layout.json`
- `offsets.json`
- `fire-vmlinux.elf`
- `src/kernels/fire/offsets.h`

`offsets.json` and the extractor are reference tooling only. The runtime binary selects offsets from the compiled kernel registry and does not import an external JSON file.

## Safety notes

- Use `--check` before any exploit run.
- Do not run the binary against an unsupported kernel.
- A failed heap-spray or `mm_struct` leak can trigger retries and may destabilize the device.
- Keep a current pstore log when investigating a reboot or kernel panic.

## License

See [LICENSE](LICENSE) for the project license and attribution details.
