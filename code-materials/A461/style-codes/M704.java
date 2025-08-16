        void drainFused() {
            int missed = 1;
            Subscriber<? super T> a = downstream;
            SimpleQueueWithConsumerIndex<Object> q = queue;

            for (;;) {
                if (cancelled) {
                    q.clear();
                    return;
                }
                Throwable ex = errors.get();
                if (ex != null) {
                    q.clear();
                    a.onError(ex);
                    return;
                }

                boolean d = q.producerIndex() == sourceCount;

                if (!q.isEmpty()) {
                    a.onNext(null);
                }

                if (d) {
                    a.onComplete();
                    return;
                }

                missed = addAndGet(-missed);
                if (missed == 0) {
                    break;
                }
            }

        }
