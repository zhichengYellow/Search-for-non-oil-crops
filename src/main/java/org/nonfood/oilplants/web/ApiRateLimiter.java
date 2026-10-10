package org.nonfood.oilplants.web;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.concurrent.ConcurrentHashMap;

/** Per-process burst limiter for anonymous endpoints; durable email throttles also live in MySQL. */
final class ApiRateLimiter {
    private record Window(ArrayDeque<Long> requests) { }
    private static final ConcurrentHashMap<String,Window> WINDOWS=new ConcurrentHashMap<>();
    private ApiRateLimiter() { }
    static boolean allow(String key,int maximum,Duration duration) {
        long now=System.currentTimeMillis(),cutoff=now-duration.toMillis();
        Window window=WINDOWS.computeIfAbsent(key,k->new Window(new ArrayDeque<>()));
        synchronized(window.requests()) {
            while(!window.requests().isEmpty()&&window.requests().peekFirst()<cutoff)window.requests().removeFirst();
            if(window.requests().size()>=maximum)return false;
            window.requests().addLast(now);
        }
        if(WINDOWS.size()>8192)WINDOWS.entrySet().removeIf(e->{synchronized(e.getValue().requests()){return e.getValue().requests().isEmpty()||e.getValue().requests().peekFirst()<cutoff;}});
        return true;
    }
}
