# Scala Reactor Concurrency for Hangman Game

## 1. Introduction

This file is mainly to record the learning process of this project.

## 2. BlockingQueueEvent

### 2.1 Busy wait

The busy wait method simply uses `synchronized`, `wait` and `notifyAll`. The method uses a `while` loop to detect whether the resource is available. Once the condition is met, it starts to operate.

❌ Problem 1: Busy wait takes up CPU resources.

❌ Problem 2: `wait` and `notifyAll` is a public method, which means that it can be called from outside the `BlockingQueue` object.

❌ Problem 3: `notifyAll` calls all the thread. If only one thread can execute, then all the other thread needs to go back to the waiting state.


⏱️ Timespend(1000 threads): 0,84s
⏱️ Timespend(5000 threads): 7,15s

### 2.2 Semaphore with NotifyAll

⏱️ Timespend(1000 threads): 0,39s
⏱️ Timespend(5000 threads): 2,31s

### 2.3  Semaphore with Notify

Because the producers only wait in `nonFull`, the consumers only wait in `nonEmpty`.
So `notify` will always call the opposite side.

⏱️ Timespend(1000 threads): 0,20s
⏱️ Timespend(5000 threads): 2,01s

❌ Problem: Edge case with `getAll`

- Scenario 1: `getAll` and `dequeue` at the same time, but the `release` in `dequeue` happens later

| Thread  1             | Thread 2            | Queue(5) | notEmpty | notFull     |
| --------------------- | ------------------- | -------- | -------- | ----------- |
| `notFull.acquire`     |                     | 0        | 0        | 4           |
| `enqueue(1)`          |                     | 1        | 0        | 4           |
| `notEmpty.release`    |                     | 1        | 1        | 4           |
|                       | `notFull.acquire`   | 1        | 1        | 3           |
|                       | `enqueue(1)`        | 2        | 1        | 3           |
|                       | `notEmpty.release`  | 2        | 2        | 3           |
| >>Want to get All<<   | >>Want to get one<< |          |          |             |
|                       | `notEmpty.acquire`  | 2        | 1        | 3           |
| `notEmpty.acquireAll` | `dequeue(1)`        | 1        | 0        | 3           |
| `dequeueAll`          |                     | 0        | 0        | 3           |
| `notFull.releaseAll`  |                     | 0        | 0        | 5           |
|                       | `notFull.release`   | 0        | 0        | 6 <-problem |

- Scenario 2: `getAll` and `enqueue` at the same time, but the `release` in `enqueue` happens later

| Thread  1                     | Thread 2                | Queue(5) | notEmpty   | notFull |
| ----------------------------- | ----------------------- | -------- | ---------- | ------- |
| `notFull.acquire`             |                         | 0        | 0          | 4       |
| `enqueue(1)`                  |                         | 1        | 0          | 4       |
| `notEmpty.release`            |                         | 1        | 1          | 4       |
|                               | `notFull.acquire`       | 1        | 1          | 3       |
|                               | `enqueue(1)`            | 2        | 1          | 3       |
|                               | `notEmpty.release`      | 2        | 2          | 3       |
| >>Want to get All<<           | >>Want to enqueue one<< |          |            |         |
|                               | `notFull.acquire`       | 2        | 2          | 2       |
| `notEmpty.acquireAll`(gets 2) | `enqueue(1)`            | 3        | 0          | 2       |
| `dequeueAll`      (dequeue 3) |                         | 0        | 0          | 2       |
| `notFull.releaseAll`          |                         | 0        | 0          | 5       |
|                               | `notEmpty.release`      | 0        | 1<-problem | 5       |

- Scenario 3: `getAll` and `enqueue` at the same time, but the `enqueue` in `enqueue` happens later

| Thread  1                     | Thread 2                | Queue(5) | notEmpty | notFull     |
| ----------------------------- | ----------------------- | -------- | -------- | ----------- |
| `notFull.acquire`             |                         | 0        | 0        | 4           |
| `enqueue(1)`                  |                         | 1        | 0        | 4           |
| `notEmpty.release`            |                         | 1        | 1        | 4           |
|                               | `notFull.acquire`       | 1        | 1        | 3           |
|                               | `enqueue(1)`            | 2        | 1        | 3           |
|                               | `notEmpty.release`      | 2        | 2        | 3           |
| >>Want to get All<<           | >>Want to enqueue one<< |          |          |             |
| `notEmpty.acquireAll`(gets 2) |                         | 2        | 0        | 3           |
|                               | `notFull.acquire`       | 2        | 0        | 2           |
| `dequeueAll`      (dequeue 2) |                         | 0        | 0        | 2           |
|                               | `enqueue(1)`            | 1        | 0        | 2           |
| `notFull.releaseAll`          |                         | 1        | 0        | 5           |
|                               | `notEmpty.release`      | 1        | 1        | 5 <-problem |

### 2.4  Change in getAll

Based on the scenario, the `releaseAll` and `acquireAll` methods cannot directly use `capacity` and `capacity`.
It needs to match the real amount of dequeue elements.


- Scenario 1: `getAll` and `dequeue` at the same time, but the `release` in `dequeue` happens later

