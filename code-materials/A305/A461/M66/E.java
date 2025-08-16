
    void drain() {
        if (getAndIncrement() != 0) {
            return;
        }

        int missed = 1;
        final Observer<? super R> downstream = this.downstream;
        final AtomicThrowable errors = this.errors;
        final AtomicReference<SwitchMapMaybeObserver<R>> inner = this.inner;

        while (true) {
            while (true) {
                if (cancelled) {
                    return;
                }

                if (errors.get() != null) {
                    if (!delayErrors) {
                        errors.tryTerminateConsumer(downstream);
                        return;
                    }
                }

                final boolean d = done;
                final SwitchMapMaybeObserver<R> current = inner.get();
                final boolean empty = current == null;

                if (d && empty) {
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

