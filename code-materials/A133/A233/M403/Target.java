    private static List<String> readLines(File batchFile) {
        List<String> list = new ArrayList<String>();
        BufferedReader br = null;
        try {
            br = new BufferedReader(new FileReader(batchFile));
            String line = br.readLine();
            while (line != null) {
                list.add(line);
                line = br.readLine();
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        }
        return list;
    }


    @Override
    public void process(final CommandProcess process) {
        try {
            Instrumentation inst = process.session().getInstrumentation();

            if (VmToolAction.getInstances.equals(action)) {
                if (className == null) {
                    process.end(-1, "The className option cannot be empty!");
                    return;
                }
                ClassLoader classLoader = null;
                if (hashCode != null) {
                    classLoader = ClassLoaderUtils.getClassLoader(inst, hashCode);
                    if (classLoader == null) {
                        process.end(-1, "Can not find classloader with hashCode: " + hashCode + ".");
                        return;
                    }
                }else if ( classLoaderClass != null) {
                    List<ClassLoader> matchedClassLoaders = ClassLoaderUtils.getClassLoaderByClassName(inst,
                            classLoaderClass);
                    if (matchedClassLoaders.size() == 1) {
                        classLoader = matchedClassLoaders.get(0);
                        hashCode = Integer.toHexString(matchedClassLoaders.get(0).hashCode());
                    } else if (matchedClassLoaders.size() > 1) {
                        Collection<ClassLoaderVO> classLoaderVOList = ClassUtils
                                .createClassLoaderVOList(matchedClassLoaders);

                        VmToolModel vmToolModel = new VmToolModel().setClassLoaderClass(classLoaderClass)
                                .setMatchedClassLoaders(classLoaderVOList);
                        process.appendResult(vmToolModel);
                        process.end(-1,
                                "Found more than one classloader by class name, please specify classloader with '-c <classloader hash>'");
                        return;
                    } else {
                        process.end(-1, "Can not find classloader by class name: " + classLoaderClass + ".");
                        return;
                    }
                }else {
                    classLoader = ClassLoader.getSystemClassLoader();
                }

                List<Class<?>> matchedClasses = new ArrayList<Class<?>>(
                        SearchUtils.searchClassOnly(inst, className, false, hashCode));
                int matchedClassSize = matchedClasses.size();
                if (matchedClassSize == 0) {
                    process.end(-1, "Can not find class by class name: " + className + ".");
                    return;
                } else if (matchedClassSize > 1) {
                    process.end(-1, "Found more than one class: " + matchedClasses + ", please specify classloader with '-c <classloader hash>'");
                    return;
                } else {
                    Object[] instances = vmToolInstance().getInstances(matchedClasses.get(0), limit);
                    Object value = instances;
                    if (express != null) {
                        Express unpooledExpress = ExpressFactory.unpooledExpress(classLoader);
                        try {
                            value = unpooledExpress.bind(new InstancesWrapper(instances)).get(express);
                        } catch (ExpressException e) {
                            logger.warn("ognl: failed execute express: " + express, e);
                            process.end(-1, "Failed to execute ognl, exception message: " + e.getMessage()
                                    + ", please check $HOME/logs/arthas/arthas.log for more details. ");
                        }
                    }

                    VmToolModel vmToolModel = new VmToolModel().setValue(new ObjectVO(value, expand));
                    process.appendResult(vmToolModel);
                    process.end();
                }
            } else if (VmToolAction.forceGc.equals(action)) {
                vmToolInstance().forceGc();
                process.write("\n");
                process.end();
                return;
            } else if (VmToolAction.interruptThread.equals(action)) {
                vmToolInstance().interruptSpecialThread(threadId);
                process.write("\n");
                process.end();

                return;
            }

            process.end();
        } catch (Throwable e) {
            logger.error("vmtool error", e);
            process.end(1, "vmtool error: " + e.getMessage());
        }
    }


    @Override
    public void process(CommandProcess process) {
        initTransformer();

        RetransformModel retransformModel = new RetransformModel();
        Instrumentation inst = process.session().getInstrumentation();

        if (this.list) {
            List<RetransformEntry> retransformEntryList = allRetransformEntries();
            retransformModel.setRetransformEntries(retransformEntryList);
            process.appendResult(retransformModel);
            process.end();
            return;
        } else if (this.deleteAll) {
            deleteAllRetransformEntry();
            process.appendResult(retransformModel);
            process.end();
            return;
        } else if (this.delete > 0) {
            deleteRetransformEntry(this.delete);
            process.end();
            return;
        } else if (this.classPattern != null) {
            Set<Class<?>> searchClass = SearchUtils.searchClass(inst, classPattern, false, this.hashCode);
            if (searchClass.isEmpty()) {
                process.end(-1, "These classes are not found in the JVM and may not be loaded: " + classPattern);
                return;
            }

            if (searchClass.size() > limit) {
                process.end(-1, "match classes size: " + searchClass.size() + ", more than limit: " + limit
                        + ", It is recommended to use a more precise class pattern.");
            }
            try {
                inst.retransformClasses(searchClass.toArray(new Class[0]));
                for (Class<?> clazz : searchClass) {
                    retransformModel.addRetransformClass(clazz.getName());
                }
                process.appendResult(retransformModel);
                process.end();
                return;
            } catch (Throwable e) {
                String message = "retransform error! " + e.toString();
                logger.error(message, e);
                process.end(-1, message);
                return;
            }
        }

        for (String path : paths) {
            File file = new File(path);
            if (!file.exists()) {
                process.end(-1, "file does not exist, path:" + path);
                return;
            }
            if (!file.isFile()) {
                process.end(-1, "not a normal file, path:" + path);
                return;
            }
            if (file.length() >= MAX_FILE_SIZE) {
                process.end(-1, "file size: " + file.length() + " >= " + MAX_FILE_SIZE + ", path: " + path);
                return;
            }
        }

        Map<String, byte[]> bytesMap = new HashMap<String, byte[]>();
        for (String path : paths) {
            RandomAccessFile f = null;
            try {
                f = new RandomAccessFile(path, "r");
                final byte[] bytes = new byte[(int) f.length()];
                f.readFully(bytes);

                final String clazzName = readClassName(bytes);

                bytesMap.put(clazzName, bytes);

            } catch (Exception e) {
                logger.warn("load class file failed: " + path, e);
                process.end(-1, "load class file failed: " + path + ", error: " + e);
                return;
            } finally {
                if (f != null) {
                    try {
                        f.close();
                    } catch (IOException e) {
                        // ignore
                    }
                }
            }
        }

        if (bytesMap.size() != paths.size()) {
            process.end(-1, "paths may contains same class name!");
            return;
        }

        List<RetransformEntry> retransformEntryList = new ArrayList<RetransformEntry>();

        List<Class<?>> classList = new ArrayList<Class<?>>();

        for (Class<?> clazz : inst.getAllLoadedClasses()) {
            if (bytesMap.containsKey(clazz.getName())) {

                if (hashCode == null && classLoaderClass != null) {
                    List<ClassLoader> matchedClassLoaders = ClassLoaderUtils.getClassLoaderByClassName(inst,
                            classLoaderClass);
                    if (matchedClassLoaders.size() == 1) {
                        hashCode = Integer.toHexString(matchedClassLoaders.get(0).hashCode());
                    } else if (matchedClassLoaders.size() > 1) {
                        Collection<ClassLoaderVO> classLoaderVOList = ClassUtils
                                .createClassLoaderVOList(matchedClassLoaders);
                        retransformModel.setClassLoaderClass(classLoaderClass)
                                .setMatchedClassLoaders(classLoaderVOList);
                        process.appendResult(retransformModel);
                        process.end(-1,
                                "Found more than one classloader by class name, please specify classloader with '-c <classloader hash>'");
                        return;
                    } else {
                        process.end(-1, "Can not find classloader by class name: " + classLoaderClass + ".");
                        return;
                    }
                }

                ClassLoader classLoader = clazz.getClassLoader();
                if (classLoader != null && hashCode != null
                        && !Integer.toHexString(classLoader.hashCode()).equals(hashCode)) {
                    continue;
                }

                RetransformEntry retransformEntry = new RetransformEntry(clazz.getName(), bytesMap.get(clazz.getName()),
                        hashCode, classLoaderClass);
                retransformEntryList.add(retransformEntry);
                classList.add(clazz);
                retransformModel.addRetransformClass(clazz.getName());

                logger.info("Try retransform class name: {}, ClassLoader: {}", clazz.getName(), clazz.getClassLoader());
            }
        }

        try {
            if (retransformEntryList.isEmpty()) {
                process.end(-1, "These classes are not found in the JVM and may not be loaded: " + bytesMap.keySet());
                return;
            }
            addRetransformEntry(retransformEntryList);

            inst.retransformClasses(classList.toArray(new Class[0]));

            process.appendResult(retransformModel);
            process.end();
        } catch (Throwable e) {
            String message = "retransform error! " + e.toString();
            logger.error(message, e);
            process.end(-1, message);
        }

    }


