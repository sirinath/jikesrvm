/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */

/**
 * O/S support services required by the java class libraries.
 * See also: BootRecord.java
 */

// Aix and Linux version.  PowerPC and IA32.

#include "sys.h"

// Only called externally from Java programs.
EXTERNAL void sysExit(int) NORETURN;

//Solaris needs BSD_COMP to be set to enable the FIONREAD ioctl
#if defined (__SVR4) && defined (__sun)
#define BSD_COMP
#endif

// Work around AIX headerfile differences: AIX 4.3 vs earlier releases
//
#ifdef _AIX43
#include </usr/include/unistd.h>
EXTERNAL void profil(void *, uint, ulong, uint);
EXTERNAL int sched_yield(void);
#endif

#include <stdio.h>
#include <stdlib.h>      // getenv() and others
#include <unistd.h>
#include <string.h>
#include <dirent.h>
#include <fcntl.h>
#include <sys/time.h>
#include <sys/resource.h> // getpriority, setpriority and PRIO_PROCESS
#include <sys/types.h>
#include <sys/wait.h>
#include <time.h>               // nanosleep() and other
#include <utime.h>
#include <setjmp.h>

#ifdef __GLIBC__
/* use glibc internal longjmp to bypass fortify checks */
EXTERNAL void __libc_longjmp (jmp_buf buf, int val) \
                    __attribute__ ((__noreturn__));
#define rvm_longjmp(buf, ret) \
        __libc_longjmp(buf, ret)
#else
#define rvm_longjmp(buf, ret) \
        longjmp(buf, ret)
#endif /* !__GLIBC__ */

#ifdef RVM_WITH_PERFEVENT
#include <perfmon/pfmlib_perf_event.h>
#include <err.h>
#endif

#if (defined RVM_FOR_LINUX) || (defined RVM_FOR_SOLARIS) 
#include <sys/stat.h>
#include <sys/sysinfo.h>
#include <netinet/in.h>
#include <signal.h>
#include <sys/ioctl.h>
#ifdef RVM_FOR_LINUX
#include <asm/ioctls.h>
#include <sys/syscall.h>
#endif

# include <sched.h>

/* OSX/Darwin */
#elif (defined __MACH__)
#include <sys/stat.h>
#include <netinet/in.h>
#include <signal.h>
#include <sys/ioctl.h>
#include <mach-o/dyld.h>
#include <mach/host_priv.h>
#include <mach/mach_init.h>
#include <mach/mach_host.h>
#include <mach/vm_map.h>
#include <mach/processor_info.h>
#include <mach/processor.h>
#include <mach/thread_act.h>
#include <sys/types.h>
#include <sys/sysctl.h>
/* As of 10.4, dlopen comes with the OS */
#include <dlfcn.h>
#define MAP_ANONYMOUS MAP_ANON
#include <sched.h>

/* AIX/PowerPC */
#else
#include <sys/cache.h>
#include <sys/ioctl.h>
#endif

#include <sys/shm.h>        /* disclaim() */
#include <strings.h>        /* bzero() */
#include <sys/mman.h>       /* mmap & munmap() */
#include <sys/shm.h>
#include <errno.h>
#include <dlfcn.h>
#include <inttypes.h>           // uintptr_t

#ifdef _AIX
EXTERNAL timer_t gettimerid(int timer_type, int notify_type);
EXTERNAL int     incinterval(timer_t id, itimerstruc_t *newvalue, itimerstruc_t *oldvalue);
#include <sys/events.h>
#endif

#ifdef RVM_FOR_HARMONY
#include "hythread.h"
#else
#include <pthread.h>
#endif

EXTERNAL Word sysMonitorCreate();
EXTERNAL void sysMonitorDestroy(Word);
EXTERNAL void sysMonitorEnter(Word);
EXTERNAL void sysMonitorExit(Word);
EXTERNAL void sysMonitorTimedWait(Word, long long);
EXTERNAL void sysMonitorWait(Word);
EXTERNAL void sysMonitorBroadcast(Word);

// #define DEBUG_SYS
// #define DEBUG_THREAD

#ifdef RVM_FOR_HARMONY
EXTERNAL int sysThreadStartup(void *args);
#else
EXTERNAL void *sysThreadStartup(void *args);
#endif

EXTERNAL void hardwareTrapHandler(int signo, siginfo_t *si, void *context);

/* This routine is not yet used by all of the functions that return strings in
 * buffers, but I hope that it will be one day. */
static int loadResultBuf(char * buf, int limit, const char *result);

static void* checkMalloc(int);
static void* checkCalloc(int, int);
static void checkFree(void*);

extern TLS_KEY_TYPE VmThreadKey;
TLS_KEY_TYPE TerminateJmpBufKey;

TLS_KEY_TYPE createThreadLocal() {
    TLS_KEY_TYPE key;
    int rc;
#ifdef RVM_FOR_HARMONY
    rc = hythread_tls_alloc(&key);
#else
    rc = pthread_key_create(&key, 0);
#endif
    if (rc != 0) {
        fprintf(SysErrorFile, "%s: alloc tls key failed (err=%d)\n", Me, rc);
        sysExit(EXIT_STATUS_SYSCALL_TROUBLE);
    }
    return key;
}

// Create keys for thread-specific data.
EXTERNAL void
sysCreateThreadSpecificDataKeys(void)
{
    TRACE_PRINTF(SysTraceFile, "%s: sysCreateThreadSpecificDataKeys\n", Me);
    int rc;

    // Create a key for thread-specific data so we can associate
    // the id of the Processor object with the pthread it is running on.
    VmThreadKey = createThreadLocal();
    TerminateJmpBufKey = createThreadLocal();
    TRACE_PRINTF(stderr, "%s: vm processor key=%lu\n", Me, VmThreadKey);
}

void setThreadLocal(TLS_KEY_TYPE key, void * value) {
#ifdef RVM_FOR_HARMONY
    int rc = hythread_tls_set(hythread_self(), key, value);
#else
    int rc = pthread_setspecific(key, value);
#endif
    if (rc != 0) {
        fprintf(SysErrorFile, "%s: set tls failed (err=%d)\n", Me, rc);
        sysExit(EXIT_STATUS_SYSCALL_TROUBLE);
    }
}

EXTERNAL void
sysStashVMThread(Address vmThread)
{
    TRACE_PRINTF(SysErrorFile, "%s: sysStashVmProcessorInPthread %p\n", Me, vmThread);
    setThreadLocal(VmThreadKey, (void*)vmThread);
}

EXTERNAL void *
getVmThread()
{
    return GET_THREAD_LOCAL(VmThreadKey);
}

// Console write (java character).
//
EXTERNAL void
sysConsoleWriteChar(unsigned value)
{
    char c = (value > 127) ? '?' : (char)value;
    // use high level stdio to ensure buffering policy is observed
    fprintf(SysTraceFile, "%c", c);
}

// Console write (java integer).
//
EXTERNAL void
sysConsoleWriteInteger(int value, int hexToo)
{
    if (hexToo==0 /*false*/)
        fprintf(SysTraceFile, "%d", value);
    else if (hexToo==1 /*true - also print in hex*/)
        fprintf(SysTraceFile, "%d (0x%08x)", value, value);
    else    /* hexToo==2 for only in hex */
        fprintf(SysTraceFile, "0x%08x", value);
}

// Console write (java long).
//
EXTERNAL void
sysConsoleWriteLong(long long value, int hexToo)
{
    if (hexToo==0 /*false*/)
        fprintf(SysTraceFile, "%lld", value);
    else if (hexToo==1 /*true - also print in hex*/) {
        int value1 = (value >> 32) & 0xFFFFFFFF;
        int value2 = value & 0xFFFFFFFF;
        fprintf(SysTraceFile, "%lld (0x%08x%08x)", value, value1, value2);
    } else { /* hexToo==2 for only in hex */
        int value1 = (value >> 32) & 0xFFFFFFFF;
        int value2 = value & 0xFFFFFFFF;
        fprintf(SysTraceFile, "0x%08x%08x", value1, value2);
    }
}

// Console write (java double).
//
EXTERNAL void
sysConsoleWriteDouble(double value,  int postDecimalDigits)
{
    if (value != value) {
        fprintf(SysTraceFile, "NaN");
    } else {
        if (postDecimalDigits > 9) postDecimalDigits = 9;
        char tmp[5] = {'%', '.', '0'+postDecimalDigits, 'f', 0};
        fprintf(SysTraceFile, tmp, value);
    }
}

// alignment checking: hardware alignment checking variables and functions

#ifdef RVM_WITH_ALIGNMENT_CHECKING

volatile int numNativeAlignTraps;
volatile int numEightByteAlignTraps;
volatile int numBadAlignTraps;

static volatile int numEnableAlignCheckingCalls = 0;
static volatile int numDisableAlignCheckingCalls = 0;

EXTERNAL void sysEnableAlignmentChecking() {
  TRACE_PRINTF(SysTraceFile, "%s: sysEnableAlignmentChecking\n", Me);
  numEnableAlignCheckingCalls++;
  if (numEnableAlignCheckingCalls > numDisableAlignCheckingCalls) {
    asm("pushf\n\t"
        "orl $0x00040000,(%esp)\n\t"
        "popf");
  }
}

EXTERNAL void sysDisableAlignmentChecking() {
  TRACE_PRINTF(SysTraceFile, "%s: sysDisableAlignmentChecking\n", Me);
  numDisableAlignCheckingCalls++;
  asm("pushf\n\t"
      "andl $0xfffbffff,(%esp)\n\t"
      "popf");
}

EXTERNAL void sysReportAlignmentChecking() {
  fprintf(SysTraceFile, "\nAlignment checking report:\n\n");
  fprintf(SysTraceFile, "# native traps (ignored by default):             %d\n", numNativeAlignTraps);
  fprintf(SysTraceFile, "# 8-byte access traps (ignored by default):      %d\n", numEightByteAlignTraps);
  fprintf(SysTraceFile, "# bad access traps (throw exception by default): %d (should be zero)\n\n", numBadAlignTraps);
  fprintf(SysTraceFile, "# calls to sysEnableAlignmentChecking():         %d\n", numEnableAlignCheckingCalls);
  fprintf(SysTraceFile, "# calls to sysDisableAlignmentChecking():        %d\n\n", numDisableAlignCheckingCalls);
  fprintf(SysTraceFile, "# native traps again (to see if changed):        %d\n", numNativeAlignTraps);
  fprintf(SysTraceFile, "# 8-byte access again (to see if changed):       %d\n\n", numEightByteAlignTraps);

  // cause a native trap to see if traps are enabled
  volatile int dummy[2];
  volatile int prevNumNativeTraps = numNativeAlignTraps;
  *(int*)((char*)dummy + 1) = 0x12345678;
  int enabled = (numNativeAlignTraps != prevNumNativeTraps);

  fprintf(SysTraceFile, "# native traps again (to see if changed):        %d\n", numNativeAlignTraps);
  fprintf(SysTraceFile, "# 8-byte access again (to see if changed):       %d\n\n", numEightByteAlignTraps);
  fprintf(SysTraceFile, "Current status of alignment checking:            %s (should be on)\n\n", (enabled ? "on" : "off"));
}

