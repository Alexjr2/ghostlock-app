# 8gen3/Android 16 Advanced Device Offset Research

## 1. Differences in calculation methods

Recent Android 15/16 devices running kernels 6.6 or 6.12 can require different
exploit calculations.

### A. Struct offset mapping

Kernel 6.12 has significant layout changes compared with 6.6. For example:

- **task_cred**: `0x820` on 6.6 and `0x900` on 6.12.
- **task_pi_blocked_on**: `0x938` on 6.6 and `0xA18` on 6.12.

**Conclusion:** If the calculation does not distinguish kernel versions, a
privilege-changing write may target the wrong address and fail silently.

### B. Stack layout offset (PSELECT_SHIFT)

On Android 16 (16.1), Profile Guided Optimization can change the `do_futex`
call depth:

- **Traditional devices:** usually `0` or `2`.
- **Recent Oppo/OnePlus devices (16.0.9+):** `-2` may be required to reach the
  intended UAF target.

## 2. Newly supported devices

The following device offset entries are included in this research:

| Device | SoC | Kernel | Calculation |
| :--- | :--- | :--- | :--- |
| **OPPO Find X9 Ultra** | Snapdragon 8 Elite | 6.12.58 | 6.12 mapping + shift 0 |
| **OnePlus 13** | Snapdragon 8 Elite | 6.6.89 | 6.6 mapping + shift 0 |
| **OPPO Pad 4 Pro** | Snapdragon 8 Elite | 6.6.89 | 6.6 mapping + shift -2 |
| **Xiaomi 17 (pudding)** | Snapdragon 8 Elite Pro | 6.12.69 | 6.12 mapping + phys 0xc7800000 |
| **Realme GT5 Pro** | Snapdragon 8 Gen 3 | 6.12.23 | 6.12 mapping + shift 2 |

## 3. Integration status

- **Main registry updated:** supports `STRUCT_OFFSETS_6_12` and
  `STRUCT_OFFSETS_6_6` macros.
- **High-precision probing retained:** native behavior probing remains enabled
  to improve vulnerability detection on these devices.
