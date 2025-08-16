
    @Override
    public byte[] transform(
            ClassLoader loader,
            String className,
            Class<?> classBeingRedefined,
            ProtectionDomain protectionDomain,
            byte[] classfileBuffer)
            throws IllegalClassFormatException {

        if (className == null) {
            return null;
        }

        className = className.replace('/', '.');

        List<RetransformEntry> allRetransformEntries = allRetransformEntries();
        // 倒序，因为要执行的配置生效
        ListIterator<RetransformEntry> listIterator =
                allRetransformEntries.listIterator(allRetransformEntries.size());
        while (listIterator.hasPrevious()) {
            RetransformEntry retransformEntry = listIterator.previous();
            int id = retransformEntry.getId();
            // 判断类名是否一致
            boolean updateFlag = false;
            // 类名一致，则看是否要比较 loader，如果不需要比较 loader，则认为成功
            if (className.equals(retransformEntry.getClassName())) {
                if (retransformEntry.getClassLoaderClass() != null
                        || retransformEntry.getHashCode() != null) {
                    updateFlag = isLoaderMatch(retransformEntry, loader);
                } else {
                    updateFlag = true;
                }
            }

            if (updateFlag) {
                logger.info(
                        "RetransformCommand match class: {}, id: {}, classLoaderClass: {}, hashCode: {}",
                        className,
                        id,
                        retransformEntry.getClassLoaderClass(),
                        retransformEntry.getHashCode());
                retransformEntry.incTransformCount();
                return retransformEntry.getBytes();
            }
        }
        return null;
    }

