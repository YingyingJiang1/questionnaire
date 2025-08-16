    @Override
    public void draw(CommandProcess process, PerfCounterModel result) {
        List<PerfCounterVO> perfCounters = result.getPerfCounters();
        boolean details = result.isDetails();
        TableElement table;
        if (details) {
            table = new TableElement(3, 1, 1, 10).leftCellPadding(1).rightCellPadding(1);
            table.row(true, label("Name").style(Decoration.bold.bold()),
                    label("Variability").style(Decoration.bold.bold()),
                    label("Units").style(Decoration.bold.bold()), label("Value").style(Decoration.bold.bold()));
        } else {
            table = new TableElement(4, 6).leftCellPadding(1).rightCellPadding(1);
            table.row(true, label("Name").style(Decoration.bold.bold()),
                    label("Value").style(Decoration.bold.bold()));
        }

        for (PerfCounterVO counter : perfCounters) {
            if (details) {
                table.row(counter.getName(), counter.getVariability(),
                        counter.getUnits(), String.valueOf(counter.getValue()));
            } else {
                table.row(counter.getName(), String.valueOf(counter.getValue()));
            }
        }
        process.write(RenderUtil.render(table, process.width()));
    }


    public void evictSessions() {
        long now = System.currentTimeMillis();
        List<Session> toClose = new ArrayList<Session>();
        for (Session session : sessions.values()) {
            // do not close if there is still job running,
            // e.g. trace command might wait for a long time before condition is met
            //TODO check background job size
            if (now - session.getLastAccessTime() > sessionTimeoutMillis && session.getForegroundJob() == null) {
                toClose.add(session);
            }
            evictConsumers(session);
        }
        for (Session session : toClose) {
            //interrupt foreground job
            Job job = session.getForegroundJob();
            if (job != null) {
                job.interrupt();
            }
            long timeOutInMinutes = sessionTimeoutMillis / 1000 / 60;
            String reason = "session is inactive for " + timeOutInMinutes + " min(s).";
            SharingResultDistributor resultDistributor = session.getResultDistributor();
            if (resultDistributor != null) {
                resultDistributor.appendResult(new MessageModel(reason));
            }
            this.removeSession(session.getSessionId());
            logger.info("Removing inactive session: {}, last access time: {}", session.getSessionId(), session.getLastAccessTime());
        }
    }


    private void drawMBeanAttributes(CommandProcess process, Map<String, List<MBeanAttributeVO>> mbeanAttributeMap) {
        for (Map.Entry<String, List<MBeanAttributeVO>> entry : mbeanAttributeMap.entrySet()) {
            String objectName = entry.getKey();
            List<MBeanAttributeVO> attributeVOList = entry.getValue();

            TableElement table = new TableElement().leftCellPadding(1).rightCellPadding(1);
            table.row(true, "OBJECT_NAME", objectName);
            table.row(true, label("NAME").style(Decoration.bold.bold()),
                    label("VALUE").style(Decoration.bold.bold()));

            for (MBeanAttributeVO attributeVO : attributeVOList) {
                String attributeName = attributeVO.getName();
                String valueStr;
                if (attributeVO.getError() != null) {
                    valueStr = RenderUtil.render(new LabelElement(attributeVO.getError()).style(Decoration.bold_off.fg(Color.red)));
                } else {
                    //convert array to list
                    // TODO support all array type
                    Object value = attributeVO.getValue();
                    if (value instanceof String[]) {
                        value = Arrays.asList((String[]) value);
                    } else if (value instanceof Integer[]) {
                        value = Arrays.asList((Integer[]) value);
                    } else if (value instanceof Long[]) {
                        value = Arrays.asList((Long[]) value);
                    } else if (value instanceof int[]) {
                        value = convertArrayToList((int[]) value);
                    } else if (value instanceof long[]) {
                        value = convertArrayToList((long[]) value);
                    }
                    //to string
                    valueStr = String.valueOf(value);
                }
                table.row(attributeName, valueStr);
            }
            process.write(RenderUtil.render(table, process.width()));
            process.write("\n");
        }
    }


    @Override
    public void process(CommandProcess process) {
        Instrumentation inst = process.session().getInstrumentation();
        ClassLoader classLoader = null;
        if (hashCode != null) {
            classLoader = ClassLoaderUtils.getClassLoader(inst, hashCode);
            if (classLoader == null) {
                process.end(-1, "Can not find classloader with hashCode: " + hashCode + ".");
                return;
            }
        } else if (classLoaderClass != null) {
            List<ClassLoader> matchedClassLoaders = ClassLoaderUtils.getClassLoaderByClassName(inst, classLoaderClass);
            if (matchedClassLoaders.size() == 1) {
                classLoader = matchedClassLoaders.get(0);
            } else if (matchedClassLoaders.size() > 1) {
                Collection<ClassLoaderVO> classLoaderVOList = ClassUtils.createClassLoaderVOList(matchedClassLoaders);
                OgnlModel ognlModel = new OgnlModel()
                        .setClassLoaderClass(classLoaderClass)
                        .setMatchedClassLoaders(classLoaderVOList);
                process.appendResult(ognlModel);
                process.end(-1, "Found more than one classloader by class name, please specify classloader with '-c <classloader hash>'");
                return;
            } else {
                process.end(-1, "Can not find classloader by class name: " + classLoaderClass + ".");
                return;
            }
        } else {
            classLoader = ClassLoader.getSystemClassLoader();
        }

        Express unpooledExpress = ExpressFactory.unpooledExpress(classLoader);
        try {
            // https://github.com/alibaba/arthas/issues/2892
            Object value = unpooledExpress.bind(new Object()).get(express);
            OgnlModel ognlModel = new OgnlModel()
                    .setValue(new ObjectVO(value, expand));
            process.appendResult(ognlModel);
            process.end();
        } catch (ExpressException e) {
            logger.warn("ognl: failed execute express: " + express, e);
            process.end(-1, "Failed to execute ognl, exception message: " + e.getMessage()
                    + ", please check $HOME/logs/arthas/arthas.log for more details. ");
        }
    }


    @Override
    public void draw(CommandProcess process, DashboardModel result) {
        int width = process.width();
        int height = process.height();

        // 上半部分放thread top。下半部分再切分为田字格，其中上面两格放memory, gc的信息。下面两格放tomcat,
        // runtime的信息
        int totalHeight = height - 1;
        int threadTopHeight;
        if (totalHeight <= 24) {
            //总高度较小时取1/2
            threadTopHeight = totalHeight / 2;
        } else {
            //总高度较大时取1/3，但不少于上面的值(24/2=12)
            threadTopHeight = totalHeight / 3;
            if (threadTopHeight < 12) {
                threadTopHeight = 12;
            }
        }
        int lowerHalf = totalHeight - threadTopHeight;

        //Memory至少保留8行, 显示metaspace信息
        int memoryInfoHeight = lowerHalf / 2;
        if (memoryInfoHeight < 8) {
            memoryInfoHeight = Math.min(8, lowerHalf);
        }

        //runtime
        TableElement runtimeInfoTable = drawRuntimeInfo(result.getRuntimeInfo());
        //tomcat
        TableElement tomcatInfoTable = drawTomcatInfo(result.getTomcatInfo());
        int runtimeInfoHeight = Math.max(runtimeInfoTable.getRows().size(), tomcatInfoTable == null ? 0 : tomcatInfoTable.getRows().size());
        if (runtimeInfoHeight < lowerHalf - memoryInfoHeight) {
            //如果runtimeInfo高度有剩余，则增大MemoryInfo的高度
            memoryInfoHeight = lowerHalf - runtimeInfoHeight;
        } else {
            runtimeInfoHeight = lowerHalf - memoryInfoHeight;
        }

        //如果MemoryInfo高度有剩余，则增大ThreadHeight
        int maxMemoryInfoHeight = getMemoryInfoHeight(result.getMemoryInfo());
        memoryInfoHeight = Math.min(memoryInfoHeight, maxMemoryInfoHeight);
        threadTopHeight = totalHeight - memoryInfoHeight - runtimeInfoHeight;

        String threadInfo = ViewRenderUtil.drawThreadInfo(result.getThreads(), width, threadTopHeight);
        String memoryAndGc = drawMemoryInfoAndGcInfo(result.getMemoryInfo(), result.getGcInfos(), width, memoryInfoHeight);
        String runTimeAndTomcat = drawRuntimeInfoAndTomcatInfo(runtimeInfoTable, tomcatInfoTable, width, runtimeInfoHeight);

        process.write(threadInfo + memoryAndGc + runTimeAndTomcat);
    }


    public static String drawThreadInfo(List<ThreadVO> threads, int width, int height) {
        TableElement table = new TableElement(1, 6, 3, 2, 2, 2, 2, 2, 2, 2).overflow(Overflow.HIDDEN).rightCellPadding(1);

        // Header
        table.add(
                new RowElement().style(Decoration.bold.fg(Color.black).bg(Color.white)).add(
                        "ID",
                        "NAME",
                        "GROUP",
                        "PRIORITY",
                        "STATE",
                        "%CPU",
                        "DELTA_TIME",
                        "TIME",
                        "INTERRUPTED",
                        "DAEMON"
                )
        );

        int count = 0;
        for (ThreadVO thread : threads) {
            Color color = colorMapping.get(thread.getState());
            String time = formatTimeMills(thread.getTime());
            String deltaTime = formatTimeMillsToSeconds(thread.getDeltaTime());
            double cpu = thread.getCpu();

            LabelElement daemonLabel = new LabelElement(thread.isDaemon());
            if (!thread.isDaemon()) {
                daemonLabel.setStyle(Style.style(Color.magenta));
            }
            LabelElement stateElement;
            if (thread.getState() != null) {
                stateElement = new LabelElement(thread.getState()).style(color.fg());
            } else {
                stateElement = new LabelElement("-");
            }
            table.row(
                    new LabelElement(thread.getId()),
                    new LabelElement(thread.getName()),
                    new LabelElement(thread.getGroup() != null ? thread.getGroup() : "-"),
                    new LabelElement(thread.getPriority()),
                    stateElement,
                    new LabelElement(cpu),
                    new LabelElement(deltaTime),
                    new LabelElement(time),
                    new LabelElement(thread.isInterrupted()),
                    daemonLabel
            );
            if (++count >= height) {
                break;
            }
        }
        return RenderUtil.render(table, width, height);
    }