#else

EXTERNAL void sysEnableAlignmentChecking() { }
EXTERNAL void sysDisableAlignmentChecking() { }
EXTERNAL void sysReportAlignmentChecking() { }

#endif // RVM_WITH_ALIGNMENT_CHECKING

Word DeathLock = NULL;

EXTERNAL void VMI_Initialize();

EXTERNAL void
sysInitialize()
{
    TRACE_PRINTF(SysTraceFile, "%s: sysInitialize\n", Me);
#ifdef RVM_FOR_HARMONY
    VMI_Initialize();
#endif
    DeathLock = sysMonitorCreate();
}


static bool systemExiting = false;

static const bool debugging = false;

// Exit with a return code.
//
EXTERNAL void
sysExit(int value)
{
    TRACE_PRINTF(SysErrorFile, "%s: sysExit %d\n", Me, value);
    // alignment checking: report info before exiting, then turn off checking
    #ifdef RVM_WITH_ALIGNMENT_CHECKING
    if (numEnableAlignCheckingCalls > 0) {
      sysReportAlignmentChecking();
      sysDisableAlignmentChecking();
    }
    #endif // RVM_WITH_ALIGNMENT_CHECKING

    if (lib_verbose & value != 0) {
        fprintf(SysErrorFile, "%s: exit %d\n", Me, value);
    }

    fflush(SysErrorFile);
    fflush(SysTraceFile);
    fflush(stdout);

    systemExiting = true;

    if (DeathLock) sysMonitorEnter(DeathLock);
    if (debugging && value!=0) {
        abort();
    }
    exit(value);
}

// Access host o/s command line arguments.
// Taken:    -1
//           null
// Returned: number of arguments
//
// /or/
//
// Taken:    arg number sought
//           buffer to fill
// Returned: number of bytes written to buffer (-1: arg didn't fit, buffer too small)
//
EXTERNAL int
sysArg(int argno, char *buf, int buflen)
{
    TRACE_PRINTF(SysErrorFile, "%s: sysArg %d\n", Me, argno);
    if (argno == -1) { // return arg count
        return JavaArgc;
        /***********
      for (int i = 0;;++i)
         if (JavaArgs[i] == 0)
            return i;
        **************/
    } else { // return i-th arg
        const char *src = JavaArgs[argno];
        for (int i = 0;; ++i)
        {
            if (*src == 0)
                return i;
            if (i == buflen)
                return -1;
            *buf++ = *src++;
        }
    }
    /* NOTREACHED */
}

/** Get the value of an enviroment variable.  (This refers to the C
    per-process environment.)   Used, indirectly, by VMSystem.getenv()

    Taken:    VARNAME, name of the envar we want.
         BUF, a buffer in which to place the value of that envar
              LIMIT, the size of BUF

    Returned: See the convention documented in loadResultBuf().

         0: A return value of 0 indicates that the envar was set with a
         zero-length value.   (Distinguised from unset, see below)

         -2: Indicates that the envar was unset.  This is distinguished
        from a zero-length value (see above).
*/
EXTERNAL int
sysGetenv(const char *varName, char *buf, int limit)
{
    TRACE_PRINTF(SysErrorFile, "%s: sysGetenv %s\n", Me, varName);
    return loadResultBuf(buf, limit, getenv(varName));
}



/* Copy SRC, a null-terminated string or a NULL pointer, into DEST, a buffer
 * with LIMIT characters capacity.   This is a helper function used by
 * sysGetEnv() and, later on, to be used by other functions returning strings
 * to Java.
 *
 * Handle the error handling for running out of space in BUF, in accordance
 * with the C '99 specification for snprintf() -- see sysGetEnv().
 *
 * Returned:  -2 if SRC is a NULL pointer.
 * Returned:  If enough space, the number of bytes copied to DEST.
 *
 *            If there is not enough space, we write what we can and return
 *            the # of characters that WOULD have been written to the final
 *            string BUF if enough space had been available, excluding any
 *            trailing '\0'.  This error handling is consistent with the C '99
 *            standard's behavior for the snprintf() system library function.
 *
 *            Note that this is NOT consistent with the behavior of most of
 *            the functions in this file that return strings to Java.
 *
 *            That should change with time.
 *
 *            This function will append a trailing '\0', if there is enough
 *            space, even though our caller does not need it nor use it.
 */
static int
loadResultBuf(char * dest, int limit, const char *src)
{
    if ( ! src )         // Is it set?
   return -2;      // Tell caller it was unset.

    for (int i = 0;; ++i) {
   if ( i < limit ) // If there's room for the next char of the value ...
       dest[i] = src[i];   // ... write it into the destination buffer.
   if (src[i] == '\0')
       return i;      // done, return # of chars needed for SRC
    }
}

/*
 * Performance counter support using the linux perf event system.
 */
EXTERNAL {
#ifndef RVM_WITH_PERFEVENT
void sysPerfEventInit(int events) {}
void sysPerfEventCreate(int id, const char *eventName) {}
void sysPerfEventEnable() {}
void sysPerfEventDisable() {}
void sysPerfEventRead(int id, long long *values) {}
#else
static int enabled = 0;
static int *perf_event_fds;
static struct perf_event_attr *perf_event_attrs;

void sysPerfEventInit(int numEvents)
{
  TRACE_PRINTF(SysTraceFile, "%s: sysPerfEventInit\n", Me);
  int ret = pfm_initialize();
  if (ret != PFM_SUCCESS) {
    errx(1, "error in pfm_initialize: %s", pfm_strerror(ret));
  }

  perf_event_fds = (int*)checkCalloc(numEvents, sizeof(int));
  if (!perf_event_fds) {
    errx(1, "error allocating perf_event_fds");
  }
  perf_event_attrs = (struct perf_event_attr *)checkCalloc(numEvents, sizeof(struct perf_event_attr));
  if (!perf_event_attrs) {
    errx(1, "error allocating perf_event_attrs");
  }
  for(int i=0; i < numEvents; i++) {
    perf_event_attrs[i].size = sizeof(struct perf_event_attr);
  }
  enabled = 1;
}

void sysPerfEventCreate(int id, const char *eventName)
{
  TRACE_PRINTF(SysTraceFile, "%s: sysPerfEventCreate\n", Me);
  struct perf_event_attr *pe = (perf_event_attrs + id);
  int ret = pfm_get_perf_event_encoding(eventName, PFM_PLM3, pe, NULL, NULL);
  if (ret != PFM_SUCCESS) {
    errx(1, "error creating event %d '%s': %s\n", id, eventName, pfm_strerror(ret));
  }
  pe->read_format = PERF_FORMAT_TOTAL_TIME_ENABLED | PERF_FORMAT_TOTAL_TIME_RUNNING;
  pe->disabled = 1;
  pe->inherit = 1;
  perf_event_fds[id] = perf_event_open(pe, 0, -1, -1, 0);
  if (perf_event_fds[id] == -1) {
    err(1, "error in perf_event_open for event %d '%s'", id, eventName);
  }
}

void sysPerfEventEnable()
{
  TRACE_PRINTF(SysTraceFile, "%s: sysPerfEventEnable\n", Me);
  if (enabled) {
    if (prctl(PR_TASK_PERF_EVENTS_ENABLE)) {
      err(1, "error in prctl(PR_TASK_PERF_EVENTS_ENABLE)");
    }
  }
}

void sysPerfEventDisable()
{
  TRACE_PRINTF(SysTraceFile, "%s: sysPerfEventDisable\n", Me);
  if (enabled) {
    if (prctl(PR_TASK_PERF_EVENTS_DISABLE)) {
      err(1, "error in prctl(PR_TASK_PERF_EVENTS_DISABLE)");
    }
  }
}

void sysPerfEventRead(int id, long long *values)
{
  TRACE_PRINTF(SysTraceFile, "%s: sysPerfEventRead\n", Me);
  size_t expectedBytes = 3 * sizeof(long long);
  int ret = read(perf_event_fds[id], values, expectedBytes);
  if (ret < 0) {
    err(1, "error reading event: %s", strerror(errno));
  }
  if (ret != expectedBytes) {
    errx(1, "read of perf event did not return 3 64-bit values");
  }
}
#endif
}

//------------------------//
// Filesystem operations. //
//------------------------//

// Get file status.
// Taken:    null terminated filename
//           kind of info desired (see FileSystem.STAT_XXX)
// Returned: status (-1=error)
//
// As of August 2003, this is never used. --Steve Augart
EXTERNAL int
sysStat(char *name, int kind)
{
    TRACE_PRINTF(SysTraceFile, "%s: sysStat %s %d\n", Me, name, kind);

    struct stat info;

    if (stat(name, &info))
        return -1; // does not exist, or other trouble

    switch (kind) {
    case FileSystem_STAT_EXISTS:
        return 1;                              // exists
    case FileSystem_STAT_IS_FILE:
        return S_ISREG(info.st_mode) != 0; // is file
    case FileSystem_STAT_IS_DIRECTORY:
        return S_ISDIR(info.st_mode) != 0; // is directory
    case FileSystem_STAT_IS_READABLE:
        return (info.st_mode & S_IREAD) != 0; // is readable by owner
    case FileSystem_STAT_IS_WRITABLE:
        return (info.st_mode & S_IWRITE) != 0; // is writable by owner
    case FileSystem_STAT_LAST_MODIFIED:
        return info.st_mtime;   // time of last modification
    case FileSystem_STAT_LENGTH:
        return info.st_size;    // length
    }
    return -1; // unrecognized request
}

// Check user's perms.
// Taken:    null terminated filename
//           kind of access perm to check for (see FileSystem.ACCESS_W_OK)
// Returned: 0 on success (-1=error)
//
EXTERNAL int
sysAccess(char *name, int kind)
{
    TRACE_PRINTF(SysTraceFile, "%s: sysAccess %s\n", Me, name);
    return access(name, kind);
}

// How many bytes can be read from file/socket without blocking?
// Taken:    file/socket descriptor
// Returned: >=0: count, ThreadIOConstants_FD_INVALID: bad file descriptor,
//          -1: other error
//
EXTERNAL int
sysBytesAvailable(int fd)
{
    TRACE_PRINTF(SysTraceFile, "%s: bytesAvailable %d\n", Me, fd);
    int count = 0;
    if (ioctl(fd, FIONREAD, &count) == -1)
    {
        return -1;
    }
    TRACE_PRINTF(SysTraceFile, "%s: available fd=%d count=%d\n", Me, fd, count);
    return count;
}

EXTERNAL int
sysSyncFile(int fd)
{
    TRACE_PRINTF(SysTraceFile, "%s: sync %d\n", Me, fd);
    if (fsync(fd) != 0) {
        // some kinds of files cannot be sync'ed, so don't print error message
        // however, do return error code in case some application cares
        return -1;
    }
    return 0;
}

