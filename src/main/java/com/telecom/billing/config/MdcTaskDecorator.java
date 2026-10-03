package com.telecom.billing.config;

import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

import java.util.Map;

/** Copies the submitting thread's MDC to the worker and restores the worker's own afterwards. */
public class MdcTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        Map<String, String> context = MDC.getCopyOfContextMap();
        return () -> {
            Map<String, String> previous = MDC.getCopyOfContextMap();
            try {
                if (context != null) MDC.setContextMap(context); else MDC.clear();
                runnable.run();
            } finally {
                if (previous != null) MDC.setContextMap(previous); else MDC.clear();
            }
        };
    }
}
