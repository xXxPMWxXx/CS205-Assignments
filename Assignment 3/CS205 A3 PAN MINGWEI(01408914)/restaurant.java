/*
 * Name: Pan Mingwei - 01408914
 * Email ID: mingwei.pan.2022
 */

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;

/**
 * The Restaurant Simulation Program
 *
 * Design Choices:
 * 
 * 1. BlockingQueues:
 * - orderPlacementQueue and preparedOrderQueue are used to represent the
 * workflow of orders from being placed to being prepared. BlockingQueue is
 * chosen for its thread-safe properties, ensuring that concurrent access by
 * multiple threads (waiters and chefs) does not lead to race conditions.
 * - The queues' capacities are set based on configuration, simulating limited
 * space for orders in different stages of processing.
 *
 * 2. Semaphores:
 * - Waiter thread : placeOrderSemaphore and serveSemaphore control access to
 * critical sections for place and serve orders
 * - Chef thread : chefSemaphore is for the chef thread to start
 * preparing new orders, ensuring that there is slot in the
 * preparedOrderQueue before a chef start preparing the order.
 * - Overall, semaphores help in controlling the flow of orders through the
 * system, preventing deadlock and ensuring fairness among threads.
 *
 * 3. Synchronization:
 * - A lock object is used in critical sections where shared mutable state is
 * accessed or modified. This is crucial for maintaining consistency of shared
 * counters (like ordersPlaced and ordersProcessed) ensuring that the order are
 * in sequence and logging are atomic.
 *
 * 4. Thread Management:
 * - Threads for waiters and chefs are dynamically created based on the
 * configuration settings(based on the number of waiters and chefs).
 * - Each thread are independent but coordinates through shared
 * varaibles and synchronization mechanisms.
 * - The main thread waits for all waiter and chef threads to complete
 * before ending the simulation, ensuring that all orders are processed.
 *
 * 5. Interrupts and Volatile Variables:
 * - Volatile variables are used for shared counters and flags to ensure
 * visibility of updates across threads.
 * - Interrupts are employed to gracefully terminate waiter and chef threads
 * once their work is complete, ensuring that the program exits cleanly without
 * leaving any threads running.
 *
 */

public class restaurant {
    private static final String LOG_FILE = "log.txt";
    // Avoid race conditions
    private static volatile BlockingQueue<Integer> orderPlacementQueue;
    private static volatile BlockingQueue<Integer> preparedOrderQueue;

    // Counter to total count
    private static volatile int ordersProcessed = 0; // Counter for total number of order processed
    private static volatile int ordersPlaced = 0; // Counter for total number of placed orders
    private static volatile int ordersServed = 0; // Counter for total number of served orders

    // To keep track of current order in orderPlacementQueue, including
    // order is been processing, so that the waiter would not place order
    // immediately after chef finish prepared the order.
    private static volatile int currentOrderPlaced = 0;

    private static final Object lock = new Object();

    // Declare static variables
    private static int numberOfChefs;
    private static int numberOfWaiters;
    private static int numberOfOrders;
    private static int timeOfOrderPlacement;
    private static int timeOfOrderPreparation;
    private static int timeOfOrderServing;
    private static int sizeOfOrderPlacementQueue;
    private static int sizeOfPreparedOrderQueue;

