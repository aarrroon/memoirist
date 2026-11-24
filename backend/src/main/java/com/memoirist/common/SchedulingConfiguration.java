package com.memoirist.common;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Turns on {@code @Scheduled}. Without it the annotation is silently ignored and the job never runs. Every scheduled job
 * is only a trigger that calls a service method (the Recently deleted sweep today; unlock emails and others later), so
 * the work can also be started another way (Q5).
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
class SchedulingConfiguration {}
