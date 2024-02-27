/*
 * Name: Pan Mingwei - 01408914
 * Email ID: mingwei.pan.2022
 */
#include <signal.h>
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <time.h>
#include <unistd.h>

/******************************************************************************
 * Types
 ******************************************************************************/

typedef enum process_state {
    RUNNING = 0,
    READY = 1,
    STOPPED = 2,
    TERMINATED = 3,
    UNUSED = -1
} process_state;

typedef struct process_record {
    pid_t pid;
    process_state state;
    char file_name[255];
    int total_runtime;
    int remaining_time;
    time_t start_time;
} process_record;

/******************************************************************************
 * Globals
 ******************************************************************************/

enum {
    MAX_PROCESSES = 64
};

process_record process_records[MAX_PROCESSES];

/******************************************************************************
 * Helper functions
 ******************************************************************************/

//Method to change all the process state to UNUSED at the start of the manager program
void initializeProcessRecords(void) {
    for (int i = 0; i < MAX_PROCESSES; ++i) {
        process_records[i].state = UNUSED; // Set all to UNUSED state
    }
}

// Method to update the process's runtime information
void updateRemainingTime(int pid, int index) {
    time_t current_time = time(NULL);
    if (process_records[index].pid == pid) {
        int time_ran = current_time - process_records[index].start_time;
        process_records[index].remaining_time = process_records[index].remaining_time - time_ran;
    }
}

//Method to be call, when there is a change in the process state. The method will check and run the shortest remaining runtime process
void runShortestProcess(void) {
    time_t current_time = time(NULL);
    // STOP the current running process and change to READY state
    for (int i = 0; i < MAX_PROCESSES; ++i) {
        if (process_records[i].state == RUNNING) {
            // update remaining runtime
            updateRemainingTime(process_records[i].pid, i);
            // stop the new process and set the state to ready
            kill(process_records[i].pid, SIGSTOP);
            process_records[i].state = READY;
            break; // stop the loop, since there is only process with RUNNING state
        }
    }

    int shortestPID = -1;
    int shortestIndex = -1;
    int shortestRuntime = -1;
    // loop all the ready state, to find PID with shortest runtime
    for (int i = 0; i < MAX_PROCESSES; ++i) {
        if (process_records[i].state == READY) {
            if (shortestRuntime == -1 || process_records[i].remaining_time < shortestRuntime) {
                shortestRuntime = process_records[i].remaining_time;
                shortestPID = process_records[i].pid;
                shortestIndex = i;
            }
        }
    }

    if (shortestPID != -1) {
        kill(process_records[shortestIndex].pid, SIGCONT);
        process_records[shortestIndex].state = RUNNING;
        // update the starting timestamp of the process
        process_records[shortestIndex].start_time = current_time;
    } else {
        printf("There is no process in READY state to dispatch.\n");
    }
}

//Method when the current running process is ended its runtime
void currentProcessEnded(int pid) {
    // STOP the current running process and change to READY state
    for (int i = 0; i < MAX_PROCESSES; ++i) {
        if (process_records[i].state == RUNNING && process_records[i].pid == pid) {
            // update remaining runtime
            process_records[i].remaining_time = 0;
            // TERMINATED the process and as it finish the runtime
            kill(process_records[i].pid, SIGSTOP);
            process_records[i].state = TERMINATED;
            printf("\n");
            printf("Process %d has completed its execution; proceeding to execute the next shortest-duration process.\n", process_records[i].pid);
            break; // stop the loop, since there is only process with RUNNING state
        }
    }
    // Run the next process
    runShortestProcess();
}

//Method to handle the case where the process's runtime finished
//it will catch when the child process send exit signal and will run the next shortest remaining runtime  process
void sigchld_handler(int signum) {
    (void)signum; // Mark the parameter as unused
    int status;
    pid_t pid = waitpid(-1, &status, WNOHANG);
    if (pid > 0) {
        // Triggered when the current process runtime finishes and will run the next shortest runtime process which is in ready state.
        currentProcessEnded(pid);
    }
}

void setup_sigchld_handler(void) {
    struct sigaction sa;
    memset(&sa, 0, sizeof(sa));
    sa.sa_handler = sigchld_handler;
    sigemptyset(&sa.sa_mask);
    sa.sa_flags = SA_RESTART;
    if (sigaction(SIGCHLD, &sa, NULL) == -1) {
        perror("sigaction");
        exit(EXIT_FAILURE);
    }
}

/******************************************************************************
 * Command Functions
 ******************************************************************************/

void perform_run(char *args[]) {
    int index = -1;
    for (int i = 0; i < MAX_PROCESSES; ++i) {
        if (process_records[i].state == UNUSED) {
            index = i;
            break;
        }
    }

    if (index < 0) {
        printf("no process slots available.\n");
        return;
    }

    pid_t pid = fork();
    if (pid < 0) {
        fprintf(stderr, "fork failed\n");
        return;
    }

    if (pid == 0) {
        const int len = strlen(args[0]);
        char exec[len + 3];
        strcpy(exec, "./");
        strcat(exec, args[0]);
        execvp(exec, args);
        // Unreachable code unless execution failed.
        exit(EXIT_FAILURE);
    }
    process_record *const p = &process_records[index];

    // Update process's information
    p->pid = pid;
    p->total_runtime = atoi(args[2]);
    p->remaining_time = atoi(args[2]);
    strcat(p->file_name, args[1]);
    kill(p->pid, SIGSTOP);
    p->state = READY;
    // get shortest remaining runtime to run
    runShortestProcess();
}