// Read one byte from file.
// Taken:    file descriptor
// Returned: data read (-3: error, -2: operation would block, -1: eof, >= 0: valid)
//
EXTERNAL int
sysReadByte(int fd)
{
    TRACE_PRINTF(SysTraceFile, "%s: readByte %d\n", Me, fd);
    unsigned char ch;
    int rc;

again:
    switch ( rc = read(fd, &ch, 1))
    {
    case  1:
        /*fprintf(SysTraceFile, "%s: read (byte) ch is %d\n", Me, (int) ch);*/
        return (int) ch;
    case  0:
        /*fprintf(SysTraceFile, "%s: read (byte) rc is 0\n", Me);*/
        return -1;
    default:
        /*fprintf(SysTraceFile, "%s: read (byte) rc is %d\n", Me, rc);*/
        if (errno == EAGAIN)
            return -2;  // Read would have blocked
        else if (errno == EINTR)
            goto again; // Read was interrupted; try again
        else
            return -3;  // Some other error
    }
}

// Write one byte to file.
// Taken:    file descriptor
//           data to write
// Returned: -2 operation would block, -1: error, 0: success
//
EXTERNAL int
sysWriteByte(int fd, int data)
{
    char ch = data;
    TRACE_PRINTF(SysTraceFile, "%s: writeByte %d %c\n", Me, fd, ch);
again:
    int rc = write(fd, &ch, 1);
    if (rc == 1)
        return 0; // success
    else if (errno == EAGAIN)
        return -2; // operation would block
    else if (errno == EINTR)
        goto again; // interrupted by signal; try again
    else {
        fprintf(SysErrorFile, "%s: writeByte, fd=%d, write returned error %d (%s)\n", Me,
                fd, errno, strerror(errno));
        return -1; // some kind of error
    }
}

// Read multiple bytes from file or socket.
// Taken:    file or socket descriptor
//           buffer to be filled
//           number of bytes requested
// Returned: number of bytes delivered (-2: error, -1: socket would have blocked)
//
EXTERNAL int
sysReadBytes(int fd, char *buf, int cnt)
{
    TRACE_PRINTF(SysTraceFile, "%s: read %d %p %d\n", Me, fd, buf, cnt);
again:
    int rc = read(fd, buf, cnt);
    if (rc >= 0)
        return rc;
    int err = errno;
    if (err == EAGAIN)
    {
        TRACE_PRINTF(SysTraceFile, "%s: read on %d would have blocked: needs retry\n", Me, fd);
        return -1;
    }
    else if (err == EINTR)
        goto again; // interrupted by signal; try again
    fprintf(SysTraceFile, "%s: read error %d (%s) on %d\n", Me,
            err, strerror(err), fd);
    return -2;
}

// Write multiple bytes to file or socket.
// Taken:    file or socket descriptor
//           buffer to be written
//           number of bytes to write
// Returned: number of bytes written (-2: error, -1: socket would have blocked,
//           -3 EPIPE error)
//
EXTERNAL int
sysWriteBytes(int fd, char *buf, int cnt)
{
    TRACE_PRINTF(SysTraceFile, "%s: write %d %p %d\n", Me, fd, buf, cnt);
again:
    int rc = write(fd, buf, cnt);
    if (rc >= 0)
        return rc;
    int err = errno;
    if (err == EAGAIN)
    {
        TRACE_PRINTF(SysTraceFile, "%s: write on %d would have blocked: needs retry\n", Me, fd);
        return -1;
    }
    if (err == EINTR)
        goto again; // interrupted by signal; try again
    if (err == EPIPE)
    {
        TRACE_PRINTF(SysTraceFile, "%s: write on %d with nobody to read it\n", Me, fd);
        return -3;
    }
    fprintf(SysTraceFile, "%s: write error %d (%s) on %d\n", Me,
            err, strerror( err ), fd);
    return -2;
}

// Close file or socket.
// Taken:    file/socket descriptor
// Returned:  0: success
//           -1: file/socket not currently open
//           -2: i/o error
//
static int sysClose(int fd)
{
    TRACE_PRINTF(SysTraceFile, "%s: close %d\n", Me, fd);
    if ( -1 == fd ) return -1;
    int rc = close(fd);
    if (rc == 0) return 0; // success
    if (errno == EBADF) return -1; // not currently open
    return -2; // some other error
}

// Set the close-on-exec flag for given file descriptor.
//
// Taken: the file descriptor
// Returned: 0 if sucessful, nonzero otherwise
//
EXTERNAL int
sysSetFdCloseOnExec(int fd)
{
    TRACE_PRINTF(SysTraceFile, "%s: setFdCloseOnExec %d\n", Me, fd);
    return fcntl(fd, F_SETFD, FD_CLOEXEC);
}

/////////////////// time operations /////////////////

EXTERNAL long long
sysCurrentTimeMillis()
{
    TRACE_PRINTF(SysTraceFile, "%s: sysCurrentTimeMillis\n", Me);
    int rc;
    long long returnValue;
    struct timeval tv;
    struct timezone tz;

    returnValue = 0;

    rc = gettimeofday(&tv, &tz);
    if (rc != 0) {
        returnValue = rc;
    } else {
        returnValue = ((long long) tv.tv_sec * 1000) + tv.tv_usec/1000;
    }

    return returnValue;
}


#ifdef __MACH__
mach_timebase_info_data_t timebaseInfo;
#endif

EXTERNAL long long
sysNanoTime()
{
    TRACE_PRINTF(SysTraceFile, "%s: sysNanoTime\n", Me);
    long long retVal;
#ifndef __MACH__
    struct timespec tp;
    int rc = clock_gettime(CLOCK_REALTIME, &tp);
    if (rc != 0) {
        retVal = rc;
        if (lib_verbose) {
              fprintf(stderr, "sysNanoTime: Non-zero return code %d from clock_gettime\n", rc);
        }
    } else {
        retVal = (((long long) tp.tv_sec) * 1000000000) + tp.tv_nsec;
    }
#else
    struct timeval tv;

    gettimeofday(&tv,NULL);

    retVal=tv.tv_sec;
    retVal*=1000;
    retVal*=1000;
    retVal+=tv.tv_usec;
    retVal*=1000;
#endif
    return retVal;
}


/** Routine to sleep for a number of nanoseconds (howLongNanos).  This is
 * ridiculous on regular Linux, where we actually only sleep in increments of
 * 1/HZ (1/100 of a second on x86).  Luckily, Linux will round up.
 *
 * This is just used internally in the scheduler, but we might as well make
 * the function work properly even if it gets used for other purposes.
 *
 * We don't return anything, since we don't need to right now.  Just try to
 * sleep; if interrupted, return.
 */
EXTERNAL void
sysNanoSleep(long long howLongNanos)
{
    struct timespec req;
    const long long nanosPerSec = 1000LL * 1000 * 1000;
    TRACE_PRINTF(SysTraceFile, "%s: sysNanosleep %lld\n", Me, howLongNanos);
    req.tv_sec = howLongNanos / nanosPerSec;
    req.tv_nsec = howLongNanos % nanosPerSec;
    int ret = nanosleep(&req, (struct timespec *) NULL);
    if (ret < 0) {
        if (errno == EINTR)
            /* EINTR is expected, since we do use signals internally. */
            return;

        fprintf(SysErrorFile, "%s: nanosleep(<tv_sec=%ld,tv_nsec=%ld>) failed:"
                " %s (errno=%d)\n"
                "  That should never happen; please report it as a bug.\n",
                Me, req.tv_sec, req.tv_nsec,
                strerror( errno ), errno);
    }
    // Done.
}


//-----------------------//
// Processor operations. //
//-----------------------//

#ifdef _AIX
#include <sys/systemcfg.h>
#endif

// How many physical cpu's are present and actually online?
// Assume 1 if no other good ansewr.
// Taken:    nothing
// Returned: number of cpu's
//
// Note: this function is only called once.  If it were called more often
// than that, we would want to use a static variable to indicate that we'd
// already printed the WARNING messages and were not about to print any more.

EXTERNAL int
sysNumProcessors()
{
    static int firstRun = 1;
    int numCpus = -1;  /* -1 means failure. */
    TRACE_PRINTF(SysTraceFile, "%s: sysNumProcessors\n", Me);  

#ifdef __GNU_LIBRARY__      // get_nprocs is part of the GNU C library.
    /* get_nprocs_conf will give us a how many processors the operating
       system configured.  The number of processors actually online is what
       we want.  */
    // numCpus = get_nprocs_conf();
    errno = 0;
    numCpus = get_nprocs();
    // It is not clear if get_nprocs can ever return failure; assume it might.
    if (numCpus < 1) {
       if (firstRun) fprintf(SysTraceFile, "%s: WARNING: get_nprocs() returned %d (errno=%d)\n", Me, numCpus, errno);
       /* Continue on.  Try to get a better answer by some other method, not
          that it's likely, but this should not be a fatal error. */
    }
#endif

#if defined(CTL_HW) && defined(HW_NCPU)
    if (numCpus < 1) {
        int mib[2];
        size_t len;
        mib[0] = CTL_HW;
        mib[1] = HW_NCPU;
        len = sizeof(numCpus);
        errno = 0;
        if (sysctl(mib, 2, &numCpus, &len, NULL, 0) < 0) {
            if (firstRun) fprintf(SysTraceFile, "%s: WARNING: sysctl(CTL_HW,HW_NCPU) failed;"
                    " errno = %d\n", Me, errno);
            numCpus = -1;       // failed so far...
        };
    }
#endif

#if defined(_SC_NPROCESSORS_ONLN)
    if (numCpus < 0) {
        /* This alternative is probably the same as
         *  _system_configuration.ncpus.  This one says how many CPUs are
         *  actually on line.  It seems to be supported on AIX, at least; I
         *  yanked this out of sysVirtualProcessorBind.
         */
        numCpus = sysconf(_SC_NPROCESSORS_ONLN); // does not set errno
        if (numCpus < 0) {
            if (firstRun) fprintf(SysTraceFile, "%s: WARNING: sysconf(_SC_NPROCESSORS_ONLN)"
                    " failed\n", Me);
        }
    }
#endif

#ifdef _AIX
    if (numCpus < 0) {
        numCpus = _system_configuration.ncpus;
        if (numCpus < 0) {
            if (firstRun) fprintf(SysTraceFile, "%s: WARNING: _system_configuration.ncpus"
                    " has the insane value %d\n" , Me, numCpus);
        }
    }
#endif

    if (numCpus < 0) {
        if (firstRun) TRACE_PRINTF(SysTraceFile, "%s: WARNING: Can not figure out how many CPUs"
                              " are online; assuming 1\n", Me);
        numCpus = 1;            // Default
    }

#ifdef DEBUG_SYS
    fprintf(SysTraceFile, "%s: sysNumProcessors: returning %d\n", Me, numCpus );
#endif
    firstRun = 0;
    return numCpus;
}

