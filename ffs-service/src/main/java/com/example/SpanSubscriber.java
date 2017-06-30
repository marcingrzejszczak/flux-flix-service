package com.example;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Tracer;
import reactor.util.Logger;
import reactor.util.Loggers;
import reactor.util.context.Context;
import reactor.util.context.Contextualized;

import java.util.concurrent.atomic.AtomicBoolean;

class SpanSubscriber extends AtomicBoolean
		implements Subscriber<Object>, Subscription, Contextualized {

	static final Logger log = Loggers.getLogger(SpanSubscriber.class);

	private final Span span;
	private final Span rootSpan;
	private final Subscriber<? super Object> subscriber;
	private final Context                    context;
	private final Tracer tracer;
	private       Subscription               s;

	SpanSubscriber(Subscriber<? super Object> subscriber, Context ctx, Tracer tracer,
			String name) {
		this.subscriber = subscriber;
		this.tracer = tracer;
		Span root = ctx.getOrDefault(Span.class, tracer.getCurrentSpan());
		if (log.isDebugEnabled()) {
			log.debug("Span from context [{}]", root);
		}
		this.rootSpan = root != null && root.getSavedSpan() == null ? root : null;
		if (log.isDebugEnabled()) {
			log.debug("Stored context root span [{}]", this.rootSpan);
		}
		this.span = tracer.createSpan(name, root);
		if (log.isDebugEnabled()) {
			log.debug("Created span [{}]", this.span);
		}
		this.context = ctx.put(Span.class, this.span);
	}

	@Override public void onSubscribe(Subscription subscription) {
		if (log.isDebugEnabled()) {
			log.debug("On subscribe");
		}
		this.s = subscription;
		tracer.continueSpan(span);
		if (log.isDebugEnabled()) {
			log.debug("On subscribe - span continued");
		}
		subscriber.onSubscribe(this);
	}

	@Override
	public void request(long n) {
		if (log.isDebugEnabled()) {
			log.debug("Request");
		}
		tracer.continueSpan(span);
		if (log.isDebugEnabled()) {
			log.debug("Request - continued");
		}
		s.request(n);
		// We're in the main thread so we don't want to pollute it with wrong spans
		// that's why we need to detach the current one and continue with its parent
		Span rootSpan = span;
		while (rootSpan != null && rootSpan.getSpanId() != rootSpan.getTraceId()) {
			if (log.isDebugEnabled()) {
				log.debug("Will detach span {}", rootSpan);
			}
			rootSpan = tracer.detach(rootSpan);
		}
		tracer.continueSpan(rootSpan);
		if (log.isDebugEnabled()) {
			log.debug("Request after cleaning. Current span [{}]", tracer.getCurrentSpan());
		}
	}

	@Override
	public void cancel() {
		try {
			if (log.isDebugEnabled()) {
				log.debug("Cancel");
			}
			s.cancel();
		}
		finally {
			cleanup();
		}
	}

	@Override public void onNext(Object o) {
		subscriber.onNext(o);
	}

	@Override public void onError(Throwable throwable) {
		try {
			subscriber.onError(throwable);
		}
		finally {
			cleanup();
		}
	}

	@Override public void onComplete() {
		try {
			subscriber.onComplete();
		}
		finally {
			cleanup();
		}
	}

	void cleanup() {
		if (compareAndSet(false, true)) {
			if (log.isDebugEnabled()) {
				log.debug("Cleaning up");
			}
			if (tracer.getCurrentSpan() != span) {
				if (log.isDebugEnabled()) {
					log.debug("Detaching span");
				}
				tracer.detach(tracer.getCurrentSpan());
				tracer.continueSpan(span);
				if (log.isDebugEnabled()) {
					log.debug("Continuing span");
				}
			}
			if (log.isDebugEnabled()) {
				log.debug("Closing span");
			}
			tracer.close(span);
			if (log.isDebugEnabled()) {
				log.debug("Span closed");
			}
			if (rootSpan != null) {
				tracer.continueSpan(rootSpan);
				tracer.close(rootSpan);
				if (log.isDebugEnabled()) {
					log.debug("Closed root span");
				}
			}
		}
	}

	@Override public Context currentContext() {
		return this.context;
	}
}