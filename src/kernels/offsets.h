#ifndef OFFSETS_H
#define OFFSETS_H

#include <stdint.h>

struct kernel_offsets {
  const char *uname_r;
  /* Exact Build.MODEL + "_" + Build.DISPLAY identifier; optional. */
  const char *build_id;
  /* Bootloader-selected physical load address; 0 uses target.h. */
  uint64_t kernel_phys_load;
  /* Kernel image mapping base and linear map base; 0 uses target.h. */
  uint64_t kimage_text_base, page_offset;
  /* Retained physical base alias from imported offset tables. */
  uint64_t phys_offset;
  /* pselect fd_set waiter word shift; 0 uses target.h default. */
  int pselect_waiter_shift;
  /* Global symbols: extracted from kallsyms or the matching vmlinux. */
  uint64_t off_init_task, off_init_cred, off_init_uts_ns, off_empty_zero_page;
  uint64_t off_root_task_group, off_selinux_enforcing, off_kptr_restrict;
  uint64_t off_selinux_blob_sizes, off_security_hook_heads, off_kmalloc_caches;
  uint64_t off_anon_pipe_buf_ops, off_ashmem_misc_fops, off_ashmem_fops;
  uint64_t off_ashmem_ioctl, off_ashmem_compat_ioctl, off_ashmem_mmap;
  uint64_t off_ashmem_open, off_ashmem_release, off_ashmem_show_fdinfo;
  uint64_t off_configfs_read_iter, off_configfs_bin_write_iter;
  uint64_t off_copy_splice_read, off_noop_llseek;
  /* Imported symbols retained for registry compatibility. */
  uint64_t off_cap_capable_active, off_system_unbound_wq;
  uint64_t off_call_usermodehelper_exec_work;
  uint64_t off_slide_nfulnl_logger, off_slide_loggers_0_1, off_slide_boot_id;

  /* BTF struct fields for this exact kernel; 0 uses target.h defaults. */
  uint32_t task_prio, task_normal_prio, task_sched_task_group;
  uint32_t task_pi_lock, task_pi_waiters, task_pi_top_task, task_pi_blocked_on;
  uint32_t task_pid, task_tgid, task_atomic_flags;
  uint32_t task_real_cred, task_cred, task_comm, task_tasks, task_seccomp;
  uint32_t task_vr_tag_a, task_vr_tag_b, task_flags, task_thread_info_flags_bit;
};

#define OFFSETS_ENTRY(uname, ...) { .uname_r = uname, __VA_ARGS__ }


#define STRUCT_OFFSETS_6_6_FIRE                                             \
  /* BTF: task_struct fields for Fire 6.6.58. */                         \
  .task_prio = 0x84, .task_normal_prio = 0x8c, .task_sched_task_group = 0x348, \
  .task_pi_lock = 0x90c, .task_pi_waiters = 0x920,                             \
  .task_pi_top_task = 0x930, .task_pi_blocked_on = 0x938,                      \
  .task_pid = 0x618, .task_tgid = 0x61c,                                       \
  .task_atomic_flags = 0x5d8, .task_real_cred = 0x818, .task_cred = 0x820,     \
  .task_comm = 0x830, .task_tasks = 0x550, .task_seccomp = 0x8e8

static const struct kernel_offsets known_offsets[] = {
/* Add new kernels by creating src/kernels/<uname-release>/offsets.h */
#include "6.6.58-android15-8-g33c1ba9ffede-4k/offsets.h"
/* Single-line entries for standalone versions */
		  { .uname_r = NULL }
};
#endif
