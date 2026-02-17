package com.stafflabs.aspect;

import com.stafflabs.config.ResilienceConfig;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * AOP Aspect to conditionally bypass Resilience4j annotations.
 * When resilience is disabled, methods execute directly without any resilience
 * patterns.
 * This aspect must have higher precedence than Resilience4j aspects to
 * intercept first.
 */
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
@Slf4j
public class ResilienceToggleAspect {

        private final ResilienceConfig resilienceConfig;
        private final MeterRegistry meterRegistry;

        /**
         * Intercepts methods annotated with any Resilience4j annotation.
         * If resilience is disabled, bypasses the resilience chain and invokes the
         * method directly.
         */
        @Around("@annotation(io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker) || " +
                        "@annotation(io.github.resilience4j.retry.annotation.Retry) || " +
                        "@annotation(io.github.resilience4j.bulkhead.annotation.Bulkhead) || " +
                        "@annotation(io.github.resilience4j.ratelimiter.annotation.RateLimiter)")
        public Object aroundResilienceAnnotations(ProceedingJoinPoint joinPoint) throws Throwable {

                if (!resilienceConfig.isEnabled()) {
                        // Resilience is disabled - bypass all resilience patterns by directly invoking
                        // the target method
                        log.info("Resilience is Disabled - BYPASSING aspects for method: {}",
                                        joinPoint.getSignature().toShortString());

                        meterRegistry.counter("resilience.toggle.bypassed",
                                        "method", joinPoint.getSignature().toShortString()).increment();

                        try {
                                // Use reflection to call the target method on the actual bean, bypassing all
                                // other aspects in the chain
                                java.lang.reflect.Method method = ((org.aspectj.lang.reflect.MethodSignature) joinPoint
                                                .getSignature()).getMethod();
                                return method.invoke(joinPoint.getTarget(), joinPoint.getArgs());
                        } catch (java.lang.reflect.InvocationTargetException e) {
                                // Important: unwrapping the actual exception thrown by the target method
                                throw e.getCause();
                        }
                }

                // Resilience is enabled - let Resilience4j aspects handle it
                log.trace("Resilience is Enabled - proceeding with resilience patterns for method: {}",
                                joinPoint.getSignature().toShortString());

                meterRegistry.counter("resilience.toggle.active",
                                "method", joinPoint.getSignature().toShortString()).increment();

                // Proceed with the normal chain (Resilience4j aspects will catch this)
                return joinPoint.proceed();
        }
}
