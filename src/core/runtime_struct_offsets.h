#ifndef RUNTIME_STRUCT_OFFSETS_H
#define RUNTIME_STRUCT_OFFSETS_H

#include "../kernels/offsets.h"

extern const struct kernel_offsets *active_offsets;

#define _RSO(field, fallback) (active_offsets && active_offsets->field ? active_offsets->field : (fallback))
#define _RSO_64(field, fallback) ((uint64_t)_RSO(field, fallback))
#define _RSO_IMAGE(field, fallback) \
  ((active_offsets && active_offsets->field) ? (KIMAGE_TEXT_BASE_DYNAMIC + active_offsets->field) : (fallback))

/* Override layout macros with the selected device entry. */
#undef KIMAGE_TEXT_BASE
#undef P0_PAGE_OFFSET
#define KIMAGE_TEXT_BASE_DYNAMIC \
  ((active_offsets && active_offsets->kimage_text_base) ? active_offsets->kimage_text_base : 0xffffffc080000000ULL)
#define KIMAGE_TEXT_BASE KIMAGE_TEXT_BASE_DYNAMIC
#define P0_PAGE_OFFSET \
  ((active_offsets && active_offsets->page_offset) ? active_offsets->page_offset : 0xffffff8000000000ULL)
#undef DIRECT_MAP_BASE
#define DIRECT_MAP_BASE P0_PAGE_OFFSET

/* Override symbol macros with the selected device entry. */
#undef INIT_TASK
#undef INIT_CRED
#undef ROOT_TASK_GROUP
#undef SELINUX_ENFORCING
#undef SELINUX_BLOB_SIZES
#undef SECURITY_HOOK_HEADS
#undef SLIDE_NFULNL_LOGGER_IMAGE
#undef SLIDE_LOGGERS_0_1_IMAGE
#undef SLIDE_RANDOM_BOOT_ID_DATA_IMAGE
#undef SLIDE_INIT_TASK_IMAGE
#undef SLIDE_ROOT_TASK_GROUP_IMAGE
#undef SLIDE_SYSCTL_BOOTID_IMAGE
#undef ANON_PIPE_BUF_OPS
#undef ASHMEM_MISC_FOPS
#undef ASHMEM_FOPS
#undef ASHMEM_IOCTL
#undef ASHMEM_COMPAT_IOCTL
#undef ASHMEM_MMAP
#undef ASHMEM_OPEN
#undef ASHMEM_RELEASE
#undef ASHMEM_SHOW_FDINFO
#undef CONFIGFS_READ_ITER
#undef CONFIGFS_BIN_WRITE_ITER
#undef COPY_SPLICE_READ
#undef NOOP_LLSEEK
#undef CAP_CAPABLE_ACTIVE
#undef SYSTEM_UNBOUND_WQ
#undef CALL_USERMODEHELPER_EXEC_WORK
#undef KMALLOC_CACHES

#define INIT_TASK           _RSO_IMAGE(off_init_task, INIT_TASK_OFF)
#define INIT_CRED           _RSO_IMAGE(off_init_cred, INIT_CRED_OFF)
#define ROOT_TASK_GROUP     _RSO_IMAGE(off_root_task_group, ROOT_TASK_GROUP_OFF)
#define SELINUX_ENFORCING   _RSO_IMAGE(off_selinux_enforcing, SELINUX_ENFORCING_OFF)
#define SELINUX_BLOB_SIZES  _RSO_IMAGE(off_selinux_blob_sizes, SELINUX_BLOB_SIZES_OFF)
#define SECURITY_HOOK_HEADS _RSO_IMAGE(off_security_hook_heads, SECURITY_HOOK_HEADS_OFF)

#define SLIDE_NFULNL_LOGGER_IMAGE \
  _RSO_IMAGE(off_slide_nfulnl_logger, SLIDE_NFULNL_LOGGER_OFF)
#define SLIDE_LOGGERS_0_1_IMAGE \
  _RSO_IMAGE(off_slide_loggers_0_1, SLIDE_LOGGERS_0_1_OFF)
#define SLIDE_RANDOM_BOOT_ID_DATA_IMAGE \
  _RSO_IMAGE(off_slide_boot_id, SLIDE_RANDOM_BOOT_ID_DATA_OFF)
#define SLIDE_INIT_TASK_IMAGE _RSO_IMAGE(off_init_task, INIT_TASK_OFF)
#define SLIDE_ROOT_TASK_GROUP_IMAGE \
  _RSO_IMAGE(off_root_task_group, ROOT_TASK_GROUP_OFF)
#define SLIDE_SYSCTL_BOOTID_IMAGE \
  _RSO_IMAGE(off_slide_boot_id, SLIDE_SYSCTL_BOOTID_OFF)

