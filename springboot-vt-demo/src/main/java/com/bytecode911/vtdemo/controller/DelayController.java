package com.bytecode911.vtdemo.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Stands in for a slow downstream call (a DB query, another microservice, etc.).
 * Because spring.threads.virtual.enabled=true in application.yml, each incoming
 * request is already handled on a virtual thread by Tomcat -- Thread.sleep() here
 * parks the virtual thread instead of blocking a fixed-size platform pool.
 */
@RestController
public class DelayController {

    @GetMapping("/api/delay/{ms}")
    public String delay(@PathVariable long ms) throws InterruptedException {
        Thread.sleep(ms);
        return "ok";
    }

    @GetMapping("/api/thread-info")
    public String threadInfo() {
        return Thread.currentThread().toString();
    }
}