// Create a native thread
// Taken:    register values to use for pthread startup
// Returned: virtual processor's OS handle
EXTERNAL Word
sysThreadCreate(Address tr, Address ip, Address fp)
{
    Address    *sysThreadArguments;
    int            rc;
    
    TRACE_PRINTF(SysTraceFile, "%s: sysThreadCreate %p %p %p\n", Me, tr, ip, fp);

    // create arguments
    //
    sysThreadArguments = (Address*) checkMalloc(sizeof(Address) * 3);
    sysThreadArguments[0] = tr;
    sysThreadArguments[1] = ip;
    sysThreadArguments[2] = fp;

#ifdef RVM_FOR_HARMONY
    hythread_t      sysThreadHandle;

    if ((rc = hythread_create(&sysThreadHandle, 0, HYTHREAD_PRIORITY_NORMAL, 0, sysThreadStartup, sysThreadArguments)))
    {
        fprintf(SysErrorFile, "%s: hythread_create failed (rc=%d)\n", Me, rc);
        sysExit(EXIT_STATUS_SYSCALL_TROUBLE);
    }
#else
    pthread_attr_t sysThreadAttributes;
    pthread_t      sysThreadHandle;

    // create attributes
    //
    if ((rc = pthread_attr_init(&sysThreadAttributes))) {
        fprintf(SysErrorFile, "%s: pthread_attr_init failed (rc=%d)\n", Me, rc);
        sysExit(EXIT_STATUS_SYSCALL_TROUBLE);
    }

    // force 1:1 pthread to kernel thread mapping (on AIX 4.3)
    //
    pthread_attr_setscope(&sysThreadAttributes, PTHREAD_SCOPE_SYSTEM);

    // create native thread
    //
    if ((rc = pthread_create(&sysThreadHandle,
                             &sysThreadAttributes,
                             sysThreadStartup,
                             sysThreadArguments)))
    {
        fprintf(SysErrorFile, "%s: pthread_create failed (rc=%d)\n", Me, rc);
        sysExit(EXIT_STATUS_SYSCALL_TROUBLE);
    }

    if ((rc = pthread_detach(sysThreadHandle)))
    {
        fprintf(SysErrorFile, "%s: pthread_detach failed (rc=%d)\n", Me, rc);
        sysExit(EXIT_STATUS_SYSCALL_TROUBLE);
    }
    TRACE_PRINTF(SysTraceFile, "%s: pthread_create 0x%08x\n", Me, (Address) sysThreadHandle);
#endif

    return (Word)sysThreadHandle;
}

EXTERNAL int
sysThreadBindSupported()
{
  int result=0;
  TRACE_PRINTF(SysTraceFile, "%s: sysThreadBindSupported\n", Me);
#ifdef RVM_FOR_AIX
  result=1;
#endif
#ifdef RVM_FOR_LINUX
  result=1;
#endif
  return result;
}

EXTERNAL void
sysThreadBind(int cpuId)
{
    TRACE_PRINTF(SysTraceFile, "%s: sysThreadBind\n", Me);
    // bindprocessor() seems to be only on AIX
#ifdef RVM_FOR_AIX
    int rc = bindprocessor(BINDTHREAD, thread_self(), cpuId);
    fprintf(SysTraceFile, "%s: bindprocessor pthread %d (kernel thread %d) %s to cpu %d\n", Me, pthread_self(), thread_self(), (rc ? "NOT bound" : "bound"), cpuId);

    if (rc) {
        fprintf(SysErrorFile, "%s: bindprocessor failed (errno=%d): ", Me, errno);
        perror(NULL);
        sysExit(EXIT_STATUS_SYSCALL_TROUBLE);
    }
#endif

#ifndef RVM_FOR_HARMONY
#ifdef RVM_FOR_LINUX
    cpu_set_t cpuset;
    CPU_ZERO(&cpuset);
    CPU_SET(cpuId, &cpuset);

    pthread_setaffinity_np(pthread_self(), sizeof(cpuset), &cpuset);
#endif
#endif
}

#ifdef RVM_FOR_HARMONY
EXTERNAL int
sysThreadStartup(void *args)
#else
EXTERNAL void *
sysThreadStartup(void *args)
#endif
{
    /* install a stack for hardwareTrapHandler() to run on */
    stack_t stack;
    char *stackBuf;

    memset (&stack, 0, sizeof stack);
    stack.ss_sp = stackBuf = (char*) checkMalloc(sizeof(char) * SIGSTKSZ);
    stack.ss_flags = 0;
    stack.ss_size = SIGSTKSZ;
    if (sigaltstack (&stack, 0)) {
        fprintf(stderr,"sigaltstack failed (errno=%d)\n",errno);
        exit(1);
    }

    Address tr       = ((Address *)args)[0];

    jmp_buf *jb = (jmp_buf*)checkMalloc(sizeof(jmp_buf));
    if (setjmp(*jb)) {
        // this is where we come to terminate the thread
#ifdef RVM_FOR_HARMONY
        hythread_detach(NULL);
#endif
        checkFree(jb);
        *(int*)(tr + RVMThread_execStatus_offset) = RVMThread_TERMINATED;

        // disable the signal stack (first retreiving the current one)
        sigaltstack(0, &stack);
        stack.ss_flags = SS_DISABLE;
        sigaltstack(&stack, 0);

        // check if the signal stack is the one in stackBuf
        if (stack.ss_sp != stackBuf) {
            // no; release it as well
            checkFree(stack.ss_sp);
        }

        // release signal stack allocated here
        checkFree(stackBuf);
        // release arguments
        checkFree(args);
    } else {
        setThreadLocal(TerminateJmpBufKey, (void*)jb);

        Address ip       = ((Address *)args)[1];
        Address fp       = ((Address *)args)[2];

        TRACE_PRINTF(SysTraceFile, "%s: sysThreadStartup: pr=%p ip=%p fp=%p\n", Me, tr, ip, fp);

        // branch to vm code
        //
#ifndef RVM_FOR_POWERPC
        {
            *(Address *) (tr + Thread_framePointer_offset) = fp;
            Address sp = fp + Constants_STACKFRAME_BODY_OFFSET;
            bootThread((void*)ip, (void*)tr, (void*)sp);
        }
#else
        bootThread((int)(Word)getJTOC(), tr, ip, fp);
#endif

        // not reached
        //
        fprintf(SysTraceFile, "%s: sysThreadStartup: failed\n", Me);
    }
#ifdef RVM_FOR_HARMONY
    return 0;
#else
    return NULL;
#endif
}

// Routines to support sleep/wakeup of idle threads:
// CRA, Maria
// 09/14/00
//

/*
  sysGetThreadId() just returns the thread ID of
  the current thread.

  This happens to be only called once, at thread startup time, but please
  don't rely on that fact.
*/
EXTERNAL Word
sysGetThreadId()
{
    TRACE_PRINTF(SysTraceFile, "%s: sysGetThreadId\n", Me);
    return (Word)getThreadId();
}

EXTERNAL void*
getThreadId()
{
    
#ifdef RVM_FOR_HARMONY
    void* thread = (void*)hythread_self();
#else
    void* thread = (void*)pthread_self();
#endif

    TRACE_PRINTF(SysTraceFile, "%s: getThreadId: thread %x\n", Me, thread);
    return thread;
}

/* Perform some initialization related to
  per-thread signal handling for that thread. (Block SIGCONT, set up a special
  signal handling stack for the thread.)

  This is only called once, at thread startup time. */
EXTERNAL void
sysSetupHardwareTrapHandler()
{
    int rc;                     // retval from subfunction.

#ifndef RVM_FOR_AIX
    /*
     *  Provide space for this pthread to process exceptions.  This is
     * needed on Linux because multiple pthreads can handle signals
     * concurrently, since the masking of signals during handling applies
     * on a per-pthread basis.
     */
    stack_t stack;

    memset (&stack, 0, sizeof stack);
    stack.ss_sp = (char*) checkMalloc(sizeof(char) * SIGSTKSZ);

    stack.ss_size = SIGSTKSZ;
    if (sigaltstack (&stack, 0)) {
        /* Only fails with EINVAL, ENOMEM, EPERM */
        fprintf (SysErrorFile, "sigaltstack failed (errno=%d): ", errno);
        perror(NULL);
        sysExit(EXIT_STATUS_IMPOSSIBLE_LIBRARY_FUNCTION_ERROR);
    }
#endif

    /*
     * Block the CONT signal.  This makes SIGCONT reach this
     * pthread only when this pthread performs a sigwait().
     * --Maria
     */
    sigset_t input_set, output_set;
    sigemptyset(&input_set);
    sigaddset(&input_set, SIGCONT);

#ifdef RVM_FOR_AIX
    rc = sigthreadmask(SIG_BLOCK, &input_set, &output_set);
    /* like pthread_sigmask, sigthreadmask can only return EINVAL, EFAULT, and
     * EPERM.  Again, these are all good reasons to complain and croak. */
#else
    rc = pthread_sigmask(SIG_BLOCK, &input_set, &output_set);
    /* pthread_sigmask can only return the following errors.  Either of them
     * indicates serious trouble and is grounds for aborting the process:
     * EINVAL EFAULT.  */
#endif
    if (rc) {
        fprintf (SysErrorFile, "pthread_sigmask or sigthreadmask failed (errno=%d): ", errno);
        perror(NULL);
        sysExit(EXIT_STATUS_IMPOSSIBLE_LIBRARY_FUNCTION_ERROR);
    }

}

//
// Yield execution of current virtual processor back to o/s.
// Taken:    nothing
// Returned: nothing
//
EXTERNAL void
sysThreadYield()
{
    TRACE_PRINTF(SysTraceFile, "%s: sysThreadYield\n", Me);

    /** According to the Linux manpage, sched_yield()'s presence can be
     *  tested for by using the #define _POSIX_PRIORITY_SCHEDULING, and if
     *  that is not present to use the sysconf feature, searching against
     *  _SC_PRIORITY_SCHEDULING.  However, I don't really trust it, since
     *  the AIX 5.1 include files include this definition:
     *      ./unistd.h:#undef _POSIX_PRIORITY_SCHEDULING
     *  so my trust that it is implemented properly is scanty.  --augart
     */
#ifdef RVM_FOR_HARMONY
    hythread_yield();
#else
    sched_yield();
#endif
}

// Determine if a given thread can use pthread_setschedparam to
// configure its priority, this is based on the current priority
// of the thread.
//
// The result will be true on all systems other than Linux where
// pthread_setschedparam cannot be used with SCHED_OTHER policy.
//
static int hasPthreadPriority(Word thread_id)
{
    struct sched_param param;
    int policy;
    if (!pthread_getschedparam((pthread_t)thread_id, &policy, &param)) {
        int min = sched_get_priority_min(policy);
        int max = sched_get_priority_max(policy);
        if (min || max) {
            return 1;
        }
    }
    return 0;
}

// Return a handle which can be used to manipulate a threads priority
// on Linux this will be the kernel thread_id, on other systems the
// standard thread id.
EXTERNAL Word
sysGetThreadPriorityHandle()
{
    TRACE_PRINTF(SysTraceFile, "%s: sysGetThreadPriorityHandle\n", Me);
    // gettid() syscall is Linux specific, detect its syscall number macro
    #ifdef SYS_gettid
    pid_t tid = (pid_t) syscall(SYS_gettid);
    if (tid != -1)
        return (Word) tid;
    #endif /* SYS_gettid */
    return (Word) getThreadId();
}

// Compute the default (or middle) priority for a given policy.
static int defaultPriority(int policy)
{
    int min = sched_get_priority_min(policy);
    int max = sched_get_priority_max(policy);
    return min + ((max - min) / 2);
}

