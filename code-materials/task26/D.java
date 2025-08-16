
void drain() {
    // Return immediately if this drain call is not the first in the sequence
    if (getAndIncrement() != 0) {
        return;
    }

    int missed = 1;
    Observer<? super R> downstream = this.downstream;
    AtomicThrowable errors = this.errors;
    AtomicReference<SwitchMapMaybeObserver<R>> innerObserverRef = this.inner;

    for (;;) {
        // Inner loop processes items until break condition
        for (;;) {
            if (cancelled) {
                return;
            }

            // Check for errors
            Throwable error = errors.get();
            if (error != null) {
                if (!delayErrors) {
                    errors.tryTerminateConsumer(downstream);
                    return;
                }
            }

            boolean isDone = done;
            SwitchMapMaybeObserver<R> currentObserver = innerObserverRef.get();
            boolean isCurrentObserverEmpty = currentObserver == null;

            // Terminate if done and no current observer
            if (isDone && isCurrentObserverEmpty) {
                errors.tryTerminateConsumer(downstream);
                return;
            }

            // Break if no observer or no item to emit
            if (isCurrentObserverEmpty || currentObserver.item == null) {
                break;
            }

            // Clear the current observer to prevent re-emission
            innerObserverRef.compareAndSet(currentObserver, null);

            // Emit the item
            downstream.onNext(currentObserver.item);
        }

        // Reduce the missed count and check if we can exit the outer loop
        missed = addAndGet(-missed);
        if (missed == 0) {
            break;
        }
    }
}