    // To initialize static variables
    static {
        int[] settings = new int[8];
        // Assume the config file is in the same directory
        String filePath = "config.txt";
        // Keep track of the index
        int index = 0;
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                // Check if the line is empty or consists solely of whitespace
                if (line.trim().isEmpty()) {
                    System.err.println("Empty or whitespace-only line detected at line " + lineNumber + ".");
                    throw new RuntimeException(
                            "Invalid file format: empty or whitespace-only line at line " + lineNumber + ".");
                }
                // Split line at the first occurrence of '#', if present
                String[] parts = line.split("#", 2);
                // Try to parse the integer part (before any comment)
                try {
                    // Trim the space in between
                    int value = Integer.parseInt(parts[0].trim());
                    settings[index++] = value;
                } catch (NumberFormatException e) {
                    System.err.println("Error: Invalid format detected. Terminating initialization. Please make sure you have config.txt file in the folder!");
                    throw new RuntimeException("Initialization failed due to invalid file format. Please make sure you have config.txt file in the folder!");
                }
            }
        } catch (IOException e) {
            System.err.println("Error reading the file: " + e.getMessage());
            throw new RuntimeException("Initialization failed due to IO error.");
        }

        // Assign values to static variables and ensure we have 8 elements.
        if (index == 8) {
            numberOfChefs = settings[0];
            numberOfWaiters = settings[1];
            numberOfOrders = settings[2];
            timeOfOrderPlacement = settings[3];
            timeOfOrderPreparation = settings[4];
            timeOfOrderServing = settings[5];
            sizeOfOrderPlacementQueue = settings[6];
            sizeOfPreparedOrderQueue = settings[7];
        } else {
            throw new RuntimeException("Initialization failed due to insufficient data.");
        }
    }

    // To store the threads
    private static volatile Thread[] waiterThreads = new Thread[numberOfWaiters];
    private static volatile Thread[] chefThreads = new Thread[numberOfChefs];

    // Semaphore for waiter and chef threads based on the size of the respective
    // size of the queue
    private static volatile Semaphore placeOrderSemaphore = new Semaphore(sizeOfOrderPlacementQueue, true);
    private static volatile Semaphore serveSemaphore = new Semaphore(sizeOfPreparedOrderQueue, true);
    private static volatile Semaphore chefSemaphore = new Semaphore(sizeOfPreparedOrderQueue, true);

    public static void main(String[] args) {
        System.out.println("Program started!");
        // Queue for Chef to process
        orderPlacementQueue = new LinkedBlockingQueue<>(sizeOfOrderPlacementQueue);
        // Queue for the order been processed by Chef and ready for waiter to serve
        preparedOrderQueue = new LinkedBlockingQueue<>(sizeOfPreparedOrderQueue);

        // Create and start waiter threads
        for (int i = 0; i < numberOfWaiters; i++) {
            Waiter waiter = new Waiter(i);
            waiterThreads[i] = new Thread(waiter);
            waiterThreads[i].start();
        }

        // Create and start chef threads
        for (int i = 0; i < numberOfChefs; i++) {
            Chef chef = new Chef(i);
            chefThreads[i] = new Thread(chef);
            chefThreads[i].start();
        }

        // Wait for all waiter threads to finish
        for (Thread thread : waiterThreads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        // Wait for all chef threads to finish
        for (Thread thread : chefThreads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        System.out.println("Done.");
    }

    // Waiter thread implementation
    static class Waiter implements Runnable {
        private final int waiterId;

        Waiter(int id) {
            this.waiterId = id;
        }

        @Override
        public void run() {
            while (ordersServed < numberOfOrders) {
                try {
                    // When waiter want place order, take semaphore first and which will be realsed
                    // by chef thread when they finish processing the order
                    placeOrderSemaphore.acquire();
                    // Place orders until the total number is reached and currentOrderPlaced should
                    // be below sizeOfOrderPlacementQueue, only start place next order when chef
                    // finish processing order => second condition to avoid the behavior of the
                    // blocking queue, should only start placing order after chef finish processing
                    if (ordersPlaced < numberOfOrders && currentOrderPlaced < sizeOfOrderPlacementQueue) {
                        currentOrderPlaced++;
                        placeOrder();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

                // Serve prepared orders if available, without waiting for all orders to be
                // placed
                try {
                    serveSemaphore.acquire(); // Acquire permit before serving an order
                    if (!preparedOrderQueue.isEmpty()) {
                        serveOrder();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    serveSemaphore.release(); // Release permit after serving an order
                }
            }
            // If reach here, means no more orders to be served, end all the waiter threads
            interruptOtherWaiters();
            System.out.println("Waiter " + this.waiterId + " thread finished.");
        }

        // To notify other waiter threads to finish, as they are waiting for the
        // blocking queue
        private void interruptOtherWaiters() {
            for (Thread waiterThread : waiterThreads) {
                // Make sure not to interrupt itself
                if (waiterThread != Thread.currentThread()) {
                    waiterThread.interrupt();
                }
            }
        }

        private void placeOrder() {
            try {

                // Simulate order placing time
                Thread.sleep(timeOfOrderPlacement);
                // lock the section, so that the order will be in order
                synchronized (lock) {
                    // To check order placement, without the condition, when there is multiple
                    if (ordersPlaced < numberOfOrders) {
                        orderPlacementQueue.put(ordersPlaced); // Place an order
                        // waiter thread, it will create extra orders
                        log("Waiter", waiterId, "Order Placed", ordersPlaced);
                        ordersPlaced++;
                    }
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        private void serveOrder() {
            try {
                // Take a prepared order
                Integer order = preparedOrderQueue.take();
                // Simulate order serving time
                Thread.sleep(timeOfOrderServing);
                ordersServed++;
                log("Waiter", waiterId, "Order Served", order);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    // Chef thread implementation
    static class Chef implements Runnable {
        private final int chefId;

        Chef(int id) {
            this.chefId = id;
        }

        @Override
        public void run() {
            // Keep running until the order has been processed is equal to total number of
            // orders
            while (ordersProcessed < numberOfOrders) {
                try {
                    chefSemaphore.acquire(); // Acquire permit before preparing an order
                    prepareOrder();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    chefSemaphore.release(); // Release permit after preparing an order
                    placeOrderSemaphore.release(); // Release permit for waiter to place next order
                }
            }
            // If reach here, means no more orders to be process, interrupt and end all the
            // chef threads
            interruptOtherChefs();
            System.out.println("Chef " + this.chefId + " thread finished.");

        }

        private void prepareOrder() {
            try {
                if (preparedOrderQueue.remainingCapacity() != 0) {
                    Integer order = orderPlacementQueue.take();
                    Thread.sleep(timeOfOrderPreparation);
                    // lock the section, so that chef will prepare order in sequence
                    synchronized (lock) {
                        currentOrderPlaced--; // minus from the currentOrderPlaced, so that the waiter know that they
                                              // can start to place the next order
                        preparedOrderQueue.put(order);
                        // Retrieve an order from the order queue
                        log("Chef", chefId, "Order Prepared", order);
                        ordersProcessed++;
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // To notify other chef threads to finish, as they are waiting for the blocking
        // queue
        private void interruptOtherChefs() {
            for (Thread chefThread : chefThreads) {
                // Make sure not to interrupt itself
                if (chefThread != Thread.currentThread()) {
                    chefThread.interrupt();
                }
            }
        }
    }

    // Method to log messages
    synchronized static void log(String threadType, int threadId, String action, int orderId) {
        long timestamp = new java.sql.Timestamp(System.currentTimeMillis()).getTime();
        // Format the message
        String message = String.format("[%s] %s %d: %s - Order %d", timestamp, threadType, threadId, action, orderId);
        // Write the message into the log file
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(LOG_FILE, true))) {
            writer.write(message);
            writer.newLine();
        } catch (IOException e) {
            System.err.println("Error writing to log file: " + e.getMessage());
        }
    }
}