// Get the thread priority as an offset from the default.
EXTERNAL int
sysGetThreadPriority(Word thread, Word handle)
{
    TRACE_PRINTF(SysTraceFile, "%s: sysGetThreadPriority\n", Me);
    // use pthread priority mechanisms where possible
    if (hasPthreadPriority(thread)) {
        struct sched_param param;
        int policy;
        if (!pthread_getschedparam((pthread_t)thread, &policy, &param)) {
            return param.sched_priority - defaultPriority(policy);
        }
    } else if (thread != handle) {
        // fallback to setpriority if handle is valid
        // i.e. handle is tid from gettid()
        int result;
        errno = 0; // as result can be legally be -1
        result = getpriority(PRIO_PROCESS, (int) handle);
        if (errno == 0) {
            // default priority is 0, low number -> high priority
            return -result;
        }

    }
    return 0;
}

// Set the thread priority as an offset from the default.
EXTERNAL int
sysSetThreadPriority(Word thread, Word handle, int priority)
{
    TRACE_PRINTF(SysTraceFile, "%s: sysSetThreadPriority\n", Me);
    // fast path
    if (sysGetThreadPriority(thread, handle) == priority)
        return 0;

    // use pthread priority mechanisms where possible
    if (hasPthreadPriority(thread)) {
        struct sched_param param;
        int policy;
        int result = pthread_getschedparam((pthread_t)thread, &policy, &param);
        if (!result) {
            param.sched_priority = defaultPriority(policy) + priority;
            return pthread_setschedparam((pthread_t)thread, policy, &param);
        } else {
            return result;
        }
    } else if (thread != handle) {
        // fallback to setpriority if handle is valid
        // i.e. handle is tid from gettid()
        // default priority is 0, low number -> high priority
        return setpriority(PRIO_PROCESS, (int) handle, -priority);
    }
    return -1;
}

////////////// Pthread mutex and condition functions /////////////

#ifndef RVM_FOR_HARMONY
typedef struct {
    pthread_mutex_t mutex;
    pthread_cond_t cond;
} vmmonitor_t;
#endif

EXTERNAL Word
sysMonitorCreate()
{
    TRACE_PRINTF(SysTraceFile, "%s: sysMonitorCreate\n", Me);
#ifdef RVM_FOR_HARMONY
    hythread_monitor_t monitor;
    hythread_monitor_init_with_name(&monitor, 0, NULL);
#else
    vmmonitor_t *monitor = (vmmonitor_t*) checkMalloc(sizeof(vmmonitor_t));
    pthread_mutex_init(&monitor->mutex, NULL);
    pthread_cond_init(&monitor->cond, NULL);
#endif
    return (Word)monitor;
}

EXTERNAL void
sysMonitorDestroy(Word _monitor)
{
    TRACE_PRINTF(SysTraceFile, "%s: sysMonitorDestroy\n", Me);
#ifdef RVM_FOR_HARMONY
    hythread_monitor_destroy((hythread_monitor_t)_monitor);
#else
    vmmonitor_t *monitor = (vmmonitor_t*)_monitor;
    pthread_mutex_destroy(&monitor->mutex);
    pthread_cond_destroy(&monitor->cond);
    checkFree(monitor);
#endif
}

EXTERNAL void
sysMonitorEnter(Word _monitor)
{
    TRACE_PRINTF(SysTraceFile, "%s: sysMonitorEnter\n", Me);
#ifdef RVM_FOR_HARMONY
    hythread_monitor_enter((hythread_monitor_t)_monitor);
#else
    vmmonitor_t *monitor = (vmmonitor_t*)_monitor;
    pthread_mutex_lock(&monitor->mutex);
#endif
}

EXTERNAL void
sysMonitorExit(Word _monitor)
{
    TRACE_PRINTF(SysTraceFile, "%s: sysMonitorExit\n", Me);
#ifdef RVM_FOR_HARMONY
    hythread_monitor_exit((hythread_monitor_t)_monitor);
#else
    vmmonitor_t *monitor = (vmmonitor_t*)_monitor;
    pthread_mutex_unlock(&monitor->mutex);
#endif
}

EXTERNAL void
sysMonitorTimedWaitAbsolute(Word _monitor, long long whenWakeupNanos)
{
    TRACE_PRINTF(SysTraceFile, "%s: sysMonitorTimedWaitAbsolute\n", Me);
#ifdef RVM_FOR_HARMONY
    // syscall wait is absolute, but harmony monitor wait is relative.
    whenWakeupNanos -= sysNanoTime();
    if (whenWakeupNanos <= 0) return;
    hythread_monitor_wait_timed((hythread_monitor_t)_monitor, (I_64)(whenWakeupNanos / 1000000LL), (IDATA)(whenWakeupNanos % 1000000LL));
#else
    timespec ts;
    ts.tv_sec = (time_t)(whenWakeupNanos/1000000000LL);
    ts.tv_nsec = (long)(whenWakeupNanos%1000000000LL);
#ifdef DEBUG_THREAD
      fprintf(stderr, "starting wait at %lld until %lld (%ld, %ld)\n",
             sysNanoTime(),whenWakeupNanos,ts.tv_sec,ts.tv_nsec);
      fflush(stderr);
#endif
    vmmonitor_t *monitor = (vmmonitor_t*)_monitor;
    int rc = pthread_cond_timedwait(&monitor->cond, &monitor->mutex, &ts);
#ifdef DEBUG_THREAD
      fprintf(stderr, "returned from wait at %lld instead of %lld with res = %d\n",
             sysNanoTime(),whenWakeupNanos,rc);
      fflush(stderr);
#endif
#endif
}

EXTERNAL void
sysMonitorWait(Word _monitor)
{
    TRACE_PRINTF(SysTraceFile, "%s: sysMonitorWait\n", Me);
#ifdef RVM_FOR_HARMONY
    hythread_monitor_wait((hythread_monitor_t)_monitor);
#else
    vmmonitor_t *monitor = (vmmonitor_t*)_monitor;
    pthread_cond_wait(&monitor->cond, &monitor->mutex);
#endif
}

EXTERNAL void
sysMonitorBroadcast(Word _monitor)
{
    TRACE_PRINTF(SysTraceFile, "%s: sysMonitorBroadcast\n", Me);
#ifdef RVM_FOR_HARMONY
    hythread_monitor_notify_all((hythread_monitor_t)_monitor);
#else
    vmmonitor_t *monitor = (vmmonitor_t*)_monitor;
    pthread_cond_broadcast(&monitor->cond);
#endif
}

EXTERNAL void
sysThreadTerminate()
{
    TRACE_PRINTF(SysTraceFile, "%s: sysThreadTerminate\n", Me);
#ifdef RVM_FOR_POWERPC
    asm("sync");
#endif
    jmp_buf *jb = (jmp_buf*)GET_THREAD_LOCAL(TerminateJmpBufKey);
    if (jb==NULL) {
        jb=&primordial_jb;
    }
    rvm_longjmp(*jb,1);
}

//------------------------//
// Arithmetic operations. //
//------------------------//

EXTERNAL long long
sysLongDivide(long long a, long long b)
{
    TRACE_PRINTF(SysTraceFile, "%s: sysLongDivide %lld / %lld\n", Me, a, b);  
    return a/b;
}

EXTERNAL long long
sysLongRemainder(long long a, long long b)
{
    TRACE_PRINTF(SysTraceFile, "%s: sysLongRemainder %lld %% %lld\n", Me, a, b);
    return a % b;
}

EXTERNAL double
sysLongToDouble(long long a)
{
    TRACE_PRINTF(SysTraceFile, "%s: sysLongToDouble %lld\n", Me, a);
    return (double)a;
}

EXTERNAL float
sysLongToFloat(long long a)
{
    TRACE_PRINTF(SysTraceFile, "%s: sysLongToFloat %lld\n", Me, a);
    return (float)a;
}

double maxlong = 0.5 + (double)0x7fffffffffffffffLL;
double maxint  = 0.5 + (double)0x7fffffff;

EXTERNAL int
sysFloatToInt(float a)
{
    TRACE_PRINTF(SysTraceFile, "%s: sysFloatToInt %f\n", Me, a);
    if (maxint <= a) return 0x7fffffff;
    if (a <= -maxint) return 0x80000000;
    if (a != a) return 0; // NaN => 0
    return (int)a;
}

EXTERNAL int
sysDoubleToInt(double a)
{
    TRACE_PRINTF(SysTraceFile, "%s: sysDoubleToInt %f\n", Me, a);
    if (maxint <= a) return 0x7fffffff;
    if (a <= -maxint) return 0x80000000;
    if (a != a) return 0; // NaN => 0
    return (int)a;
}

EXTERNAL long long
sysFloatToLong(float a)
{
    TRACE_PRINTF(SysTraceFile, "%s: sysFloatToLong %f\n", Me, a);
    if (maxlong <= a) return 0x7fffffffffffffffLL;
    if (a <= -maxlong) return 0x8000000000000000LL;
    return (long long)a;
}

EXTERNAL long long
sysDoubleToLong(double a)
{
    TRACE_PRINTF(SysTraceFile, "%s: sysDoubleToLong %f\n", Me, a);
    if (maxlong <= a) return 0x7fffffffffffffffLL;
    if (a <= -maxlong) return 0x8000000000000000LL;
    return (long long)a;
}

// sysDoubleRemainder is only used on PPC
#include <math.h>
EXTERNAL double
sysDoubleRemainder(double a, double b)
{
    TRACE_PRINTF(SysTraceFile, "%s: sysDoubleRemainder %f %% %f\n", Me, a);
    double tmp = remainder(a, b);
    if (a > 0.0) {
        if (b > 0.0) {
            if (tmp < 0.0) {
                tmp += b;
            }
        } else if (b < 0.0) {
            if (tmp < 0.0) {
                tmp -= b;
            }
        }
    } else {
        if (b > 0.0) {
            if (tmp > 0.0) {
                tmp -= b;
            }
        } else {
            if (tmp > 0.0) {
                tmp += b;
            }
        }
    }
    return tmp;
}

/* Used to parse command line arguments that are
   doubles and floats early in booting before it
   is safe to call Float.valueOf or Double.valueOf.   This is only used in
   parsing command-line arguments, so we can safely print error messages that
   assume the user specified this number as part of a command-line argument. */
EXTERNAL float
sysPrimitiveParseFloat(const char * buf)
{
    TRACE_PRINTF(SysTraceFile, "%s: sysPrimitiveParseFloat %s\n", Me, buf);
    if (! buf[0] ) {
   fprintf(SysErrorFile, "%s: Got an empty string as a command-line"
      " argument that is supposed to be a"
      " floating-point number\n", Me);
        exit(EXIT_STATUS_BOGUS_COMMAND_LINE_ARG);
    }
    char *end;         // This prototype is kinda broken.  It really
            // should be char *.  But isn't.
    errno = 0;
    float f = (float)strtod(buf, &end);
    if (errno) {
   fprintf(SysErrorFile, "%s: Trouble while converting the"
      " command-line argument \"%s\" to a"
      " floating-point number: %s\n", Me, buf, strerror(errno));
   exit(EXIT_STATUS_BOGUS_COMMAND_LINE_ARG);
    }
    if (*end != '\0') {
        fprintf(SysErrorFile, "%s: Got a command-line argument that"
      " is supposed to be a floating-point value,"
      " but isn't: %s\n", Me, buf);
        exit(EXIT_STATUS_BOGUS_COMMAND_LINE_ARG);
    }
    return f;
}