| Thread  1                        | Thread 2            | Queue(5) | notEmpty | notFull |
| -------------------------------- | ------------------- | -------- | -------- | ------- |
| `notFull.acquire`                |                     | 0        | 0        | 4       |
| `enqueue(1)`                     |                     | 1        | 0        | 4       |
| `notEmpty.release`               |                     | 1        | 1        | 4       |
|                                  | `notFull.acquire`   | 1        | 1        | 3       |
|                                  | `enqueue(1)`        | 2        | 1        | 3       |
|                                  | `notEmpty.release`  | 2        | 2        | 3       |
| >>Want to get All<<              | >>Want to get one<< |          |          |         |
|                                  | `notEmpty.acquire`  | 2        | 1        | 3       |
| `notEmpty.acquireAll`(gets 1)    | `dequeue(1)`        | 1        | 0        | 3       |
| `dequeueAll`         (dequeue 1) |                     | 0        | 0        | 3       |
| `notFull.releaseAll` (release 1) |                     | 0        | 0        | 4       |
|                                  | `notFull.release`   | 0        | 0        | 5       |

- Scenario 2: `getAll` and `enqueue` at the same time, but the `release` in `enqueue` happens later

| Thread  1                        | Thread 2                | Queue(5) | notEmpty | notFull |
| -------------------------------- | ----------------------- | -------- | -------- | ------- |
| `notFull.acquire`                |                         | 0        | 0        | 4       |
| `enqueue(1)`                     |                         | 1        | 0        | 4       |
| `notEmpty.release`               |                         | 1        | 1        | 4       |
|                                  | `notFull.acquire`       | 1        | 1        | 3       |
|                                  | `enqueue(1)`            | 2        | 1        | 3       |
|                                  | `notEmpty.release`      | 2        | 2        | 3       |
| >>Want to get All<<              | >>Want to enqueue one<< |          |          |         |
|                                  | `notFull.acquire`       | 2        | 2        | 2       |
| `notEmpty.acquireAll`(gets 2)    | `enqueue(1)`            | 3        | 0        | 2       |
| `notEmpty.permits -= 1`          |                         | 0        | -1       | 2       |
| `dequeueAll`         (dequeue 3) |                         | 0        | -1       | 2       |
| `notFull.releaseAll` (release 3) |                         | 0        | -1       | 5       |
|                                  | `notEmpty.release`      | 0        | 0        | 5       |

- Scenario 3: `getAll` and `enqueue` at the same time, but the `enqueue` in `enqueue` happens later

| Thread  1                       | Thread 2                | Queue(5) | notEmpty | notFull |
| ------------------------------- | ----------------------- | -------- | -------- | ------- |
| `notFull.acquire`               |                         | 0        | 0        | 4       |
| `enqueue(1)`                    |                         | 1        | 0        | 4       |
| `notEmpty.release`              |                         | 1        | 1        | 4       |
|                                 | `notFull.acquire`       | 1        | 1        | 3       |
|                                 | `enqueue(1)`            | 2        | 1        | 3       |
|                                 | `notEmpty.release`      | 2        | 2        | 3       |
| >>Want to get All<<             | >>Want to enqueue one<< |          |          |         |
| `notEmpty.acquireAll`(gets 2)   |                         | 2        | 0        | 3       |
|                                 | `notFull.acquire`       | 2        | 0        | 2       |
| `dequeueAll`      (dequeue 2)   |                         | 0        | 0        | 2       |
|                                 | `enqueue(1)`            | 1        | 0        | 2       |
| `notFull.releaseAll`(release 2) |                         | 1        | 0        | 4       |
|                                 | `notEmpty.release`      | 1        | 1        | 5       |


But new edge case ...


- Scenario 2: `getAll` and `enqueue` at the same time, but the `release` in `enqueue` happens later

| Thread  1                        | Thread 2                | Thread 3           | Queue(5) | notEmpty | notFull |
| -------------------------------- | ----------------------- | ------------------ | -------- | -------- | ------- |
| `notFull.acquire`                |                         |                    | 0        | 0        | 4       |
| `enqueue(1)`                     |                         |                    | 1        | 0        | 4       |
| `notEmpty.release`               |                         |                    | 1        | 1        | 4       |
|                                  | `notFull.acquire`       |                    | 1        | 1        | 3       |
|                                  | `enqueue(1)`            |                    | 2        | 1        | 3       |
|                                  | `notEmpty.release`      |                    | 2        | 2        | 3       |
| >>Want to get All<<              | >>Want to enqueue one<< | >>Want to get <<   |          |          |         |
|                                  | `notFull.acquire`       |                    | 2        | 2        | 2       |
| `notEmpty.acquireAll`(gets 2)    | `enqueue(1)`            |                    | 3        | 0        | 2       |
|                                  | `notEmpty.release`      |                    | 3        | 1        | 2       |
|                                  |                         | notEmpty.acquire   | 3        | 0        | 2       |
| `notEmpty.permits -= 1`          |                         |                    | 0        | -1       | 2       |
| `dequeueAll`         (dequeue 3) |                         |                    | 0        | -1       | 2       |
| `notFull.releaseAll` (release 3) |                         | dequeue <--problem | 0        | -1       | 5       |
|                                  |                         |                    | 0        | 0        | 5       |