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

### 2.3 

⏱️ Timespend(1000 threads): 0,20s
⏱️ Timespend(5000 threads): 2,01s