void perform_resume(char *args[]) {
    const pid_t pid = atoi(args[0]);
    if (pid <= 0) {
        printf("The process ID must be a positive integer.\n");
        return;
    }
    for (int i = 0; i < MAX_PROCESSES; ++i) {
        process_record *const p = &process_records[i];
        if (p->pid == pid) {
            // check if the process state is in STOPPED state
            if (p->state == TERMINATED) {
                printf("%d cannot be resumed as it has been termindated.\n", p->pid);
                return;
            }
            if (p->state == RUNNING) {
                printf("%d cannot be resumed as it in running state.\n", p->pid);
                return;
            }
            if (p->state == READY) {
                printf("%d is already in ready state.\n", p->pid);
                return;
            }
            // change to READY state
            p->state = READY;
            runShortestProcess();
            return;
        }
    }
    printf("Process %d not found.\n", pid);
}

void perform_kill(char *args[]) {
    const pid_t pid = atoi(args[0]);
    if (pid <= 0) {
        printf("The process ID must be a positive integer.\n");
        return;
    }
    for (int i = 0; i < MAX_PROCESSES; ++i) {
        process_record *const p = &process_records[i];
        if (p->pid == pid) {
            // message to inform user, the current process is already termindated
            if (p->state == TERMINATED) {
                printf("%d already termindated.\n", p->pid);
                return;
            }
            bool isRunning = false;
            if (p->state == RUNNING) {
                isRunning = true;
            }
            // Kill the process and update its infromations, this will be catch by SIGCHLD and run the next shortest process
            kill(p->pid, SIGTERM);
            p->state = TERMINATED;
            // if it is runing, will be catch by SIGCHLD and run the next shortest process
            if (isRunning) {
                updateRemainingTime(p->pid, i);
            }
            // stop the method
            return;
        }
    }
    printf("Process %d not found.\n", pid);
}

void perform_stop(char *args[]) {
    const pid_t pid = atoi(args[0]);
    if (pid <= 0) {
        printf("The process ID must be a positive integer.\n");
        return;
    }
    for (int i = 0; i < MAX_PROCESSES; ++i) {
        process_record *const p = &process_records[i];
        if (p->pid == pid) {
            if (p->state == TERMINATED) {
                printf("%d cannot be stopped as it has been terminated.\n", p->pid);
                return;
            }
            // message to inform user, the current process is already in stopped state
            if (p->state == STOPPED) {
                printf("%d already in stopped state.\n", p->pid);
                return;
            }
            bool isRunning = false;
            if (p->state == RUNNING) {
                isRunning = true;
            }
            // change to STOPPED state and update its infromations
            kill(p->pid, SIGSTOP);
            p->state = STOPPED;
            if (isRunning) {
                updateRemainingTime(p->pid, i);
                runShortestProcess();
            }
            return;
        }
    }
    printf("Process %d not found.\n", pid);
}

void perform_list(void) {
    // loop through all child processes, display state
    bool has_process = false;

    for (int i = 0; i < MAX_PROCESSES; ++i) {
        process_record *const p = &process_records[i];

        if (p->state != UNUSED) {
            printf("%d,%d \n", p->pid, p->state);
            has_process = true;
        }
    }

    if (!has_process) {
        printf("No processes to list.\n");
    }
}

void perform_exit(void) {
    for (int i = 0; i < MAX_PROCESSES; ++i) {
        if (process_records[i].state != TERMINATED && process_records[i].state != UNUSED) {
            // Send SIGTERM to the process that is not terminated
            kill(process_records[i].pid, SIGTERM);
            printf("[%d] %d terminated\n", i, process_records[i].pid);
            process_records[i].state = TERMINATED;
        }
    }
}

char *get_input(char *buffer, char *args[], int args_count_max) {
    // capture a command
    printf("\x1B[34m"
           "cs205"
           "\x1B[0m"
           "$ ");
    fgets(buffer, 79, stdin);
    for (char *c = buffer; *c != '\0'; ++c) {
        if ((*c == '\r') || (*c == '\n')) {
            *c = '\0';
            break;
        }
    }
    strcat(buffer, " ");
    // tokenize command's arguments
    char *p = strtok(buffer, " ");
    int arg_cnt = 0;
    while (p != NULL) {
        args[arg_cnt++] = p;
        if (arg_cnt == args_count_max - 1) {
            break;
        }
        p = strtok(NULL, " ");
    }
    args[arg_cnt] = NULL;
    return args[0];
}

/******************************************************************************
 * Entry point
 ******************************************************************************/

int main(void) {
    initializeProcessRecords();
    setup_sigchld_handler();
    char buffer[80];
    // NULL-terminated array
    char *args[10];
    const int args_count = sizeof(args) / sizeof(*args);
    while (true) {
        char *const cmd = get_input(buffer, args, args_count);
        if (strcmp(cmd, "kill") == 0) {
            perform_kill(&args[1]);
        } else if (strcmp(cmd, "run") == 0) {
            perform_run(&args[1]);
        } else if (strcmp(cmd, "stop") == 0) {
            perform_stop(&args[1]);
        } else if (strcmp(cmd, "resume") == 0) {
            perform_resume(&args[1]);
        } else if (strcmp(cmd, "list") == 0) {
            perform_list();
        } else if (strcmp(cmd, "exit") == 0) {
            perform_exit();
            break;
        } else {
            printf("invalid command\n");
        }
    }
    return EXIT_SUCCESS;
}
