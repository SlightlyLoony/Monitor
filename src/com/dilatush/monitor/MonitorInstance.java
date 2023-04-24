package com.dilatush.monitor;

import com.dilatush.monitor.monitors.AMonitor;

import java.time.Duration;
import java.util.Map;

public record MonitorInstance( Class<? extends AMonitor> monitorClass, Map<String,Object> parameters, Duration interval ) {}
