package com.example;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.sleuth.TraceKeys;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.instrument.async.TraceableScheduledExecutorService;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Hooks;
import reactor.core.scheduler.Schedulers;
import reactor.util.context.Context;

import javax.annotation.PostConstruct;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Supplier;

/**
 * Created by mgrzejszczak.
 */
@Configuration
class TraceReactorConfiguration {

	@Autowired Tracer tracer;
	@Autowired TraceKeys traceKeys;

	@PostConstruct public void foo() {
		//ExceptionUtils.setFail(true);
		Hooks.onNewSubscriber((pub, sub) ->
				new SpanSubscriber(sub, Context.from(sub), tracer, pub.toString()));
		Schedulers.setFactory(new Schedulers.Factory() {
			@Override public ScheduledExecutorService decorateScheduledExecutorService(
					String schedulerType,
					Supplier<? extends ScheduledExecutorService> actual) {
				return new TraceableScheduledExecutorService(actual.get(), tracer,
						traceKeys, (object, defaultValue) -> "foo");
			}
		});
	}
}
