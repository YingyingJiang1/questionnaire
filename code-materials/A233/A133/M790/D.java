
@Override
public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
        ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
        
    if (className == null) {
        return null;
    }

    className = className.replace('/', '.');
    final List<RetransformEntry> allRetransformEntries = allRetransformEntries();
    
    // Process entries in reverse order
    final ListIterator<RetransformEntry> listIterator = 
        allRetransformEntries.listIterator(allRetransformEntries.size());
    
    while (listIterator.hasPrevious()) {
        final RetransformEntry retransformEntry = listIterator.previous();
        final int id = retransformEntry.getId();
        boolean updateFlag = false;

        // Check class name match
        if (className.equals(retransformEntry.getClassName())) {
            if (retransformEntry.getClassLoaderClass() != null || 
                retransformEntry.getHashCode() != null) {
                updateFlag = isLoaderMatch(retransformEntry, loader);
            } else {
                updateFlag = true;
            }
        }

        if (updateFlag) {
            logger.info("RetransformCommand match class: {}, id: {}, classLoaderClass: {}, hashCode: {}",
                className, id, retransformEntry.getClassLoaderClass(), retransformEntry.getHashCode());
            retransformEntry.incTransformCount();
            return retransformEntry.getBytes();
        }
    }

    return null;
}
