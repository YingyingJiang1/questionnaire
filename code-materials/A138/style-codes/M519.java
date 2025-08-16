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