// Used to parse command line arguments that are
// ints and bytes early in booting before it
// is safe to call Integer.parseInt and Byte.parseByte
// This is only used in
// parsing command-line arguments, so we can safely print error messages that
// assume the user specified this number as part of a command-line argument.
EXTERNAL int
sysPrimitiveParseInt(const char * buf)
{
    TRACE_PRINTF(SysTraceFile, "%s: sysPrimitiveParseInt %s\n", Me, buf);
    if (! buf[0] ) {
   fprintf(SysErrorFile, "%s: Got an empty string as a command-line"
      " argument that is supposed to be an integer\n", Me);
        exit(EXIT_STATUS_BOGUS_COMMAND_LINE_ARG);
    }
    char *end;
    errno = 0;
    long l = strtol(buf, &end, 0);
    if (errno) {
   fprintf(SysErrorFile, "%s: Trouble while converting the"
      " command-line argument \"%s\" to an integer: %s\n",
      Me, buf, strerror(errno));
   exit(EXIT_STATUS_BOGUS_COMMAND_LINE_ARG);
    }
    if (*end != '\0') {
        fprintf(SysErrorFile, "%s: Got a command-line argument that is supposed to be an integer, but isn't: %s\n", Me, buf);
        exit(EXIT_STATUS_BOGUS_COMMAND_LINE_ARG);
    }
    int32_t ret = l;
    if ((long) ret != l) {
        fprintf(SysErrorFile, "%s: Got a command-line argument that is supposed to be an integer, but its value does not fit into a Java 32-bit integer: %s\n", Me, buf);
        exit(EXIT_STATUS_BOGUS_COMMAND_LINE_ARG);
    }
    return ret;
}

/** Parse memory sizes.
    @return negative values to indicate errors. */
EXTERNAL jlong
sysParseMemorySize(const char *sizeName, /*  "initial heap" or "maximum heap"
                                            or "initial stack" or
                                            "maximum stack" */
                   const char *sizeFlag, // e.g., "ms" or "mx" or "ss" or "sg" or "sx"
                   const char *defaultFactor, // "M" or "K" are used
                   int roundTo,  // Round to PAGE_SIZE_BYTES or to 4.
                   const char *token /* e.g., "-Xms200M" or "-Xms200" */,
                   const char *subtoken /* e.g., "200M" or "200" */)
{
    TRACE_PRINTF(SysErrorFile, "%s: sysParseMemorySize %s\n", Me, token);
    bool fastExit = false;
    unsigned ret_uns=  parse_memory_size(sizeName, sizeFlag, defaultFactor,
                                         (unsigned) roundTo, token, subtoken,
                                         &fastExit);
    if (fastExit)
        return -1;
    else
        return (jlong) ret_uns;
}



//-------------------//
// Memory operations //
//-------------------//

// Memory to memory copy. Memory regions must not overlap.
//
EXTERNAL void
sysCopy(void *dst, const void *src, Extent cnt)
{
    TRACE_PRINTF(SysTraceFile, "%s: sysCopy %p %p %d\n", Me, dst, src, cnt);
    memcpy(dst, src, cnt);
}

// Memory to memory copy. Memory regions may overlap.
//
EXTERNAL void
sysMemmove(void *dst, const void *src, Extent cnt)
{
    TRACE_PRINTF(SysTraceFile, "%s: sysMemmove %p %p %d\n", Me, dst, src, cnt);
    memmove(dst, src, cnt);
}



int inRVMAddressSpace(Address a);

static void*
checkMalloc(int length)
{
    void *result=malloc(length);
    if (inRVMAddressSpace((Address)result)) {
      fprintf(stderr,"malloc returned something that is in RVM address space: %p\n",result);
    }
    return result;
}

static void*
checkCalloc(int numElements, int sizeOfOneElement)
{
    void *result=calloc(numElements,sizeOfOneElement);
    if (inRVMAddressSpace((Address)result)) {
      fprintf(stderr,"calloc returned something that is in RVM address space: %p\n",result);
    }
    return result;
}

static void
checkFree(void* mem)
{
    free(mem);
}

// Allocate memory.
//
EXTERNAL void *
sysMalloc(int length)
{
    TRACE_PRINTF(SysTraceFile, "%s: sysMalloc %d\n", Me, length);
    return checkMalloc(length);
}

EXTERNAL void *
sysCalloc(int length)
{
  TRACE_PRINTF(SysTraceFile, "%s: sysCalloc %d\n", Me, length);
  return checkCalloc(1, length);
}

// Release memory.
//
EXTERNAL void
sysFree(void *location)
{
    TRACE_PRINTF(SysTraceFile, "%s: sysFree %p\n", Me, location);
    checkFree(location);
}

// Zero a range of memory with non-temporal instructions on x86
EXTERNAL void
sysZeroNT(void *dst, Extent cnt)
{
  TRACE_PRINTF(SysTraceFile, "%s: sysZeroNT %p %d\n", Me, dst, cnt);
#ifdef RVM_FOR_SSE2
  char *buf = (char *) dst;
  unsigned int len = cnt;

  __asm__ volatile (
        ".align 4 \n\t"
        "cmp $0x10, %%esi \n\t"
        "jl 0f \n\t"
        "pxor %%xmm0, %%xmm0 \n\t"
        "16: \n\t"
        "test $0xf, %%edi \n\t"
        "je 64f \n\t"
        "movb $0,(%%edi) \n\t"
        "inc %%edi \n\t"
        "dec %%esi \n\t"
        "jmp 16b \n\t"
        "64: \n\t"
        "cmp $128, %%esi \n\t"
        "jl 0f \n\t"
        "movntdq %%xmm0, 0x0(%%edi) \n\t"
        "movntdq %%xmm0, 0x10(%%edi) \n\t"
        "movntdq %%xmm0, 0x20(%%edi) \n\t"
        "movntdq %%xmm0, 0x30(%%edi) \n\t"
        "movntdq %%xmm0, 0x40(%%edi) \n\t"
        "movntdq %%xmm0, 0x50(%%edi) \n\t"
        "movntdq %%xmm0, 0x60(%%edi) \n\t"
        "movntdq %%xmm0, 0x70(%%edi) \n\t"

        "add $128, %%edi \n\t"
        "sub $128, %%esi \n\t"
        "jmp 64b \n\t"
        "0: \n\t"
        "sfence \n\t"
        : "+S"(len),"+D" ( buf ));

  while (__builtin_expect (len--, 0)){
    *buf++ = 0;
  }
#else
  memset(dst, 0x00, cnt);
#endif    
}

// Zero a range of memory bytes.
//
EXTERNAL void
sysZero(void *dst, Extent cnt)
{
    TRACE_PRINTF(SysTraceFile, "%s: sysZero %p %d\n", Me, dst, cnt);
    memset(dst, 0x00, cnt);
}

// Zero a range of memory pages.
// Taken:    start of range (must be a page boundary)
//           size of range, in bytes (must be multiple of page size, 4096)
// Returned: nothing
//
EXTERNAL void
sysZeroPages(void *dst, int cnt)
{
    // uncomment one of the following
    //
#define STRATEGY 1 /* for optimum pBOB numbers */
// #define STRATEGY 2 /* for more realistic workload */
// #define STRATEGY 3 /* as yet untested */

    TRACE_PRINTF(SysTraceFile, "%s: sysZeroPages %p %d\n", Me, dst, cnt);

#if (STRATEGY == 1)
    // Zero memory by touching all the bytes.
    // Advantage:    fewer page faults during mutation
    // Disadvantage: more page faults during collection, at least until
    //               steady state working set is achieved
    //
    sysZero(dst, cnt);
#endif

#if (STRATEGY == 2)
    // Zero memory by using munmap() followed by mmap().
    // This assumes that bootImageRunner.C has used mmap()
    // to acquire memory for the VM bootimage and heap.
    // Advantage:    fewer page faults during collection
    // Disadvantage: more page faults during mutation
    //
    int rc = munmap(dst, cnt);
    if (rc != 0)
    {
        fprintf(SysErrorFile, "%s: munmap failed (errno=%d): ", Me, errno);
        perror(NULL);
        sysExit(EXIT_STATUS_SYSCALL_TROUBLE);
    }

    void *addr = mmap(dst, cnt, PROT_READ | PROT_WRITE | PROT_EXEC, MAP_ANONYMOUS | MAP_FIXED, -1, 0);
    if (addr == (void *)-1)
    {
        fprintf(SysErrorFile, "%s: mmap failed (errno=%d): ", Me, errno);
        perror(NULL);
        sysExit(EXIT_STATUS_SYSCALL_TROUBLE);
    }
#endif

#if (STRATEGY == 3)
    // Zero memory by using disclaim().
    // This assumes that bootImageRunner.C has used malloc()
    // to acquire memory for the VM bootimage and heap and requires use of
    // the binder option -bmaxdata:0x80000000 which allows large malloc heaps
    // Advantage:    ? haven't tried this strategy yet
    // Disadvantage: ? haven't tried this strategy yet
    //
    int rc = disclaim((char *)dst, cnt, ZERO_MEM);
    if (rc != 0)
    {
        fprintf(SysErrorFile, "%s: disclaim failed (errno=%d): ", Me, errno);
        perror(NULL);
        sysExit(EXIT_STATUS_SYSCALL_TROUBLE);
    }
#endif

#undef STRATEGY
}

//PNT: use a soft handshake whenever we do this.
// Synchronize caches: force data in dcache to be written out to main memory
// so that it will be seen by icache when instructions are fetched back.
//
// Taken:    start of address range
//           size of address range (bytes)
// Returned: nothing
//
//
EXTERNAL void
sysSyncCache(void *address, size_t size)
{
    TRACE_PRINTF(SysTraceFile, "%s: sync %p %zd\n", Me, address, size);

#ifdef RVM_FOR_POWERPC
  #ifdef RVM_FOR_AIX
    _sync_cache_range((caddr_t) address, size);
  #else
    if (size < 0) {
      fprintf(SysErrorFile, "%s: tried to sync a region of negative size!\n", Me);
      sysExit(EXIT_STATUS_SYSCALL_TROUBLE);
    }

    /* See section 3.2.1 of PowerPC Virtual Environment Architecture */
    uintptr_t start = (uintptr_t)address;
    uintptr_t end = start + size;
    uintptr_t addr;

    /* update storage */
    /* Note: if one knew the cache line size, one could write a better loop */
    for (addr=start; addr < end; ++addr)
      asm("dcbst 0,%0" : : "r" (addr) );

    /* wait for update to commit */
    asm("sync");

    /* invalidate icache */
    /* Note: if one knew the cache line size, one could write a better loop */
    for (addr=start; addr<end; ++addr)
      asm("icbi 0,%0" : : "r" (addr) );

    /* context synchronization */
    asm("isync");
  #endif
#endif
}