#define ANON_PIPE_BUF_OPS   _RSO_IMAGE(off_anon_pipe_buf_ops, 0)
#define ASHMEM_MISC_FOPS    _RSO_IMAGE(off_ashmem_misc_fops, 0)
#define ASHMEM_FOPS         _RSO_IMAGE(off_ashmem_fops, 0)
#define ASHMEM_IOCTL        _RSO_IMAGE(off_ashmem_ioctl, 0)
#define ASHMEM_COMPAT_IOCTL _RSO_IMAGE(off_ashmem_compat_ioctl, 0)
#define ASHMEM_MMAP         _RSO_IMAGE(off_ashmem_mmap, 0)
#define ASHMEM_OPEN         _RSO_IMAGE(off_ashmem_open, 0)
#define ASHMEM_RELEASE      _RSO_IMAGE(off_ashmem_release, 0)
#define ASHMEM_SHOW_FDINFO  _RSO_IMAGE(off_ashmem_show_fdinfo, 0)
#define CONFIGFS_READ_ITER  _RSO_IMAGE(off_configfs_read_iter, 0)
#define CONFIGFS_BIN_WRITE_ITER _RSO_IMAGE(off_configfs_bin_write_iter, 0)
#define COPY_SPLICE_READ    _RSO_IMAGE(off_copy_splice_read, 0)
#define NOOP_LLSEEK         _RSO_IMAGE(off_noop_llseek, 0)
#define CAP_CAPABLE_ACTIVE  _RSO_IMAGE(off_cap_capable_active, 0)
#define SYSTEM_UNBOUND_WQ   _RSO_IMAGE(off_system_unbound_wq, 0)
#define CALL_USERMODEHELPER_EXEC_WORK _RSO_IMAGE(off_call_usermodehelper_exec_work, 0)
#define KMALLOC_CACHES      _RSO_IMAGE(off_kmalloc_caches, 0)

#undef FAKE_TASK_PRIO_OFF
#undef FAKE_TASK_NORMAL_PRIO_OFF
#undef FAKE_TASK_TASK_GROUP_OFF
#undef FAKE_TASK_PI_LOCK_OFF
#undef FAKE_TASK_PI_WAITERS_OFF
#undef FAKE_TASK_PI_TOP_TASK_OFF
#undef FAKE_TASK_PI_BLOCKED_ON_OFF
#undef TASK_PID_OFF
#undef TASK_TGID_OFF
#undef TASK_ATOMIC_FLAGS_OFF
#undef TASK_REAL_CRED_OFF
#undef TASK_CRED_OFF
#undef TASK_COMM_OFF
#undef TASK_TASKS_OFF
#undef TASK_SECCOMP_OFF
#undef TASK_VR_TAG_A_OFF
#undef TASK_VR_TAG_B_OFF
#undef TASK_FLAGS_OFF
#undef TASK_THREAD_INFO_FLAGS_BIT
#undef MM_OWNER_OFF

#define FAKE_TASK_PRIO_OFF           _RSO(task_prio, 0x94)
#define FAKE_TASK_NORMAL_PRIO_OFF    _RSO(task_normal_prio, 0x9C)
#define FAKE_TASK_TASK_GROUP_OFF     _RSO(task_sched_task_group, 0x420)
#define FAKE_TASK_PI_LOCK_OFF        _RSO(task_pi_lock, 0x9EC)
#define FAKE_TASK_PI_WAITERS_OFF     _RSO(task_pi_waiters, 0xA00)
#define FAKE_TASK_PI_TOP_TASK_OFF    _RSO(task_pi_top_task, 0xA10)
#define FAKE_TASK_PI_BLOCKED_ON_OFF  _RSO(task_pi_blocked_on, 0xA18)
#define TASK_PID_OFF             _RSO(task_pid, 0x708)
#define TASK_TGID_OFF            _RSO(task_tgid, 0x70C)
#define TASK_ATOMIC_FLAGS_OFF    _RSO(task_atomic_flags, 0x6C8)
#define TASK_REAL_CRED_OFF       _RSO(task_real_cred, 0x8F8)
#define TASK_CRED_OFF            _RSO(task_cred, 0x900)
#define TASK_COMM_OFF            _RSO(task_comm, 0x910)
#define TASK_TASKS_OFF           _RSO(task_tasks, 0x638)
#define TASK_SECCOMP_OFF         _RSO(task_seccomp, 0x9C8)
#define TASK_VR_TAG_A_OFF        _RSO(task_vr_tag_a, 0x06)
#define TASK_VR_TAG_B_OFF        _RSO(task_vr_tag_b, 0x2c)
#define TASK_FLAGS_OFF           _RSO(task_flags, 0x00)
#define TASK_THREAD_INFO_FLAGS_BIT _RSO(task_thread_info_flags_bit, 0x400)
#define MM_OWNER_OFF             _RSO(mm_owner, 0x410)

#endif
