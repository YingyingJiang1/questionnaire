
        void drain() {
            if (getAndIncrement() != 0) {
                return;
            }

            int missed = 1;
            final Observer<? super R> downstream = this.downstream;
            final AtomicThrowable errors = this.errors;
            final AtomicReference<SwitchMapMaybeObserver<R>> inner = this.inner;

            for (;;) {
                for (;;) {
                    if (cancelled) {
                        return;
                    }

                    // Check if any errors occurred before processing
                    if (errors.get() != null) {
                        if (!delayErrors) {
                            errors.tryTerminateConsumer(downstream);
                            return;
                        }
                    }

                    final boolean done = this.done;
                    final SwitchMapMaybeObserver<R> current = inner.get();
                    final boolean empty = current == null;

                    // Handle terminal state with empty queue
                    if (done && empty) {
                        errors.tryTerminateConsumer(downstream);
                        return;
                    }

                    if (empty || current.item == null) {
                        break;
                    }

                    inner.compareAndSet(current, null);
                    downstream.onNext(current.item);
                }

                missed = addAndGet(-missed);
                if (missed == 0) {
                    break;
                }
            }
        }