//-----------------//
// MMAP operations //
//-----------------//

// mmap - general case
// Taken: start address (Java ADDRESS)
//        length of region (Java EXTENT)
//        desired protection (Java int)
//        flags (Java int)
//        file descriptor (Java int)
//        offset (Java long)  [to cover 64 bit file systems]
// Returned: address of region (or -1 on failure) (Java ADDRESS)

EXTERNAL void *
sysMMap(char *start , size_t length ,
        int protection , int flags ,
        int fd , Offset offset)
{
   TRACE_PRINTF(SysTraceFile, "%s: sysMMap %p %zd %d %d %d %d\n",
                 Me, start, length, protection, flags, fd, offset);
   void *result=mmap(start, (size_t)(length), protection, flags, fd, (off_t)offset);
   return result;
}

// Same as mmap, but with more debugging support.
// Returned: address of region if successful; errno (1 to 127) otherwise

EXTERNAL void *
sysMMapErrno(char *start , size_t length ,
        int protection , int flags ,
        int fd , Offset offset)
{
  TRACE_PRINTF(SysTraceFile, "%s: sysMMapErrno %p %d %d %d %d %d\n",
               Me, start, length, protection, flags, fd, offset);
  void* res = mmap(start, (size_t)(length), protection, flags, fd, (off_t)offset);
  if (res == (void *) -1){
#if RVM_FOR_32_ADDR
    fprintf(stderr, "mmap (%x, %u, %d, %d, %d, %d) failed with %d: ",
       (Address) start, (unsigned) length, protection, flags, fd, offset, errno);
#else
    fprintf(stderr, "mmap (%llx, %u, %d, %d, -1, 0) failed with %d: ",
       (Address) start, (unsigned) length, protection, flags, errno);
#endif
    return (void *) errno;
  }else{
    TRACE_PRINTF(SysTraceFile, "mmap succeeded- region = [%p ... %p]    size = %zd\n",
                res, (void*)(((size_t)res) + length), length);
    return res;
  }
}

// mprotect
// Taken: start address (Java ADDRESS)
//        length of region (Java EXTENT)
//        new protection (Java int)
// Returned: 0 (success) or -1 (failure) (Java int)
EXTERNAL int
sysMProtect(char *start, size_t length, int prot)
{
    TRACE_PRINTF(SysTraceFile, "%s: sysMProtect %p %zd %d\n",
                 Me, start, length, prot);
    return mprotect(start, length, prot);
}

// getpagesize
// Taken: (no arguments)
// Returned: page size in bytes (Java int)
EXTERNAL int
sysGetPageSize()
{
    TRACE_PRINTF(SysTraceFile, "%s: sysGetPageSize\n", Me);
    return (int)(getpagesize());
}

//----------------//
// JNI operations //
//----------------//

// Load dynamic library.
// Taken:
// Returned: a handler for this library, null if none loaded
//
EXTERNAL void*
sysDlopen(char *libname)
{
    TRACE_PRINTF(SysTraceFile, "%s: sysDlopen %s\n", Me, libname);
    void * libHandler;
    do {
        libHandler = dlopen(libname, RTLD_LAZY|RTLD_GLOBAL);
    }
    while( (libHandler == 0 /*null*/) && (errno == EINTR) );
    if (libHandler == 0) {
        fprintf(SysErrorFile,
                "%s: error loading library %s: %s\n", Me,
                libname, dlerror());
//      return 0;
    }

    return libHandler;
}

// Look up symbol in dynamic library.
// Taken:
// Returned:
//
EXTERNAL void*
sysDlsym(Address libHandler, char *symbolName)
{
    TRACE_PRINTF(SysTraceFile, "%s: sysDlsym %s\n", Me, symbolName);
    return dlsym((void *) libHandler, symbolName);
}

EXTERNAL int
getArrayLength(void* ptr)
{
    return *(int*)(((char *)ptr) + ObjectModel_ARRAY_LENGTH_OFFSET);
}

// VMMath
EXTERNAL double
sysVMMathSin(double a) {
    TRACE_PRINTF(SysTraceFile, "%s: sysVMMathSin %f\n", Me, a);
    return sin(a);
}

EXTERNAL double
sysVMMathCos(double a) {
    TRACE_PRINTF(SysTraceFile, "%s: sysVMMathCos %f\n", Me, a);
    return cos(a);
}

EXTERNAL double
sysVMMathTan(double a) {
    TRACE_PRINTF(SysTraceFile, "%s: sysVMMathTan %f\n", Me, a);
    return tan(a);
}

EXTERNAL double
sysVMMathAsin(double a) {
    TRACE_PRINTF(SysTraceFile, "%s: sysVMMathAsin %f\n", Me, a);
    return asin(a);
}

EXTERNAL double
sysVMMathAcos(double a) {
    TRACE_PRINTF(SysTraceFile, "%s: sysVMMathAcos %f\n", Me, a);
    return acos(a);
}

EXTERNAL double
sysVMMathAtan(double a) {
    TRACE_PRINTF(SysTraceFile, "%s: sysVMMathAtan %f\n", Me, a);
    return atan(a);
}

EXTERNAL double
sysVMMathAtan2(double a, double b) {
    TRACE_PRINTF(SysTraceFile, "%s: sysVMMathAtan2 %f %f\n", Me, a, b);
    return atan2(a, b);
}

EXTERNAL double
sysVMMathCosh(double a) {
    TRACE_PRINTF(SysTraceFile, "%s: sysVMMathCosh %f\n", Me, a);
    return cosh(a);
}

EXTERNAL double
sysVMMathSinh(double a) {
    TRACE_PRINTF(SysTraceFile, "%s: sysVMMathSinh %f\n", Me, a);
    return sinh(a);
}

EXTERNAL double
sysVMMathTanh(double a) {
    TRACE_PRINTF(SysTraceFile, "%s: sysVMMathTanh %f\n", Me, a);
    return tanh(a);
}

EXTERNAL double
sysVMMathExp(double a) {
    TRACE_PRINTF(SysTraceFile, "%s: sysVMMathExp %f\n", Me, a);
    return exp(a);
}

EXTERNAL double
sysVMMathLog(double a) {
    TRACE_PRINTF(SysTraceFile, "%s: sysVMMathLog %f\n", Me, a);
    return log(a);
}

EXTERNAL double
sysVMMathSqrt(double a) {
    TRACE_PRINTF(SysTraceFile, "%s: sysVMMathSqrt %f\n", Me, a);
    return sqrt(a);
}

EXTERNAL double
sysVMMathPow(double a, double b) {
    TRACE_PRINTF(SysTraceFile, "%s: sysVMMathPow %f %f\n", Me, a, b);
    return pow(a, b);
}

EXTERNAL double
sysVMMathIEEEremainder(double a, double b) {
    TRACE_PRINTF(SysTraceFile, "%s: sysVMMathIEEEremainder %f %f\n", Me, a, b);
    return remainder(a, b);
}

EXTERNAL double
sysVMMathCeil(double a) {
    TRACE_PRINTF(SysTraceFile, "%s: sysVMMathCeil %f\n", Me, a);
    return ceil(a);
}

EXTERNAL double
sysVMMathFloor(double a) {
    TRACE_PRINTF(SysTraceFile, "%s: sysVMMathFloor %f\n", Me, a);
    return floor(a);
}

EXTERNAL double
sysVMMathRint(double a) {
    TRACE_PRINTF(SysTraceFile, "%s: sysVMMathRint %f\n", Me, a);
    return rint(a);
}

EXTERNAL double
sysVMMathCbrt(double a) {
    TRACE_PRINTF(SysTraceFile, "%s: sysVMMathCbrt %f\n", Me, a);
    return cbrt(a);
}

EXTERNAL double
sysVMMathExpm1(double a) {
    TRACE_PRINTF(SysTraceFile, "%s: sysVMMathExpm1 %f\n", Me, a);
    return expm1(a);
}

EXTERNAL double
sysVMMathHypot(double a, double b) {
    TRACE_PRINTF(SysTraceFile, "%s: sysVMMathHypot %f %f\n", Me, a, b);
    return hypot(a, b);
}

EXTERNAL double
sysVMMathLog10(double a) {
    TRACE_PRINTF(SysTraceFile, "%s: sysVMMathLog10 %f\n", Me, a);
    return log10(a);
}

EXTERNAL double
sysVMMathLog1p(double a) {
    TRACE_PRINTF(SysTraceFile, "%s: sysVMMathLog1p %f\n", Me, a);
    return log1p(a);
}

#ifdef RVM_WITH_GCSPY
// GCspy

//NOTE It is the responsibility of the calling code to
//     check that server, driver etc are non-null.

EXTERNAL {
#include "gcspy_gc_stream.h"
#include "gcspy_main_server.h"
#include "gcspy_gc_driver.h"
#include "gcspy_color_db.h"
#include "gcspy_utils.h"
}

typedef void * (*pthread_start_routine_t)(void *);

static gcspy_main_server_t server;

// debugging
#define GCSPY_TRACE 0
#define GCSPY_TRACE_PRINTF(...) if(GCSPY_TRACE) fprintf(PORTLIB, SysTraceFile, __VA_ARGS__)

static int stream_count = 0;
static int stream_len;

EXTERNAL gcspy_gc_stream_t *
gcspyDriverAddStream (gcspy_gc_driver_t *driver, int id) {
  GCSPY_TRACE_PRINTF(SysTraceFile, "gcspyDriverAddStream: driver=%x(%s), id=%d...",
          driver, driver->name, id);
  gcspy_gc_stream_t *stream = gcspy_driverAddStream(driver, id);
  GCSPY_TRACE_PRINTF(SysTraceFile, "stream=%x\n", stream);
  return stream;
}

EXTERNAL void
gcspyDriverEndOutput (gcspy_gc_driver_t *driver) {
  int len;
  GCSPY_TRACE_PRINTF(SysTraceFile, "gcspyDriverEndOutput: driver=%x(%s), len=%d, written=%d\n",
                        driver, driver->name, stream_len, stream_count);
  stream_count = 0;
  /*??*/
  gcspy_buffered_output_t *output =
      gcspy_command_stream_get_output(driver->interpreter);
  len = gcspy_bufferedOutputGetLen(output);
  GCSPY_TRACE_PRINTF(SysTraceFile, "gcspyDriverEndOutput: interpreter has len=%d\n", len);
  gcspy_driverEndOutput(driver);
}

EXTERNAL void
gcspyDriverInit (gcspy_gc_driver_t *driver, int id, char *serverName, char *driverName,
                 char *title, char *blockInfo, int tileNum,
                 char *unused, int mainSpace) {
  GCSPY_TRACE_PRINTF(SysTraceFile, "gcspyDriverInit: driver=%x, id=%d, serverName=%s, driverName=%s, title=%s, blockInfo=%s, %d tiles, used=%s, mainSpace=%d\n",
                   driver, id, serverName, driverName,
                   title, blockInfo, tileNum,
                   unused, mainSpace);
  gcspy_driverInit(driver, id, serverName, driverName,
                   title, blockInfo, tileNum,
                   unused, mainSpace);
}

