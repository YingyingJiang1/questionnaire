
public static void drawPlayException(TableElement table, ObjectVO throwableVO) {
    table.row("IS-RETURN", "false");
    table.row("IS-EXCEPTION", "true");

    Throwable cause = (throwableVO.getObject() instanceof InvocationTargetException) 
        ? ((Throwable) throwableVO.getObject()).getCause() 
        : (Throwable) throwableVO.getObject();

    if (throwableVO.needExpand()) {
        table.row("THROW-EXCEPTION", new ObjectView(cause, throwableVO.expandOrDefault()).draw());
    } else {
        StringWriter stringWriter = new StringWriter();
        try (PrintWriter printWriter = new PrintWriter(stringWriter)) {
            cause.printStackTrace(printWriter);
            table.row("THROW-EXCEPTION", stringWriter.toString());
        }
    }
}
