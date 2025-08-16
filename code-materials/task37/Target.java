        @SuppressWarnings("unchecked")
        @Override
        public void onSubscribe(Subscription s) {
            if (SubscriptionHelper.setOnce(this, s)) {
                if (s instanceof QueueSubscription) {
                    QueueSubscription<T> f = (QueueSubscription<T>) s;

                    int m = f.requestFusion(QueueSubscription.ANY | QueueSubscription.BOUNDARY);

                    if (m == QueueSubscription.SYNC) {
                        sourceMode = m;
                        queue = f;
                        done = true;
                        parent.drain();
                        return;
                    }
                    if (m == QueueSubscription.ASYNC) {
                        sourceMode = m;
                        queue = f;
                        s.request(prefetch);
                        return;
                    }
                }

                queue = new SpscArrayQueue<>(prefetch);

                s.request(prefetch);
            }
        }


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


        @Override
        public void run(long n) {
            long emitted = 0L;
            Iterator<T> iterator = this.iterator;
            Subscriber<? super T> downstream = this.downstream;

            for (;;) {

                if (cancelled) {
                    clear();
                    break;
                } else {
                    T next;
                    try {
                        next = Objects.requireNonNull(iterator.next(), "The Stream's Iterator returned a null value");
                    } catch (Throwable ex) {
                        Exceptions.throwIfFatal(ex);
                        downstream.onError(ex);
                        cancelled = true;
                        continue;
                    }

                    downstream.onNext(next);

                    if (cancelled) {
                        continue;
                    }

                    try {
                        if (!iterator.hasNext()) {
                            downstream.onComplete();
                            cancelled = true;
                            continue;
                        }
                    } catch (Throwable ex) {
                        Exceptions.throwIfFatal(ex);
                        downstream.onError(ex);
                        cancelled = true;
                        continue;
                    }

                    if (++emitted != n) {
                        continue;
                    }
                }

                n = get();
                if (emitted == n) {
                    if (compareAndSet(n, 0L)) {
                        break;
                    }
                    n = get();
                }
            }
        }


        @Override
        public void onNext(T t) {
            if (done) {
                return;
            }
            boolean callOnOverflow = false;
            boolean callError = false;
            boolean callDrain = false;
            Deque<T> dq = deque;
            T toDrop = null;
            synchronized (dq) {
               if (dq.size() == bufferSize) {
                   switch (strategy) {
                   case DROP_LATEST:
                       toDrop = dq.pollLast();
                       dq.offer(t);
                       callOnOverflow = true;
                       break;
                   case DROP_OLDEST:
                       toDrop = dq.poll();
                       dq.offer(t);
                       callOnOverflow = true;
                       break;
                   default:
                       // signal error
                       toDrop = t;
                       callError = true;
                       break;
                   }
               } else {
                   dq.offer(t);
                   callDrain = true;
               }
            }

            if (callOnOverflow && onOverflow != null) {
                try {
                    onOverflow.run();
                } catch (Throwable ex) {
                    Exceptions.throwIfFatal(ex);
                    upstream.cancel();
                    onError(ex);
                }
            }

            if (onDropped != null && toDrop != null) {
                try {
                    onDropped.accept(toDrop);
                } catch (Throwable ex) {
                    Exceptions.throwIfFatal(ex);
                    upstream.cancel();
                    onError(ex);
                }
            }

            if (callError) {
                upstream.cancel();
                onError(MissingBackpressureException.createDefault());
            }

            if (callDrain) {
                drain();
            }
        }


        void drain() {
            if (getAndIncrement() != 0) {
                return;
            }

            Subscriber<? super R> a = downstream;
            Iterator<? extends R> iterator = this.it;

            if (outputFused && iterator != null) {
                a.onNext(null);
                a.onComplete();
                return;
            }

            int missed = 1;

            for (;;) {

                if (iterator != null) {
                    long r = requested.get();
                    long e = 0L;

                    if (r == Long.MAX_VALUE) {
                        fastPath(a, iterator);
                        return;
                    }

                    while (e != r) {
                        if (cancelled) {
                            return;
                        }

                        R v;

                        try {
                            v = Objects.requireNonNull(iterator.next(), "The iterator returned a null value");
                        } catch (Throwable ex) {
                            Exceptions.throwIfFatal(ex);
                            a.onError(ex);
                            return;
                        }

                        a.onNext(v);

                        if (cancelled) {
                            return;
                        }

                        e++;

                        boolean b;

                        try {
                            b = iterator.hasNext();
                        } catch (Throwable ex) {
                            Exceptions.throwIfFatal(ex);
                            a.onError(ex);
                            return;
                        }

                        if (!b) {
                            a.onComplete();
                            return;
                        }
                    }

                    if (e != 0L) {
                        BackpressureHelper.produced(requested, e);
                    }
                }

                missed = addAndGet(-missed);
                if (missed == 0) {
                    break;
                }

                if (iterator == null) {
                    iterator = it;
                }
            }
        }