EXTERNAL void
gcspyDriverInitOutput (gcspy_gc_driver_t *driver) {
  GCSPY_TRACE_PRINTF(SysTraceFile, "gcspyDriverInitOutput: driver=%x(s)\n",
          driver, driver->name);
  gcspy_driverInitOutput(driver);
}

EXTERNAL void
gcspyDriverResize (gcspy_gc_driver_t *driver, int size) {
  GCSPY_TRACE_PRINTF(SysTraceFile, "gcspyDriverResize: driver=%x(%s), size %d\n",
          driver, driver->name, size);
  gcspy_driverResize(driver, size);
}

EXTERNAL void
gcspyDriverSetTileName (gcspy_gc_driver_t *driver, int tile, char *format, long value) {
  char buffer[128];
  sprintf(buffer, format, value);
  GCSPY_TRACE_PRINTF(SysTraceFile, "gcspyDriverSetTileName: driver=%x(%s), tile %d %s\n", driver, driver->name, tile, buffer);
  gcspy_driverSetTileName(driver, tile, buffer);
}

EXTERNAL void
gcspyDriverSetTileNameRange (gcspy_gc_driver_t *driver, int tile, Address start, Address end) {
  char name[256];
#ifndef RVM_FOR_32_ADDR
  snprintf(name, sizeof name, "   [%016llx-%016llx)", start, end);
#else
  snprintf(name, sizeof name, "   [%08x-%08x)", start, end);
#endif
  gcspyDriverSetTileName(driver, tile, name, 0);
}

EXTERNAL void
gcspyDriverSpaceInfo (gcspy_gc_driver_t *driver, char *spaceInfo) {
  GCSPY_TRACE_PRINTF(SysTraceFile, "gcspyDriverSpaceInfo: driver=%x(%s), spaceInfo = +%s+(%x)\n", driver, driver->name, spaceInfo, spaceInfo);
  gcspy_driverSpaceInfo(driver, spaceInfo);
}

EXTERNAL void
gcspyDriverStartComm (gcspy_gc_driver_t *driver) {
  GCSPY_TRACE_PRINTF(SysTraceFile, "gcspyDriverStartComm: driver=%x(%s)\n", driver, driver->name);
  gcspy_driverStartComm(driver);
}

EXTERNAL void
gcspyDriverStream (gcspy_gc_driver_t *driver, int id, int len) {
  GCSPY_TRACE_PRINTF(SysTraceFile, "gcspyDriverStream: driver=%x(%s), id=%d(%s), len=%d\n",
          driver, driver->name, id, driver->streams[id].name, len);
  stream_count = 0;
  stream_len = len;
  gcspy_driverStream(driver, id, len);
}

EXTERNAL void
gcspyDriverStreamByteValue (gcspy_gc_driver_t *driver, int val) {
  GCSPY_TRACE_PRINTF(SysTraceFile, "gcspyDriverStreamByteValue: driver=%x, val=%d\n", driver, val);
  stream_count++;
  gcspy_driverStreamByteValue(driver, val);
}

EXTERNAL void
gcspyDriverStreamShortValue (gcspy_gc_driver_t *driver, short val) {
  GCSPY_TRACE_PRINTF(SysTraceFile, "gcspyDriverStreamShortValue: driver=%x, val=%d\n", driver, val);
  stream_count++;
  gcspy_driverStreamShortValue(driver, val);
}

EXTERNAL void
gcspyDriverStreamIntValue (gcspy_gc_driver_t *driver, int val) {
  GCSPY_TRACE_PRINTF(SysTraceFile, "gcspyDriverStreamIntValue: driver=%x, val=%d\n", driver, val);
  stream_count++;
  gcspy_driverStreamIntValue(driver, val);
}

EXTERNAL void
gcspyDriverSummary (gcspy_gc_driver_t *driver, int id, int len) {
  GCSPY_TRACE_PRINTF(SysTraceFile, "gcspyDriverSummary: driver=%x(%s), id=%d(%s), len=%d\n",
          driver, driver->name, id, driver->streams[id].name, len);
  stream_count = 0;
  stream_len = len;
  gcspy_driverSummary(driver, id, len);
}

EXTERNAL void
gcspyDriverSummaryValue (gcspy_gc_driver_t *driver, int val) {
  GCSPY_TRACE_PRINTF(SysTraceFile, "gcspyDriverSummaryValue: driver=%x, val=%d\n", driver, val);
  stream_count++;
  gcspy_driverSummaryValue(driver, val);
}

/* Note: passed driver but uses driver->interpreter */
EXTERNAL void
gcspyIntWriteControl (gcspy_gc_driver_t *driver, int id, int len) {
  GCSPY_TRACE_PRINTF(SysTraceFile, "gcspyIntWriteControl: driver=%x(%s), interpreter=%x, id=%d, len=%d\n", driver, driver->name, driver->interpreter, id, len);
  stream_count = 0;
  stream_len = len;
  gcspy_intWriteControl(driver->interpreter, id, len);
}

EXTERNAL gcspy_gc_driver_t *
gcspyMainServerAddDriver (gcspy_main_server_t *server) {
  GCSPY_TRACE_PRINTF(SysTraceFile, "gcspyMainServerAddDriver: server address = %x(%s), adding driver...", server, server->name);
  gcspy_gc_driver_t *driver = gcspy_mainServerAddDriver(server);
  GCSPY_TRACE_PRINTF(SysTraceFile, "address = %d\n", driver);
  return driver;
}

EXTERNAL void
gcspyMainServerAddEvent (gcspy_main_server_t *server, int event, const char *name) {
  GCSPY_TRACE_PRINTF(SysTraceFile, "gcspyMainServerAddEvent: server address = %x(%s), event=%d, name=%s\n", server, server->name, event, name);
  gcspy_mainServerAddEvent(server, event, name);
}

EXTERNAL gcspy_main_server_t *
gcspyMainServerInit (int port, int len, const char *name, int verbose) {
  GCSPY_TRACE_PRINTF(SysTraceFile, "gcspyMainServerInit: server=%x, port=%d, len=%d, name=%s, verbose=%d\n", &server, port, len, name, verbose);
  gcspy_mainServerInit(&server, port, len, name, verbose);
  return &server;
}

EXTERNAL int
gcspyMainServerIsConnected (gcspy_main_server_t *server, int event) {
  GCSPY_TRACE_PRINTF(SysTraceFile, "gcspyMainServerIsConnected: server=%x, event=%d...", &server, event);
  int res = gcspy_mainServerIsConnected(server, event);
  if (res)
    GCSPY_TRACE_PRINTF(SysTraceFile, "connected\n");
  else
    GCSPY_TRACE_PRINTF(SysTraceFile, "not connected\n");
  return res;
}

typedef void gcspyMainServerOuterLoop_t(gcspy_main_server_t *);

EXTERNAL gcspyMainServerOuterLoop_t *
gcspyMainServerOuterLoop () {
  /* return gcspy_mainServerOuterLoop;*/
  return gcspy_mainServerMainLoop;
}

EXTERNAL void
gcspyMainServerSafepoint (gcspy_main_server_t *server, int event) {
  GCSPY_TRACE_PRINTF(SysTraceFile, "gcspyMainServerSafepoint: server=%x, event=%d\n", &server, event);
  gcspy_mainServerSafepoint(server, event);
}

EXTERNAL void
gcspyMainServerSetGeneralInfo (gcspy_main_server_t *server, char *generalInfo) {
  GCSPY_TRACE_PRINTF(SysTraceFile, "gcspyMainServerSetGeneralInfo: server=%x, info=%s\n", &server, generalInfo);
  gcspy_mainServerSetGeneralInfo(server, generalInfo);
}

EXTERNAL void
gcspyMainServerStartCompensationTimer (gcspy_main_server_t *server) {
  GCSPY_TRACE_PRINTF(SysTraceFile, "gcspyMainServerStartCompensationTimer: server=%x\n", server);
  gcspy_mainServerStartCompensationTimer(server);
}

EXTERNAL void
gcspyMainServerStopCompensationTimer (gcspy_main_server_t *server) {
  GCSPY_TRACE_PRINTF(SysTraceFile, "gcspyMainServerStopCompensationTimer: server=%x\n", server);
  gcspy_mainServerStopCompensationTimer(server);
}

EXTERNAL void
gcspyStartserver (gcspy_main_server_t *server, int wait, void *loop) {
//#ifndef __linux__
//  printf("I am not Linux!");
//     exit(EXIT_STATUS_UNSUPPORTED_INTERNAL_OP);
//#endif __linux__
  GCSPY_TRACE_PRINTF(SysTraceFile, "gcspyStartserver: starting thread, wait=%d\n", wait);
  pthread_t tid;
  int res = pthread_create(&tid, NULL,
                          (pthread_start_routine_t) loop,  server);
  if (res != 0) {
      fprintf(SysErrorFile,"Couldn't create thread.\n");
      exit(EXIT_STATUS_MISC_TROUBLE);
  }

  if(wait) {
    GCSPY_TRACE_PRINTF(SysTraceFile, "gcspy_mainServerWaitForClient: server=%x\n", server);
    gcspy_mainServerWaitForClient(server);
  }
}

EXTERNAL void
gcspyStreamInit (gcspy_gc_stream_t *stream, int id, int dataType, char *streamName,
                 int minValue, int maxValue, int zeroValue, int defaultValue,
                 char *stringPre, char *stringPost, int presentation, int paintStyle,
                 int indexMaxStream, int red, int green, int blue) {
  gcspy_color_t colour;
  colour.red = (unsigned char) red;
  colour.green = (unsigned char) green;
  colour.blue = (unsigned char) blue;
  GCSPY_TRACE_PRINTF(SysTraceFile, "gcspyStreamInit: stream=%x, id=%d, dataType=%d, streamName=\"%s\", min=%d, max=%d, zero=%d, default=%d, pre=\"%s\", post=\"%s\", presentation=%d, style=%d, maxIndex=%d, colour=%x<%d,%d,%d>\n",
                   stream, id, dataType, streamName,
                   minValue, maxValue, zeroValue, defaultValue,
       stringPre, stringPost, presentation, paintStyle,
       indexMaxStream, &colour, colour.red, colour.green, colour.blue);
  gcspy_streamInit(stream, id, dataType, streamName,
                   minValue, maxValue, zeroValue,defaultValue,
       stringPre, stringPost, presentation, paintStyle,
       indexMaxStream, &colour);
}

EXTERNAL void
gcspyFormatSize (char *buffer, int size) {
  GCSPY_TRACE_PRINTF(SysTraceFile, "gcspyFormatSize: size=%d...", size);
  strcpy(buffer, gcspy_formatSize(size));
  GCSPY_TRACE_PRINTF(SysTraceFile, "buffer=%s\n", buffer);
}

EXTERNAL int
gcspySprintf(char *str, const char *format, char *arg) {
  GCSPY_TRACE_PRINTF(SysTraceFile, "sprintf: str=%x, format=%s, arg=%s\n", str, format, arg);
  int res = sprintf(str, format, arg);
  GCSPY_TRACE_PRINTF(SysTraceFile, "sprintf: result=%s (%x)\n", str, str);
  return res;
}

#endif
