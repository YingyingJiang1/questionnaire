
  public static void drawPlayException(TableElement table, ObjectVO throwableVO) {
    // 执行失败:输出失败状态
    table.row("IS-RETURN", String.valueOf(false));
    table.row("IS-EXCEPTION", String.valueOf(true));

    // 执行失败:输出失败异常信息
    Throwable cause;
    Throwable t = (Throwable) throwableVO.getObject();
    if (t instanceof InvocationTargetException) {
      cause = t.getCause();
    } else {
      cause = t;
    }

    if (throwableVO.needExpand()) {
      table.row("THROW-EXCEPTION", new ObjectView(cause, throwableVO.expandOrDefault()).draw());
    } else {
      StringWriter stringWriter = new StringWriter();
      PrintWriter printWriter = new PrintWriter(stringWriter);
      try {
        cause.printStackTrace(printWriter);
        table.row("THROW-EXCEPTION", stringWriter.toString());
      } finally {
        printWriter.close();
      }
    }
  }
